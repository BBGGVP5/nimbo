param(
    [string]$GoRoot = "$env:USERPROFILE/go/pkg/mod/golang.org/toolchain@v0.0.1-go1.27.1.windows-amd64",
    [string]$Python = "$env:LOCALAPPDATA/Programs/Python/Python311/python.exe",
    [string]$AndroidSdk = "$env:LOCALAPPDATA/Android/Sdk",
    [string]$CacheDirectory,
    [ValidateRange(1,4)][int]$Parallelism = 1,
    [switch]$Build
)
$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$bridge = Join-Path $repo 'tools/native/android-bridge'
$appleBridge = Join-Path $repo 'iosApp/GoBridge'
$mihomo = Join-Path $repo 'tools/native/mihomo-core'
$archive = Join-Path $repo 'artifacts/libxray-26.9.30/libxray-codeload.tar.gz'
$go = Join-Path $GoRoot 'bin/go.exe'
if (!(Test-Path -LiteralPath $go) -or !(Test-Path -LiteralPath $Python)) { throw 'Installed Go 1.27.1 and Python are required.' }
if ((Get-FileHash -LiteralPath $archive).Hash.ToLowerInvariant() -ne '0b9162518c1eb2aadca13f39e63e9c5f7c904c4843ad6bc8f8251f2d1f99c185') { throw 'LibXray source archive pin mismatch.' }
if ((Get-Content -LiteralPath (Join-Path $appleBridge 'go.mod') -Raw) -notmatch 'nimbo/mihomocore') { throw 'Combined mobile module graph is not prepared; refusing an Xray-only artifact.' }

# Fresh staging paths only. Never overwrites the installed AAR or another build.
$stage = Join-Path $repo ('artifacts/mihomo-android-native-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0,8))
$source = Join-Path $stage 'source'
New-Item -ItemType Directory -Path $source -Force | Out-Null
$env:GOROOT = $GoRoot
$env:GOTOOLCHAIN = 'local'; $env:GOWORK = 'off'; $env:GOENV = 'off'; $env:GOTELEMETRY = 'off'
$env:GOPROXY = 'https://proxy.golang.org'; $env:GOSUMDB = 'sum.golang.org'
$env:GOPRIVATE = ''; $env:GONOPROXY = ''; $env:GONOSUMDB = ''; $env:GOINSECURE = ''
$env:GOOS = 'windows'; $env:GOARCH = 'amd64'; $env:CGO_ENABLED = '0'
$env:GOMAXPROCS = [string]$Parallelism; $env:GOFLAGS = "-p=$Parallelism -tags=with_gvisor,no_tailscale,no_zerotier,no_easytier"
$env:GOPATH = Join-Path $stage 'gopath'
$env:GOMODCACHE = Join-Path $mihomo '.build/mod'
if (!$CacheDirectory) { $CacheDirectory = Join-Path $repo '.build-dependencies/libxray-mihomo-android/cache' }
$env:GOCACHE = [IO.Path]::GetFullPath($CacheDirectory)
$env:GOTMPDIR = Join-Path $stage 'tmp'
$env:GOBIN = Join-Path $stage 'bin'
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
$env:ANDROID_HOME = $AndroidSdk; $env:ANDROID_SDK_ROOT = $AndroidSdk
$env:ANDROID_NDK_HOME = Join-Path $AndroidSdk 'ndk/28.2.13676358'
$env:LIBXRAY_GOMOBILE_VERSION = 'v0.0.0-20260908204917-8b95e45f8d3e'
$env:PATH = (Join-Path $GoRoot 'bin') + ';' + $env:GOBIN + ';' + (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:PATH
foreach ($dir in @($env:GOPATH,$env:GOCACHE,$env:GOTMPDIR,$env:GOBIN)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
if ((& $go version) -ne 'go version go1.27.1 windows/amd64') { throw 'Go toolchain pin mismatch.' }
# Native bsdtar avoids depending on Git's hidden gzip subprocess PATH.
& "$env:SystemRoot/System32/tar.exe" -xzf $archive --strip-components=1 -C $source
if ($LASTEXITCODE) { throw 'Source extraction failed.' }
Copy-Item -LiteralPath (Join-Path $appleBridge 'go.mod'),(Join-Path $appleBridge 'go.sum') -Destination $source
Get-ChildItem -LiteralPath $bridge -Filter '*.go' -File | Copy-Item -Destination $source
Copy-Item -LiteralPath (Join-Path $repo 'tools/native/libxray-memory/memory_ios.go') -Destination (Join-Path $source 'memory/memory_ios.go')

$inputs = @((Join-Path $repo 'tools/native/libxray-memory'),$bridge,$appleBridge,(Join-Path $repo 'tools/native/awg-core'),$mihomo) | ForEach-Object {
    Get-ChildItem -LiteralPath $_ -Recurse -File | Where-Object {
        $_.FullName -notmatch '[\\/]\.build[\\/]' -and ($_.Extension -eq '.go' -or $_.Name -in @('go.mod','go.sum','pins.json','mihomo-session-lifecycle.patch','mihomo-reality-client-version.patch'))
    }
} | ForEach-Object { [ordered]@{ path=$_.FullName; sha256=(Get-FileHash -LiteralPath $_.FullName).Hash.ToLowerInvariant() } }
$inputs | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $stage 'inputs.json') -Encoding utf8

# Compile immutable source snapshots. A concurrent editor must never change a
# package between Go's import scan and compilation (even a standard-library
# import addition can otherwise produce misleading missing importcfg errors).
$snapshots = @{}
foreach ($name in @('awg-core','mihomo-core')) {
    $original = Join-Path $repo "tools/native/$name"
    $snapshot = Join-Path $stage "adapters/$name"
    New-Item -ItemType Directory -Path $snapshot -Force | Out-Null
    Get-ChildItem -LiteralPath $original -Force | Where-Object { $_.Name -notin @('.build','.git') } | Copy-Item -Destination $snapshot -Recurse
    foreach ($entry in $inputs | Where-Object { $_.path.StartsWith($original + [IO.Path]::DirectorySeparatorChar) }) {
        $relative = $entry.path.Substring($original.Length).TrimStart('\','/')
        $copied = Join-Path $snapshot $relative
        if ((Get-FileHash -LiteralPath $copied).Hash.ToLowerInvariant() -ne $entry.sha256) { throw 'Native source changed while taking snapshot; retry after source freeze.' }
    }
    $snapshots[$name] = $snapshot
}

Push-Location $source
try {
    & $go mod edit "-replace=nimbo/awgcore=$($snapshots['awg-core'].Replace('\','/'))"
    if ($LASTEXITCODE) { throw 'Cannot stage root dependency replacements.' }
    # Share the Apple source verifier: validate every protobuf file, not merely
    # go.mod, and install the effective replacements at the root module.
    & $Python (Join-Path $PSScriptRoot 'prepare-mihomo-merged.py') --source-dir $source --dependency-dir (Join-Path $stage 'dependencies') --native-dir $snapshots['mihomo-core'] --go $go
    if ($LASTEXITCODE) { throw 'Merged native source verification failed.' }
    & $go test -mod=readonly -count=1 -timeout=180s -run '^Test[^E]' . 2>&1 | Tee-Object -FilePath (Join-Path $stage 'host-tests.log')
    if ($LASTEXITCODE) { throw 'Combined LibXray/Mihomo host tests failed. No production library changed.' }
    if ($Build) {
        # gomobile/gobind can exceed Windows commit limits on the full graph:
        # bound parser workers and serialize the four ABI builds in staged tool copies.
        & $Python (Join-Path $PSScriptRoot 'stage_gomobile_memory_limits.py') --go $go --source-dir $source --stage-dir $stage
        if ($LASTEXITCODE) { throw 'Could not apply bounded gomobile build staging; no production library changed.' }
        $beforeMod = (Get-FileHash 'go.mod').Hash; $beforeSum = (Get-FileHash 'go.sum').Hash
        # Use the pinned upstream builder. It generates gomobile Java/JNI, all
        # four ABIs and 16 KiB aligned libraries, and restores temporary Go edits.
        & $Python build/main.py android 2>&1 | Tee-Object -FilePath (Join-Path $stage 'aar-build.log')
        if ($LASTEXITCODE) { throw 'Android native build failed. No production library changed.' }
        if ((Get-FileHash 'go.mod').Hash -ne $beforeMod -or (Get-FileHash 'go.sum').Hash -ne $beforeSum) { throw 'Upstream builder did not restore the module lock.' }
        $aar = Join-Path $source 'libXray.aar'
        if (!(Test-Path -LiteralPath $aar)) { throw 'No AAR produced.' }
        & $Python (Join-Path $PSScriptRoot 'verify-libxray-mihomo-aar.py') $aar --baseline (Join-Path $repo 'app/libs/libxray.aar') --javap (Join-Path $env:JAVA_HOME 'bin/javap.exe') --go $go
        if ($LASTEXITCODE) { throw 'Merged AAR failed compatibility checks; not promoted.' }
    }
    foreach ($inputFile in $inputs) {
        if ((Get-FileHash -LiteralPath $inputFile.path).Hash.ToLowerInvariant() -ne $inputFile.sha256) { throw "Build inputs changed: $($inputFile.path). Artifact not approved." }
    }
    [ordered]@{ stage=$stage; androidBuilt=[bool]$Build; productionLibraryChanged=$false; libXray='26.9.30'; mihomo='v1.19.31' } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $stage 'result.json') -Encoding utf8
    Write-Output "Staged build: $stage (never installed or promoted automatically)"
} finally { Pop-Location }
