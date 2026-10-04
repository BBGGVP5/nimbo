import { create } from 'zustand';
import { coreApi, type CoreAvailability,type CoreProfilesState,type CoreRuntime,type CoreSnapshot,type CorePreference,type CoreSession } from './lib/coreApi';
import { CoreIntent,coreSession,sameCoreSession,coreErrorCode } from './lib/coreProfiles';
import { useAppStore } from './store';
interface CoreState {
 data:CoreProfilesState|null; availability:CoreAvailability[]; runtime:CoreRuntime|null; snapshot:CoreSnapshot|null;
 busy:string|null; error:string|null; loaded:boolean; delay:{name:string;ms:number}|null;
 refresh:()=>Promise<void>; preference:(core:CorePreference)=>Promise<void>; connect:(id:string)=>Promise<void>; stop:()=>Promise<void>;
 mutate:(label:string,work:()=>Promise<unknown>)=>Promise<boolean>;
 live:(kind:'snapshot'|'select'|'provider'|'rules'|'delay',name?:string,value?:string,timeout?:number)=>Promise<void>;
}
const intent=new CoreIntent();let readInFlight=false;
async function readState(){const [data,availability,runtime]=await Promise.all([coreApi.profiles(),coreApi.availability(),coreApi.runtime()]);return{data,availability,runtime};}
export const useCoreStore=create<CoreState>((set,get)=>({
 data:null,availability:[],runtime:null,snapshot:null,busy:null,error:null,loaded:false,delay:null,
 refresh:async()=>{
  if(readInFlight||get().busy)return;readInFlight=true;const ticket=intent.begin();
  try{const next=await readState();if(!intent.current(ticket))return;const same=sameCoreSession(coreSession(get().runtime),coreSession(next.runtime));set({...next,loaded:true,error:null,...(!same?{snapshot:null,delay:null}:{})});}
  catch(e){if(intent.current(ticket))set({error:coreErrorCode(e),loaded:true});}
  finally{readInFlight=false;}
 },
 mutate:async(label,work)=>{
  if(get().busy)return false;const ticket=intent.begin();set({busy:label,error:null});
  try{await work();if(!intent.current(ticket))return false;const next=await readState();if(!intent.current(ticket))return false;const same=sameCoreSession(coreSession(get().runtime),coreSession(next.runtime));set({...next,loaded:true,...(!same?{snapshot:null,delay:null}:{})});return true;}
  catch(e){if(intent.current(ticket))set({error:coreErrorCode(e)});return false;}
  finally{if(intent.current(ticket))set({busy:null});}
 },
 preference:async(core)=>{
  if(get().busy)return;const ticket=intent.begin();set({busy:'preference',error:null});
  try{
   await coreApi.preference(core);if(!intent.current(ticket))return;
   const next=await readState();if(!intent.current(ticket))return;
   if(next.data.preferred_core!==core)throw Error('READBACK_MISMATCH');set({...next,loaded:true,...(!sameCoreSession(coreSession(get().runtime),coreSession(next.runtime))?{snapshot:null,delay:null}:{})});
   await useAppStore.getState().hydrate?.();
  }catch(e){
   if(intent.current(ticket)){
    const error=coreErrorCode(e);
    try {const next=await readState();if(intent.current(ticket))set({...next,loaded:true,...(!sameCoreSession(coreSession(get().runtime),coreSession(next.runtime))?{snapshot:null,delay:null}:{})});await useAppStore.getState().hydrate?.();}catch{}
    if(intent.current(ticket))set({error});
   }
  }finally{if(intent.current(ticket))set({busy:null});}
 },
 connect:async(id)=>{
  const ok=await get().mutate('connect',async()=>{const runtime=await coreApi.connect(id);if(!runtime.running||runtime.profile_id!==id||!runtime.session_id)throw Error('RUNTIME_NOT_RUNNING');});
  await useAppStore.getState().syncStatus();
  if(ok)await get().live('snapshot');
 },
 stop:async()=>{
  const ticket=intent.begin();set({busy:'disconnect',error:null,snapshot:null,delay:null,runtime:null});
  try{await useAppStore.getState().disconnectServer();if(!intent.current(ticket))return;const next=await readState();if(intent.current(ticket))set({...next,loaded:true,...(!sameCoreSession(coreSession(get().runtime),coreSession(next.runtime))?{snapshot:null,delay:null}:{})});}
  catch(e){if(intent.current(ticket))set({error:coreErrorCode(e)});}finally{if(intent.current(ticket))set({busy:null});}
 },
 live:async(kind,name='',value='',timeout=5000)=>{
  const session=coreSession(get().runtime);if(!session||get().busy)return;
  const ticket=intent.begin();set({busy:kind,error:null,...(kind==='delay'?{delay:null}:{})});
  try{
   const result=kind==='select'?await coreApi.select(session,name,value):kind==='provider'?await coreApi.refreshProvider(session,name):kind==='rules'?await coreApi.refreshRuleProvider(session,name):kind==='delay'?await coreApi.delay(session,name,value,timeout):await coreApi.snapshot(session);
   if(!intent.current(ticket))return;
   // Re-read actual child identity before accepting an in-flight controller reply.
   const runtime=await coreApi.runtime();if(!intent.current(ticket))return;
   if(!sameCoreSession(session,coreSession(runtime))){set({runtime,snapshot:null,delay:null});throw Error('STALE_GENERATION');}
   if(kind==='delay'){const ms=(result as {delayMs:number}).delayMs;if(!Number.isFinite(ms)||ms<0)throw Error('READBACK_MISMATCH');set({delay:{name,ms}});}
   else{const snapshot=result as CoreSnapshot;if(kind==='select'&&snapshot.groups[name]?.now!==value)throw Error('READBACK_MISMATCH');set({snapshot,runtime});}
  }catch(e){if(intent.current(ticket)){const error=coreErrorCode(e);set({error,...(error==='STALE_GENERATION'||error==='CONNECTION_CANCELLED'?{snapshot:null,delay:null}:{})});}}
  finally{if(intent.current(ticket))set({busy:null});}
 },
}));
export function activeCoreSession():CoreSession|null{return coreSession(useCoreStore.getState().runtime);}
