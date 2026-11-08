import {useEffect, useRef, useState} from "react";
import {api} from "../lib/api";
import {coreApi} from "../lib/coreApi";
import {buildSupportReport, readSupportSource} from "../lib/supportReport";
import {useMessages} from "../lib/i18n";
import {Dialog} from "./Universal";
import {NimboSelect} from "./NimboSelect";
import "./support-report.css";

export function SupportReport() {
  const ru = useMessages().common.locale.startsWith("ru");
  const [open, setOpen] = useState(false), [busy, setBusy] = useState(false);
  const [report, setReport] = useState<string | null>(null);
  const [issue, setIssue] = useState("connection");
  const [feedback, setFeedback] = useState("");
  const [copying, setCopying] = useState(false);
  const generation = useRef(0);
  useEffect(() => () => { generation.current++; }, []);
  const close = () => { generation.current++; setOpen(false); setBusy(false); setCopying(false); setReport(null); setFeedback(""); };
  async function prepare() {
    const ticket = ++generation.current;
    setBusy(true); setReport(null); setFeedback("");
    try {
      const [version, status, device, profiles, logs] = await Promise.all([
        readSupportSource(api.getAppVersion()), readSupportSource(api.getStatus()),
        readSupportSource(api.getDeviceInfo()), readSupportSource(coreApi.profiles()),
        readSupportSource(api.getTunnelLogs(1000)),
      ]);
      if (generation.current !== ticket) return;
      if ([version, status, device, profiles, logs].every(value => value === undefined)) {
        setFeedback(ru ? "Данные недоступны. Попробуйте ещё раз." : "Data unavailable. Please try again.");
      } else setReport(buildSupportReport({version, status, device, profiles, logs, issue}));
    } catch {
      if (generation.current === ticket) setFeedback(ru ? "Не удалось подготовить отчёт." : "Could not prepare the report.");
    } finally { if (generation.current === ticket) setBusy(false); }
  }
  async function copy() {
    if (!report || copying) return;
    const ticket = generation.current;
    setCopying(true); setFeedback("");
    try {
      await api.writeClipboardText(report);
      if (ticket === generation.current) setFeedback(ru ? "Скопировано. Отправьте отчёт в поддержку вместе с описанием проблемы." : "Copied. Send it to support with a description of the problem.");
    } catch {
      if (ticket === generation.current) setFeedback(ru ? "Не удалось скопировать. Можно выделить текст отчёта вручную." : "Could not copy. You can select the report text manually.");
    } finally { if (ticket === generation.current) setCopying(false); }
  }
  return <>
    <button type="button" className="btn" onClick={() => setOpen(true)}>{ru ? "Отчёт об ошибке" : "Error report"}</button>
    {open && <Dialog title={ru ? "Отчёт об ошибке" : "Error report"} closeLabel={ru ? "Закрыть" : "Close"} onClose={close}
      className="support-report-dialog" subtitle={ru ? "Проверьте данные перед отправкой" : "Review before sharing"}
      footer={<><button type="button" className="btn" disabled={busy || copying} onClick={() => void prepare()}>{busy ? (ru ? "Подготовка…" : "Preparing…") : (ru ? "Подготовить отчёт" : "Prepare report")}</button>
        {report && <button type="button" className="btn btn-primary" disabled={copying} onClick={() => void copy()}>{copying ? (ru ? "Копирование…" : "Copying…") : (ru ? "Скопировать отчёт" : "Copy report")}</button>}</>}>
      <p className="support-report-privacy">{ru ? "Только техническая сводка: версия, состояние VPN и счётчики событий. Без ключей, ссылок подписок, адресов и текста журналов. Ничего не отправляется автоматически." : "Technical summary only: version, VPN state and event counts. No credentials, subscription links, addresses or log text. Nothing is sent automatically."}</p>
      <label className="support-report-topic">{ru ? "Что не работает?" : "What went wrong?"}
        <NimboSelect aria-label={ru ? "Категория проблемы" : "Issue category"} value={issue} disabled={busy || copying} onChange={event => {setIssue(event.target.value); setReport(null); setFeedback("");}}>
          {[['connection', ru ? 'Подключение' : 'Connection'], ['subscription', ru ? 'Подписка' : 'Subscription'], ['ping', ru ? 'Пинг' : 'Ping'], ['startup', ru ? 'Запуск приложения' : 'App startup'], ['other', ru ? 'Другое' : 'Other']].map(([value, label]) => <option key={value} value={value}>{label}</option>)}
        </NimboSelect>
      </label>
      {busy && <p role="status">{ru ? "Собираем доступные данные. VPN не переключается." : "Collecting available data. The VPN is not changed."}</p>}
      {report && <><p>{ru ? "Ниже — весь текст, который будет скопирован. Недоступные поля отмечены null/false; выбранное ядро не обязательно запущено." : "This is the complete text that will be copied. Unavailable fields are null/false; the preferred core may not be running."}</p>
        <pre tabIndex={0} aria-label={ru ? "Предпросмотр отчёта" : "Report preview"} className="support-report-preview">{report}</pre></>}
      {feedback && <p role="status">{feedback}</p>}
    </Dialog>}
  </>;
}
