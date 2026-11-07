/** M32 browser may reach only patient-consent governance routes, never upstream proof provisioning. */
const id="[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[1-8][a-fA-F0-9]{3}-[89abAB][a-fA-F0-9]{3}-[a-fA-F0-9]{12}";
const record=new RegExp("^(directives|disclosures|cases)/"+id+"/(verify|activate|revoke|review|close)$");
export function safeSegments(parts:unknown):parts is string[]{
 return Array.isArray(parts)&&parts.length>=1&&parts.length<=3&&
  parts.every(x=>typeof x==="string"&&x.length>0&&x.length<=80&&/^[a-zA-Z0-9_-]+$/.test(x));
}
export function isAllowed(method:"GET"|"POST",path:string){
 if(method==="GET")return ["directives","disclosures","cases","authorization/evaluate"].includes(path);
 return ["directives","disclosures","cases"].includes(path)||record.test(path)&&
  (path.startsWith("directives/")&&/(verify|activate|revoke)$/.test(path)||
   path.startsWith("disclosures/")&&path.endsWith("/review")||
   path.startsWith("cases/")&&path.endsWith("/close"));
}
export function safeQuery(path:string,search:URLSearchParams):URLSearchParams|null{
 const copy=new URLSearchParams();
 if(path==="authorization/evaluate"){
  if([...search.keys()].some(k=>!["subject","purpose","recipient","operation"].includes(k)))return null;
  if([...new Set([...search.keys()])].length!==4||[...search.keys()].length!==4)return null;
  const subject=search.get("subject");
  if(!subject||!new RegExp("^"+id+"$").test(subject))return null;
  copy.set("subject",subject);
  for(const key of ["purpose","recipient","operation"]){
   const value=search.get(key);
   if(!value||!/^[A-Z][A-Z0-9_]{1,63}$/.test(value))return null;
   copy.set(key,value);
  }
 }else if(methodQueryList(path)){
  if([...search.keys()].some(k=>k!=="limit")||[...search.keys()].length>1)return null;
  if(search.has("limit")){
   const n=search.get("limit")!;
   if(!/^\d{1,3}$/.test(n)||Number(n)<1||Number(n)>100)return null;
   copy.set("limit",n);
  }
 }else if([...search.keys()].length) return null;
 return copy;
}
function methodQueryList(path:string){return ["directives","disclosures","cases"].includes(path);}
