import { invoke } from '@tauri-apps/api/core';
import { isTauriRuntime } from './api';

export interface OnDemandSettings { enabled: boolean; wifi: boolean; ethernet: boolean; cellular: boolean; trusted_ssids: string[] }
export interface OnDemandStatus { settings: OnDemandSettings; paused: boolean; phase: string; target_name: string | null; supported: boolean }
export const defaultOnDemand: OnDemandSettings = { enabled: false, wifi: true, ethernet: false, cellular: true, trusted_ssids: [] };

export function validatedOnDemand(settings: OnDemandSettings, names: string): OnDemandSettings {
  const trusted_ssids = [...new Set(names.split(/\r?\n/).map(s => s.trim()).filter(Boolean))];
  if (trusted_ssids.length > 32) throw Error('TOO_MANY_SSIDS');
  if (trusted_ssids.some(s => new TextEncoder().encode(s).length > 32 || /[\u0000-\u001f\u007f-\u009f]/.test(s))) throw Error('INVALID_SSID');
  if (settings.enabled && !settings.wifi && !settings.ethernet && !settings.cellular) throw Error('NO_TRANSPORT');
  return { ...settings, trusted_ssids };
}
export function onDemandError(error: unknown, ru: boolean): string {
  const code = error instanceof Error ? error.message : String(error);
  const messages: Record<string, [string, string]> = {
    INVALID_SSID: ['Имя Wi-Fi: до 32 байт, без управляющих символов.', 'Wi-Fi names must be at most 32 bytes and contain no control characters.'],
    TOO_MANY_SSIDS: ['Можно добавить до 32 доверенных сетей.', 'Add up to 32 trusted networks.'],
    NO_TRANSPORT: ['Выберите хотя бы один тип сети.', 'Select at least one network type.'],
    CONNECT_FIRST: ['Сначала подключитесь к нужному серверу, затем включите правила.', 'Connect to the desired server before enabling these rules.'],
    TARGET_UNAVAILABLE: ['Сохранённый сервер недоступен. Подключитесь к другому серверу и сохраните правила.', 'The saved server is unavailable. Connect to another server and save the rules.'],
    PLATFORM_UNAVAILABLE: ['На этой системе правила недоступны.', 'Network rules are unavailable on this system.'],
    SETTINGS_CANCELLED: ['Сохранение отменено новым действием. Повторите при необходимости.', 'A newer action cancelled saving. Retry if needed.'],
  };
  return messages[code]?.[ru ? 0 : 1] ?? (ru ? 'Не удалось прочитать или сохранить правила. Попробуйте ещё раз.' : 'Could not read or save the rules. Please retry.');
}
export const onDemandApi = {
  get: async (): Promise<OnDemandStatus> => {
    if (!isTauriRuntime()) throw Error('NATIVE_UNAVAILABLE');
    return invoke('get_on_demand');
  },
  save: async (settings: OnDemandSettings): Promise<OnDemandStatus> => {
    if (!isTauriRuntime()) throw Error('NATIVE_UNAVAILABLE');
    return invoke('set_on_demand', { settings });
  },
};
