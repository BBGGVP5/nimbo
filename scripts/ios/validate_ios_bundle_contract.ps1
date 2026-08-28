[CmdletBinding()]
param(
    [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
)

$ErrorActionPreference = "Stop"

$plistPath = Join-Path $ProjectRoot "iosApp\Nimbo\Info.plist"
$projectPath = Join-Path $ProjectRoot "iosApp\project.yml"

if (-not (Test-Path -LiteralPath $plistPath)) {
    throw "Missing iOS Info.plist: $plistPath"
}
if (-not (Test-Path -LiteralPath $projectPath)) {
    throw "Missing XcodeGen project definition: $projectPath"
}

[xml]$plist = Get-Content -LiteralPath $plistPath -Raw
$dictionary = $plist.plist.dict
$nodes = @($dictionary.ChildNodes | Where-Object { $_.NodeType -eq [System.Xml.XmlNodeType]::Element })
$composeKeyIndex = -1
for ($index = 0; $index -lt $nodes.Count; $index++) {
    if ($nodes[$index].Name -eq "key" -and $nodes[$index].InnerText -eq "CADisableMinimumFrameDurationOnPhone") {
        $composeKeyIndex = $index
        break
    }
}

if ($composeKeyIndex -lt 0 -or $composeKeyIndex + 1 -ge $nodes.Count -or $nodes[$composeKeyIndex + 1].Name -ne "true") {
    throw "Info.plist must declare CADisableMinimumFrameDurationOnPhone=true for Compose UIKit."
}

$projectYaml = Get-Content -LiteralPath $projectPath -Raw
if ($projectYaml -notmatch '(?m)^\s{8}CADisableMinimumFrameDurationOnPhone:\s*true\s*$') {
    throw "project.yml must generate CADisableMinimumFrameDurationOnPhone=true."
}

Write-Host "iOS bundle contract is valid."
