export type PingResult = {state:'pending'} | {state:'success';ms:number} | {state:'error';reason:'timeout'|'unavailable'};
export interface PingSnapshot { key:string|null; running:boolean; waiting:boolean; results:Map<string,PingResult> }

/** The native controller serializes checks. Do not flood its operation lock or own VPN intent. */
export class MihomoPingQueue {
 private key:string|null=null;
 private revision=0;
 private running=false;
 private waiting=false;
 private results=new Map<string,PingResult>();
 constructor(private readonly publish:(state:PingSnapshot)=>void) {}
 private emit(){this.publish({key:this.key,running:this.running,waiting:this.waiting,results:new Map(this.results)});}
 setContext(key:string|null){
  if(key===this.key)return;
  this.key=key;this.revision++;this.running=false;this.results.clear();this.emit();
 }
 cancel(){
  this.revision++;this.running=false;
  for(const [name,result] of this.results)if(result.state==='pending')this.results.delete(name);
  this.emit();
 }
 async run(names:string[],probe:(name:string)=>Promise<{delayMs:unknown}>,current:()=>boolean):Promise<void>{
  // A cancelled invoke is bounded by the native timeout but may still be draining.
  // Never queue a replacement behind it on the native operation lock.
  if(!this.key||this.running||this.waiting||!current())return;
  const members=[...new Set(names)].filter(name=>name.length>0);
  if(!members.length)return;
  const ticket=++this.revision;this.running=true;this.waiting=true;
  for(const name of members)this.results.set(name,{state:'pending'});
  this.emit();
  const valid=()=>ticket===this.revision&&current();
  const discard=()=>{if(ticket===this.revision){this.results.clear();this.cancel();}};
  try{
   for(const name of members){
    if(!valid()){discard();return;}
    let result:PingResult;
    try{
     const {delayMs:ms}=await probe(name);
     if(typeof ms!=='number'||!Number.isInteger(ms)||ms<0||ms>65535)throw Error('INVALID_DELAY');
     result={state:'success',ms};
    }catch(error){
     if(/\b(STALE_GENERATION|STALE_REVISION|SOURCE_DIGEST_MISMATCH|CONNECTION_CANCELLED|PROBE_CANCELLED|NOT_RUNNING|CORE_EXITED)\b/.test(String(error))){discard();return;}
     // Only fixed presentation states leave this queue, never native error details.
     result={state:'error',reason:/\b(PROBE_TIMEOUT|DELAY_TIMEOUT)\b/.test(String(error))?'timeout':'unavailable'};
    }
    if(!valid()){discard();return;}
    this.results.set(name,result);this.emit();
   }
  }finally{this.waiting=false;if(ticket===this.revision)this.running=false;this.emit();}
 }
}
