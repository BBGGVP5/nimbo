# Nimbo Android Beta 2 Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Выпустить Android-сборку 1.1.0 Beta 2 с корректной Beta-иконкой уведомления, чистым Liquid Glass, расширенным конструктором иконок и автоскрытием нижней панели.

**Architecture:** Системные launcher-иконки продолжают переключаться через заранее объявленные activity-alias, поскольку Android не разрешает менять ресурс основной иконки произвольным PNG во время работы. Конструктор собирает пользовательский bitmap для уведомлений, превью и закрепляемого ярлыка; скролл передаёт направление в единое состояние нижней панели через nested scroll, а стеклянные слои рисуются по Outline формы вместо прямоугольного слоя.

**Tech Stack:** Kotlin, Jetpack Compose, Android NotificationCompat, ShortcutManager, SharedPreferences, Android vector/bitmap resources, JUnit 4.

---

### Task 1: Beta-иконка уведомлений

**Files:**
- Create: `app/src/main/res/drawable-nodpi/nimbo_beta_notification.png`
- Modify: `app/src/main/java/com/danila/nimbo/utils/NotificationManager.kt`
- Modify: `app/src/main/java/com/danila/nimbo/utils/PreferencesManager.kt`

- [ ] **Step 1:** Сгенерировать квадратный вариант на основе фирменного облака Nimbo с отдельной читаемой плашкой `BETA` в правом верхнем углу.
- [ ] **Step 2:** Проверить изображение визуально: текст читается, плашка не касается облака и остаётся внутри круглой маски уведомления.
- [ ] **Step 3:** Добавить загрузку пользовательского bitmap с fallback на `nimbo_beta_notification`.
- [ ] **Step 4:** Использовать bitmap в уведомлениях, не заменяя обязательный монохромный `smallIcon`.

### Task 2: Чистая отрисовка Liquid Glass

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/ui/components/LiquidGlassSurface.kt`

- [ ] **Step 1:** Получить `Outline` переданной формы внутри `drawWithCache`.
- [ ] **Step 2:** Заменить прямоугольные проходы бликов и преломлений на `drawOutline(outline, brush)`.
- [ ] **Step 3:** Собрать release-ресурсы и проверить отсутствие ошибок Compose Graphics.

### Task 3: Модель конструктора иконок

**Files:**
- Create: `app/src/main/java/com/danila/nimbo/utils/CustomAppIconManager.kt`
- Create: `app/src/test/java/com/danila/nimbo/utils/AppIconCustomizationPolicyTest.kt`
- Modify: `app/src/main/java/com/danila/nimbo/utils/PreferencesManager.kt`

- [ ] **Step 1:** Добавить перечисления формы (`ROUNDED`, `SQUIRCLE`, `CIRCLE`) и облака (`ORIGINAL`, `MONOCHROME`, `OUTLINE`).
- [ ] **Step 2:** Написать тесты безопасного восстановления сохранённых значений и ограничения палитры.
- [ ] **Step 3:** Реализовать сохранение выбранной формы, цвета, варианта облака и режима пользовательского изображения.
- [ ] **Step 4:** Реализовать сборку 256×256 bitmap через Canvas с выбранной формой, фоном, облаком и опциональной Beta-плашкой.
- [ ] **Step 5:** Реализовать `requestPinShortcut()` для добавления собранного значка на рабочий стол с системным подтверждением.

### Task 4: Экран «Иконка приложения»

**Files:**
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/AppIconSettingsScreen.kt`

- [ ] **Step 1:** Сохранить сетку настоящих launcher-пресетов.
- [ ] **Step 2:** Добавить живое превью конструктора.
- [ ] **Step 3:** Добавить выбор формы, палитры и вида облака с понятным выделением выбранного значения.
- [ ] **Step 4:** Подключить выбор изображения из галереи как полноценный источник конструктора.
- [ ] **Step 5:** Добавить действия «Использовать в уведомлениях» и «Добавить ярлык на рабочий стол» и текст об ограничении Android.

### Task 5: Автоскрытие нижней панели

**Files:**
- Create: `app/src/main/java/com/danila/nimbo/ui/navigation/BottomBarScrollPolicy.kt`
- Create: `app/src/test/java/com/danila/nimbo/ui/navigation/BottomBarScrollPolicyTest.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/navigation/NavGraph.kt`
- Modify: `app/src/main/java/com/danila/nimbo/ui/screens/AppearanceSettingsScreen.kt`
- Modify: `app/src/main/java/com/danila/nimbo/utils/PreferencesManager.kt`

- [ ] **Step 1:** Написать тест направления: прокрутка вниз скрывает, вверх показывает, малые колебания игнорируются.
- [ ] **Step 2:** Добавить persisted-настройку `bottomBarAutoHideEnabled`.
- [ ] **Step 3:** Подключить root nested-scroll connection к `NavHost`.
- [ ] **Step 4:** При прокрутке вниз запускать `slideOutVertically + fadeOut`, при прокрутке вверх — немедленный `slideInVertically + fadeIn`.
- [ ] **Step 5:** После 450 мс без событий прокрутки возвращать панель автоматически.
- [ ] **Step 6:** Добавить переключатель «Скрывать панель при прокрутке» в настройки темы.

### Task 6: Версия Beta 2

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/test/java/com/danila/nimbo/network/UpdateManagerTest.kt` при необходимости метаданных.

- [ ] **Step 1:** Установить `versionCode = 6`.
- [ ] **Step 2:** Установить `versionName = "1.1.0-beta.2"`.
- [ ] **Step 3:** Убедиться, что имена APK формируются с Beta 2.

### Task 7: Проверка

**Files:**
- Verify: `app/src/main/**`
- Verify: `app/src/test/**`

- [ ] **Step 1:** Выполнить `./gradlew :app:testDebugUnitTest` и получить `BUILD SUCCESSFUL`.
- [ ] **Step 2:** Выполнить `./gradlew :app:compileReleaseKotlin :app:processReleaseResources` и получить `BUILD SUCCESSFUL`.
- [ ] **Step 3:** Проверить выходные имена, versionCode/versionName и наличие нового изображения в упакованных ресурсах.
- [ ] **Step 4:** Собрать перечень пользовательских изменений для Beta 2.
