import type { CoreAvailability,CoreRuntime,CoreSession,CorePreference,CoreGroup } from './coreApi';
export const MAX_CORE_SOURCE_BYTES=4*1024*1024;
/** Do not trim, parse YAML, normalize newlines or strip BOM: digest belongs to the original UTF8 source. */
export function validateCoreSource(source:string):string {
 if(!source.trim())throw Error('EMPTY_SOURCE');
 if(new TextEncoder().encode(source).length>MAX_CORE_SOURCE_BYTES)throw Error('SOURCE_TOO_LARGE');
 if(/^https?:\/\/\S+$/i.test(source.trim()))throw Error('FULL_PROFILE_URL_IMPORT_UNAVAILABLE');
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
 if(!availability.system_proxy_available)return availability.reason||'SYSTEM_PROXY_PLATFORM_UNAVAILABLE';
 if(mode!=='system_proxy')return 'MIHOMO_TUN_UNAVAILABLE';
 if(killSwitch)return 'MIHOMO_KILL_SWITCH_UNAVAILABLE';
 if(preference&&preference!=='auto'&&preference!=='mihomo')return 'UNSUPPORTED_CORE';
 return null;
}
export function groupCanSelect(group:CoreGroup){return group.type?.toLowerCase()==='selector';}
export function validDelayUrl(value:string){try{const u=new URL(value);return ['http:','https:'].includes(u.protocol)&&!u.username&&!u.password;}catch{return false;}}
/** Backend/source errors can contain secrets. Render only known codes, never raw source or native exception text. */
export function coreErrorCode(error:unknown){const match=String(error).match(/\b(NATIVE_CORE_API_UNAVAILABLE|CORE_UNAVAILABLE|CORE_HASH_MISMATCH|CORE_MISMATCH|UNSUPPORTED_CORE|DISCONNECT_BEFORE_CORE_CHANGE|MIHOMO_TUN_UNAVAILABLE|MIHOMO_KILL_SWITCH_UNAVAILABLE|STALE_REVISION|STALE_GENERATION|CONNECTION_CANCELLED|UNSUPPORTED_FIELD|PROFILE_ACTIVE|PROFILE_NOT_FOUND|EMPTY_SOURCE|SOURCE_TOO_LARGE|FULL_PROFILE_URL_IMPORT_UNAVAILABLE|SYSTEM_PROXY_PLATFORM_UNAVAILABLE|INVALID_YAML|INVALID_CONFIG|INVALID_GRAPH|INVALID_SELECTION|NOT_SELECTABLE|NOT_REFRESHABLE|PROVIDER_REFRESH|DELAY_FAILED|CORE_EXITED|NOT_RUNNING|BUSY|RUNTIME_NOT_RUNNING|READBACK_MISMATCH)\b/);return match?.[0]||'CORE_OPERATION_FAILED';}

export function mihomoErrorMessage(error: unknown, ru: boolean): string {
 const code = coreErrorCode(error);
 const messages: Record<string, [string, string]> = {
  MIHOMO_TUN_UNAVAILABLE: ['Выберите только System Proxy в настройках подключения. Mihomo TUN и Both пока недоступны.', 'Choose System Proxy only in Connection settings. Mihomo TUN and Both are not available.'],
  MIHOMO_KILL_SWITCH_UNAVAILABLE: ['Mihomo пока не поддерживает Kill Switch. Отключите его самостоятельно в настройках, если хотите продолжить.', 'Mihomo does not support Kill Switch yet. Turn it off yourself in Connection settings if you want to continue.'],
  CORE_UNAVAILABLE: ['Проверенный Mihomo runtime отсутствует в этой сборке.', 'This build does not contain an available verified Mihomo runtime.'],
  NATIVE_CORE_API_UNAVAILABLE: ['Управление ядром доступно только в desktop-приложении.', 'Core management is available only in the desktop app.'],
  CORE_MISMATCH: ['Для полного YAML выберите Авто или Mihomo. Обычные ссылки серверов не преобразуются в Mihomo.', 'Choose Auto or Mihomo for a full YAML profile. Individual server links are not converted to Mihomo.'],
  UNSUPPORTED_CORE: ['Выберите Авто или Mihomo для полного YAML-профиля.', 'Choose Auto or Mihomo for a full YAML profile.'],
  FULL_PROFILE_URL_IMPORT_UNAVAILABLE: ['Импорт URL пока недоступен. Загрузите полный YAML как файл или вставьте его содержимое.', 'URL import is not available yet. Import the complete YAML file or paste its contents.'],
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
