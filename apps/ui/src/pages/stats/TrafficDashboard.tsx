import { AdBlockingControl } from "../../components/AdBlockingControl";
import { formatBytes, type TrafficStats } from "../../lib/api";
import { useMessages } from "../../lib/i18n";
import { trafficDashboardValues, type StatsRange } from "../../lib/statisticsPresentation";
import "./traffic-dashboard.css";

export function TrafficDashboard({ stats, range, connected, speedAvailable }: {
  stats: TrafficStats | null; range: StatsRange; connected: boolean; speedAvailable: boolean;
}) {
  const m = useMessages();
  const ru = m.common.locale.startsWith("ru");
  const t = (r: string, e: string) => ru ? r : e;
  const values = trafficDashboardValues(stats, range, connected, speedAvailable);
  const bytes = (value: number | null) => value === null ? "—" : formatBytes(value);
  const route = values.route;
  const share = route && route.total > 0 ? route.proxy / route.total : null;
  const percentage = (value: number) => new Intl.NumberFormat(m.common.locale, { style:"percent", maximumFractionDigits:1 }).format(value);
  const scope = range === "session" ? t("Текущая сессия", "Current session") : range === "month" ? t("Текущий месяц", "This month") : t("За всё время", "All time");
  const routeHint = range !== "session"
    ? t("Разбивка маршрутов доступна только для текущей сессии.", "Route breakdown is available for the current session only.")
    : !connected ? t("Подключитесь, чтобы увидеть измерения маршрутов.", "Connect to see route measurements.")
    : !route ? t("Ядро не предоставило счётчики маршрутов.", "The core has not reported route counters.")
    : route.total === 0 ? t("Счётчики доступны. Трафика пока нет.", "Counters are available. No traffic yet.")
    : t("Измеренные байты через Nimbo · текущая сессия", "Measured bytes handled by Nimbo · current session");

  return <div className="traffic-dashboard">
    <div className="traffic-volume-grid">
      {(["upload", "download"] as const).map(direction => <section className={`traffic-volume-card traffic-${direction}`} key={direction} aria-label={direction === "upload" ? m.statistics.uploaded : m.statistics.received}>
        <div className="traffic-volume-heading"><span className="traffic-direction-icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"><path d={direction === "upload" ? "M12 19V5m-6 6 6-6 6 6" : "M12 5v14m-6-6 6 6 6-6"}/></svg></span><h2>{direction === "upload" ? m.statistics.uploaded : m.statistics.received}</h2><span className="traffic-live-label">{t("Сейчас", "Now")}</span></div>
        <strong className="traffic-volume-value" data-testid={`${direction}-total`}>{bytes(values[direction])}</strong>
        <div className="traffic-volume-footer"><span>{scope}</span><strong data-testid={`${direction}-speed`}>{bytes(values[direction === "upload" ? "uploadSpeed" : "downloadSpeed"])}{values[direction === "upload" ? "uploadSpeed" : "downloadSpeed"] !== null && "/s"}</strong></div>
      </section>)}
    </div>
    <div className="traffic-detail-grid">
      <section className="traffic-route-card" aria-label={t("Трафик по маршрутам", "Traffic by route")}>
        <div className="traffic-card-heading"><h2>{t("Трафик по маршрутам", "Traffic by route")}</h2><span>{t("Сессия", "Session")}</span></div>
        <div className="traffic-route-body">
          <div className="traffic-ring" data-state={!route ? "unavailable" : share === null ? "empty" : "measured"}>
            <svg viewBox="0 0 160 160" role="img" aria-label={share === null ? routeHint : `${t("Через прокси", "Proxied")} ${percentage(share)}, ${t("Напрямую", "Direct")} ${percentage(1 - share)}`}>
              <circle className="traffic-ring-track" cx="80" cy="80" r="66" />
              {share !== null && <><circle className="traffic-ring-direct" cx="80" cy="80" r="66" /><circle className="traffic-ring-proxy" cx="80" cy="80" r="66" pathLength="100" strokeDasharray={`${share * 100} 100`} transform="rotate(-90 80 80)" /></>}
            </svg>
            <div className="traffic-ring-center"><strong>{route ? bytes(route.total) : "—"}</strong><span>{t("Всего", "Total")}</span></div>
          </div>
          <dl className="traffic-route-legend">
            {(["proxy", "direct"] as const).map(key => <div key={key}><dt><i className={`traffic-route-dot traffic-dot-${key}`} />{key === "proxy" ? t("Через прокси", "Proxied") : t("Напрямую", "Direct")}</dt><dd><strong data-testid={`${key}-bytes`}>{bytes(route?.[key] ?? null)}</strong><span>{share === null ? "—" : percentage(key === "proxy" ? share : 1 - share)}</span></dd></div>)}
          </dl>
        </div>
        <p className="traffic-measurement-note">{routeHint}</p>
      </section>
      <section className="traffic-protocol-card" aria-label={t("Активные соединения", "Active connections")}>
        <div className="traffic-card-heading"><h2>{t("Активные соединения", "Active connections")}</h2><span>{t("Сейчас", "Now")}</span></div>
        <div className="traffic-protocol-grid">{(["tcp", "udp"] as const).map(protocol => <div key={protocol}><span>{protocol.toUpperCase()}</span><strong data-testid={`${protocol}-count`}>{values[protocol]?.toLocaleString(m.common.locale) ?? "—"}</strong><small>{values[protocol] === null ? t("Нет данных", "Unavailable") : t("Активных", "Active")}</small></div>)}</div>
        <p className="traffic-measurement-note">{t("Соединения, отслеживаемые ядром. Не число пакетов; при отсутствии данных показано «—».", "Connections tracked by the core. These are not packet counts; missing data is shown as “—”.")}</p>
      </section>
    </div>
    <AdBlockingControl />
  </div>;
}
