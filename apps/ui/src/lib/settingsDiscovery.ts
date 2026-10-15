export const SETTINGS_ADVANCED_KEY = 'nimbo.settings.advanced';

export function matchesSettingsEntry(query: string, text: string, advancedOnly: boolean, advanced: boolean): boolean {
  const tokens = query.trim().toLowerCase().split(/\s+/).filter(Boolean);
  return tokens.length ? tokens.every(token => text.toLowerCase().includes(token)) : !advancedOnly || advanced;
}

export function readSettingsAdvanced(): boolean {
  try { return localStorage.getItem(SETTINGS_ADVANCED_KEY) === 'true'; } catch { return false; }
}

export function writeSettingsAdvanced(value: boolean): boolean {
  try { localStorage.setItem(SETTINGS_ADVANCED_KEY, String(value)); return true; } catch { return false; }
}
