import { createRoot } from 'react-dom/client';
import { useState } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { AppWorkspaceBar, DesktopBottomNavigation } from '../../src/App';
import { SignalSidebar } from '../../src/components/SignalSidebar';
import { NimboSelect } from '../../src/components/NimboSelect';
import { ConnectionStateIcon } from '../../src/components/ConnectionStateIcon';
import { Home } from '../../src/pages/Home';
import { Subscriptions } from '../../src/pages/Subscriptions';
import { Notifications } from '../../src/pages/Notifications';
import { TrayMenu } from '../../src/tray-menu/TrayMenu';
import { useAppStore } from '../../src/store';
import { useCoreStore } from '../../src/coreStore';
import { api, defaultAppPreferences, type Subscription } from '../../src/lib/api';
import { desktopNavItems } from '../../src/lib/desktopNavigation';
import { messages } from '../../src/lib/i18n';
import warningFixture from '../fixtures/mihomo-warning-inspection.json';
import tabFixture from '../fixtures/mihomo-tabbed-inspection.json';
import 'flag-icons/css/flag-icons.min.css';
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
const prefs = {...defaultAppPreferences,language:'ru',ui_style:'signal',theme_mode:params.get('theme')==='light'?'light':'dark',show_subscription_logo:false,servers_connect_button:params.get('button')==='compact'?'compact':'classic'} as typeof defaultAppPreferences;
document.body.dataset.uiStyle='signal'; document.body.dataset.theme=prefs.theme_mode;
const server = {id:'test-fi',name:'Финляндия',protocol:{kind:'vless',address:'fixture.invalid',port:443,uuid:'fixture',encryption:'none',stream:{network:'tcp',security:'reality'}}} as Subscription['servers'][number];
const sub: Subscription = {url:'https://fixture.invalid/sub',name:'Провайдер',servers:[server],info:{upload:1024,download:4096,total:1048576,expire:0},fetched_at:1,meta:{description:'Описание подписки: доступные локации и новости провайдера.',mihomo_profile_id:'yaml-fixture'}};
if(params.has('long')) sub.meta!.description='  Новости провайдера\n\n  Новая локация <script>literal</script>.\n'+('Длинное объявление провайдера.\n').repeat(16)+'  ';
if(params.has('announcement')) sub.meta!.description='🛡️ Провайдер\n📅 Срок: бессрочно\n📊 Использовано: 12 GiB\n🆔 2 · профиль теста\nПомощь: @provider_support';
const announcementFixture=window as unknown as {fixtureDescription:string;setFixtureDescription:(description:string)=>void};
announcementFixture.fixtureDescription=sub.meta!.description!;
announcementFixture.setFixtureDescription=description=>{
 announcementFixture.fixtureDescription=description;
 useAppStore.setState(state=>({subscriptions:state.subscriptions.map(item=>item.url===sub.url?{...item,meta:{...item.meta,description}}:item)}));
};
const coreFixture=window as unknown as {fixtureCoreUpdated:boolean;refreshFixtureSubscription:()=>void;connectFixtureProfile:()=>Promise<void>;changePingSettings:()=>void;changeCoreSession:()=>void;setNativeSession:(id:string)=>void;unmountMihomo:()=>void};
coreFixture.connectFixtureProfile=()=>useCoreStore.getState().connect('yaml-fixture');
coreFixture.changePingSettings=()=>useAppStore.setState(state=>({preferences:{...state.preferences,latency_test_url:'https://changed.invalid/204',latency_timeout_ms:60000}}));
coreFixture.changeCoreSession=()=>{coreFixture.setNativeSession('new-fixture-session');useCoreStore.setState({runtime:{running:true,profile_id:'yaml-fixture',session_id:'new-fixture-session',native_generation:2,mixed_address:null,network_owner:'desktop-proxy'}});};
coreFixture.unmountMihomo=()=>useCoreStore.setState(state=>({data:{...state.data!,preferred_core:'auto'}}));
coreFixture.fixtureCoreUpdated=false;
coreFixture.refreshFixtureSubscription=()=>{
 coreFixture.fixtureCoreUpdated=true;
 useAppStore.setState(state=>({subscriptions:state.subscriptions.map(item=>item.url===sub.url?{...item,fetched_at:(item.fetched_at??0)+1}:item)}));
};
const inspection={api:1,sourceDigest:'fixture',nativeValidated:false,issues:[],graph:{groups:[{name:'VPN',type:'select',proxies:['DIRECT','REJECT']},{name:'Auto',type:'url-test',proxies:['node']}]}};
const calls:string[]=[]; (window as unknown as {polishCalls:string[]}).polishCalls=calls;
useAppStore.setState({subscriptions:params.has('multiple')?[sub,{...sub,url:'https://fixture.invalid/second',name:'Вторая подписка'}]:[sub],activeServerId:server.id,activeSubscriptionUrl:sub.url,status:{state:params.get('state')??'disconnected',connection_mode:'system_proxy'} as never,preferences:prefs,
  hydrate:async()=>{}, syncStatus:async()=>{},setActiveServer:async id=>{useAppStore.setState({activeServerId:id});},
  connectServer:async id=>{calls.push('legacy:'+id);},
  disconnectServer:async()=>{calls.push('disconnect');useAppStore.setState({status:{state:'disconnected',connection_mode:'system_proxy'} as never});},
  refreshSubscription:async url=>{calls.push('refresh:'+url); await new Promise(r=>setTimeout(r,150)); if(params.has('inspectFail'))useAppStore.setState(state=>({subscriptions:state.subscriptions.map(item=>({...item,fetched_at:(item.fetched_at??0)+1}))})); return sub;},
});
useCoreStore.setState({loaded:true,data:{preferred_core:mihomo?'mihomo':'auto',active_profile_id:null,profiles:[{id:'yaml-fixture',name:'Провайдер · YAML',kind:'mihomo_yaml',source_digest:'fixture',revision:1,selections:{},inspection}]},runtime:null});
if(params.has('running')) useCoreStore.setState({runtime:{running:true,profile_id:'yaml-fixture',session_id:'fixture-session',native_generation:1,mixed_address:null,network_owner:'desktop-proxy'}});
if(params.has('warning')) useCoreStore.setState(state=>({data:{...state.data!,profiles:state.data!.profiles.map(p=>({...p,source_digest:warningFixture.inspection.sourceDigest,inspection:warningFixture.inspection}))}}));
if(params.has('cards')) useCoreStore.setState(state=>({data:{...state.data!,profiles:state.data!.profiles.map(p=>({...p,source_digest:tabFixture.sourceDigest,inspection:tabFixture}))}}));
if(params.has('empty')) useCoreStore.setState(state=>({data:{...state.data!,profiles:state.data!.profiles.map(p=>({...p,inspection:{...inspection,graph:{groups:[]}}}))}}));
if(params.has('inspect')||params.has('inspectFail')) useCoreStore.setState(state=>({data:{...state.data!,profiles:state.data!.profiles.map(p=>({...p,inspection:null}))}}));
if(params.has('stale')) useCoreStore.setState(state=>({data:{...state.data!,profiles:state.data!.profiles.map(p=>({...p,inspection:{...inspection,sourceDigest:'old'}}))}}));
if(params.has('foreign')) useCoreStore.setState({runtime:{running:true,profile_id:'foreign',session_id:'foreign-session',native_generation:2,mixed_address:null,network_owner:'desktop-proxy'},snapshot:{groups:{Foreign:{type:'Selector',all:['private-foreign-node'],now:'private-foreign-node'}},providers:{},ruleProviders:{}}});
api.listAppProxyRules=async()=>[];
api.getSubscriptionLogo=async()=>null;
api.getAppVersion=async()=>'fixture'; api.getUserAgentOverride=async()=>null;
api.getDeviceInfo=async()=>{throw Error('No device access in fixture');};

function SelectFixture() {
 const [value,setValue]=useState('auto');
 return <div style={{padding:20,maxWidth:460}}><label htmlFor="fixture-select">Ядро</label><NimboSelect id="fixture-select" value={value} onChange={e=>{setValue(e.target.value);calls.push('choose:'+e.target.value);}}><option value="auto">Авто</option><option value="awg" disabled>AWG — недоступно</option><option value="xray">Xray</option><option value="mihomo">Mihomo</option></NimboSelect><p data-value>{value}</p><button>Снаружи</button></div>;
}
function MotionFixture() {
 const [state,setState]=useState('power');
 return <div style={{padding:40}}><button className="nimbo-connect-action" data-variant="round" aria-label="Состояние соединения"><ConnectionStateIcon connected={state==='cloud'} busy={state==='loading'} motion={!params.has('motionOff')}/></button>
   {['power','loading','cloud'].map(value=><button key={value} onClick={()=>setState(value)}>{value}</button>)}</div>;
}
function Shell() {return <div className="app-shell"><SignalSidebar labels={messages.ru} items={desktopNavItems.map(item=>({...item,icon:<svg viewBox="0 0 24 24"><path d="M4 4h16v16H4z"/></svg>}))} label={key=>String(messages.ru.app[key as keyof typeof messages.ru.app])} unread={0} version="v1.3.0-beta.1" coreLabel={messages.ru.settings.connection} coreState="idle" updateLabel={params.has('update')?'Обновить':null} onUpdate={()=>calls.push('update')} width={232}/><main className="app-main"><AppWorkspaceBar/>{mode==='profiles'?<Subscriptions/>:mode==='notifications'?<Notifications/>:<Home/>}</main><DesktopBottomNavigation/></div>;}
const content=mode==='tray'?<TrayMenu previewState={{connected:false,activeServerId:'test-fi',autoSelected:false,connectionMode:'system_proxy',subscriptionCount:1,serverCount:1,language:'ru',visualPreferences:prefs,providerTheme:null,servers:[{id:'test-fi',name:'Финляндия'}],needsAdmin:false}}/>:mode==='motion'?<MotionFixture/>:mode==='select'?<SelectFixture/>:<Shell/>;
if(mode==='tray') void import('../../src/tray-menu/tray-menu.css');
createRoot(document.getElementById('root')!).render(<MemoryRouter initialEntries={[mode==='profiles'?'/subscriptions':mode==='notifications'?'/notifications':'/']}>{content}</MemoryRouter>);
