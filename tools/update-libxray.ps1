[CmdletBinding()]
param(
    [string]$Version,
    [string]$AndroidProjectPath = (Join-Path $PSScriptRoot "..\apps\android")
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Get-LibXrayRelease {
    Invoke-RestMethod -Headers @{ "User-Agent" = "Nimbo-libXray-updater" } `
        -Uri "https://api.github.com/repos/XTLS/libXray/releases/latest"
}

$release = if ([string]::IsNullOrWhiteSpace($Version)) {
    Get-LibXrayRelease
} else {
    $normalized = $Version.Trim().TrimStart("v")
    Invoke-RestMethod -Headers @{ "User-Agent" = "Nimbo-libXray-updater" } `
        -Uri "https://api.github.com/repos/XTLS/libXray/releases/tags/v$normalized"
}

if ($release.prerelease -or $release.draft) {
    throw "Разрешены только стабильные опубликованные релизы libXray."
}

$asset = $release.assets | Where-Object { $_.name -eq "libxray-android.zip" } | Select-Object -First 1
if ($null -eq $asset) {
    throw "В релизе $($release.tag_name) нет libxray-android.zip."
}

$destination = Join-Path $AndroidProjectPath "app\libs\libxray.aar"
if (-not (Test-Path (Split-Path -Parent $destination))) {
    throw "Не найдена Android-папка: $AndroidProjectPath"
}

$workDirectory = Join-Path $env:TEMP "nimbo-libxray-$($release.tag_name)-$PID"
try {
    New-Item -ItemType Directory -Path $workDirectory | Out-Null
    $archive = Join-Path $workDirectory "libxray-android.zip"
    $unpacked = Join-Path $workDirectory "unpacked"

    Invoke-WebRequest -Headers @{ "User-Agent" = "Nimbo-libXray-updater" } `
        -Uri $asset.browser_download_url `
        -OutFile $archive
    Expand-Archive -LiteralPath $archive -DestinationPath $unpacked

    $aars = @(Get-ChildItem -LiteralPath $unpacked -Recurse -Filter "*.aar")
    if ($aars.Count -ne 1) {
        throw "Ожидался один AAR, найдено: $($aars.Count)."
    }

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::OpenRead($aars[0].FullName)
    try {
        foreach ($required in @("classes.jar", "jni/arm64-v8a/libgojni.so", "jni/armeabi-v7a/libgojni.so")) {
            if (-not ($zip.Entries.FullName -contains $required)) {
                throw "AAR не содержит обязательный файл: $required"
            }
        }
    } finally {
        $zip.Dispose()
    }

    Copy-Item -LiteralPath $aars[0].FullName -Destination $destination -Force
    $hash = (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash
    [PSCustomObject]@{
        Version = $release.tag_name
        Destination = $destination
        Sha256 = $hash
    } | Format-List
} finally {
    if (Test-Path $workDirectory) {
        Remove-Item -LiteralPath $workDirectory -Recurse -Force
    }
}
