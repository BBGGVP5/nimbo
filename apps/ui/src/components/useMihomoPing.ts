import { useEffect, useRef, useState } from 'react';
import { useCoreStore } from '../coreStore';
import { useAppStore } from '../store';
import { isTauriRuntime } from '../lib/api';
import { coreApi } from '../lib/coreApi';
import { coreSession, sameCoreSession } from '../lib/coreProfiles';
import { normalizeLatencyTimeout, normalizeLatencyUrl } from '../lib/latency';
import { MihomoPingQueue, type PingSnapshot } from '../lib/mihomoPing';

function pingScope(profileId:string|undefined,subscriptionUrl:string){
 const core=useCoreStore.getState(),app=useAppStore.getState();
 const session=coreSession(core.runtime),profile=core.data?.profiles.find(p=>p.id===profileId);
 if(!isTauriRuntime()||!profile||!session||session.profileId!==profileId)return null;
 const url=normalizeLatencyUrl(app.preferences.latency_test_url);
 const timeout=Math.min(30000,normalizeLatencyTimeout(app.preferences.latency_timeout_ms));
 const generation=core.runtime?.native_generation;
 // A changed current route invalidates group latency, too. Cache nothing on disk.
 const key=JSON.stringify([profileId,profile.source_digest,profile.revision,
  app.subscriptions.find(sub=>sub.url===subscriptionUrl)?.fetched_at,session.sessionId,generation,
  core.snapshot?.groups,core.snapshot?.providers,url,timeout]);
 return {key,session,generation,url,timeout};
}

export function useMihomoPing(profileId:string|undefined,subscriptionUrl:string){
 // Subscribe so scope changes clear pending/results immediately, not just at the next click.
 useCoreStore();useAppStore();
 const scope=pingScope(profileId,subscriptionUrl),key=scope?.key??null;
 const mounted=useRef(false);
 const [state,setState]=useState<PingSnapshot>({key:null,running:false,waiting:false,results:new Map()});
 const [queue]=useState(()=>new MihomoPingQueue(next=>{if(mounted.current)setState(next);}));
 useEffect(()=>{
  mounted.current=true;queue.setContext(key);
  return ()=>{mounted.current=false;queue.setContext(null);};
 },[queue,key]);
 const run=(names:string[])=>{
  const request=pingScope(profileId,subscriptionUrl);
  if(!request||useCoreStore.getState().busy)return;
  queue.setContext(request.key);
  void queue.run(names,async name=>{
   const result=await coreApi.delay(request.session,name,request.url,request.timeout);
   const runtime=await coreApi.runtime();
   if(!sameCoreSession(request.session,coreSession(runtime))||runtime.native_generation!==request.generation){
    void useCoreStore.getState().refresh();throw Error('STALE_GENERATION');
   }
   return result;
  },()=>pingScope(profileId,subscriptionUrl)?.key===request.key);
 };
 return {results:state.key===key?state.results:new Map(),running:state.key===key&&state.running,
  available:!!scope&&!useCoreStore.getState().busy&&!state.waiting,run,cancel:()=>queue.cancel()};
}
