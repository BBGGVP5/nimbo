param(
  [string[]]$Targets = @("x86_64-pc-windows-msvc", "i686-pc-windows-msvc", "aarch64-pc-windows-msvc"),
  [switch]$Strict
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$appDir = Resolve-Path (Join-Path $scriptDir "..\..")
$repoRoot = Resolve-Path (Join-Path $scriptDir "..\..\..\..")
$installerDir = Join-Path $repoRoot "target\release\bundle\nsis"
$installerScript = Join-Path $scriptDir "nimbo-installer.nsi"

function Find-CommandPath([string]$Name, [string[]]$Candidates) {
  $command = (Get-Command $Name -ErrorAction SilentlyContinue).Source
  if ($command) {
    return $command
  }

  return $Candidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
}

function Resolve-CurrentTarget {
  $arch = $env:PROCESSOR_ARCHITECTURE
  if ($arch -eq "ARM64") {
    return "aarch64-pc-windows-msvc"
  }
  if ($arch -eq "x86") {
    return "i686-pc-windows-msvc"
  }
  return "x86_64-pc-windows-msvc"
}

function Resolve-ArchName([string]$Target) {
  switch ($Target) {
    "x86_64-pc-windows-msvc" { return "x64" }
    "i686-pc-windows-msvc" { return "x86" }
    "aarch64-pc-windows-msvc" { return "arm64" }
    default { return ($Target -replace "[^A-Za-z0-9_-]", "_") }
  }
}

function Resolve-ReleaseExe([string]$Target) {
  $targetExe = Join-Path $repoRoot "target\$Target\release\nimbo-ui.exe"
  if (Test-Path -LiteralPath $targetExe) {
    return $targetExe
  }

  $hostExe = Join-Path $repoRoot "target\release\nimbo-ui.exe"
  if (Test-Path -LiteralPath $hostExe) {
    return $hostExe
  }

  throw "Release executable was not found for target ${Target}."
}

if ($Targets.Count -eq 1 -and $Targets[0].ToLowerInvariant() -eq "current") {
  $Targets = @(Resolve-CurrentTarget)
}

$makensis = Find-CommandPath "makensis.exe" @(
  "C:\Program Files (x86)\NSIS\Bin\makensis.exe",
  "C:\Program Files\NSIS\Bin\makensis.exe",
  "C:\Program Files (x86)\NSIS\makensis.exe",
  "C:\Program Files\NSIS\makensis.exe"
)

if (-not $makensis) {
  throw "NSIS makensis.exe was not found. Install it with: winget install --id NSIS.NSIS -e"
}

$tauriCli = Join-Path $appDir "node_modules\.bin\tauri.cmd"
if (-not (Test-Path -LiteralPath $tauriCli)) {
  throw "Tauri CLI was not found. Run npm install in apps\ui first."
}

New-Item -ItemType Directory -Force -Path $installerDir | Out-Null

$created = @()
$failed = @()

foreach ($target in $Targets) {
  $target = $target.Trim()
  if (-not $target) {
    continue
  }

  $arch = Resolve-ArchName $target
  $outputFile = Join-Path $installerDir "Nimbo_0.1.0_${arch}-setup.exe"

  Write-Host ""
  Write-Host "Building Nimbo for $target ($arch)..."

  $rustup = (Get-Command "rustup.exe" -ErrorAction SilentlyContinue).Source
  if ($rustup) {
    & $rustup target add $target
    if ($LASTEXITCODE -ne 0) {
      $message = "rustup target add failed for $target."
      if ($Strict) { throw $message }
      Write-Warning $message
      $failed += $target
      continue
    }
  }

  Push-Location $appDir
  try {
    & $tauriCli build --no-bundle --target $target
    if ($LASTEXITCODE -ne 0) {
      throw "Tauri build failed with exit code $LASTEXITCODE."
    }
  } catch {
    if ($Strict) {
      throw
    }
    Write-Warning "Skipped ${target}: $($_.Exception.Message)"
    $failed += $target
    continue
  } finally {
    Pop-Location
  }

  $releaseExe = Resolve-ReleaseExe $target

  & $makensis `
    "/INPUTCHARSET" `
    "UTF8" `
    "/DPRODUCT_ARCH=$arch" `
    "/DRELEASE_EXE=$releaseExe" `
    "/DOUT_FILE=$outputFile" `
    $installerScript

  if ($LASTEXITCODE -ne 0) {
    $message = "NSIS failed for $target with exit code $LASTEXITCODE."
    if ($Strict) { throw $message }
    Write-Warning $message
    $failed += $target
    continue
  }

  $created += $outputFile
  Write-Host "Installer created: $outputFile"
}

if ($created.Count -eq 0) {
  throw "No installers were created."
}

Write-Host ""
Write-Host "Created installers:"
$created | ForEach-Object { Write-Host " - $_" }

if ($failed.Count -gt 0) {
  Write-Warning "Some targets were skipped: $($failed -join ', ')"
  Write-Warning "Install the matching Rust target and MSVC build tools, then rerun with -Strict if you want failures to stop the build."
}
