import React from "react";
import ReactDOM from "react-dom/client";
import { getCurrentWindow } from "@tauri-apps/api/window";
import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
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
  existing_install: boolean;
  helper_installed: boolean;
  helper_running: boolean;
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

interface ProgressEvent {
  step: string;
  state: StepState;
  progress: number;
  detail: string;
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

const baseSteps: Step[] = [
  { id: "prepare", title: "Подготовка", detail: "Проверка окружения", state: "queued" },
  { id: "files", title: "Файлы", detail: "Nimbo.exe и компоненты", state: "queued" },
  { id: "tun", title: "TUN", detail: "Сетевые зависимости", state: "queued" },
  { id: "service", title: "Хелпер", detail: "Системный сервис", state: "queued" },
  { id: "shortcuts", title: "Ярлыки", detail: "Меню Пуск и рабочий стол", state: "queued" },
  { id: "registry", title: "Система", detail: "Удаление и протокол nimbo://", state: "queued" },
];

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

function progressLabel(state: StepState): string {
  switch (state) {
    case "done":
      return "Готово";
    case "running":
      return "Идет";
    case "failed":
      return "Ошибка";
    default:
      return "Ожидает";
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

function App() {
  const [probe, setProbe] = React.useState<InstallerProbe | null>(null);
  const [installDir, setInstallDir] = React.useState("");
  const [desktopShortcut, setDesktopShortcut] = React.useState(true);
  const [startMenuShortcut, setStartMenuShortcut] = React.useState(true);
  const [launchAfterInstall, setLaunchAfterInstall] = React.useState(true);
  const [steps, setSteps] = React.useState<Step[]>(baseSteps);
  const [progress, setProgress] = React.useState(0);
  const [displayedProgress, setDisplayedProgress] = React.useState(0);
  const [phase, setPhase] = React.useState<"idle" | "installing" | "done" | "failed">("idle");
  const [error, setError] = React.useState<string | null>(null);
  const [result, setResult] = React.useState<InstallResult | null>(null);
  const versionLabel = probe?.product_version ? `Версия ${probe.product_version}` : "Версия";
  const archLabel = probe?.product_arch ?? "Windows";
  const currentStep =
    steps.find((step) => step.state === "running") ??
    steps.find((step) => step.state === "failed") ??
    [...steps].reverse().find((step) => step.state === "done") ??
    steps[0];

  React.useEffect(() => {
    let frame = 0;
    let start: number | null = null;
    const initial = displayedProgress;
    const target = progress;
    if (initial === target) return;
    const duration = 600;
    const tick = (timestamp: number) => {
      if (start === null) start = timestamp;
      const t = Math.min(1, (timestamp - start) / duration);
      const eased = 1 - Math.pow(1 - t, 3);
      setDisplayedProgress(Math.round(initial + (target - initial) * eased));
      if (t < 1) frame = requestAnimationFrame(tick);
    };
    frame = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frame);
  }, [progress]);

  React.useEffect(() => {
    void invoke<InstallerProbe>("probe_installation")
      .then((value) => {
        setProbe(value);
        setInstallDir(value.default_install_dir);
      })
      .catch((err) => setError(String(err)));

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
    setSteps(baseSteps);
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
      setError(String(err));
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
      setError(`Не удалось открыть выбор папки: ${String(err)}`);
    }
  };

  const close = () => {
    void getCurrentWindow().close();
  };

  return (
    <main className="installer-shell" onMouseDown={startShellDrag}>
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
          <div>
            <div className="brand-title">Nimbo</div>
            <div className="brand-caption">Установщик</div>
          </div>
        </div>

        <div className="rail-center">
          <div
            className={`progress-orbit${phase === "done" ? " is-done" : ""}`}
            aria-hidden="true"
          >
            {phase === "done" ? (
              <svg className="progress-check" viewBox="0 0 64 64" width="68" height="68">
                <path
                  d="M18 33 L28 44 L47 22"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="6"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                />
              </svg>
            ) : (
              <div className="progress-number">{displayedProgress}%</div>
            )}
            <div className="progress-ring" style={{ "--progress": progress } as React.CSSProperties} />
          </div>

          <div
            key={currentStep.id + currentStep.state}
            className={`current-step is-${currentStep.state}`}
          >
            <div className="current-step-head">
              <span className="current-step-dot" />
              <div className="current-step-title">{currentStep.title}</div>
            </div>
            <div className="current-step-state">{progressLabel(currentStep.state)}</div>
          </div>
        </div>
      </section>

      <section className="install-panel">
        <button className="window-close" type="button" onClick={close} aria-label="Закрыть">×</button>

        {phase === "done" ? (
          <div className="done-screen no-window-drag">
            <div className="done-art" aria-hidden="true">
              <svg viewBox="0 0 64 64" width="64" height="64">
                <circle cx="32" cy="32" r="28" fill="none" stroke="currentColor" strokeWidth="3" opacity="0.25" />
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
              Всё готово. Папка установки — <span className="done-path">{result?.install_dir || installDir}</span>.
              Можно открыть Nimbo или закрыть этот установщик.
            </p>
            <div className="actions done-actions">
              <button className="ghost-button" type="button" onClick={close}>
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
              <div className="hero-badges">
                <span className="mode-pill">{archLabel}</span>
                <span className="version-pill">{versionLabel}</span>
              </div>
              <h1 className="hero-title">
                {probe?.existing_install ? "Обновление " : "Установка "}
                <span className="hero-title-accent">Nimbo</span>
              </h1>
              <p>
                {probe?.existing_install
                  ? "Обновим Nimbo до последней версии. Ваши подписки и настройки останутся на месте — нужно только подтвердить установку."
                  : "Установим Nimbo за минуту. Подготовим приложение, сетевые компоненты и ярлыки — после этого можно сразу подключаться."}
              </p>
            </div>

            <div className="status-grid no-window-drag">
              <div className="status-tile path-tile">
                <div className="status-title-row">
                  <span className="status-kicker">Папка установки</span>
                  <button
                    className="folder-button"
                    type="button"
                    onClick={chooseInstallDir}
                    disabled={phase === "installing"}
                  >
                    Выбрать
                  </button>
                </div>
                <textarea
                  value={installDir}
                  disabled={phase === "installing"}
                  onChange={(event) => setInstallDir(event.target.value.replace(/[\r\n]+/g, ""))}
                  aria-label="Папка установки"
                  spellCheck={false}
                  rows={1}
                  wrap="off"
                />
              </div>
              <div className="status-tile compact">
                <span className="status-kicker">Хелпер</span>
                <strong>{probe?.helper_running ? "Запущен" : probe?.helper_installed ? "Установлен" : "Будет установлен"}</strong>
              </div>
            </div>

            <div className="option-row no-window-drag">
              <label className="toggle">
                <input
                  type="checkbox"
                  checked={startMenuShortcut}
                  disabled={phase === "installing"}
                  onChange={(event) => setStartMenuShortcut(event.target.checked)}
                />
                <span />
                Меню Пуск
              </label>
              <label className="toggle">
                <input
                  type="checkbox"
                  checked={desktopShortcut}
                  disabled={phase === "installing"}
                  onChange={(event) => setDesktopShortcut(event.target.checked)}
                />
                <span />
                Рабочий стол
              </label>
              <label className="toggle">
                <input
                  type="checkbox"
                  checked={launchAfterInstall}
                  disabled={phase === "installing"}
                  onChange={(event) => setLaunchAfterInstall(event.target.checked)}
                />
                <span />
                Открыть после установки
              </label>
            </div>

            <div className="detail-card no-window-drag">
              <div className="detail-head">
                <span>{phase === "failed" ? "Нужно внимание" : "Ход установки"}</span>
                <b>{displayedProgress}%</b>
              </div>
              <div className="meter"><span style={{ width: `${progress}%` }} /></div>
              <div className="detail-lines">
                {steps.map((step) => (
                  <div key={step.id} className={`detail-line detail-${step.state}`}>
                    <span>{step.title}</span>
                    <small>{step.detail}</small>
                  </div>
                ))}
              </div>
            </div>

            {error && <div className="error-box no-window-drag">{error}</div>}

            <div className="actions no-window-drag">
              <button className="ghost-button" type="button" onClick={close} disabled={phase === "installing"}>
                Закрыть
              </button>
              <button className="primary-button" type="button" onClick={install} disabled={phase === "installing" || !installDir.trim()}>
                {phase === "installing" ? "Устанавливаем..." : probe?.existing_install ? "Установить обновление" : "Установить"}
              </button>
            </div>
          </>
        )}
      </section>
    </main>
  );
}

ReactDOM.createRoot(document.getElementById("root")!).render(<App />);
