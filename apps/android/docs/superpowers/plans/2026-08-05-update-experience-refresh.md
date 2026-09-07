# Update Experience Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Сделать экран обновлений Nimbo информативнее и компактнее, добавить полноэкранное завершение установки, синхронизировать превью иконок и убрать белые прямоугольники Liquid Glass.

**Architecture:** Форматирование версии и даты остаётся в чистом `UpdateUiText`, чтобы его можно было проверить JVM-тестами. Экран обновлений собирается из небольших Compose-компонентов: компактного выбора канала, заголовка истории и метаданных сборки. Post-install экран остаётся диалогом без системной ширины, но рисует полноэкранный фон, одноразовый салют и анимированную галочку. Общий стеклянный материал убирает покоящиеся GPU-слои и сложные заливки, вызывающие прямоугольные артефакты на Android 17.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Android vector drawables, JUnit 4, Gradle.

---

### Task 1: Метаданные истории изменений

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/UpdateUiText.kt`
- Modify: `app/src/test/java/com/danila/nimbo/ui/screens/UpdateUiTextTest.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/UpdateScreen.kt`

- [ ] **Step 1: Добавить падающие тесты версии и даты**

```kotlin
assertEquals("v1.1.0 Beta 3", UpdateUiText.versionLabel("v1.1.0-beta.3", "ru"))
assertEquals("5 августа 2026 · 18:40", UpdateUiText.releaseDate("2026-08-05T14:40:00Z", "ru", ZoneId.of("Europe/Samara")))
```

- [ ] **Step 2: Запустить тесты и подтвердить падение**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.danila.nimbo.ui.screens.UpdateUiTextTest`

Expected: FAIL, методы `versionLabel` и `releaseDate` ещё отсутствуют.

- [ ] **Step 3: Реализовать локализованные форматтеры**

```kotlin
fun versionLabel(value: String, language: String): String
fun releaseDate(value: String?, language: String, zoneId: ZoneId = ZoneId.systemDefault()): String?
```

Версия удаляет повторный `v`, отображает prerelease как `Beta N`, дата использует русский или английский месяц и локальное время.

- [ ] **Step 4: Подключить заголовок истории**

В `UpdateScreen.kt` перед Markdown показать версию, дату публикации/обновления, канал и платформу Android.

### Task 2: Компактный и понятный экран обновлений

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/UpdateScreen.kt`

- [ ] **Step 1: Вынести выбор канала в `UpdateChannelPicker`**

```kotlin
@Composable
private fun UpdateChannelPicker(
    value: UpdateChannel,
    onValueChange: (UpdateChannel) -> Unit
)
```

Контрол и меню имеют одну компактную ширину; элементы меню содержат только текст, выбранный вариант выделяется цветом и начертанием без ведущих иконок.

- [ ] **Step 2: Поднять историю выше системных данных**

Порядок: состояние → история текущей сборки → настройки → система. Это оставляет главное содержимое выше технической информации.

- [ ] **Step 3: Сделать карточки живее без перегрузки**

Добавить анимированную смену статуса, компактные метаданные версии/даты/канала и сохранить текущий прогресс загрузки с процентом и размером.

### Task 3: Полноэкранное завершение обновления

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/ui/components/PostUpdateDialog.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/NimboMiniApp.kt`

- [ ] **Step 1: Передать changelog и время установки**

```kotlin
PostUpdateDialog(
    versionName = preferencesManager.lastInstalledUpdateVersion.orEmpty(),
    changelog = preferencesManager.lastInstalledUpdateChangelog.orEmpty(),
    installedAt = packageManager.getPackageInfo(packageName, 0).lastUpdateTime,
    onDismiss = { ... },
    onShowChanges = { ... }
)
```

- [ ] **Step 2: Нарисовать полноэкранный фон**

Canvas рисует спокойные движущиеся линии от нижних углов к центру. При отключённых фоновых анимациях линии остаются статичными.

- [ ] **Step 3: Добавить одноразовую анимацию успеха**

`Animatable` последовательно раскрывает кольцо и галочку; частицы один раз проходят вдоль границы и исчезают. Экран не запускает постоянный салют.

- [ ] **Step 4: Сделать действия понятными**

Основная кнопка открывает историю, вторичная закрывает экран. На экране видны версия, дата и короткая выжимка изменений.

### Task 4: Иконка и честные превью

**Files:**
- Modify: `app/src/main/res/drawable/ic_launcher_beta_badge_canvas.xml`
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/AppIconSettingsScreen.kt`

- [ ] **Step 1: Увеличить плашку Beta**

Сохранить центр в правой верхней безопасной зоне, увеличить геометрию примерно на 20%, не сдвигая её на облако.

- [ ] **Step 2: Обновить превью основной иконки**

Галерея продолжает использовать реальные adaptive icon resources, а крупный preview получает системную маску и отступы, совпадающие с рабочим столом.

- [ ] **Step 3: Добавить preview уведомления**

Показать компактный макет уведомления с bitmap из `CustomAppIconManager.notificationLargeIcon`, чтобы переключатель «Для уведомлений» имел видимый результат.

### Task 5: Белые прямоугольники Liquid Glass

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/ui/components/LiquidInteraction.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/components/LiquidGlassSurface.kt`
- Modify: `app/src/test/java/com/danila/nimbo/ui/components/LiquidGlassMaterialPolicyTest.kt`

- [ ] **Step 1: Не создавать `graphicsLayer` в покое**

`liquidTouchDeformation` добавляет слой только пока `pressProgress > 0`, чтобы Android 17 не показывал прямоугольную backing texture на каждом элементе.

- [ ] **Step 2: Упростить заливку стекла**

Внутренняя поверхность рисуется одной полупрозрачной path-bound заливкой. Перелив остаётся на границе; перекрывающиеся внутренние gradient textures удаляются.

- [ ] **Step 3: Прогнать материальные тесты**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.danila.nimbo.ui.components.*"`

Expected: PASS.

### Task 6: Общая проверка

**Files:**
- Verify: `app/src/main/java/com/danila/nimbo/ui/screens/UpdateScreen.kt`
- Verify: `app/src/main/java/com/danila/nimbo/ui/components/PostUpdateDialog.kt`
- Verify: `app/src/main/java/com/danila/nimbo/ui/screens/AppIconSettingsScreen.kt`

- [ ] **Step 1: Запустить unit tests**

Run: `./gradlew.bat :app:testDebugUnitTest`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Собрать debug APK**

Run: `./gradlew.bat :app:assembleDebug`

Expected: BUILD SUCCESSFUL и APK в `app/build/outputs/apk/debug/`.

- [ ] **Step 3: Проверить ресурсы release-варианта**

Run: `./gradlew.bat :app:processReleaseResources`

Expected: BUILD SUCCESSFUL без ошибок vector drawable и adaptive icon.
