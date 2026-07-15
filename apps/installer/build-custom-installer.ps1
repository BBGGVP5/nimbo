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

function Get-XrayArchiveName([string]$TargetTriple) {
  switch ($TargetTriple) {
    "x86_64-pc-windows-msvc" { return "Xray-windows-64.zip" }
    "i686-pc-windows-msvc" { return "Xray-windows-32.zip" }
    "aarch64-pc-windows-msvc" { return "Xray-windows-arm64-v8a.zip" }
    default { throw "Unsupported Xray target: $TargetTriple" }
  }
}

function Install-XrayPayload([string]$TargetTriple) {
  $archiveName = Get-XrayArchiveName $TargetTriple
  $downloadBase = "https://github.com/XTLS/Xray-core/releases/latest/download"
  $workDir = Join-Path $repoRoot "target\xray\.downloads\$TargetTriple"
  $archivePath = Join-Path $workDir $archiveName
  $digestPath = "$archivePath.dgst"
  $extractDir = Join-Path $workDir "extract"
  $payloadDir = Join-Path $repoRoot "target\xray\$TargetTriple"
  $payloadPath = Join-Path $payloadDir "xray.exe"

  New-Item -ItemType Directory -Force -Path $workDir, $extractDir, $payloadDir | Out-Null
  Write-Host "Downloading verified Xray payload for $TargetTriple..."
  $headers = @{ "User-Agent" = "Nimbo installer build" }
  Invoke-WebRequest -Uri "$downloadBase/$archiveName" -OutFile $archivePath -Headers $headers
  Invoke-WebRequest -Uri "$downloadBase/$archiveName.dgst" -OutFile $digestPath -Headers $headers

  $digestText = Get-Content -Raw -LiteralPath $digestPath
  $digestMatch = [regex]::Match($digestText, '(?im)^\s*(?:SHA256|SHA2-256)\s*=\s*([0-9a-f]{64})\s*$')
  if (-not $digestMatch.Success) {
    throw "The official Xray checksum file does not contain a SHA-256 digest: $digestPath"
  }

  $expectedHash = $digestMatch.Groups[1].Value.ToLowerInvariant()
  $actualHash = (Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash.ToLowerInvariant()
  if ($actualHash -ne $expectedHash) {
    throw "Xray archive SHA-256 mismatch for $TargetTriple. Expected $expectedHash, got $actualHash."
  }

  Expand-Archive -LiteralPath $archivePath -DestinationPath $extractDir -Force
  foreach ($runtimeFile in @("xray.exe", "geoip.dat", "geosite.dat")) {
    $runtimeAsset = Get-ChildItem -LiteralPath $extractDir -Recurse -File -Filter $runtimeFile |
      Select-Object -First 1
    if (-not $runtimeAsset) {
      throw "The verified Xray archive does not contain ${runtimeFile}: $archiveName"
    }
    Copy-Item -LiteralPath $runtimeAsset.FullName -Destination (Join-Path $payloadDir $runtimeFile) -Force
  }
  Write-Host "Embedded Xray runtime: $payloadPath"
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
$script:msvcToolchainCache = @{}

function Test-RustTargetInstalled([string]$TargetTriple) {
  $installed = & rustup target list --installed 2>$null
  if ($LASTEXITCODE -ne 0) { return $true }  # rustup unavailable; let cargo decide
  foreach ($line in $installed) {
    if ($line.Trim() -eq $TargetTriple) { return $true }
  }
  return $false
}

function Get-MsvcToolchain([string]$TargetTriple) {
  if ($script:msvcToolchainCache.ContainsKey($TargetTriple)) {
    return $script:msvcToolchainCache[$TargetTriple]
  }

  $arch = Get-MsvcArchFolder $TargetTriple
  if (-not $arch) { return $null }

  $vswhere = Join-Path ${env:ProgramFiles(x86)} "Microsoft Visual Studio\Installer\vswhere.exe"
  if (-not (Test-Path -LiteralPath $vswhere)) {
    return $null
  }

  $components = @("Microsoft.VisualStudio.Component.VC.Tools.x86.x64")
  if ($arch -eq "arm64") {
    $components += "Microsoft.VisualStudio.Component.VC.Tools.ARM64"
  }
  $vsRoot = & $vswhere -latest -products * -requires $components -property installationPath 2>$null |
    Select-Object -First 1
  if ([string]::IsNullOrWhiteSpace($vsRoot)) { return $null }
  $vsRoot = $vsRoot.Trim()

  $vcvarsall = Join-Path $vsRoot "VC\Auxiliary\Build\vcvarsall.bat"
  if (-not (Test-Path -LiteralPath $vcvarsall)) { return $null }

  $msvcRoot = Join-Path $vsRoot "VC\Tools\MSVC"
  if (-not (Test-Path -LiteralPath $msvcRoot)) { return $null }

  $clangDir = $null
  if ($arch -eq "arm64") {
    $clangCandidates = @(
      (Join-Path $vsRoot "VC\Tools\Llvm\x64\bin\clang.exe"),
      (Join-Path $env:ProgramFiles "LLVM\bin\clang.exe")
    )
    foreach ($clang in $clangCandidates) {
      if (Test-Path -LiteralPath $clang) {
        $clangDir = Split-Path -Parent $clang
        break
      }
    }
    if (-not $clangDir) { return $null }
  }

  $versions = Get-ChildItem -LiteralPath $msvcRoot -Directory -ErrorAction SilentlyContinue |
    Sort-Object Name -Descending
  foreach ($ver in $versions) {
    $candidate = Join-Path $ver.FullName "bin\Hostx64\$arch\link.exe"
    if (Test-Path -LiteralPath $candidate) {
      $vcvarsArgument = switch ($arch) {
        "x64" { "x64" }
        "x86" { "x64_x86" }
        "arm64" { "x64_arm64" }
      }
      $toolchain = [PSCustomObject]@{
        VcVarsAll = $vcvarsall
        Argument = $vcvarsArgument
        ClangDir = $clangDir
      }
      $script:msvcToolchainCache[$TargetTriple] = $toolchain
      return $toolchain
    }
  }
  return $null
}

function Invoke-MsvcCommand([string]$TargetTriple, [string]$WorkingDirectory, [string]$CommandLine) {
  $toolchain = Get-MsvcToolchain $TargetTriple
  if (-not $toolchain) {
    throw "MSVC toolchain for $TargetTriple is unavailable."
  }

  $clangPath = if ($toolchain.ClangDir) { "set `"PATH=$($toolchain.ClangDir);%PATH%`" && " } else { "" }
  $fullCommand = "call `"$($toolchain.VcVarsAll)`" $($toolchain.Argument) >nul && cd /d `"$WorkingDirectory`" && $clangPath$CommandLine"
  & cmd.exe /d /s /c $fullCommand
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

  $msvcToolchain = Get-MsvcToolchain $TargetTriple
  if (-not $msvcToolchain) {
    $arch = Get-MsvcArchFolder $TargetTriple
    $required = if ($arch -eq "arm64") { "MSVC C++ ARM64 build tools and LLVM clang" } else { "MSVC C++ $arch build tools" }
    Write-Warning "Skipping $TargetTriple — toolchain not found. Install $required via the Visual Studio Installer."
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
  if (-not (Test-Path -LiteralPath (Join-Path $uiDir "node_modules"))) {
    & npm ci
    if ($LASTEXITCODE -ne 0) { throw "npm ci failed for Nimbo UI." }
  }
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
  $cargoExe = (Get-Command cargo -CommandType Application -ErrorAction Stop).Source
  foreach ($targetTriple in $Target) {
    Write-Host "Building app payload for $targetTriple..."

    # `--features custom-protocol` is required for the Tauri runtime to use the
    # embedded frontend assets. Without it, `tauri::generate_context!` compiles
    # nimbo-ui in dev mode and the installed app tries to load
    # http://127.0.0.1:1420 (ERR_CONNECTION_REFUSED). `cargo tauri build`
    # passes this automatically; this raw `cargo build` does not.
    Invoke-MsvcCommand $targetTriple $repoRoot "`"$cargoExe`" build -p nimbo-ui --release --features custom-protocol --target $targetTriple"
    if ($LASTEXITCODE -ne 0) { throw "cargo build -p nimbo-ui failed for $targetTriple." }

    Invoke-MsvcCommand $targetTriple $repoRoot "`"$cargoExe`" build -p nimbo-svc --release --target $targetTriple"
    if ($LASTEXITCODE -ne 0) { throw "cargo build -p nimbo-svc failed for $targetTriple." }
  }
} finally {
  Pop-Location
}

foreach ($targetTriple in $Target) {
  Install-XrayPayload $targetTriple
}

$createdInstallers = @()

foreach ($targetTriple in $Target) {
  Push-Location $installerDir
  try {
    if (-not (Test-Path -LiteralPath (Join-Path $installerDir "node_modules"))) {
      & npm ci
      if ($LASTEXITCODE -ne 0) { throw "npm ci failed for custom installer." }
    }

    Write-Host "Building custom installer for $targetTriple..."
    $tauriCmd = Join-Path $installerDir "node_modules\.bin\tauri.cmd"
    if (-not (Test-Path -LiteralPath $tauriCmd)) {
      throw "Tauri CLI was not installed: $tauriCmd"
    }
    Invoke-MsvcCommand $targetTriple $installerDir "call `"$tauriCmd`" build --no-bundle --target $targetTriple"
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
