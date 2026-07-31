param(
    [string]$Version,
    [string]$AndroidProjectPath = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Resolve-FullPath([string]$Path) {
    [IO.Path]::GetFullPath($Path).TrimEnd([IO.Path]::DirectorySeparatorChar)
}

$projectRoot = Resolve-FullPath $AndroidProjectPath
$targetPath = Resolve-FullPath (Join-Path $projectRoot 'app\libs\libxray.aar')
$allowedRoot = Resolve-FullPath (Join-Path $projectRoot 'app\libs')
if (-not $targetPath.StartsWith("$allowedRoot$([IO.Path]::DirectorySeparatorChar)", [StringComparison]::OrdinalIgnoreCase)) {
    throw "The libXray target is outside app\libs: $targetPath"
}
if (-not (Test-Path -LiteralPath $allowedRoot -PathType Container)) {
    throw "Android app\libs directory was not found: $allowedRoot"
}

$tag = if ([string]::IsNullOrWhiteSpace($Version)) {
    $null
} elseif ($Version.StartsWith('v', [StringComparison]::OrdinalIgnoreCase)) {
    $Version
} else {
    "v$Version"
}
$headers = @{ 'User-Agent' = 'Nimbo-libXray-updater' }
$releaseUri = if ($tag) {
    "https://api.github.com/repos/XTLS/libXray/releases/tags/$tag"
} else {
    'https://api.github.com/repos/XTLS/libXray/releases/latest'
}
$release = Invoke-RestMethod -Headers $headers -Uri $releaseUri
if ($release.draft -or $release.prerelease) {
    throw "Refusing a draft or prerelease libXray build: $($release.tag_name)"
}

$assets = @($release.assets | Where-Object name -eq 'libxray-android.zip')
if ($assets.Count -ne 1) {
    throw "Expected exactly one libxray-android.zip asset, found $($assets.Count)."
}
$asset = $assets[0]
$digest = [string]$asset.digest
if ($digest -notmatch '^sha256:([0-9a-fA-F]{64})$') {
    throw "The official GitHub asset has no valid SHA-256 digest: $digest"
}
$expectedArchiveHash = $Matches[1].ToUpperInvariant()

$temporaryRoot = Resolve-FullPath (Join-Path ([IO.Path]::GetTempPath()) ("nimbo-libxray-" + [guid]::NewGuid().ToString('N')))
$systemTemporaryRoot = Resolve-FullPath ([IO.Path]::GetTempPath())
if (-not $temporaryRoot.StartsWith("$systemTemporaryRoot$([IO.Path]::DirectorySeparatorChar)", [StringComparison]::OrdinalIgnoreCase)) {
    throw "Unsafe temporary directory: $temporaryRoot"
}

try {
    $archivePath = Join-Path $temporaryRoot 'libxray-android.zip'
    $extractPath = Join-Path $temporaryRoot 'extract'
    New-Item -ItemType Directory -Path $temporaryRoot, $extractPath | Out-Null

    Invoke-WebRequest -Headers $headers -Uri $asset.browser_download_url -OutFile $archivePath
    $actualArchiveHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $archivePath).Hash
    if ($actualArchiveHash -ne $expectedArchiveHash) {
        throw "libXray archive SHA-256 mismatch. Expected $expectedArchiveHash, got $actualArchiveHash."
    }

    Expand-Archive -LiteralPath $archivePath -DestinationPath $extractPath
    $aars = @(Get-ChildItem -LiteralPath $extractPath -Recurse -File -Filter '*.aar')
    if ($aars.Count -ne 1) {
        throw "Expected exactly one AAR in the verified archive, found $($aars.Count)."
    }

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $aarArchive = [IO.Compression.ZipFile]::OpenRead($aars[0].FullName)
    try {
        $entries = @($aarArchive.Entries | ForEach-Object { $_.FullName.Replace('\', '/') })
        $requiredEntries = @(
            'classes.jar',
            'jni/armeabi-v7a/libgojni.so',
            'jni/arm64-v8a/libgojni.so',
            'jni/x86/libgojni.so',
            'jni/x86_64/libgojni.so'
        )
        foreach ($requiredEntry in $requiredEntries) {
            if ($entries -cnotcontains $requiredEntry) {
                throw "Verified libXray AAR is missing $requiredEntry."
            }
        }
    } finally {
        $aarArchive.Dispose()
    }

    $oldHash = if (Test-Path -LiteralPath $targetPath -PathType Leaf) {
        (Get-FileHash -Algorithm SHA256 -LiteralPath $targetPath).Hash
    } else {
        $null
    }
    $newHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $aars[0].FullName).Hash
    $partialPath = "$targetPath.partial"
    $backupPath = "$targetPath.backup"
    Copy-Item -LiteralPath $aars[0].FullName -Destination $partialPath -Force
    if ((Get-FileHash -Algorithm SHA256 -LiteralPath $partialPath).Hash -ne $newHash) {
        throw 'The staged libXray AAR hash changed during copy.'
    }

    if (Test-Path -LiteralPath $targetPath -PathType Leaf) {
        [IO.File]::Replace($partialPath, $targetPath, $backupPath, $true)
        Remove-Item -LiteralPath $backupPath -Force
    } else {
        Move-Item -LiteralPath $partialPath -Destination $targetPath
    }

    Write-Output "libXray release: $($release.tag_name)"
    Write-Output "Official archive SHA-256: $actualArchiveHash"
    if ($oldHash) { Write-Output "Previous AAR SHA-256: $oldHash" }
    Write-Output "Installed AAR SHA-256: $newHash"
    Write-Output 'Update app BuildConfig.LIBXRAY_VERSION if the release tag changed, then run the Android tests and build.'
} finally {
    if (Test-Path -LiteralPath $temporaryRoot) {
        Remove-Item -LiteralPath $temporaryRoot -Recurse -Force
    }
}
