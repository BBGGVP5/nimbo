param([string]$GoRoot, [switch]$PrepareOnly, [switch]$Resolve)
$ErrorActionPreference = 'Stop'
$Python = "$env:LOCALAPPDATA/Programs/Python/Python311/python.exe"
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../tools/native/mihomo-core'))
if (!$GoRoot) { $GoRoot = Join-Path $env:USERPROFILE 'go/pkg/mod/golang.org/toolchain@v0.0.1-go1.27.1.windows-amd64' }
$go = Join-Path $GoRoot 'bin/go.exe'
if (!(Test-Path -LiteralPath $go)) { throw 'Existing Go 1.27.1 required; no toolchain/binary download performed.' }
$env:GOROOT=$GoRoot; $env:GOTOOLCHAIN='local'; $env:GOWORK='off'; $env:GOENV='off'
$env:GOPROXY='https://proxy.golang.org'; $env:GOSUMDB='sum.golang.org'
$env:GOPRIVATE=''; $env:GONOPROXY=''; $env:GONOSUMDB=''; $env:GOINSECURE=''
$env:GOTELEMETRY='off'; $env:GOMAXPROCS='2'; $env:GOFLAGS='-p=2'; $env:CGO_ENABLED='0'
$env:GOOS='windows'; $env:GOARCH='amd64'
$env:GOPATH=Join-Path $root '.build/gopath'; $env:GOMODCACHE=Join-Path $root '.build/mod'
$env:GOCACHE=Join-Path $root '.build/cache'; $env:GOTMPDIR=Join-Path $root '.build/tmp'
$env:TEMP=$env:GOTMPDIR; $env:TMP=$env:GOTMPDIR
foreach ($p in @($env:GOPATH,$env:GOMODCACHE,$env:GOCACHE,$env:GOTMPDIR,(Join-Path $root '.build/bin'),(Join-Path $root 'licenses'))) { New-Item -ItemType Directory -Force -Path $p | Out-Null }
if ((& $go version) -notmatch 'go1\.27\.1 windows/amd64') { throw 'Toolchain pin mismatch' }
$pins=Get-Content -Raw (Join-Path $root 'pins.json') | ConvertFrom-Json
$mihomoSource=$null
Push-Location $root
try {
  foreach ($pin in @($pins,$pins.protobuf)) {
    $out=& $go mod download -json "$($pin.module)@$($pin.version)"
    if ($LASTEXITCODE) { throw "Source download failed: $out" }
    $src=($out -join "`n") | ConvertFrom-Json
    if ($src.Sum -ne $pin.sum -or $src.GoModSum -ne $pin.goModSum) { throw 'Source checksum pin mismatch' }
    if ($src.Origin -and $src.Origin.Hash -ne $pin.commit) { throw 'Origin commit mismatch' }
    if ($pin -eq $pins) {
      $mihomoSource=$src.Dir
      if ((Get-FileHash $src.Zip -Algorithm SHA256).Hash.ToLowerInvariant() -ne $pins.sourceZipSHA256) { throw 'Source zip mismatch' }
      Copy-Item -Force -LiteralPath (Join-Path $src.Dir 'LICENSE') -Destination 'licenses/Mihomo-LICENSE'
      Copy-Item -Force -LiteralPath (Join-Path $src.Dir 'README.md') -Destination 'licenses/Mihomo-README.md'
    } else {
      $dst=Join-Path $root '.build/protobuf'
      if (!(Test-Path -LiteralPath $dst)) {
        Copy-Item -LiteralPath $src.Dir -Destination $dst -Recurse
        Get-ChildItem -LiteralPath $dst -File -Recurse | ForEach-Object { $_.IsReadOnly=$false }
        $mod=Join-Path $dst 'go.mod'
        if ((Get-FileHash $mod).Hash.ToLowerInvariant() -ne $pin.originalGoModSHA256) { throw 'Unexpected original protobuf go.mod' }
        [IO.File]::WriteAllText($mod,([IO.File]::ReadAllText($mod).Replace('go 1.20','go 1.22')),[Text.UTF8Encoding]::new($false))
      }
      # Verify EVERY staged source file, not just go.mod. Local replace bypasses sumdb.
      $originalFiles=@(Get-ChildItem -LiteralPath $src.Dir -Recurse -File)
      if (@(Get-ChildItem -LiteralPath $dst -Recurse -File).Count -ne $originalFiles.Count) { throw 'Protobuf source file set mismatch' }
      foreach ($f in $originalFiles) {
        $rel=[IO.Path]::GetRelativePath($src.Dir,$f.FullName); $expected=(Get-FileHash -LiteralPath $f.FullName).Hash.ToLowerInvariant()
        if ($rel -eq 'go.mod') { $expected=$pin.patchedGoModSHA256 }
        if ((Get-FileHash -LiteralPath (Join-Path $dst $rel)).Hash.ToLowerInvariant() -ne $expected) { throw "Protobuf source modification: $rel" }
      }
      Copy-Item -Force -LiteralPath (Join-Path $src.Dir 'LICENSE') -Destination 'licenses/Protobuf-LICENSE'
      Copy-Item -Force -LiteralPath (Join-Path $src.Dir 'PATENTS') -Destination 'licenses/Protobuf-PATENTS'
    }
  }
  if ($PrepareOnly) { return }
  if ($Resolve) { & $go mod tidy; if ($LASTEXITCODE) { throw 'Dependency resolution failed' } }
  & $go mod download; if ($LASTEXITCODE) { throw 'Dependency source download failed' }
  & $go mod verify; if ($LASTEXITCODE) { throw 'Source verification failed' }
  if (!$mihomoSource) { throw 'Verified pinned Mihomo source directory is missing.' }
  $stage=Join-Path $root ('.build/mihomo-patched-' + [guid]::NewGuid().ToString('N'))
  New-Item -ItemType Directory -Path $stage -Force | Out-Null
  $stagedModule=Join-Path $stage 'dependencies'
  $patchManifest=& $Python (Join-Path $PSScriptRoot 'prepare-mihomo-merged.py') --stage-only --source-dir $mihomoSource --dependency-dir $stagedModule --native-dir $root
  if ($LASTEXITCODE) { throw 'Could not stage the pinned Mihomo lifecycle patch.' }
  $patchManifest | Set-Content -LiteralPath (Join-Path $stage 'mihomo-patch-manifest.json') -Encoding utf8
  $modfile=Join-Path $stage 'mihomo-core.mod'
  Copy-Item -LiteralPath (Join-Path $root 'go.mod') -Destination $modfile
  Copy-Item -LiteralPath (Join-Path $root 'go.sum') -Destination ([IO.Path]::ChangeExtension($modfile,'.sum'))
  $stagedMihomo=(Join-Path $stagedModule 'mihomo').Replace('\','/')
  & $go mod edit "-modfile=$modfile" "-replace=github.com/metacubex/mihomo=$stagedMihomo"
  if ($LASTEXITCODE) { throw 'Could not set the verified Mihomo lifecycle source replacement.' }
  $sourcePaths=@((Get-ChildItem -LiteralPath $root -Filter '*.go' -File).FullName)+@((Get-ChildItem -LiteralPath (Join-Path $root 'cmd') -Filter '*.go' -Recurse -File).FullName)
  foreach($p in @('API.md','README.md','VERIFICATION.md','go.mod','go.sum','pins.json','protobuf-directive.patch','mihomo-session-lifecycle.patch','mihomo-reality-client-version.patch','testdata/inspect-source.yaml','testdata/inspect-wire-v1.json')) {if(Test-Path -LiteralPath (Join-Path $root $p)){$sourcePaths+=Join-Path $root $p}}
  $sourceFiles=@($sourcePaths | Sort-Object -Unique | ForEach-Object {[ordered]@{path=([IO.Path]::GetRelativePath($root,$_)).Replace('\','/');sha256=(Get-FileHash -LiteralPath $_).Hash.ToLowerInvariant()}})
  & $go test '-tags=no_tailscale,no_zerotier,no_easytier' "-modfile=$modfile" -mod=readonly -count=1 -timeout=180s ./... 2>&1 | Tee-Object '.build/tests.log'
  if ($LASTEXITCODE) { throw 'Native tests failed' }
  & (Join-Path $PSScriptRoot 'collect-mihomo-source-manifest.ps1') -Go $go
  & $go build '-tags=no_tailscale,no_zerotier,no_easytier' "-modfile=$modfile" -mod=readonly -trimpath -buildvcs=false -o .build/bin/nimbo-mihomo.exe ./cmd/nimbo-mihomo
  if ($LASTEXITCODE) { throw 'Source build failed' }
  foreach($s in $sourceFiles){if((Get-FileHash -LiteralPath (Join-Path $root $s.path)).Hash.ToLowerInvariant() -ne $s.sha256){throw "Source changed during build: $($s.path); no manifest published"}}
  [ordered]@{apiVersion=1;coreVersion=$pins.version;coreCommit=$pins.commit;toolchain=(& $go version);target='windows/amd64';sha256=(Get-FileHash '.build/bin/nimbo-mihomo.exe').Hash.ToLowerInvariant();goModSHA256=(Get-FileHash 'go.mod').Hash.ToLowerInvariant();goSumSHA256=(Get-FileHash 'go.sum').Hash.ToLowerInvariant();effectiveModSHA256=(Get-FileHash $modfile).Hash.ToLowerInvariant();lifecyclePatchSHA256=(Get-FileHash (Join-Path $root 'mihomo-session-lifecycle.patch')).Hash.ToLowerInvariant();realityPatchSHA256=(Get-FileHash (Join-Path $root 'mihomo-reality-client-version.patch')).Hash.ToLowerInvariant();pinsSHA256=(Get-FileHash 'pins.json').Hash.ToLowerInvariant();sourceLicenseManifestSHA256=(Get-FileHash 'source-license-manifest.json').Hash.ToLowerInvariant();sourceFiles=$sourceFiles} | ConvertTo-Json -Depth 5 | Set-Content '.build/bin/build-manifest.json' -Encoding utf8
} finally { Pop-Location }
