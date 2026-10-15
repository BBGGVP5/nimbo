param([Parameter(Mandatory=$true)][string]$CoreSource)
$ErrorActionPreference='Stop'
$core=[IO.Path]::GetFullPath($CoreSource)
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$manifestPath=Join-Path $core '.build/bin/build-manifest.json'
$binary=Join-Path $core '.build/bin/nimbo-mihomo.exe'
$pinsPath=Join-Path $core 'pins.json'
$manifest=Get-Content -Raw -LiteralPath $manifestPath | ConvertFrom-Json
$pins=Get-Content -Raw -LiteralPath $pinsPath | ConvertFrom-Json
if ($pins.version -ne 'v1.19.32' -or $pins.commit -ne '88dcbf7f1614a67c3b36b848ee3592dfa92ada36' -or $pins.apiVersion -ne 1) {throw 'Unexpected source pin'}
if ($manifest.apiVersion -ne 1 -or $manifest.coreVersion -ne $pins.version -or $manifest.coreCommit -ne $pins.commit -or $manifest.target -ne 'windows/amd64' -or $manifest.toolchain -notmatch 'go1\.27\.1 windows/amd64') {throw 'Unexpected helper build identity'}
foreach ($pair in @(@($binary,$manifest.sha256),@((Join-Path $core 'go.mod'),$manifest.goModSHA256),@((Join-Path $core 'go.sum'),$manifest.goSumSHA256),@($pinsPath,$manifest.pinsSHA256))) {
 if ((Get-FileHash -LiteralPath $pair[0] -Algorithm SHA256).Hash.ToLowerInvariant() -ne $pair[1]) {throw 'Source/build hash mismatch; ask native owner to rebuild, never download a binary'}
}
# Preflight every required artifact before writing any staging output.
foreach ($name in @('Mihomo-LICENSE','Mihomo-README.md','Protobuf-LICENSE','Protobuf-PATENTS')) {
 if (!(Test-Path -LiteralPath (Join-Path $core "licenses/$name") -PathType Leaf)) {throw "Missing upstream notice $name"}
}
if (!(Test-Path -LiteralPath (Join-Path $core 'protobuf-directive.patch') -PathType Leaf)) {throw 'Missing reviewed protobuf directive patch'}
# Final staging requires a build-time source snapshot, not currently edited sources.
if (!$manifest.sourceFiles -or !$manifest.sourceLicenseManifestSHA256) {throw 'Build lacks frozen adapter-source provenance; wait for native final manifest'}
$rootPrefix=$core+[IO.Path]::DirectorySeparatorChar
foreach ($entry in $manifest.sourceFiles) {
 $source=[IO.Path]::GetFullPath((Join-Path $core $entry.path))
 if (!$source.StartsWith($rootPrefix,[StringComparison]::OrdinalIgnoreCase)) {throw 'Source manifest path escapes module root'}
 if ((Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant() -ne $entry.sha256) {throw 'Adapter source changed since build; ask native owner for frozen snapshot'}
}
$licenseManifestPath=Join-Path $core 'source-license-manifest.json'
if ((Get-FileHash -LiteralPath $licenseManifestPath -Algorithm SHA256).Hash.ToLowerInvariant() -ne $manifest.sourceLicenseManifestSHA256) {throw 'Build license inventory hash mismatch'}
$licenseManifest=Get-Content -Raw -LiteralPath $licenseManifestPath | ConvertFrom-Json
foreach ($field in @('goModSHA256','goSumSHA256','pinsSHA256')) {
 if ($licenseManifest.$field -ne $manifest.$field) {throw 'License inventory does not match built dependency lock'}
}
$noticeFiles=@()
foreach ($module in $licenseManifest.modules) {
 foreach ($notice in $module.notices) {
  $source=[IO.Path]::GetFullPath((Join-Path $core $notice.path))
  $licensesRoot=[IO.Path]::GetFullPath((Join-Path $core 'licenses'))+[IO.Path]::DirectorySeparatorChar
  if (!$source.StartsWith($licensesRoot,[StringComparison]::OrdinalIgnoreCase)) {throw 'Notice escapes source licenses directory'}
  if ((Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant() -ne $notice.sha256) {throw 'Transitive notice hash mismatch'}
  $noticeFiles+=,[ordered]@{source=$source;relative=$notice.path.Substring('licenses/'.Length)}
 }
}
$dest=Join-Path $root 'resources/mihomo/windows-x64'
New-Item -ItemType Directory -Force -Path $dest | Out-Null
# This stages only explicitly named build outputs, never the native cache/private data.
Copy-Item -Force -LiteralPath $binary -Destination (Join-Path $dest 'nimbo-mihomo.exe')
Copy-Item -Force -LiteralPath $manifestPath -Destination (Join-Path $dest 'build-manifest.json')
Copy-Item -Force -LiteralPath $pinsPath -Destination (Join-Path $dest 'pins.json')
$notices=Join-Path $root 'resources/mihomo/notices'
New-Item -ItemType Directory -Force -Path $notices | Out-Null
foreach ($name in @('Mihomo-LICENSE','Mihomo-README.md','Protobuf-LICENSE','Protobuf-PATENTS')) {
 $source=Join-Path $core "licenses/$name"
 if (!(Test-Path -LiteralPath $source)) {throw "Missing upstream notice $name"}
 Copy-Item -Force -LiteralPath $source -Destination (Join-Path $notices $name)
}
Copy-Item -Force -LiteralPath (Join-Path $core 'protobuf-directive.patch') -Destination (Join-Path $notices 'protobuf-directive.patch')
foreach ($notice in $noticeFiles) {
 $target=Join-Path $notices $notice.relative
 New-Item -ItemType Directory -Force -Path (Split-Path -Parent $target) | Out-Null
 Copy-Item -Force -LiteralPath $notice.source -Destination $target
}
Copy-Item -Force -LiteralPath $licenseManifestPath -Destination (Join-Path $notices 'source-license-manifest.json')
foreach ($file in @('go.mod','go.sum')) {Copy-Item -Force -LiteralPath (Join-Path $core $file) -Destination (Join-Path $dest $file)}
$sourceDest=Join-Path $root 'resources/mihomo/adapter-source'
foreach ($entry in $manifest.sourceFiles) {
 $target=Join-Path $sourceDest $entry.path
 New-Item -ItemType Directory -Force -Path (Split-Path -Parent $target) | Out-Null
 Copy-Item -Force -LiteralPath (Join-Path $core $entry.path) -Destination $target
 if ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant() -ne $entry.sha256) {throw 'Source changed during staging; do not build this partial output'}
}

if ((Get-FileHash -LiteralPath (Join-Path $dest 'nimbo-mihomo.exe') -Algorithm SHA256).Hash.ToLowerInvariant() -ne $manifest.sha256) {throw 'Binary changed during staging'}
[ordered]@{sourceRoot=$core;target=$manifest.target;binarySHA256=$manifest.sha256;buildSourceFiles=$manifest.sourceFiles;sourceLicenseManifestSHA256=$manifest.sourceLicenseManifestSHA256;releaseGate='Corresponding-source/license review remains mandatory; no deployment performed'} |
 ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $dest 'desktop-stage-receipt.json') -Encoding utf8
# Go's module cache notices are read-only. Do not propagate that attribute into
# Tauri's copy-on-build resources; it breaks the second incremental Windows build.
Get-ChildItem -LiteralPath (Join-Path $root 'resources/mihomo') -File -Recurse | ForEach-Object {
 if ($_.IsReadOnly) {$_.IsReadOnly=$false}
}
Write-Output "Staged verified source-built Mihomo Windows x64: $($manifest.sha256)"

