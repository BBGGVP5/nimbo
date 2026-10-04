import { createRoot } from 'react-dom/client';
import { useState } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { AppWorkspaceBar, DesktopBottomNavigation } from '../../src/App';
import { SignalSidebar } from '../../src/components/SignalSidebar';
import { NimboSelect } from '../../src/components/NimboSelect';
import { Home } from '../../src/pages/Home';
import { Subscriptions } from '../../src/pages/Subscriptions';
import { TrayMenu } from '../../src/tray-menu/TrayMenu';
import { useAppStore } from '../../src/store';
import { useCoreStore } from '../../src/coreStore';
import { api, defaultAppPreferences, type Subscription } from '../../src/lib/api';
import { desktopNavItems } from '../../src/lib/desktopNavigation';
import { messages } from '../../src/lib/i18n';
import '../../src/styles.css';
import '../../src/universal.css';
import '../../src/secondary.css';
import '../../src/preview-parity.css';
import '../../src/desktop-shell.css';
import '../../src/preview-fonts.css';

// Deliberately isolated data; the runner rejects all unexpected IPC.
const params = new URLSearchParams(location.search);
const mode = params.get('mode') ?? 'home';
const mihomo = params.has('mihomo');
const prefs = {...defaultAppPreferences,language:'ru',ui_style:'signal',theme_mode:params.get('theme')==='light'?'light':'dark',show_subscription_logo:false} as typeof defaultAppPreferences;
document.body.dataset.uiStyle='signal'; document.body.dataset.theme=prefs.theme_mode;
const server = {id:'test-fi',name:'Финляндия',protocol:{kind:'vless',address:'fixture.invalid',port:443,uuid:'fixture',encryption:'none',stream:{network:'tcp',security:'reality'}}} as Subscription['servers'][number];
const sub: Subscription = {url:'https://fixture.invalid/sub',name:'Провайдер',servers:[server],info:{upload:1024,download:4096,total:1048576,expire:0},fetched_at:1,meta:{description:'Описание подписки: доступные локации и новости провайдера.',mihomo_profile_id:'yaml-fixture'}};
const calls:string[]=[]; (window as unknown as {polishCalls:string[]}).polishCalls=calls;
useAppStore.setState({subscriptions:[sub],activeServerId:server.id,activeSubscriptionUrl:sub.url,status:{state:'disconnected',connection_mode:'system_proxy'} as never,preferences:prefs,
  hydrate:async()=>{}, syncStatus:async()=>{},setActiveServer:async id=>{useAppStore.setState({activeServerId:id});},
  connectServer:async id=>{calls.push('legacy:'+id);},
  refreshSubscription:async url=>{calls.push('refresh:'+url); await new Promise(r=>setTimeout(r,150)); return sub;},
});
useCoreStore.setState({loaded:true,data:{preferred_core:mihomo?'mihomo':'auto',active_profile_id:null,profiles:[{id:'yaml-fixture',name:'Провайдер · YAML',kind:'mihomo_yaml',source_digest:'fixture',revision:1,selections:{},inspection:null}]},runtime:null});
api.listAppProxyRules=async()=>[];
api.getSubscriptionLogo=async()=>null;
api.getAppVersion=async()=>'fixture'; api.getUserAgentOverride=async()=>null;
api.getDeviceInfo=async()=>{throw Error('No device access in fixture');};

function SelectFixture() {
 const [value,setValue]=useState('auto');
 return <div style={{padding:20,maxWidth:460}}><label htmlFor="fixture-select">Ядро</label><NimboSelect id="fixture-select" value={value} onChange={e=>{setValue(e.target.value);calls.push('choose:'+e.target.value);}}><option value="auto">Авто</option><option value="awg" disabled>AWG — недоступно</option><option value="xray">Xray</option><option value="mihomo">Mihomo</option></NimboSelect><p data-value>{value}</p><button>Снаружи</button></div>;
}
function Shell() {return <div className="app-shell"><SignalSidebar labels={messages.ru} items={desktopNavItems.map(item=>({...item,icon:<svg viewBox="0 0 24 24"><path d="M4 4h16v16H4z"/></svg>}))} label={key=>String(messages.ru.app[key as keyof typeof messages.ru.app])} unread={0} version="fixture" coreLabel="VPN" coreState="idle" width={232}/><main className="app-main"><AppWorkspaceBar/>{mode==='profiles'?<Subscriptions/>:<Home/>}</main><DesktopBottomNavigation/></div>;}
const content=mode==='tray'?<TrayMenu previewState={{connected:false,activeServerId:'test-fi',autoSelected:false,connectionMode:'system_proxy',subscriptionCount:1,serverCount:1,language:'ru',visualPreferences:prefs,providerTheme:null,servers:[{id:'test-fi',name:'Финляндия'}],needsAdmin:false}}/>:mode==='select'?<SelectFixture/>:<Shell/>;
if(mode==='tray') void import('../../src/tray-menu/tray-menu.css');
createRoot(document.getElementById('root')!).render(<MemoryRouter initialEntries={[mode==='profiles'?'/subscriptions':'/']}>{content}</MemoryRouter>);
