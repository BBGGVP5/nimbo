import { invoke as tauriInvoke } from '@tauri-apps/api/core';
import { isTauriRuntime } from './api';
export type CoreKind = 'xray' | 'awg' | 'mihomo';
export type CorePreference = 'auto' | CoreKind;
export interface CoreAvailability { core: CoreKind; selector_available: boolean; binary_verified: boolean; inspect_available: boolean; system_proxy_available: boolean; tun_available: boolean; both_available?: boolean; kill_switch_available?: boolean; reason: string | null }
export interface CoreProfile { id: string; name: string; kind: 'mihomo_yaml'|'xray_json'|'awg_ini'; source_digest: string; revision: number; selections: Record<string,string>; inspection: null | { api: number; sourceDigest: string; nativeValidated: boolean; graph: Record<string,unknown>; issues: {code:string;path:string}[] } }
export interface CoreProfilesState { preferred_core: CorePreference; active_profile_id: string|null; profiles: CoreProfile[] }
export interface CoreRuntime { running: boolean; profile_id: string|null; session_id: string|null; native_generation: number|null; mixed_address: string|null; network_owner: 'desktop-proxy'|'desktop-tun'|'none' }
export interface CoreGroup { name?: string; type?: string; now?: string; all?: string[]; hidden?: boolean }
export interface CoreProvider { name?: string; vehicleType?: string; version?: number|string; proxies?: {name?:string;type?:string}[] }
export interface CoreRuleProvider { name?: string; vehicleType?: string; behavior?: string; ruleCount?: number }
export interface CoreSnapshot { groups: Record<string,CoreGroup>; providers: Record<string,CoreProvider>; ruleProviders: Record<string,CoreRuleProvider> }
export interface CoreSession { profileId: string; sessionId: string }
// There is deliberately no localStorage/demo implementation: only native acknowledgement is authoritative.
async function call<T>(command:string,args?:Record<string,unknown>):Promise<T> {
 if(!isTauriRuntime()) throw new Error('NATIVE_CORE_API_UNAVAILABLE');
 return tauriInvoke<T>(command,args);
}
export const coreApi = {
 availability:()=>call<CoreAvailability[]>('get_core_availability'),
 prepareTun:()=>call<void>('prepare_mihomo_tun'),
 profiles:()=>call<CoreProfilesState>('get_core_profiles'),
 preference:(core:CorePreference)=>call<void>('set_core_preference',{core}),
 import:(name:string,source:string)=>call<{profile:CoreProfile;inspection_error:string|null}>('import_mihomo_profile',{name,source}),
 importUrl:(name:string,url:string)=>call<{profile:CoreProfile;inspection_error:string|null}>('import_mihomo_profile_url',{name,url}),
 export:(profileId:string)=>call<string>('export_core_profile',{profileId}),
 replace:(profileId:string,revision:number,source:string)=>call<CoreProfile>('replace_core_profile',{profileId,revision,source}),
 inspect:(profileId:string)=>call<CoreProfile>('inspect_core_profile',{profileId}),
 remove:(profileId:string)=>call<void>('remove_core_profile',{profileId}),
 connect:(profileId:string)=>call<CoreRuntime>('connect_mihomo_profile',{profileId}),
 runtime:()=>call<CoreRuntime>('get_mihomo_status'),
 snapshot:(session:CoreSession)=>call<CoreSnapshot>('mihomo_snapshot',{...session}),
 select:(session:CoreSession,group:string,name:string)=>call<CoreSnapshot>('mihomo_select',{...session,group,name}),
 refreshProvider:(session:CoreSession,name:string)=>call<CoreSnapshot>('mihomo_refresh_provider',{...session,name}),
 refreshRuleProvider:(session:CoreSession,name:string)=>call<CoreSnapshot>('mihomo_refresh_rule_provider',{...session,name}),
 delay:(session:CoreSession,name:string,url:string,timeoutMs:number)=>call<{delayMs:number}>('mihomo_delay',{...session,name,url,timeoutMs,expectedStatus:null}),
 probe:(profile:Pick<CoreProfile,'id'|'source_digest'|'revision'>,name:string,url:string,timeoutMs:number)=>call<{delayMs:number;sourceSHA256:string;scope:'desktop-offline-probe';vpnStarted:false}>('mihomo_probe',{
  profileId:profile.id,sourceDigest:profile.source_digest,revision:profile.revision,name,url,timeoutMs,
 }),
};
