import type { CoreAvailability,CoreRuntime,CoreSession,CorePreference,CoreGroup } from './coreApi';
export const MAX_CORE_SOURCE_BYTES=4*1024*1024;
export const MAX_CORE_URL_BYTES=8192;
export function validateCoreSourceUrl(value:string):string {
 const input=value.trim();
 try {
  if(new TextEncoder().encode(input).length>MAX_CORE_URL_BYTES || /[\s\u0000-\u001f\u007f-\u009f]/u.test(input))throw Error();
  const url=new URL(input);
  if(!['http:','https:'].includes(url.protocol)||!url.hostname||url.username||url.password||input.includes('#'))throw Error();
  return input;
 } catch { throw Error('INVALID_SOURCE_URL'); }
}
/** Do not trim, parse YAML, normalize newlines or strip BOM: digest belongs to the original UTF8 source. */
export function validateCoreSource(source:string):string {
 if(!source.trim())throw Error('EMPTY_SOURCE');
 if(new TextEncoder().encode(source).length>MAX_CORE_SOURCE_BYTES)throw Error('SOURCE_TOO_LARGE');
 if(/^https?:\/\/\S+$/i.test(source.trim()))throw Error('FULL_PROFILE_URL_USE_FIELD');
 return source;
}
export function decodeCoreSource(bytes:ArrayBuffer):string {
 if(bytes.byteLength>MAX_CORE_SOURCE_BYTES)throw Error('SOURCE_TOO_LARGE');
 return validateCoreSource(new TextDecoder('utf-8',{fatal:true,ignoreBOM:true}).decode(bytes));
}
export function coreSession(runtime:CoreRuntime|null):CoreSession|null {
 return runtime?.running&&runtime.profile_id&&runtime.session_id?{profileId:runtime.profile_id,sessionId:runtime.session_id}:null;
}
export function sameCoreSession(a:CoreSession|null,b:CoreSession|null){return !!a&&!!b&&a.profileId===b.profileId&&a.sessionId===b.sessionId;}
export function mihomoBlockReason(availability:CoreAvailability|undefined,preference:CorePreference|null|undefined,mode:string,killSwitch:boolean) {
 if(!availability?.binary_verified)return availability?.reason||'CORE_UNAVAILABLE';
 if(mode==='tun'){if(!availability.tun_available)return availability.reason||'MIHOMO_TUN_UNAVAILABLE';}
 else if(mode==='both'){if(!availability.tun_available||!availability.system_proxy_available||!availability.both_available)return availability.reason||'MIHOMO_TUN_UNAVAILABLE';}
 else if(mode==='system_proxy'){if(!availability.system_proxy_available)return availability.reason||'SYSTEM_PROXY_PLATFORM_UNAVAILABLE';}
 else return 'MIHOMO_TUN_UNAVAILABLE';
 if(killSwitch&&(!availability.kill_switch_available||mode==='system_proxy'))return 'MIHOMO_KILL_SWITCH_UNAVAILABLE';
 if(preference&&preference!=='auto'&&preference!=='mihomo')return 'UNSUPPORTED_CORE';
 return null;
}
export function groupCanSelect(group:CoreGroup){return group.type?.toLowerCase()==='selector';}
export function validDelayUrl(value:string){try{const u=new URL(value);return ['http:','https:'].includes(u.protocol)&&!u.username&&!u.password;}catch{return false;}}
/** Backend/source errors can contain secrets. Render only known codes, never raw source or native exception text. */
export function coreErrorCode(error:unknown){const match=String(error).match(/\b(KILL_SWITCH_FAILED|KILL_SWITCH_NOT_OWNED|KILL_SWITCH_RESET_REQUIRED|UNSUPPORTED_DESKTOP_CONFIG|MIHOMO_HELPER_REQUIRED|HELPER_INSTALL_FAILED|TUN_IN_USE|SOURCE_DIGEST_MISMATCH|TUN_CLEANUP_FAILED|NATIVE_CORE_API_UNAVAILABLE|CORE_UNAVAILABLE|CORE_HASH_MISMATCH|CORE_MISMATCH|UNSUPPORTED_CORE|DISCONNECT_BEFORE_CORE_CHANGE|MIHOMO_TUN_UNAVAILABLE|MIHOMO_KILL_SWITCH_UNAVAILABLE|STALE_REVISION|STALE_GENERATION|CONNECTION_CANCELLED|UNSUPPORTED_FIELD|PROFILE_ACTIVE|PROFILE_NOT_FOUND|EMPTY_SOURCE|SOURCE_TOO_LARGE|FULL_PROFILE_URL_USE_FIELD|INVALID_SOURCE_URL|SOURCE_FETCH_FAILED|SOURCE_FETCH_TIMEOUT|SOURCE_HTTP_ERROR|SOURCE_REDIRECT_BLOCKED|SOURCE_INVALID_UTF8|SOURCE_NOT_PROFILE|SYSTEM_PROXY_PLATFORM_UNAVAILABLE|INVALID_YAML|INVALID_CONFIG|INVALID_GRAPH|INVALID_SELECTION|NOT_SELECTABLE|NOT_REFRESHABLE|PROVIDER_REFRESH|DELAY_FAILED|CORE_EXITED|NOT_RUNNING|BUSY|RUNTIME_NOT_RUNNING|READBACK_MISMATCH)\b/);return match?.[0]||'CORE_OPERATION_FAILED';}

export function mihomoErrorMessage(error: unknown, ru: boolean): string {
 const code = coreErrorCode(error);
 const messages: Record<string, [string, string]> = {
  KILL_SWITCH_FAILED: ['Не удалось подтвердить защиту Kill Switch. Подключение не запущено; проверьте системный помощник.', 'Kill Switch protection could not be verified. Connection was not started; check the system helper.'],
  KILL_SWITCH_NOT_OWNED: ['Защита принадлежит другому пользователю Windows. Снять её может владелец сеанса.', 'Protection belongs to another Windows user. Its session owner must release it.'],
  KILL_SWITCH_RESET_REQUIRED: ['После сбоя, закрытия приложения или перезагрузки сохранён Kill Switch. Нажмите «Сбросить Kill Switch» перед новым подключением.', 'Kill Switch was retained after failure, app exit or a reboot. Use Reset Kill Switch before reconnecting.'],
  UNSUPPORTED_DESKTOP_CONFIG: ['Настройки профиля конфликтуют с управляемыми интерфейсом, маршрутами или DNS. Профиль не изменён.', 'Profile settings conflict with the managed interface, routes or DNS. The profile was not changed.'],
  MIHOMO_TUN_UNAVAILABLE: ['Этот сетевой режим Mihomo недоступен в текущей сборке. На Linux и Windows нужен подготовленный TUN-помощник.', 'This Mihomo network mode is unavailable. Linux and Windows require the prepared TUN helper.'],
  MIHOMO_HELPER_REQUIRED: ['Подготовьте системный помощник Mihomo TUN. Повышаются права только установки, не всего приложения.', 'Prepare the Mihomo TUN system helper. Only installation is elevated, never the GUI.'],
  HELPER_INSTALL_FAILED: ['Установка помощника отменена или не завершена.', 'Helper installation was cancelled or failed.'],
  TUN_IN_USE: ['Туннель уже занят другим сеансом. Сначала отключите его.', 'Another session owns the tunnel. Disconnect it first.'],
  TUN_CLEANUP_FAILED: ['Не удалось подтвердить очистку туннеля. Новый сеанс не запущен.', 'Tunnel cleanup was not confirmed. A new session was not started.'],
  MIHOMO_KILL_SWITCH_UNAVAILABLE: ['Kill Switch Mihomo доступен в Windows с подготовленным помощником и TUN или Both. В этом режиме или сборке он недоступен.', 'Mihomo Kill Switch requires Windows, the prepared helper and TUN or Both. It is unavailable in this mode or build.'],
  CORE_UNAVAILABLE: ['Проверенный Mihomo runtime отсутствует в этой сборке.', 'This build does not contain an available verified Mihomo runtime.'],
  NATIVE_CORE_API_UNAVAILABLE: ['Управление ядром доступно только в desktop-приложении.', 'Core management is available only in the desktop app.'],
  CORE_MISMATCH: ['Для полного YAML выберите Авто или Mihomo. Обычные ссылки серверов не преобразуются в Mihomo.', 'Choose Auto or Mihomo for a full YAML profile. Individual server links are not converted to Mihomo.'],
  UNSUPPORTED_CORE: ['Выберите Авто или Mihomo для полного YAML-профиля.', 'Choose Auto or Mihomo for a full YAML profile.'],
  FULL_PROFILE_URL_USE_FIELD: ['Вставьте ссылку в поле «Ссылка на полный YAML».', 'Paste the URL into the Complete YAML URL field.'],
  INVALID_SOURCE_URL: ['Нужна ссылка HTTP(S) без логина, пароля и фрагмента #.', 'Use an HTTP(S) URL without credentials or a # fragment.'],
  SOURCE_FETCH_FAILED: ['Не удалось загрузить профиль. Проверьте ссылку и доступ к сети.', 'Could not download the profile. Check the URL and network access.'],
  SOURCE_FETCH_TIMEOUT: ['Загрузка не завершилась за 20 секунд. Попробуйте снова.', 'The download did not finish within 20 seconds. Try again.'],
  SOURCE_HTTP_ERROR: ['Сервер не отдал профиль. Проверьте действительность ссылки.', 'The server did not return a profile. Check that the URL is valid.'],
  SOURCE_REDIRECT_BLOCKED: ['Ссылка перенаправляет на другой сайт или по кругу. Вставьте конечную ссылку на YAML.', 'The URL redirects to another origin or in a loop. Paste the final YAML URL.'],
  SOURCE_INVALID_UTF8: ['Профиль должен быть текстом UTF-8.', 'The profile must be UTF-8 text.'],
  SOURCE_NOT_PROFILE: ['По ссылке получена веб-страница, а не полный YAML-профиль.', 'The URL returned a web page, not a complete YAML profile.'],
  UNSUPPORTED_FIELD: ['Native-ядро отклонило неподдерживаемое поле YAML. Профиль сохранён без преобразования.', 'The native core rejected an unsupported YAML field. The profile is preserved without conversion.'],
  STALE_GENERATION: ['Сессия изменилась. Обновите состояние перед следующим действием.', 'The session changed. Refresh its state before the next action.'],
  SOURCE_TOO_LARGE: ['Максимальный размер YAML — 4 МиБ.', 'The YAML limit is 4 MiB.'],
  EMPTY_SOURCE: ['Добавьте полный YAML-профиль.', 'Provide a complete YAML profile.'],
 };
 return messages[code]?.[ru ? 0 : 1] ?? `${ru ? 'Операция не выполнена' : 'Operation failed'}: ${code}`;
}
export class CoreIntent {
 private revision=0;
 begin(){return ++this.revision;}
 current(ticket:number){return ticket===this.revision;}
}
