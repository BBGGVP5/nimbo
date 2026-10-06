import { useEffect, useRef, useState } from 'react';
import { useCoreStore } from '../coreStore';
import { useAppStore } from '../store';
import { isTauriRuntime } from '../lib/api';
import { coreApi } from '../lib/coreApi';
import { coreSession, sameCoreSession } from '../lib/coreProfiles';
import { normalizeLatencyTimeout, normalizeLatencyUrl } from '../lib/latency';
import { MihomoPingQueue, type PingSnapshot } from '../lib/mihomoPing';
import { subscriptionGroups } from './core-subscription-groups';

function pingScope(profileId:string|undefined,subscriptionUrl:string,enabled:boolean){
 const core=useCoreStore.getState(),app=useAppStore.getState();
 const session=coreSession(core.runtime),profile=core.data?.profiles.find(p=>p.id===profileId);
 if(!enabled||!isTauriRuntime()||!profile)return null;
 if((session&&session.profileId!==profileId)||(!session&&(core.runtime?.running||app.status?.state==='connected'||app.status?.state==='connecting'||core.busy)))return null;
 const url=normalizeLatencyUrl(app.preferences.latency_test_url);
 const timeout=Math.min(30000,normalizeLatencyTimeout(app.preferences.latency_timeout_ms));
 const generation=session?core.runtime?.native_generation:null;
 // A changed current route invalidates group latency, too. Cache nothing on disk.
 const key=JSON.stringify([profileId,profile.source_digest,profile.revision,
  app.subscriptions.find(sub=>sub.url===subscriptionUrl)?.fetched_at,session?.sessionId??'offline',generation,
  session?core.snapshot?.groups:profile.inspection?.graph,session?core.snapshot?.providers:null,url,timeout]);
 return {key,session,generation,url,timeout,profile};
}

export function useMihomoPing(profileId:string|undefined,subscriptionUrl:string,enabled=true){
 // Subscribe so scope changes clear pending/results immediately, not just at the next click.
 useCoreStore();useAppStore();
 const scope=pingScope(profileId,subscriptionUrl,enabled),key=scope?.key??null;
 const mounted=useRef(false);
 const [state,setState]=useState<PingSnapshot>({key:null,running:false,waiting:false,results:new Map()});
 const [queue]=useState(()=>new MihomoPingQueue(next=>{if(mounted.current)setState(next);}));
 useEffect(()=>{
  mounted.current=true;queue.setContext(key);
  return ()=>{mounted.current=false;queue.setContext(null);};
 },[queue,key]);
 const run=(names:string[])=>{
  const request=pingScope(profileId,subscriptionUrl,enabled);
  if(!request||useCoreStore.getState().busy)return;
  queue.setContext(request.key);
  void queue.run(names,async name=>{
   const result=request.session?await coreApi.delay(request.session,name,request.url,request.timeout):await coreApi.probe(request.profile,name,request.url,request.timeout);
   if(!request.session){
    const proof=result as Awaited<ReturnType<typeof coreApi.probe>>;
    if(proof.scope!=='desktop-offline-probe'||proof.vpnStarted!==false||proof.sourceSHA256!==request.profile.source_digest)throw Error('INVALID_OFFLINE_PROBE');
   }
   const runtime=await coreApi.runtime();
   if(request.session?(!sameCoreSession(request.session,coreSession(runtime))||runtime.native_generation!==request.generation):runtime.running){
    void useCoreStore.getState().refresh();throw Error('STALE_GENERATION');
   }
   return result;
  },()=>pingScope(profileId,subscriptionUrl,enabled)?.key===request.key);
 };
 return {results:state.key===key?state.results:new Map(),running:state.key===key&&state.running,
  available:!!scope&&!useCoreStore.getState().busy&&!state.waiting,run,
  runAll:()=>{const request=pingScope(profileId,subscriptionUrl,enabled);if(request)run(subscriptionGroups(request.profile,request.session?useCoreStore.getState().snapshot:null,!!request.session).flatMap(([,group])=>group.all??[]));},
  cancel:()=>queue.cancel()};
}
