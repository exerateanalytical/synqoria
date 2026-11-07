/** M16 browser never receives authority-proof minting, source clinical exports or cross-cell routes. */
const uuid="[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[1-8][a-fA-F0-9]{3}-[89aAbB][a-fA-F0-9]{3}-[a-fA-F0-9]{12}";
const history=new RegExp("^history/"+uuid+"$");
const action=new RegExp("^action/"+uuid+"/(authorize|send|accept|reject|transfer|attend|close|cancel)$");
export function validSegments(parts:unknown):parts is string[]{
 return Array.isArray(parts)&&parts.length>=1&&parts.length<=3&&
  parts.every(x=>typeof x==="string"&&x.length>0&&x.length<=80&&/^[a-zA-Z0-9_-]+$/.test(x));
}
export function policy(method:"GET"|"POST",route:string){
 return method==="GET"?route==="list"||history.test(route):
   route==="create"||action.test(route);
}
export function upstreamRoute(route:string){
 if(route==="list"||route==="create")return "/api/v1/referrals";
 const match=route.match(new RegExp("^(history|action)/("+uuid+")(?:/([a-z-]+))?$"));
 if(!match)return null;
 return "/api/v1/referrals/"+match[2]+(match[1]==="history"?"/history":"/"+match[3]);
}
export function scopedQuery(method:"GET"|"POST",search:URLSearchParams){
 if(method==="POST")return search.size===0?new URLSearchParams():null;
 if([...search.keys()].some(k=>k!=="limit")||[...search.keys()].length>1)return null;
 const out=new URLSearchParams();
 if(search.has("limit")){
  const v=search.get("limit")!;
  if(!/^\d{1,3}$/.test(v)||Number(v)<1||Number(v)>100)return null;
  out.set("limit",v);
 }
 return out;
}
