/** M34 staff proxy is strictly read/review-only. Never route producer ingestion or issuer provisioning from browsers. */
const uuid="[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[1-8][a-fA-F0-9]{3}-[89aAbB][a-fA-F0-9]{3}-[a-fA-F0-9]{12}";
const detail=new RegExp("^events/"+uuid+"$");
const timeline=new RegExp("^streams/"+uuid+"/(timeline|verify)$");
const manifest=new RegExp("^exports/"+uuid+"/manifest$");
const reviewExport=new RegExp("^exports/"+uuid+"/review$");
const reviewRetention=new RegExp("^retention/reviews/"+uuid+"/review$");
const getters=["events","exports","retention/holds","retention/reviews"];
const writers=["exports","retention/reviews"];
export function safeSegments(parts:unknown):parts is string[]{
 return Array.isArray(parts)&&parts.length>=1&&parts.length<=4&&
  parts.every(x=>typeof x==="string"&&x.length>0&&x.length<=80&&
   /^[A-Za-z0-9_-]+$/.test(x));
}
export function allowed(method:"GET"|"POST",route:string):boolean{
 if(method==="GET")return getters.includes(route)||detail.test(route)||
  timeline.test(route)||manifest.test(route);
 return writers.includes(route)||reviewExport.test(route)||reviewRetention.test(route);
}
export function query(route:string,source:URLSearchParams):URLSearchParams|null{
 if([...source.keys()].some(k=>k!=="limit")||[...source.keys()].length>1)return null;
 const out=new URLSearchParams();
 if(source.has("limit")){
  if(!(getters.includes(route)||/\/timeline$/.test(route)))return null;
  const value=source.get("limit")!;
  if(!/^\d{1,3}$/.test(value)||Number(value)<1||Number(value)>100)return null;
  out.set("limit",value);
 }
 return out;
}
