/**
 * Оформление, запомненное с прошлого запуска.
 *
 * Настройки живут на стороне Rust и приходят асинхронно, а до их прихода
 * интерфейс рисовался значениями по умолчанию — то есть стилем Signal. На
 * мгновение появлялся чужой экран, и только потом свой: стиль меняет не
 * только цвета, но и разметку главной.
 *
 * Поэтому выбранное оформление дублируется в localStorage. Оно читается
 * синхронно, до первой отрисовки, и служит лишь заготовкой: пришедшие
 * настройки главнее и тут же его перезаписывают.
 */
const StorageKey = "nimbo.bootAppearance";

export interface BootAppearance {
  uiStyle: string;
  /** Настройка темы: `system`, `light`, `dark`, `black`. */
  themeMode: string;
  /** Тема после разрешения `system` — её и ставим атрибутом. */
  theme: string;
  navMotion: "on" | "off";
}

export function readBootAppearance(): BootAppearance | null {
  try {
    const raw = window.localStorage.getItem(StorageKey);
    if (!raw) return null;
    const value = JSON.parse(raw) as Partial<BootAppearance>;
    if (typeof value.uiStyle !== "string" || typeof value.theme !== "string") return null;
    return {
      uiStyle: value.uiStyle,
      themeMode: typeof value.themeMode === "string" ? value.themeMode : value.theme,
      theme: value.theme,
      navMotion: value.navMotion === "off" ? "off" : "on",
    };
  } catch {
    // Приватный режим, запрет на хранилище, битое значение — заготовки просто
    // не будет, интерфейс поднимется как раньше.
    return null;
  }
}

export function rememberBootAppearance(value: BootAppearance) {
  try {
    window.localStorage.setItem(StorageKey, JSON.stringify(value));
  } catch {
    // Не сохранилось — не беда: это ускорение, а не состояние приложения.
  }
}

/** Ставит запомненное оформление на body до первой отрисовки. */
export function applyBootAppearance() {
  if (typeof window === "undefined") return;
  const value = readBootAppearance();
  if (!value) return;
  document.body.dataset.uiStyle = value.uiStyle;
  document.body.dataset.theme = value.theme;
  document.body.dataset.navMotion = value.navMotion;
}
