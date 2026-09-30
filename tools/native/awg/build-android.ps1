param(
    [string]$NdkPath = "$env:LOCALAPPDATA/Android/Sdk/ndk/28.2.13676358"
)
$ErrorActionPreference = 'Stop'
$compilerDir = Join-Path (Resolve-Path $NdkPath) 'toolchains/llvm/prebuilt/windows-x86_64/bin'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$outputDir = Join-Path $projectRoot 'artifacts/native/awg-v3.1.20260828'
New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
$previous = @{}
foreach ($name in @('GOOS','GOARCH','GOARM','CGO_ENABLED','CC','CGO_CFLAGS','CGO_LDFLAGS')) {
    $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}
Push-Location $PSScriptRoot
try {
    go mod verify
    if ($LASTEXITCODE -ne 0) { throw 'Go dependency verification failed' }
    $env:GOOS = 'android'
    $env:CGO_ENABLED = '1'
    $env:CC = '"' + (Join-Path $compilerDir 'clang.exe') + '"'
    foreach ($arch in @(
        @{ abi='arm64-v8a'; go='arm64'; target='aarch64-linux-android29' },
        @{ abi='armeabi-v7a'; go='arm'; target='armv7a-linux-androideabi29' }
    )) {
        $env:GOARCH = $arch.go
        $env:GOARM = '7'
        $env:CGO_CFLAGS = "--target=$($arch.target)"
        $env:CGO_LDFLAGS = "--target=$($arch.target) -Wl,-z,max-page-size=16384"
        $abiDir = Join-Path $outputDir $arch.abi
        New-Item -ItemType Directory -Path $abiDir -Force | Out-Null
        $library = Join-Path $abiDir 'libwg-go.so'
        go build -trimpath -buildvcs=false -buildmode=c-shared -ldflags '-s -w' -o $library .
        if ($LASTEXITCODE -ne 0) { throw "Native build failed: $($arch.abi)" }
        $metadata = go version -m $library
        if (($metadata -join "`n") -notmatch 'amneziawg-go/v3\s+v3.1.20260828') { throw 'Unexpected core version' }
        $symbols = & (Join-Path $compilerDir 'llvm-nm.exe') -D --defined-only $library
        foreach ($method in @('awgTurnOn','awgTurnOff','awgGetConfig','awgGetSocketV4','awgGetSocketV6','awgVersion')) {
            if (($symbols -join "`n") -notmatch "Java_com_danila_nimbo_awg_AmneziaWgLibrary_$method\b") {
                throw "Missing JNI export: $method"
            }
        }
        Get-FileHash -LiteralPath $library -Algorithm SHA256
    }
    Write-Host "Verified libraries staged in $outputDir. Existing application binaries have not been replaced."
} finally {
    Pop-Location
    foreach ($name in $previous.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process')
    }
}
