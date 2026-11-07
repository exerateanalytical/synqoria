/** Browser route allowlist: independent patient evidence issuance is not proxied. */
const uuid="[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[1-8][a-fA-F0-9]{3}-[89aAbB][a-fA-F0-9]{3}-[a-fA-F0-9]{12}";
const appointment=new RegExp("^appointments/"+uuid+"/(confirm|arrive|fulfil|cancel|no-show|reschedule)$");
const waitlist=new RegExp("^waitlist/"+uuid+"/(offer|accept)$");
const tables=["resources","slots","appointments","waitlist"];
export function validSegments(parts:unknown):parts is string[]{
 return Array.isArray(parts)&&parts.length>=1&&parts.length<=3&&
  parts.every(x=>typeof x==="string"&&x.length>0&&x.length<=80&&/^[a-zA-Z0-9_-]+$/.test(x));
}
export function allowed(method:"GET"|"POST",route:string){
 return method==="GET"?tables.includes(route):tables.includes(route)||appointment.test(route)||waitlist.test(route);
}
export function scopedQuery(route:string,q:URLSearchParams):URLSearchParams|null{
 if(!tables.includes(route)||[...q.keys()].some(k=>k!=="limit")||[...q.keys()].length>1)
  return null;
 const copy=new URLSearchParams();
 if(q.has("limit")){
  const v=q.get("limit")!;
  if(!/^\d{1,3}$/.test(v)||Number(v)<1||Number(v)>100)return null;
  copy.set("limit",v);
 }
 return copy;
}
