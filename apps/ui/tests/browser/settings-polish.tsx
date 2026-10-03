import { createRoot } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import { Settings } from '../../src/pages/Settings';
import { SignalProfileCard } from '../../src/pages/profiles/SignalProfileCard';
import { messages } from '../../src/lib/i18n';
import { api, type Subscription } from '../../src/lib/api';
import { useAppStore } from '../../src/store';
import '../../src/styles.css';
import '../../src/universal.css';
import '../../src/secondary.css';
import '../../src/preview-parity.css';
import '../../src/preview-fonts.css';

// No hydrate or host data: only isolated store state and test IPC supplied by the runner.
const params=new URLSearchParams(location.search);
const theme=params.get('theme')??'dark';
const style=params.get('style')==='material_you'?'material_you':params.get('style')==='dotted'?'dotted':'signal';
document.body.dataset.uiStyle=style;document.body.dataset.theme=theme;
useAppStore.setState(state=>({preferences:{...state.preferences,ui_style:style,theme_mode:theme as 'dark'|'light',language:'ru',latency_protocol:'nimbo',show_subscription_logo:true},subscriptions:[],status:null,
  setPreferences:async preferences=>{useAppStore.setState({preferences});return preferences;}}));
api.getAppVersion=async()=>'1.3.0-beta.1';
api.getDeviceInfo=async()=>{throw Error('No device data in fixture');};
api.getUserAgentOverride=async()=>null;
const logo='data:image/svg+xml,'+encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="80" height="40" viewBox="0 0 80 40"><rect width="80" height="40" fill="#fff"/><path d="M0 0h10v40H0zM70 0h10v40H70z" fill="#00bd94"/></svg>');
api.getSubscriptionLogo=async()=>logo;
const sub:Subscription={url:'https://fixture.invalid/sub',name:'Provider fixture',fetched_at:1,servers:[],info:null,meta:{logo_url:'https://fixture.invalid/logo'}};
const route='/settings'+(params.get('section')?'?section='+params.get('section'):'');
createRoot(document.getElementById('root')!).render(<MemoryRouter initialEntries={[route]}><main style={{padding:24,maxWidth:1100,margin:'0 auto'}}><Settings/>
  <div id="logo-fixture" className="universal-home"><SignalProfileCard labels={messages.ru} sub={sub} serverCount={88} refreshing={false} pinging={false} collapsed onToggleCollapsed={()=>{}} onRefresh={()=>{}} onPing={()=>{}} updatedLabel="Сегодня"/></div>
</main></MemoryRouter>);
