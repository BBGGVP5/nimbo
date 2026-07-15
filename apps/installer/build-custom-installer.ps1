param(
  [string[]]$Target = @("x86_64-pc-windows-msvc"),
  [switch]$All
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Resolve-Path (Join-Path $scriptDir "..\..")
$uiDir = Join-Path $repoRoot "apps\ui"
$installerDir = Join-Path $repoRoot "apps\installer"
$outDir = Join-Path $repoRoot "target\release\bundle\custom\windows"
$installerConfig = Get-Content -Raw -LiteralPath (Join-Path $installerDir "src-tauri\tauri.conf.json") | ConvertFrom-Json
$version = $installerConfig.version

if ($All) {
  $Target = @(
    "x86_64-pc-windows-msvc",
    "i686-pc-windows-msvc",
    "aarch64-pc-windows-msvc"
  )
} else {
  $Target = @(
    $Target |
      ForEach-Object { $_ -split "," } |
      ForEach-Object { $_.Trim() } |
      Where-Object { $_ }
  )
}

function Get-ArchSuffix([string]$TargetTriple) {
  switch ($TargetTriple) {
    "x86_64-pc-windows-msvc" { return "x64" }
    "i686-pc-windows-msvc" { return "x86" }
    "aarch64-pc-windows-msvc" { return "arm64" }
    default { return ($TargetTriple -replace "-pc-windows-msvc$", "") }
  }
}

function Get-MsvcArchFolder([string]$TargetTriple) {
  switch ($TargetTriple) {
    "x86_64-pc-windows-msvc" { return "x64" }
    "i686-pc-windows-msvc" { return "x86" }
    "aarch64-pc-windows-msvc" { return "arm64" }
    default { return $null }
  }
}

# Cache: triple -> $true if both Rust target + MSVC linker are available.
$script:toolchainCache = @{}

function Test-RustTargetInstalled([string]$TargetTriple) {
  $installed = & rustup target list --installed 2>$null
  if ($LASTEXITCODE -ne 0) { return $true }  # rustup unavailable; let cargo decide
  foreach ($line in $installed) {
    if ($line.Trim() -eq $TargetTriple) { return $true }
  }
  return $false
}

function Test-MsvcLinkerAvailable([string]$TargetTriple) {
  $arch = Get-MsvcArchFolder $TargetTriple
  if (-not $arch) { return $true }

  $vswhere = Join-Path ${env:ProgramFiles(x86)} "Microsoft Visual Studio\Installer\vswhere.exe"
  if (-not (Test-Path -LiteralPath $vswhere)) {
    # No vswhere -> can't probe; assume yes and let the build fail with a real error.
    return $true
  }

  $vsRoot = & $vswhere -latest -products * -property installationPath 2>$null
  if (-not $vsRoot) { return $true }

  $msvcRoot = Join-Path $vsRoot "VC\Tools\MSVC"
  if (-not (Test-Path -LiteralPath $msvcRoot)) { return $true }

  $versions = Get-ChildItem -LiteralPath $msvcRoot -Directory -ErrorAction SilentlyContinue |
    Sort-Object Name -Descending
  foreach ($ver in $versions) {
    $candidate = Join-Path $ver.FullName "bin\Hostx64\$arch\link.exe"
    if (Test-Path -LiteralPath $candidate) { return $true }
    $candidateX86 = Join-Path $ver.FullName "bin\Hostx86\$arch\link.exe"
    if (Test-Path -LiteralPath $candidateX86) { return $true }
  }
  return $false
}

function Test-TargetSupported([string]$TargetTriple) {
  if ($script:toolchainCache.ContainsKey($TargetTriple)) {
    return $script:toolchainCache[$TargetTriple]
  }

  $rustOk = Test-RustTargetInstalled $TargetTriple
  if (-not $rustOk) {
    Write-Warning "Skipping $TargetTriple — Rust target not installed. Run: rustup target add $TargetTriple"
    $script:toolchainCache[$TargetTriple] = $false
    return $false
  }

  $linkerOk = Test-MsvcLinkerAvailable $TargetTriple
  if (-not $linkerOk) {
    $arch = Get-MsvcArchFolder $TargetTriple
    Write-Warning "Skipping $TargetTriple — MSVC linker for $arch not found. Install it via the Visual Studio Installer (Individual components -> MSVC v143 - VS 2022 C++ $arch build tools)."
    $script:toolchainCache[$TargetTriple] = $false
    return $false
  }

  $script:toolchainCache[$TargetTriple] = $true
  return $true
}

# Filter out unsupported targets up front so we don't waste time / produce confusing errors.
$Target = @($Target | Where-Object { Test-TargetSupported $_ })
if ($Target.Count -eq 0) {
  throw "No supported build targets available. Install the missing Rust targets / MSVC components and retry."
}

New-Item -ItemType Directory -Force -Path $outDir | Out-Null

Push-Location $uiDir
try {
  & npm run build
  if ($LASTEXITCODE -ne 0) { throw "npm run build failed for Nimbo UI." }
} finally {
  Pop-Location
}

# Touch tauri.conf.json so tauri-build re-embeds the latest dist/ even if
# cargo's incremental cache thinks the crate is up to date. Without this,
# nimbo-ui.exe can ship with a stale (or empty) frontend, causing the
# installed Nimbo to hit ERR_CONNECTION_REFUSED on 127.0.0.1 because the
# WebView falls back to the dev URL.
$tauriConfPath = Join-Path $uiDir "src-tauri\tauri.conf.json"
(Get-Item -LiteralPath $tauriConfPath).LastWriteTime = Get-Date
$distIndex = Join-Path $uiDir "dist\index.html"
if (Test-Path -LiteralPath $distIndex) {
  (Get-Item -LiteralPath $distIndex).LastWriteTime = Get-Date
}

Push-Location $repoRoot
try {
  foreach ($targetTriple in $Target) {
    Write-Host "Building app payload for $targetTriple..."

    # `--features custom-protocol` is required for the Tauri runtime to use the
    # embedded frontend assets. Without it, `tauri::generate_context!` compiles
    # nimbo-ui in dev mode and the installed app tries to load
    # http://127.0.0.1:1420 (ERR_CONNECTION_REFUSED). `cargo tauri build`
    # passes this automatically; this raw `cargo build` does not.
    & cargo build -p nimbo-ui --release --features custom-protocol --target $targetTriple
    if ($LASTEXITCODE -ne 0) { throw "cargo build -p nimbo-ui failed for $targetTriple." }

    & cargo build -p nimbo-svc --release --target $targetTriple
    if ($LASTEXITCODE -ne 0) { throw "cargo build -p nimbo-svc failed for $targetTriple." }
  }
} finally {
  Pop-Location
}

$createdInstallers = @()

foreach ($targetTriple in $Target) {
  Push-Location $installerDir
  try {
    if (-not (Test-Path -LiteralPath (Join-Path $installerDir "node_modules"))) {
      & npm install
      if ($LASTEXITCODE -ne 0) { throw "npm install failed for custom installer." }
    }

    Write-Host "Building custom installer for $targetTriple..."
    & npx tauri build --no-bundle --target $targetTriple
    if ($LASTEXITCODE -ne 0) { throw "tauri build failed for custom installer $targetTriple." }
  } finally {
    Pop-Location
  }

  $built = Join-Path $repoRoot "target\$targetTriple\release\nimbo-installer.exe"
  if (-not (Test-Path -LiteralPath $built)) {
    throw "Custom installer binary was not found: $built"
  }

  $archSuffix = Get-ArchSuffix $targetTriple
  $outFile = Join-Path $outDir "NimboSetup_${version}_${archSuffix}.exe"
  Copy-Item -Force -LiteralPath $built -Destination $outFile
  $createdInstallers += $outFile
}

Write-Host "Custom installer created:"
$createdInstallers | ForEach-Object { Write-Host " - $_" }
