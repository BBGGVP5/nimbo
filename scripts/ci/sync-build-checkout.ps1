param([switch]$Apply, [string]$CheckoutPath)
$ErrorActionPreference = 'Stop'
$mobile = 'C:\Users\Danila\AndroidStudioProjects\Nimbo'
$desktop = 'C:\Users\Danila\Desktop\nimbo-app-main'
$checkout = if ($CheckoutPath) { $CheckoutPath } else { Join-Path $mobile '.codex-tmp\release-1.2.0' }
$tracked = @(& git -C $checkout ls-files)
if ($LASTEXITCODE -ne 0) { throw 'Cannot enumerate build checkout' }
$paths = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
foreach ($path in $tracked) { [void]$paths.Add($path) }
[void]$paths.Add('iosApp/RELEASE-1.2.0.md')
foreach ($source in @(
  @{root=$mobile; dirs=@('shared/src','iosApp/Nimbo','iosApp/Shared','iosApp/PacketTunnel','iosApp/ControlWidget','iosApp/GoBridge','iosApp/Tests','tools/native/awg-core','scripts/ci','scripts/ios')},
  @{root=$desktop; dirs=@('apps/ui/src','apps/ui/src-tauri/src','apps/ui/tests')}
)) {
  foreach ($dir in $source.dirs) {
    Get-ChildItem -LiteralPath (Join-Path $source.root $dir) -File -Recurse | ForEach-Object {
      [void]$paths.Add([IO.Path]::GetRelativePath($source.root, $_.FullName).Replace('\','/'))
    }
  }
}
$changed = 0
foreach ($path in ($paths | Sort-Object)) {
  $root = $null
  if ($path -match '^(app/src/main/assets/|shared/src/|iosApp/(Nimbo/|Shared/|PacketTunnel/|ControlWidget/|GoBridge/|Tests/)|tools/native/awg-core/|scripts/(ci/|ios/)|gradle/)' -or
      $path -in @('app/build.gradle.kts','shared/build.gradle.kts','iosApp/project.yml','iosApp/RELEASE-1.2.0.md','build.gradle.kts','settings.gradle.kts','gradle.properties','gradlew','gradlew.bat')) { $root = $mobile }
  elseif ($path -match '^(apps/(ui|installer)/|crates/)' -or $path -in @('Cargo.toml','Cargo.lock')) { $root = $desktop }
  if (!$root -or $path -match '(^|/)(build|target|node_modules|__pycache__|\.gradle|\.git)/|local\.properties$|\.(log|pyc|exe|test|keystore|jks|p12|mobileprovision)$') { continue }
  $src = Join-Path $root $path
  $dst = Join-Path $checkout $path
  if (!(Test-Path -LiteralPath $src -PathType Leaf)) { continue }
  $same = (Test-Path -LiteralPath $dst -PathType Leaf) -and ((Get-FileHash -LiteralPath $src).Hash -eq (Get-FileHash -LiteralPath $dst).Hash)
  if ($same) { continue }
  $changed++
  Write-Output $path
  if ($Apply) {
    New-Item -ItemType Directory -Path (Split-Path -Parent $dst) -Force | Out-Null
    Copy-Item -LiteralPath $src -Destination $dst
  }
}
Write-Output "Changed source files: $changed; applied: $Apply"
