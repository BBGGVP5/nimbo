<#
.SYNOPSIS
  Загружает релизный ключ подписи Nimbo в секреты GitHub и запускает release-сборку APK.

.DESCRIPTION
  Пароли спрашиваются скрыто и никуда не пишутся. Перед загрузкой скрипт локально
  проверяет, что ключ открывается этими паролями и что его сертификат совпадает с
  сертификатом опубликованных APK — иначе CI-сборка не встанет поверх приложения.
  Значения передаются в `gh secret set` через stdin, не через командную строку.

.EXAMPLE
  pwsh tools/ci/set-android-signing-secrets.ps1
#>
param(
    [string]$KeystorePath = (Join-Path $PSScriptRoot '..\..\..\key'),
    [string]$KeyAlias = 'key0',
    [string]$Repo = 'BBGGVP5/nimbo',
    [switch]$NoBuild
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

# Сертификат опубликованных APK (CN=Danila). Должен совпадать с android-apk.yml.
$expectedCert = 'e99f7c5ad9b25798fec22670b8108f2692771d4ca347de40881feb3487cdc06d'

$KeystorePath = (Resolve-Path -LiteralPath $KeystorePath).Path
Write-Host "Ключ: $KeystorePath (alias $KeyAlias)"

$keytool = @(
    (Get-Command keytool -ErrorAction SilentlyContinue)?.Source
    'C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe'
) | Where-Object { $_ -and (Test-Path -LiteralPath $_) } | Select-Object -First 1
if (-not $keytool) { throw 'keytool не найден (ставится вместе с Android Studio или JDK).' }
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) { throw 'gh CLI не найден.' }

function Read-Secret([string]$Prompt) {
    $secure = Read-Host -Prompt $Prompt -AsSecureString
    [Runtime.InteropServices.Marshal]::PtrToStringBSTR(
        [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
}

$storePassword = Read-Secret 'Пароль хранилища ключа (store password)'
$keyPassword = Read-Secret 'Пароль ключа key0 (Enter — такой же, обычно так и есть)'
if ([string]::IsNullOrEmpty($keyPassword)) { $keyPassword = $storePassword }

try {
    $env:NIMBO_SP = $storePassword
    $env:NIMBO_KP = $keyPassword

    # 1. Хранилище открывается и alias на месте.
    & $keytool -list -keystore $KeystorePath -storetype PKCS12 -storepass:env NIMBO_SP -alias $KeyAlias *> $null
    if ($LASTEXITCODE -ne 0) { throw 'Хранилище не открылось: неверный store password или alias.' }

    # 2. Это тот же сертификат, что у опубликованных APK.
    $certFile = New-TemporaryFile
    try {
        & $keytool -exportcert -keystore $KeystorePath -storetype PKCS12 -storepass:env NIMBO_SP `
            -alias $KeyAlias -file $certFile.FullName *> $null
        if ($LASTEXITCODE -ne 0) { throw 'Не удалось прочитать сертификат ключа.' }
        $actualCert = (Get-FileHash -Algorithm SHA256 -LiteralPath $certFile.FullName).Hash.ToLowerInvariant()
    } finally {
        Remove-Item -LiteralPath $certFile.FullName -Force -ErrorAction SilentlyContinue
    }
    if ($actualCert -ne $expectedCert) {
        throw "Сертификат ключа ($actualCert) не совпадает с опубликованными APK ($expectedCert)."
    }

    # Отдельный пароль ключа keytool для PKCS12 не проверяет (всегда берёт store
    # password), а Android Studio по умолчанию делает их одинаковыми.
    Write-Host 'Ключ проверен: пароль верный, сертификат совпадает с релизами.' -ForegroundColor Green

    $keystoreBase64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($KeystorePath))
    $secrets = [ordered]@{
        NIMBO_KEYSTORE_BASE64   = $keystoreBase64
        NIMBO_KEYSTORE_PASSWORD = $storePassword
        NIMBO_KEY_ALIAS         = $KeyAlias
        NIMBO_KEY_PASSWORD      = $keyPassword
    }
    foreach ($name in $secrets.Keys) {
        $secrets[$name] | gh secret set $name --repo $Repo
        if ($LASTEXITCODE -ne 0) { throw "gh secret set $name завершился с ошибкой." }
        Write-Host "  секрет $name загружен"
    }
} finally {
    Remove-Item Env:NIMBO_SP, Env:NIMBO_KP -ErrorAction SilentlyContinue
    $storePassword = $keyPassword = $null
}

if ($NoBuild) { return }

gh workflow run android-apk.yml --repo $Repo -f build_type=release
if ($LASTEXITCODE -ne 0) { throw 'Не удалось запустить android-apk.yml.' }
Start-Sleep -Seconds 6
$run = gh run list --repo $Repo --workflow android-apk.yml --limit 1 --json databaseId,url | ConvertFrom-Json
Write-Host "Release-сборка запущена: $($run[0].url)" -ForegroundColor Green
Write-Host 'Готовые APK появятся в артефактах прогона примерно через 8 минут.'
