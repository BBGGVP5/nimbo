import React from "react";
import ReactDOM from "react-dom/client";
import { getCurrentWindow } from "@tauri-apps/api/window";
import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import { PhysicalSize } from "@tauri-apps/api/dpi";
import nimboLogo from "../../ui/src/assets/nimbo.png";
import "./styles.css";

type StepState = "queued" | "running" | "done" | "failed";

interface Step {
  id: string;
  title: string;
  detail: string;
  state: StepState;
}

interface InstallerProbe {
  default_install_dir: string;
  product_version: string;
  product_arch: string;
  platform: "windows" | "linux" | string;
  existing_install: boolean;
  helper_installed: boolean;
  helper_running: boolean;
}

interface UninstallerProbe {
  install_dir: string;
  product_version: string;
  product_arch: string;
  platform: "windows" | "linux" | string;
  helper_installed: boolean;
  helper_running: boolean;
  user_data_dir: string;
  user_data_present: boolean;
}

interface InstallOptions {
  install_dir: string;
  start_menu_shortcut: boolean;
  desktop_shortcut: boolean;
  launch_after_install: boolean;
}

interface InstallResult {
  install_dir: string;
  app_exe: string;
}

interface UninstallOptions {
  remove_user_data: boolean;
}

interface UninstallResult {
  install_dir: string;
  removed_user_data: boolean;
}

interface ProgressEvent {
  step: string;
  state: StepState;
  progress: number;
  detail: string;
}

interface AppTheme {
  ui_style?: string | null;
  theme_mode?: string | null;
  accent_mode?: string | null;
  accent_color?: string | null;
}

type InstallerMode = "install" | "uninstall";

function resolveThemeMode(mode: string | null | undefined): "light" | "dark" | "black" {
  if (mode === "light" || mode === "dark" || mode === "black") return mode;
  if (mode === "system") {
    if (typeof window !== "undefined" && window.matchMedia?.("(prefers-color-scheme: light)").matches) {
      return "light";
    }
    return "dark";
  }
  return "dark";
}

function themeSignature(theme: AppTheme | null | undefined): string {
  if (!theme) return "null";
  return [
    theme.ui_style ?? "",
    theme.theme_mode ?? "",
    theme.accent_mode ?? "",
    theme.accent_color ?? "",
  ].join("|");
}

function applyAppTheme(theme: AppTheme | null | undefined) {
  const root = document.body;
  if (!root) return;
  const mode = resolveThemeMode(theme?.theme_mode);
  root.dataset.theme = mode;
  const uiStyle = theme?.ui_style === "material_you" ? "material_you" : "nebula";
  root.dataset.uiStyle = uiStyle;
  const accent = typeof theme?.accent_color === "string" && /^#[0-9a-f]{6}$/i.test(theme.accent_color)
    ? theme.accent_color
    : "#7c5dfa";
  root.style.setProperty("--accent", accent);
}

type ResizeDirection =
  | "East"
  | "North"
  | "NorthEast"
  | "NorthWest"
  | "South"
  | "SouthEast"
  | "SouthWest"
  | "West";

const DEFAULT_WINDOW_WIDTH = 1080;
const DEFAULT_WINDOW_HEIGHT = 680;
const MIN_RESTORED_WINDOW_WIDTH = 780;
const MIN_RESTORED_WINDOW_HEIGHT = 520;

type InstallerPhase = "idle" | "installing" | "done" | "failed";
type UninstallerPhase = "idle" | "uninstalling" | "done" | "failed";

function createSteps(platform: string | undefined): Step[] {
  if (platform === "linux") {
    return [
      { id: "prepare", title: "Подготовка", detail: "Проверка окружения", state: "queued" },
      { id: "files", title: "Файлы", detail: "Nimbo и ресурсы", state: "queued" },
      { id: "integrate", title: "Интеграция", detail: "desktop entry и nimbo://", state: "queued" },
      { id: "shortcuts", title: "Ярлыки", detail: "Меню приложений и рабочий стол", state: "queued" },
      { id: "registry", title: "Финиш", detail: "Проверка установки", state: "queued" },
    ];
  }

  return [
    { id: "prepare", title: "Подготовка", detail: "Проверка окружения", state: "queued" },
    { id: "files", title: "Файлы", detail: "Nimbo.exe и компоненты", state: "queued" },
    { id: "tun", title: "TUN", detail: "Сетевые зависимости", state: "queued" },
    { id: "service", title: "Хелпер", detail: "Системный сервис", state: "queued" },
    { id: "shortcuts", title: "Ярлыки", detail: "Меню Пуск и рабочий стол", state: "queued" },
    { id: "registry", title: "Система", detail: "Удаление и протокол nimbo://", state: "queued" },
  ];
}

function createUninstallSteps(): Step[] {
  return [
    { id: "prepare", title: "Подготовка", detail: "Останавливаем процессы и хелпер", state: "queued" },
    { id: "shortcuts", title: "Ярлыки", detail: "Меню Пуск и рабочий стол", state: "queued" },
    { id: "registry", title: "Система", detail: "Записи реестра и nimbo://", state: "queued" },
    { id: "files", title: "Файлы", detail: "Nimbo.exe, хелпер, TUN-компоненты", state: "queued" },
    { id: "user_data", title: "Данные", detail: "Подписки и настройки", state: "queued" },
    { id: "finish", title: "Финиш", detail: "Удаление папки установки", state: "queued" },
  ];
}

const resizeHandles: Array<{ direction: ResizeDirection; className: string }> = [
  { direction: "North", className: "resize-n" },
  { direction: "South", className: "resize-s" },
  { direction: "East", className: "resize-e" },
  { direction: "West", className: "resize-w" },
  { direction: "NorthEast", className: "resize-ne" },
  { direction: "NorthWest", className: "resize-nw" },
  { direction: "SouthEast", className: "resize-se" },
  { direction: "SouthWest", className: "resize-sw" },
];

const previewProbe: InstallerProbe = {
  default_install_dir: "C:\\Users\\User\\AppData\\Local\\Programs\\Nimbo",
  product_version: "1.0.0",
  product_arch: "Windows x64",
  platform: "windows",
  existing_install: false,
  helper_installed: false,
  helper_running: false,
};

const previewUninstallProbe: UninstallerProbe = {
  install_dir: "C:\\Users\\User\\AppData\\Local\\Programs\\Nimbo",
  product_version: "1.0.0",
  product_arch: "Windows x64",
  platform: "windows",
  helper_installed: true,
  helper_running: true,
  user_data_dir: "C:\\Users\\User\\AppData\\Roaming\\Nimbo",
  user_data_present: true,
};

function progressLabel(state: StepState, runningLabel: string): string {
  switch (state) {
    case "done":
      return "Готово";
    case "running":
      return runningLabel;
    case "failed":
      return "Ошибка";
    default:
      return "Ожидает";
  }
}

function StepIcon({ state }: { state: StepState }) {
  return (
    <span className={`step-icon step-icon-${state}`} aria-hidden="true">
      {state === "done" ? (
        <svg viewBox="0 0 20 20" width="14" height="14">
          <path
            d="M5 10.5 L8.6 14 L15 6.5"
            fill="none"
            stroke="currentColor"
            strokeWidth="2.4"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
      ) : state === "failed" ? (
        <svg viewBox="0 0 20 20" width="14" height="14">
          <path
            d="M6 6 L14 14 M14 6 L6 14"
            fill="none"
            stroke="currentColor"
            strokeWidth="2.4"
            strokeLinecap="round"
          />
        </svg>
      ) : state === "running" ? (
        <span className="step-icon-spinner" />
      ) : (
        <span className="step-icon-dot" />
      )}
    </span>
  );
}

function isMissingTauriBridge(error: unknown): boolean {
  const message = String(error);
  return (
    message.includes("reading 'invoke'") ||
    message.includes("__TAURI_INTERNALS__") ||
    message.includes("Tauri API")
  );
}

function formatInstallerError(error: unknown): string {
  if (isMissingTauriBridge(error)) {
    return "Запустите файл из окна Nimbo Setup. В браузере доступен только предпросмотр интерфейса.";
  }
  return String(error);
}

function installerPhaseLabel(phase: InstallerPhase): string {
  switch (phase) {
    case "installing":
      return "Установка Nimbo";
    case "done":
      return "Готово";
    case "failed":
      return "Нужно внимание";
    default:
      return "Готов к запуску";
  }
}

function uninstallerPhaseLabel(phase: UninstallerPhase): string {
  switch (phase) {
    case "uninstalling":
      return "Удаление Nimbo";
    case "done":
      return "Готово";
    case "failed":
      return "Нужно внимание";
    default:
      return "Готов к удалению";
  }
}

function startWindowDrag(event: React.MouseEvent<HTMLElement>) {
  if (event.button !== 0) {
    return;
  }
  void getCurrentWindow().startDragging();
}

function startShellDrag(event: React.MouseEvent<HTMLElement>) {
  if (event.currentTarget !== event.target) {
    return;
  }
  startWindowDrag(event);
}

function startResizeDragging(direction: ResizeDirection, event: React.MouseEvent<HTMLDivElement>) {
  event.preventDefault();
  event.stopPropagation();
  if (event.button !== 0) {
    return;
  }
  void getCurrentWindow().startResizeDragging(direction);
}

// Theme polling shared between install + uninstall views.
function useAppThemeSync() {
  React.useEffect(() => {
    applyAppTheme(null);
    let cancelled = false;
    let lastSignature = themeSignature(null);
    let mediaListener: ((event: MediaQueryListEvent) => void) | null = null;
    let mediaQuery: MediaQueryList | null = null;
    let lastTheme: AppTheme | null = null;

    const sync = async () => {
      try {
        const value = await invoke<AppTheme>("read_app_theme");
        if (cancelled) return;
        const sig = themeSignature(value);
        if (sig !== lastSignature) {
          lastSignature = sig;
          lastTheme = value;
          applyAppTheme(value);
        }
      } catch {
        // ignore (file may not exist yet or Tauri bridge unavailable in browser preview)
      }
    };

    void sync();
    const interval = window.setInterval(sync, 1000);

    if (typeof window !== "undefined" && window.matchMedia) {
      mediaQuery = window.matchMedia("(prefers-color-scheme: light)");
      mediaListener = () => {
        if (lastTheme?.theme_mode === "system") {
          applyAppTheme(lastTheme);
        }
      };
      mediaQuery.addEventListener?.("change", mediaListener);
    }

    return () => {
      cancelled = true;
      window.clearInterval(interval);
      if (mediaQuery && mediaListener) {
        mediaQuery.removeEventListener?.("change", mediaListener);
      }
    };
  }, []);
}

function useAnimatedProgress(progress: number): number {
  const [displayed, setDisplayed] = React.useState(0);
  React.useEffect(() => {
    let frame = 0;
    let start: number | null = null;
    const initial = displayed;
    const target = progress;
    if (initial === target) return;
    const duration = 600;
    const tick = (timestamp: number) => {
      if (start === null) start = timestamp;
      const t = Math.min(1, (timestamp - start) / duration);
      const eased = 1 - Math.pow(1 - t, 3);
      setDisplayed(Math.round(initial + (target - initial) * eased));
      if (t < 1) frame = requestAnimationFrame(tick);
    };
    frame = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frame);
  }, [progress]);
  return displayed;
}

function Shell({
  phaseClass,
  phaseSubline,
  steps,
  currentStep,
  currentStepIndex,
  progress,
  displayedProgress,
  brandCaption,
  runningStepLabel,
  isDone,
  children,
}: {
  phaseClass: string;
  phaseSubline: string;
  steps: Step[];
  currentStep: Step;
  currentStepIndex: number;
  progress: number;
  displayedProgress: number;
  brandCaption: string;
  runningStepLabel: string;
  isDone: boolean;
  children: React.ReactNode;
}) {
  const shellStyle = { "--progress": displayedProgress } as React.CSSProperties;
  const completedSteps = steps.filter((step) => step.state === "done").length;

  return (
    <main className={`installer-shell ${phaseClass}`} style={shellStyle} onMouseDown={startShellDrag}>
      <div className="ambient-glow" aria-hidden="true" />

      {resizeHandles.map((handle) => (
        <div
          key={handle.direction}
          className={`resize-handle ${handle.className}`}
          data-resize-handle
          onMouseDown={(event) => startResizeDragging(handle.direction, event)}
        />
      ))}

      <section className="side-rail" onMouseDown={startWindowDrag}>
        <div className="brand">
          <img src={nimboLogo} alt="" className="brand-logo" />
          <div className="brand-text">
            <div className="brand-title">Nimbo</div>
            <div className="brand-caption">{brandCaption}</div>
          </div>
        </div>

        <div className="rail-center">
          <div
            className={`progress-orbit${isDone ? " is-done" : ""}`}
            aria-hidden="true"
          >
            {isDone ? (
              <svg className="progress-check" viewBox="0 0 64 64" width="56" height="56">
                <path
                  d="M19 33 L28 42 L46 23"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="5"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                />
              </svg>
            ) : (
              <div className="progress-number">{displayedProgress}<span>%</span></div>
            )}
            <div className="progress-ring" style={{ "--progress": progress } as React.CSSProperties} />
          </div>

          <div className="progress-subline">
            <span>{phaseSubline}</span>
            <em>{completedSteps}/{steps.length}</em>
          </div>

          <div
            key={currentStep.id + currentStep.state}
            className={`current-step is-${currentStep.state}`}
          >
            <div className="current-step-dot" />
            <div className="current-step-body">
              <div className="current-step-title">{currentStep.title}</div>
              <div className="current-step-state">{progressLabel(currentStep.state, runningStepLabel)}</div>
            </div>
          </div>
        </div>

        <div className="rail-foot" aria-hidden="true">
          {steps.map((step, index) => (
            <span
              key={step.id}
              className={`rail-step rail-step-${step.state}${index === currentStepIndex ? " is-active" : ""}`}
              style={{ "--step-index": index } as React.CSSProperties}
            />
          ))}
        </div>
      </section>

      <section className="install-panel" onMouseDown={startShellDrag}>
        {children}
      </section>
    </main>
  );
}

function InstallApp() {
  const [probe, setProbe] = React.useState<InstallerProbe | null>(null);
  const [installDir, setInstallDir] = React.useState("");
  const [desktopShortcut, setDesktopShortcut] = React.useState(true);
  const [startMenuShortcut, setStartMenuShortcut] = React.useState(true);
  const [launchAfterInstall, setLaunchAfterInstall] = React.useState(true);
  const [steps, setSteps] = React.useState<Step[]>(() => createSteps("windows"));
  const [progress, setProgress] = React.useState(0);
  const [phase, setPhase] = React.useState<InstallerPhase>("idle");
  const [error, setError] = React.useState<string | null>(null);
  const [result, setResult] = React.useState<InstallResult | null>(null);
  const versionLabel = probe?.product_version ? `v${probe.product_version}` : "v—";
  const archLabel = probe?.product_arch ?? "Windows";
  const isLinux = probe?.platform === "linux";
  const stepsTemplate = React.useMemo(() => createSteps(probe?.platform), [probe?.platform]);
  const displayedProgress = useAnimatedProgress(progress);
  const currentStep =
    steps.find((step) => step.state === "running") ??
    steps.find((step) => step.state === "failed") ??
    [...steps].reverse().find((step) => step.state === "done") ??
    steps[0];
  const currentStepIndex = Math.max(0, steps.findIndex((step) => step.id === currentStep.id));

  React.useEffect(() => {
    void invoke<InstallerProbe>("probe_installation")
      .then((value) => {
        setProbe(value);
        setInstallDir(value.default_install_dir);
        setSteps(createSteps(value.platform));
      })
      .catch((err) => {
        if (isMissingTauriBridge(err)) {
          setProbe(previewProbe);
          setInstallDir(previewProbe.default_install_dir);
          setSteps(createSteps(previewProbe.platform));
          return;
        }
        setError(formatInstallerError(err));
      });

    let unlisten: (() => void) | null = null;
    void listen<ProgressEvent>("installer_progress", (event) => {
      const update = event.payload;
      setProgress(Math.max(0, Math.min(100, Math.round(update.progress))));
      setSteps((current) =>
        current.map((step) =>
          step.id === update.step
            ? { ...step, state: update.state, detail: update.detail || step.detail }
            : step,
        ),
      );
      if (update.state === "failed") {
        setPhase("failed");
        setError(update.detail);
      }
    }).then((fn) => {
      unlisten = fn;
    });
    return () => {
      if (unlisten) unlisten();
    };
  }, []);

  const install = async () => {
    setPhase("installing");
    setError(null);
    setResult(null);
    setProgress(0);
    setSteps(stepsTemplate);
    try {
      const value = await invoke<InstallResult>("install_nimbo", {
        options: {
          install_dir: installDir,
          start_menu_shortcut: startMenuShortcut,
          desktop_shortcut: desktopShortcut,
          launch_after_install: launchAfterInstall,
        } satisfies InstallOptions,
      });
      setResult(value);
      setProgress(100);
      setPhase("done");
    } catch (err) {
      setError(formatInstallerError(err));
      setPhase("failed");
    }
  };

  const openInstalled = () => {
    void invoke("open_nimbo", { installDir: result?.install_dir || installDir });
    void getCurrentWindow().close();
  };

  const chooseInstallDir = async () => {
    if (phase === "installing") {
      return;
    }
    setError(null);
    try {
      const selected = await invoke<string | null>("choose_install_dir", { currentDir: installDir });
      if (selected) {
        setInstallDir(selected);
      }
    } catch (err) {
      setError(`Не удалось открыть выбор папки: ${formatInstallerError(err)}`);
    }
  };

  const close = () => {
    void getCurrentWindow().close();
  };

  return (
    <Shell
      phaseClass={`phase-${phase}`}
      phaseSubline={installerPhaseLabel(phase)}
      steps={steps}
      currentStep={currentStep}
      currentStepIndex={currentStepIndex}
      progress={progress}
      displayedProgress={displayedProgress}
      brandCaption="Установщик"
      runningStepLabel="Установка"
      isDone={phase === "done"}
    >
      <button className="window-close" type="button" onClick={close} aria-label="Закрыть">×</button>

      {phase === "done" ? (
        <div className="done-screen no-window-drag">
          <div className="done-art" aria-hidden="true">
            <svg viewBox="0 0 64 64" width="56" height="56">
              <path
                d="M20 33 L29 42 L45 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="4"
                strokeLinecap="round"
                strokeLinejoin="round"
                className="done-check"
              />
            </svg>
          </div>
          <h1>Nimbo установлен</h1>
          <p>
            Папка установки — <span className="done-path">{result?.install_dir || installDir}</span>.
          </p>
          <div className="actions done-actions">
            <button className="ghost-button close-action-button" type="button" onClick={close}>
              Закрыть
            </button>
            <button className="primary-button success" type="button" onClick={openInstalled}>
              Открыть Nimbo
            </button>
          </div>
        </div>
      ) : (
        <>
          <div className="hero" onMouseDown={startWindowDrag}>
            <div className="hero-meta">
              <span>{probe?.existing_install ? "Обновление" : "Установка"}</span>
              <i />
              <span>{archLabel}</span>
              <i />
              <span>{versionLabel}</span>
            </div>
            <h1 className="hero-title">
              {probe?.existing_install ? "Обновление Nimbo" : "Установка Nimbo"}
            </h1>
            <p>
              {probe?.existing_install
                ? "Подписки и настройки сохранятся — подтвердите установку, чтобы получить последнюю версию."
                : isLinux
                  ? "Подготовим приложение, desktop entry и протокол nimbo://. Займёт меньше минуты."
                  : "Подготовим приложение, сетевые компоненты и ярлыки. Займёт меньше минуты."}
            </p>
          </div>

          <div className="path-row no-window-drag">
            <div className="path-row-label">
              <span className="status-kicker">Папка установки</span>
              <span className="path-row-helper">
                {isLinux
                  ? "Интеграция: будет настроена"
                  : probe?.helper_running
                    ? "Хелпер: запущен"
                    : probe?.helper_installed
                      ? "Хелпер: установлен"
                      : "Хелпер: будет установлен"}
              </span>
            </div>
            <div className="path-row-field">
              <textarea
                value={installDir}
                disabled={phase === "installing"}
                onChange={(event) => setInstallDir(event.target.value.replace(/[\r\n]+/g, ""))}
                aria-label="Папка установки"
                spellCheck={false}
                rows={1}
                wrap="off"
              />
              <button
                className="folder-button"
                type="button"
                onClick={chooseInstallDir}
                disabled={phase === "installing"}
              >
                Выбрать
              </button>
            </div>
          </div>

          <div className="option-row no-window-drag">
            <label className="toggle">
              <span className="toggle-text">{isLinux ? "Меню приложений" : "Меню Пуск"}</span>
              <input
                type="checkbox"
                checked={startMenuShortcut}
                disabled={phase === "installing"}
                onChange={(event) => setStartMenuShortcut(event.target.checked)}
              />
              <span className="toggle-switch" />
            </label>
            <label className="toggle">
              <span className="toggle-text">Рабочий стол</span>
              <input
                type="checkbox"
                checked={desktopShortcut}
                disabled={phase === "installing"}
                onChange={(event) => setDesktopShortcut(event.target.checked)}
              />
              <span className="toggle-switch" />
            </label>
            <label className="toggle">
              <span className="toggle-text">Открыть после установки</span>
              <input
                type="checkbox"
                checked={launchAfterInstall}
                disabled={phase === "installing"}
                onChange={(event) => setLaunchAfterInstall(event.target.checked)}
              />
              <span className="toggle-switch" />
            </label>
          </div>

          <div className="detail-card no-window-drag">
            <div className="detail-head">
              <span>{phase === "failed" ? "Нужно внимание" : "Ход установки"}</span>
              <b>{displayedProgress}%</b>
            </div>
            <div className="meter"><span style={{ width: `${progress}%` }} /></div>
            <div className="detail-lines">
              {steps.map((step, index) => {
                const isCurrent = index === currentStepIndex && phase !== "idle";
                return (
                  <div
                    key={step.id}
                    className={`detail-line detail-${step.state}${isCurrent ? " is-current" : ""}`}
                    style={{ "--step-index": index } as React.CSSProperties}
                  >
                    <StepIcon state={step.state} />
                    <div className="detail-line-text">
                      <span>{step.title}</span>
                      <small>{step.detail}</small>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>

          {error && <div className="error-box no-window-drag">{error}</div>}

          <div className="actions no-window-drag">
            <button className="ghost-button close-action-button" type="button" onClick={close} disabled={phase === "installing"}>
              Закрыть
            </button>
            <button className={`primary-button${phase === "installing" ? " is-busy" : ""}`} type="button" onClick={install} disabled={phase === "installing" || !installDir.trim()}>
              {phase === "installing" ? "Устанавливаем…" : probe?.existing_install ? "Установить обновление" : "Установить"}
            </button>
          </div>
        </>
      )}
    </Shell>
  );
}

function UninstallApp() {
  const [probe, setProbe] = React.useState<UninstallerProbe | null>(null);
  const [removeUserData, setRemoveUserData] = React.useState(false);
  const [steps, setSteps] = React.useState<Step[]>(() => createUninstallSteps());
  const [progress, setProgress] = React.useState(0);
  const [phase, setPhase] = React.useState<UninstallerPhase>("idle");
  const [error, setError] = React.useState<string | null>(null);
  const [result, setResult] = React.useState<UninstallResult | null>(null);
  const versionLabel = probe?.product_version ? `v${probe.product_version}` : "v—";
  const archLabel = probe?.product_arch ?? "Windows";
  const displayedProgress = useAnimatedProgress(progress);
  const currentStep =
    steps.find((step) => step.state === "running") ??
    steps.find((step) => step.state === "failed") ??
    [...steps].reverse().find((step) => step.state === "done") ??
    steps[0];
  const currentStepIndex = Math.max(0, steps.findIndex((step) => step.id === currentStep.id));

  React.useEffect(() => {
    void invoke<UninstallerProbe>("probe_uninstallation")
      .then((value) => setProbe(value))
      .catch((err) => {
        if (isMissingTauriBridge(err)) {
          setProbe(previewUninstallProbe);
          return;
        }
        setError(formatInstallerError(err));
      });

    let unlisten: (() => void) | null = null;
    void listen<ProgressEvent>("uninstaller_progress", (event) => {
      const update = event.payload;
      setProgress(Math.max(0, Math.min(100, Math.round(update.progress))));
      setSteps((current) =>
        current.map((step) =>
          step.id === update.step
            ? { ...step, state: update.state, detail: update.detail || step.detail }
            : step,
        ),
      );
      if (update.state === "failed") {
        setPhase("failed");
        setError(update.detail);
      }
    }).then((fn) => {
      unlisten = fn;
    });
    return () => {
      if (unlisten) unlisten();
    };
  }, []);

  const uninstall = async () => {
    setPhase("uninstalling");
    setError(null);
    setResult(null);
    setProgress(0);
    setSteps(createUninstallSteps());
    try {
      const value = await invoke<UninstallResult>("uninstall_nimbo", {
        options: { remove_user_data: removeUserData } satisfies UninstallOptions,
      });
      setResult(value);
      setProgress(100);
      setPhase("done");
    } catch (err) {
      setError(formatInstallerError(err));
      setPhase("failed");
    }
  };

  const close = () => {
    void getCurrentWindow().close();
  };

  const userDataDisabled = probe ? !probe.user_data_present : false;

  return (
    <Shell
      phaseClass={`phase-${phase}`}
      phaseSubline={uninstallerPhaseLabel(phase)}
      steps={steps}
      currentStep={currentStep}
      currentStepIndex={currentStepIndex}
      progress={progress}
      displayedProgress={displayedProgress}
      brandCaption="Удаление"
      runningStepLabel="Удаление"
      isDone={phase === "done"}
    >
      <button className="window-close" type="button" onClick={close} aria-label="Закрыть">×</button>

      {phase === "done" ? (
        <div className="done-screen no-window-drag">
          <div className="done-art" aria-hidden="true">
            <svg viewBox="0 0 64 64" width="56" height="56">
              <path
                d="M20 33 L29 42 L45 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="4"
                strokeLinecap="round"
                strokeLinejoin="round"
                className="done-check"
              />
            </svg>
          </div>
          <h1>Nimbo удалён</h1>
          <p>
            {result?.removed_user_data
              ? "Папка установки и пользовательские данные удалены."
              : (
                <>
                  Подписки и настройки остались в{" "}
                  <span className="done-path">{probe?.user_data_dir}</span>.
                </>
              )}
          </p>
          <div className="actions done-actions">
            <button className="primary-button success" type="button" onClick={close}>
              Закрыть
            </button>
          </div>
        </div>
      ) : (
        <>
          <div className="hero" onMouseDown={startWindowDrag}>
            <div className="hero-meta">
              <span>Удаление</span>
              <i />
              <span>{archLabel}</span>
              <i />
              <span>{versionLabel}</span>
            </div>
            <h1 className="hero-title">Удаление Nimbo</h1>
            <p>
              Остановим хелпер, удалим ярлыки и файлы. Подписки и настройки можно сохранить или
              удалить ниже.
            </p>
          </div>

          <div className="path-row no-window-drag">
            <div className="path-row-label">
              <span className="status-kicker">Папка установки</span>
              <span className="path-row-helper">
                {probe?.helper_running
                  ? "Хелпер: запущен"
                  : probe?.helper_installed
                    ? "Хелпер: установлен"
                    : "Хелпер: не установлен"}
              </span>
            </div>
            <div className="path-row-field">
              <textarea
                value={probe?.install_dir ?? ""}
                disabled
                aria-label="Папка установки"
                spellCheck={false}
                rows={1}
                wrap="off"
              />
            </div>
          </div>

          <div className="option-row option-row-single no-window-drag">
            <label
              className="toggle"
              title={
                userDataDisabled
                  ? "В AppData нет сохранённых данных Nimbo"
                  : `Удалить ${probe?.user_data_dir ?? "AppData\\Nimbo"}`
              }
            >
              <span className="toggle-text">Удалить подписки и настройки</span>
              <input
                type="checkbox"
                checked={removeUserData}
                disabled={phase === "uninstalling" || userDataDisabled}
                onChange={(event) => setRemoveUserData(event.target.checked)}
              />
              <span className="toggle-switch" />
            </label>
          </div>

          <div className="detail-card no-window-drag">
            <div className="detail-head">
              <span>{phase === "failed" ? "Нужно внимание" : "Ход удаления"}</span>
              <b>{displayedProgress}%</b>
            </div>
            <div className="meter"><span style={{ width: `${progress}%` }} /></div>
            <div className="detail-lines">
              {steps.map((step, index) => {
                const isCurrent = index === currentStepIndex && phase !== "idle";
                return (
                  <div
                    key={step.id}
                    className={`detail-line detail-${step.state}${isCurrent ? " is-current" : ""}`}
                    style={{ "--step-index": index } as React.CSSProperties}
                  >
                    <StepIcon state={step.state} />
                    <div className="detail-line-text">
                      <span>{step.title}</span>
                      <small>{step.detail}</small>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>

          {error && <div className="error-box no-window-drag">{error}</div>}

          <div className="actions no-window-drag">
            <button className="ghost-button close-action-button" type="button" onClick={close} disabled={phase === "uninstalling"}>
              Отмена
            </button>
            <button
              className={`primary-button danger${phase === "uninstalling" ? " is-busy" : ""}`}
              type="button"
              onClick={uninstall}
              disabled={phase === "uninstalling"}
            >
              {phase === "uninstalling" ? "Удаляем…" : "Удалить Nimbo"}
            </button>
          </div>
        </>
      )}
    </Shell>
  );
}

function Root() {
  const [mode, setMode] = React.useState<InstallerMode | null>(null);
  useAppThemeSync();

  React.useEffect(() => {
    void invoke<InstallerMode>("get_installer_mode")
      .then((value) => setMode(value === "uninstall" ? "uninstall" : "install"))
      .catch(() => {
        // Tauri bridge missing (browser preview) — default to install.
        setMode("install");
      });
  }, []);

  React.useEffect(() => {
    const preventContextMenu = (event: MouseEvent) => event.preventDefault();
    document.addEventListener("contextmenu", preventContextMenu);

    // 1. Restore window size if saved
    const savedWidth = localStorage.getItem("nimbo.setup.windowWidth");
    const savedHeight = localStorage.getItem("nimbo.setup.windowHeight");
    if (savedWidth && savedHeight) {
      const w = parseInt(savedWidth, 10);
      const h = parseInt(savedHeight, 10);
      if (!isNaN(w) && !isNaN(h)) {
        const width = Math.min(Math.max(w, MIN_RESTORED_WINDOW_WIDTH), DEFAULT_WINDOW_WIDTH);
        const height = Math.min(Math.max(h, MIN_RESTORED_WINDOW_HEIGHT), DEFAULT_WINDOW_HEIGHT);
        void getCurrentWindow().setSize(new PhysicalSize(width, height)).catch(() => undefined);
      }
    }

    // 2. Listen to resize to save size
    let unlisten: (() => void) | null = null;
    const setupResizeListener = async () => {
      try {
        const appWindow = getCurrentWindow();
        const unsub = await appWindow.onResized(async () => {
          const size = await appWindow.innerSize();
          const isMaximized = await appWindow.isMaximized();
          const isMinimized = await appWindow.isMinimized();
          if (!isMaximized && !isMinimized && size.width > 200 && size.height > 200) {
            localStorage.setItem("nimbo.setup.windowWidth", size.width.toString());
            localStorage.setItem("nimbo.setup.windowHeight", size.height.toString());
          }
        });
        unlisten = unsub;
      } catch (err) {
        console.error("Failed to setup resize listener", err);
      }
    };
    void setupResizeListener();

    return () => {
      document.removeEventListener("contextmenu", preventContextMenu);
      if (unlisten) unlisten();
    };
  }, []);

  if (mode === null) {
    return null;
  }
  return mode === "uninstall" ? <UninstallApp /> : <InstallApp />;
}

ReactDOM.createRoot(document.getElementById("root")!).render(<Root />);
