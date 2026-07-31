# Подготовка релиза Nimbo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Подготовить Nimbo к воспроизводимым Windows/Linux-релизам, добавить безопасную загрузку Xray для Linux, сохранить существующий Android-модуль и опубликовать чистое дерево в `BBGGVP5/nimbo`.

**Architecture:** Исходное дерево остаётся Rust workspace с приложениями в `apps/` и общими библиотеками в `crates/`; существующий Android Gradle-проект сохраняется в `apps/android/` отдельно от desktop-клиента. Приложение обновляет себя из целевого репозитория, а Xray при отсутствии скачивается из официального stable release только после проверки SHA-256. CI разделяет проверки, матричные сборки и публикацию tag-релиза.

**Tech Stack:** Rust 2021, Tauri 2, React 19, TypeScript, Vite, GitHub Actions, NSIS, AppImage/deb, Xray-core.

---

### Task 1: Зафиксировать идентичность и безопасную конфигурацию релиза

**Files:**
- Modify: `Cargo.toml`
- Modify: `apps/ui/src-tauri/tauri.conf.json`
- Modify: `apps/ui/src-tauri/src/updater.rs`
- Modify: `apps/ui/src/lib/api.ts`
- Modify: `.gitignore`
- Test: `apps/ui/src-tauri/src/updater.rs`

- [ ] **Step 1: Заменить владельца и URL репозитория в workspace**

```toml
[workspace.package]
authors = ["BBGGVP5"]
repository = "https://github.com/BBGGVP5/nimbo"
```

- [ ] **Step 2: Ограничить CSP только нужными origins Tauri**

```json
"security": {
  "csp": {
    "default-src": "'self' customprotocol: asset:",
    "connect-src": "ipc: http://ipc.localhost",
    "img-src": "'self' asset: http://asset.localhost blob: data:",
    "style-src": "'self' 'unsafe-inline'"
  }
}
```

- [ ] **Step 3: Направить runtime-проверку и fallback ссылку обновления на новый репозиторий**

```rust
const DEFAULT_RELEASE_API_URL: &str =
    "https://api.github.com/repos/BBGGVP5/nimbo/releases/latest";
```

```ts
download_url: "https://github.com/BBGGVP5/nimbo/releases"
```

- [ ] **Step 4: Добавить unit-тест для URL стабильного канала обновлений**

```rust
#[test]
fn default_release_channel_is_bbgvp5_nimbo() {
    assert_eq!(
        DEFAULT_RELEASE_API_URL,
        "https://api.github.com/repos/BBGGVP5/nimbo/releases/latest"
    );
}
```

- [ ] **Step 5: Исключить из репозитория локальные логи и рабочие каталоги ассистентов**

```gitignore
.claude/
.codex-logs/
```

- [ ] **Step 6: Проверить целевой unit-тест**

Run: `cargo test -p nimbo-ui updater::tests::default_release_channel_is_bbgvp5_nimbo`

Expected: `test result: ok. 1 passed`

- [ ] **Step 7: Commit**

```bash
git add Cargo.toml apps/ui/src-tauri/tauri.conf.json apps/ui/src-tauri/src/updater.rs apps/ui/src/lib/api.ts .gitignore
git commit -m "release: point Nimbo updates to BBGGVP5 repository"
```

### Task 2: Сделать загрузку Xray кроссплатформенной и проверяемой

**Files:**
- Modify: `Cargo.toml`
- Modify: `apps/ui/src-tauri/Cargo.toml`
- Modify: `apps/ui/src-tauri/src/commands.rs`
- Test: `apps/ui/src-tauri/src/commands.rs`

- [ ] **Step 1: Добавить `sha2` в общие Rust зависимости и UI crate**

```toml
[workspace.dependencies]
sha2 = "0.10"
```

```toml
[dependencies]
sha2.workspace = true
```

- [ ] **Step 2: Описать mapping официальных архивов Xray**

```rust
fn xray_release_archive_url() -> Option<String> {
    let archive = match (std::env::consts::OS, std::env::consts::ARCH) {
        ("windows", "x86_64") => "Xray-windows-64.zip",
        ("windows", "x86") => "Xray-windows-32.zip",
        ("windows", "aarch64") => "Xray-windows-arm64-v8a.zip",
        ("linux", "x86_64") => "Xray-linux-64.zip",
        ("linux", "x86") => "Xray-linux-32.zip",
        ("linux", "aarch64") => "Xray-linux-arm64-v8a.zip",
        _ => return None,
    };
    Some(format!("https://github.com/XTLS/Xray-core/releases/latest/download/{archive}"))
}
```

- [ ] **Step 3: Загружать архив и его `.dgst`, сверять SHA-256 перед распаковкой**

```rust
fn verify_xray_archive_digest(bytes: &[u8], digest_file: &str) -> Result<(), String> {
    let expected = digest_file
        .lines()
        .find_map(|line| line.trim().strip_prefix("SHA256=").map(str::trim))
        .filter(|value| value.len() == 64 && value.chars().all(|ch| ch.is_ascii_hexdigit()))
        .ok_or_else(|| "В .dgst Xray нет корректной SHA256-суммы.".to_string())?;
    let actual = format!("{:x}", sha2::Sha256::digest(bytes));
    if actual.eq_ignore_ascii_case(expected) { Ok(()) } else { Err("Контрольная сумма Xray не совпала.".into()) }
}
```

- [ ] **Step 4: Извлечь только исполняемый файл в временный файл и атомарно заменить target**

```rust
let temporary = bin_dir.join(format!(".{}.{}.partial", xray_exe_name(), std::process::id()));
std::fs::rename(&temporary, &target)
    .map_err(|e| format!("Не удалось установить Xray: {e}"))?;
```

- [ ] **Step 5: На Unix выставить executable bit**

```rust
#[cfg(unix)]
{
    use std::os::unix::fs::PermissionsExt;
    std::fs::set_permissions(&temporary, std::fs::Permissions::from_mode(0o755))?;
}
```

- [ ] **Step 6: Добавить тесты для валидной и повреждённой суммы**

```rust
#[test]
fn validates_xray_archive_sha256() {
    verify_xray_archive_digest(b"nimbo", "SHA256= fae4ccc83b91d0f3d002cc9799e33d28a11fd847ab1aa9adf60061ddcde09105")
        .unwrap();
}
```

- [ ] **Step 7: Проверить unit-тесты Xray download path**

Run: `cargo test -p nimbo-ui commands::tests::validates_xray_archive_sha256`

Expected: `test result: ok. 1 passed`

- [ ] **Step 8: Commit**

```bash
git add Cargo.toml Cargo.lock apps/ui/src-tauri/Cargo.toml apps/ui/src-tauri/src/commands.rs
git commit -m "feat: download verified Xray runtime on Linux"
```

### Task 3: Обновить только совместимые зависимости и зафиксировать Android layout

**Files:**
- Modify: `Cargo.lock`
- Modify: `apps/ui/package-lock.json`
- Modify: `apps/installer/package-lock.json`
- Create: `apps/README.md`
- Create: `apps/android/README.md`
- Modify: `README.md`

- [ ] **Step 1: Обновить lockfiles в пределах текущих semver диапазонов**

```bash
cargo update
(cd apps/ui && npm update)
(cd apps/installer && npm update)
```

- [ ] **Step 2: Документировать целевую структуру `apps/` с существующим Android-клиентом**

```text
apps/
├── ui/          # Общий desktop UI: Tauri + React
├── service/     # Windows service для привилегированных операций
├── installer/   # Кастомные установщики Windows/Linux
└── android/     # Kotlin + Jetpack Compose Android-клиент
```

- [ ] **Step 3: Сохранить Android Gradle-проект, инструменты обновления libXray и Android workflow**

```markdown
# Android

Android-клиент Nimbo содержит Gradle-wrapper, Kotlin/Compose исходники, unit-тесты и `libxray.aar`. Локальная подпись APK не хранится в репозитории.
```

- [ ] **Step 4: Переписать README как русскоязычную точку входа**

```markdown
## Платформы

| Платформа | Статус |
|---|---|
| Windows 10/11 | Основная платформа. |
| Linux | Экспериментальная сборка AppImage/deb; Xray скачивается при первом запуске. |
| Android 10+ | Kotlin/Compose-клиент в `apps/android/`; подписанный APK требует отдельный keystore. |
```

- [ ] **Step 5: Проверить отсутствие production уязвимостей и собрать оба frontend-пакета**

Run: `npm audit --omit=dev --audit-level=high && npm run build` in `apps/ui`, then the same in `apps/installer`.

Expected: `found 0 vulnerabilities` and both TypeScript/Vite builds succeed.

- [ ] **Step 6: Commit**

```bash
git add Cargo.lock apps/ui/package-lock.json apps/installer/package-lock.json apps/README.md apps/android/README.md README.md
git commit -m "chore: refresh release dependencies and document app layout"
```

### Task 4: Автоматизировать проверку и публикацию Windows/Linux релизов

**Files:**
- Create: `.github/workflows/ci.yml`
- Create: `.github/workflows/release.yml`
- Modify: `.github/workflows/build-linux.yml`

- [ ] **Step 1: Добавить CI matrix для Rust тестов и frontend builds**

```yaml
strategy:
  matrix:
    os: [windows-latest, ubuntu-22.04]
steps:
  - uses: actions/checkout@v4
  - uses: dtolnay/rust-toolchain@stable
  - uses: actions/setup-node@v4
    with:
      node-version: 22
      cache: npm
      cache-dependency-path: |
        apps/ui/package-lock.json
        apps/installer/package-lock.json
  - run: cargo test --workspace --all-targets
  - run: npm ci && npm run build
    working-directory: apps/ui
```

- [ ] **Step 2: Собрать Windows setup и Linux AppImage/deb на tag `v*`**

```yaml
on:
  push:
    tags: ["v*"]
  workflow_dispatch:
```

- [ ] **Step 3: Сохранить выходные файлы как artifacts и загрузить их в GitHub Release**

```yaml
- name: Publish release assets
  env:
    GH_TOKEN: ${{ github.token }}
  run: |
    gh release view "${{ github.ref_name }}" || gh release create "${{ github.ref_name }}" --generate-notes
    gh release upload "${{ github.ref_name }}" release-assets/* --clobber
```

- [ ] **Step 4: Удалить старый workflow, который дублирует Linux build без release assets**

```bash
git rm .github/workflows/build-linux.yml
```

- [ ] **Step 5: Проверить YAML и запуск локальных сборок**

Run: `npm run build` in both `apps/ui` and `apps/installer`; `cargo test --workspace --all-targets`; `cargo clippy --workspace --all-targets -- -D warnings`.

Expected: all commands exit with code `0`.

- [ ] **Step 6: Commit**

```bash
git add .github/workflows
git commit -m "ci: automate Windows and Linux release builds"
```

### Task 5: Подготовить чистое дерево источников и опубликовать его

**Files:**
- Copy source files from: `C:/Users/Danila/Desktop/nimbo-app-main`
- Destination: `C:/Users/Danila/Desktop/nimbo-app-main-sources`
- Publish: `https://github.com/BBGGVP5/nimbo`

- [ ] **Step 1: Создать ветку от текущего `BBGGVP5/nimbo/main`, не переписывая историю**

```bash
git fetch https://github.com/BBGGVP5/nimbo.git main
git switch -c release-prep FETCH_HEAD
```

- [ ] **Step 2: Синхронизировать только исходники и документы**

```text
Include: .github/, apps/, crates/, docs/, Cargo.toml, Cargo.lock, CHANGELOG_NIMBO.md, README.md, nimbo*.png, nimbo.ico
Exclude: .git/, target/, node_modules/, dist/, *.log, .claude/, .codex-logs/, docs/superpowers/plans/
```

- [ ] **Step 3: Удалить из целевой копии игнорируемые build artifacts и проверить Git status**

Run: `git status --ignored --short`

Expected: no tracked `target`, `node_modules`, `dist`, generated Tauri schemas or logs.

- [ ] **Step 4: Создать релизный commit от имени BBGGVP5**

```bash
git config user.name "BBGGVP5"
git config user.email "danilarodakov@yandex.ru"
git add -A
git commit -m "release: publish Nimbo source tree"
```

- [ ] **Step 5: Fast-forward main и отправить в целевой remote**

```bash
git switch main
git merge --ff-only release-prep
git remote set-url origin https://github.com/BBGGVP5/nimbo.git
git push -u origin main
```

- [ ] **Step 6: Проверить опубликованный SHA и URL обновлений**

Run: `git ls-remote origin refs/heads/main` and `git show --format=fuller --stat HEAD`.

Expected: remote `main` points at the release-source commit authored by `BBGGVP5`.
