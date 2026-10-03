param([Parameter(Mandatory=$true)][string]$Go, [string]$ModFile)
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../tools/native/mihomo-core'))
Push-Location $root
try {
  $modArgs=@('-mod=readonly'); if($ModFile){$modArgs+="-modfile=$ModFile"}
  $rows=& $Go list @modArgs -m -f '{{if not .Main}}{{.Path}}|{{.Version}}|{{if .Replace}}{{.Replace.Path}}|{{.Replace.Version}}|{{.Replace.Dir}}{{else}}||{{.Dir}}{{end}}{{end}}' all
  if($LASTEXITCODE){throw 'Module manifest resolution failed'}
  $reached=@(& $Go list @modArgs -deps -f '{{if .Module}}{{.Module.Path}}{{end}}' ./cmd/nimbo-mihomo | Sort-Object -Unique)
  if($LASTEXITCODE){throw 'Reachable module resolution failed'}
  $modules=@()
  foreach($row in $rows){
    if(!$row){continue};$parts=$row.Split('|');$path=$parts[0];$version=$parts[1];$source=$parts[4]
    $notices=@()
    if($source -and (Test-Path -LiteralPath $source)){
      $key=($path+'@'+$version) -replace '[^a-zA-Z0-9._@-]','_'
      foreach($f in (Get-ChildItem -LiteralPath $source -Recurse -File | Where-Object {$_.Name -match '^(LICENSE|LICENCE|COPYING|NOTICE|PATENTS|AUTHORS)(\..*|[-_].*)?$'})){
        $relative=[IO.Path]::GetRelativePath($source,$f.FullName)
        $dest=Join-Path $root "licenses/modules/$key/$relative"
        New-Item -ItemType Directory -Force -Path (Split-Path $dest) | Out-Null
        Copy-Item -Force -LiteralPath $f.FullName -Destination $dest
        $notices+=@{path=([IO.Path]::GetRelativePath($root,$dest)).Replace('\','/');sha256=(Get-FileHash -LiteralPath $dest).Hash.ToLowerInvariant()}
      }
    }
    $modules+=@{module=$path;version=$version;replacement=$parts[2];replacementVersion=$parts[3];reachedByCLI=($reached -contains $path);notices=$notices}
  }
  [ordered]@{schemaVersion=1;apiVersion=1;goModSHA256=(Get-FileHash go.mod).Hash.ToLowerInvariant();goSumSHA256=(Get-FileHash go.sum).Hash.ToLowerInvariant();pinsSHA256=(Get-FileHash pins.json).Hash.ToLowerInvariant();modules=$modules;notes='Source h1 checksums in go.sum; original module zips in .build/mod/cache/download; local protobuf tree verified file-by-file by build helper. No binary publishing. Review missing notices before distribution.'} | ConvertTo-Json -Depth 10 | Set-Content 'source-license-manifest.json' -Encoding utf8
} finally {Pop-Location}
