# Nimbo Release Source Monorepo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Подготовить проверяемые русскоязычные исходники Nimbo для публикации: Android-релиз с актуальным libXray и чистый монорепозиторий, готовый к добавлению Windows/Linux-клиента.

**Architecture:** В корне остаются общие документы, автоматизация и CI. Полноценный Android-проект переезжает в `apps/android`; для будущего desktop-клиента создаётся отдельный документированный контур `apps/desktop`, без фиктивной реализации. libXray обновляется только из официального релиза и проверяется как Android AAR до сборки.

**Tech Stack:** Kotlin, Jetpack Compose, Android Gradle Plugin, Gradle, libXray, GitHub Actions, Dependabot.

---

## File structure

- `apps/android/` — сборочный Android-проект, Gradle wrapper, приложение и актуальный `libxray.aar`.
- `apps/desktop/README.md` — контракт и целевая структура будущего Windows/Linux-клиента.
- `docs/ARCHITECTURE.md` — ответственность каталогов и границы платформ.
- `docs/RELEASE.md` — подпись, проверка, обновление движка и выпуск Android-релиза.
- `tools/update-libxray.ps1` — воспроизводимое обновление Android AAR с GitHub Releases.
- `.github/workflows/android.yml` — CI для unit-тестов, lint и release APK.
- `.github/dependabot.yml` — регулярная проверка Gradle и GitHub Actions зависимостей.

### Task 1: Зафиксировать исходное состояние и скачать проверенный libXray

**Files:**
- Modify: `app/libs/libxray.aar`
- Delete: `app/libs/libbox.aar`
- Delete: `app/libs/libbox.so`

- [ ] **Step 1: Проверить текущую версию и официальный релиз**

Run:

```powershell
Invoke-RestMethod -Headers @{ 'User-Agent' = 'Nimbo-release-audit' } `
  -Uri 'https://api.github.com/repos/XTLS/libXray/releases/latest' |
  Select-Object tag_name, published_at
```

Expected: стабильный тег `v26.7.11` или более новый официальный release.

- [ ] **Step 2: Скачать именно Android asset и проверить его структуру**

Run:

```powershell
Invoke-WebRequest `
  -Uri 'https://github.com/XTLS/libXray/releases/download/v26.7.11/libxray-android.zip' `
  -OutFile "$env:TEMP/libxray-android.zip"
Expand-Archive "$env:TEMP/libxray-android.zip" "$env:TEMP/libxray-android" -Force
Get-ChildItem "$env:TEMP/libxray-android" -Recurse -Filter '*.aar'
```

Expected: AAR с `classes.jar` и `jni/arm64-v8a/libgojni.so`, совместимый с текущим `System.loadLibrary("gojni")`.

- [ ] **Step 3: Заменить использующееся ядро и убрать неиспользуемые бинарники**

Run:

```powershell
Copy-Item "$env:TEMP/libxray-android/libxray.aar" 'app/libs/libxray.aar' -Force
Remove-Item 'app/libs/libbox.aar', 'app/libs/libbox.so'
```

Expected: в `app/libs` остаётся только необходимый `libxray.aar`; `app/build.gradle.kts` не ссылается на libbox.

### Task 2: Сделать Android-конфигурацию пригодной для безопасного релиза

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/proguard-rules.pro`
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/AboutScreen.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/NimboMiniApp.kt`
- Create: `app/signing.properties.example`

- [ ] **Step 1: Повысить версию приложения и убрать подпись debug-ключом из release**

Change the Android default configuration to:

```kotlin
versionCode = 2
versionName = "1.0.1"
```

Load optional release signing values from Gradle properties (`nimboReleaseStoreFile`, `nimboReleaseStorePassword`, `nimboReleaseKeyAlias`, `nimboReleaseKeyPassword`); configure the release signing config only when all values are present. Do not set `signingConfigs.getByName("debug")` for a release build.

- [ ] **Step 2: Enable code and resource shrinking with narrow keep rules**

Change the release block to:

```kotlin
isMinifyEnabled = true
isShrinkResources = true
```

Keep the libXray API and its JNI-bound classes in `proguard-rules.pro`:

```proguard
-keep class libXray.** { *; }
-keep class com.danila.nimbo.network.UpdateWorker { <init>(...); }
-keep class com.danila.nimbo.service.SubscriptionUpdateWorker { <init>(...); }
```

Retain only reflection/serialization rules that are demonstrably required; remove full-package `-keep` rules that prevent R8 from optimizing all UI, network, VPN, and utility code.

- [ ] **Step 3: Use a single verified engine version in the UI**

Replace both hard-coded `26.3.27` values with `26.7.11` so the About views correspond to the bundled AAR.

- [ ] **Step 4: Add safe signing template**

Create `app/signing.properties.example`:

```properties
nimboReleaseStoreFile=C:/keys/nimbo-release.jks
nimboReleaseStorePassword=change-me
nimboReleaseKeyAlias=nimbo
nimboReleaseKeyPassword=change-me
```

- [ ] **Step 5: Verify Android checks and optimized release**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
```

Expected: `BUILD SUCCESSFUL`, a shrunk release APK per supported Android ABI, and no debug signing configuration in the release variant.

### Task 3: Add reproducible core update tooling and release documentation

**Files:**
- Create: `tools/update-libxray.ps1`
- Create: `docs/RELEASE.md`
- Modify: `.gitignore`

- [ ] **Step 1: Create the update script**

The script must accept `-Version` and `-AndroidProjectPath`, default to the latest stable `XTLS/libXray` release, reject prereleases, download `libxray-android.zip`, find exactly one AAR, validate `jni/arm64-v8a/libgojni.so`, replace `app/libs/libxray.aar`, and output the tag plus SHA-256.

- [ ] **Step 2: Document the signed release flow in Russian**

Document these exact commands:

```powershell
Copy-Item apps/android/app/signing.properties.example apps/android/app/signing.properties
cd apps/android
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
```

State that `signing.properties` is local-only and that a release APK must never be signed with the debug key.

- [ ] **Step 3: Exclude local secrets and output artifacts**

Add these lines to `.gitignore`:

```gitignore
apps/android/app/signing.properties
apps/android/.gradle/
apps/android/**/build/
*.apk
*.aab
```

### Task 4: Assemble a clean multi-platform source repository

**Files:**
- Create: `apps/android/**` (migrated Android project)
- Create: `apps/desktop/README.md`
- Create: `docs/ARCHITECTURE.md`
- Modify: `README.md`
- Modify: `README.en.md`

- [ ] **Step 1: Start from the existing `BBGGVP5/nimbo` repository**

Run:

```powershell
git clone https://github.com/BBGGVP5/nimbo.git C:\Users\Danila\Desktop\nimbo-app-main-sources
```

Expected: a Git working copy whose remote is `origin` pointing to `BBGGVP5/nimbo`.

- [ ] **Step 2: Copy only source-controlled Android files to `apps/android`**

Copy Gradle files, `app/src`, `app/libs/libxray.aar`, `app/proguard-rules.pro`, and Android documentation. Exclude `.gradle`, `.idea`, `build`, `app/debug`, `app/release`, `local.properties`, APKs, baseline profile output, and private signing files.

- [ ] **Step 3: Document the platform boundary**

Create `apps/desktop/README.md` explaining that Windows/Linux code belongs there when implemented, with shared formats and protocol parsing extracted into `packages/core` only after a real shared API exists. Do not add empty fake desktop application code.

- [ ] **Step 4: Replace inaccurate availability claims**

Update the Russian root README to describe the available Android client, the current Xray engine release, source layout, build command, and planned desktop area. Update the English README to the same factual scope.

### Task 5: Automate verification and publish to GitHub

**Files:**
- Create: `.github/workflows/android.yml`
- Create: `.github/dependabot.yml`
- Modify: `.gitattributes`

- [ ] **Step 1: Add GitHub Actions Android CI**

Create a workflow triggered on `push` and `pull_request` that checks out the repository, installs Temurin JDK 21, runs in `apps/android`, executes:

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
```

Upload `apps/android/app/build/outputs/apk/release/*.apk` only as a workflow artifact; do not publish an unsigned APK as a GitHub Release.

- [ ] **Step 2: Add dependency maintenance**

Create Dependabot entries for `gradle` in `/apps/android` and `github-actions` in `/`, each weekly, so library and workflow updates become reviewable pull requests.

- [ ] **Step 3: Commit and push the clean source tree**

Run:

```powershell
git add .
git commit -m "Релиз: Android-исходники, libXray 26.7.11 и CI"
git push origin main
```

Expected: GitHub `BBGGVP5/nimbo` contains the same clean working tree as `C:\Users\Danila\Desktop\nimbo-app-main-sources` and commits are authored by the authenticated account `BBGGVP5`.

## Self-review

- **Покрытие требований:** релизная оптимизация — Task 2; обновление ядра — Task 1 и 3; русская документация — Tasks 3 and 4; понятные папки для Android/desktop — Task 4; локальная папка и GitHub — Task 5.
- **Отсутствие фиктивной desktop-реализации:** Windows/Linux-код не заявляется готовым, потому что его нет в исходных данных.
- **Безопасность:** Task 2 убирает debug signing из release, Task 3 исключает реальные ключи, Task 5 не публикует неподписанный APK как пользовательский релиз.
