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
