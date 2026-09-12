param([switch]$Apply)
$ErrorActionPreference = 'Stop'
$mobile = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$desktop = 'C:/Users/Danila/Desktop/nimbo-app-main'
$checkout = Join-Path $mobile '.codex-tmp/release-1.2.0'
if (!(Test-Path -LiteralPath (Join-Path $checkout '.git'))) { throw 'Expected release checkout' }
$sources = @(
    @{ root=$mobile; dirs=@('app/src','app/libs','shared/src','gradle','iosApp/Nimbo','iosApp/Shared','iosApp/PacketTunnel','iosApp/ControlWidget','iosApp/GoBridge','iosApp/Tests','tools/native/awg','tools/native/awg-core','tools/release','scripts/ci','scripts/ios'); files=@('app/build.gradle.kts','app/proguard-rules.pro','shared/build.gradle.kts','iosApp/project.yml','build.gradle.kts','settings.gradle.kts','gradle.properties','gradlew','gradlew.bat','RELEASE_NOTES_1.2.0.md') },
    @{ root=$desktop; dirs=@('apps/ui','apps/service','apps/installer','crates'); files=@('Cargo.toml','Cargo.lock') }
)
$count = 0
foreach ($source in $sources) {
    $paths = @($source.files)
    foreach ($dir in $source.dirs) {
        $paths += Get-ChildItem -LiteralPath (Join-Path $source.root $dir) -Recurse -File | ForEach-Object { [IO.Path]::GetRelativePath($source.root,$_.FullName).Replace('\','/') }
    }
    foreach ($path in ($paths | Sort-Object -Unique)) {
        if ($path -match '(^|/)(build|target|node_modules|dist|gen|\.gradle|\.git|\.codex-logs|__pycache__)/|(^|/)(local\.properties|signing\.properties)$|\.(jks|keystore|p12|mobileprovision|log|partial|pyc)$|(^|/)\.env($|\.)') { continue }
        # Desktop native AWG artifacts are rebuilt by CI from the pinned Go sources.
        if ($path -match '^apps/ui/src-tauri/resources/awg/.+/(nimbo-awg|nimbo-awg\.exe)(\.manifest\.json)?$') { continue }
        $src = Join-Path $source.root $path
        if (!(Test-Path -LiteralPath $src -PathType Leaf)) { throw "Missing source: $path" }
        $dst = Join-Path $checkout $path
        if ((Test-Path -LiteralPath $dst -PathType Leaf) -and (Get-FileHash -LiteralPath $src).Hash -eq (Get-FileHash -LiteralPath $dst).Hash) { continue }
        $count++
        Write-Output $path
        if ($Apply) {
            New-Item -ItemType Directory -Path (Split-Path -Parent $dst) -Force | Out-Null
            Copy-Item -LiteralPath $src -Destination $dst
        }
    }
}
Write-Output "Source differences: $count; applied: $Apply"
