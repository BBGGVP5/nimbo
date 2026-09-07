import { defaultAppPreferences } from '../lib/api';
import type { TrayState } from './TrayMenu';

// Browser-only development fixture: no real profiles, VPN commands or account data.
export const trayPreview: TrayState = {
  connected: false, activeServerId: 'demo-1', connectionMode: 'system_proxy',
  subscriptionCount: 1, serverCount: 3, language: 'ru',
  visualPreferences: defaultAppPreferences, providerTheme: null, needsAdmin: false,
  servers: [
    { id: 'demo-1', name: 'Амстердам', subscriptionName: 'Личный профиль', latencyMs: 42 },
    { id: 'demo-2', name: 'Франкфурт — резервный сервер с длинным названием', subscriptionName: 'Личный профиль', latencyMs: 68 },
    { id: 'demo-3', name: 'Хельсинки', subscriptionName: 'Личный профиль', latencyMs: null },
  ],
};
