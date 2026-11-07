/** No institution-issued keys, external source ingesters, credentials or replay senders are in this BFF. */
const uuid="[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[1-8][a-fA-F0-9]{3}-[89aAbB][a-fA-F0-9]{3}-[a-fA-F0-9]{12}";
const tables=["partners","api_products","developer_apps","mapping_versions","messages","replay_requests"];
const creates=["partners","api_products","developer_apps","mapping_versions","replay_requests"];
const action=new RegExp("^(partners|api_products|developer_apps|mapping_versions|messages|replay_requests)/"+
 uuid+"/(verify|enable|suspend|retire|publish|approve|validate|review)$");
const allowedActions:Record<string,string[]>={
 partners:["verify","enable","suspend","retire"],api_products:["publish"],
 developer_apps:["approve","suspend"],mapping_versions:["validate","approve"],
 messages:["review"],replay_requests:["review"]
};
export function safeSegments(parts:unknown):parts is string[]{
 return Array.isArray(parts)&&parts.length>=1&&parts.length<=3&&
  parts.every(x=>typeof x==="string"&&x.length>0&&x.length<=80&&/^[A-Za-z0-9_-]+$/.test(x));
}
export function allowed(method:"GET"|"POST",path:string):boolean{
 if(method==="GET")return tables.includes(path);
 if(creates.includes(path))return true;
 const match=path.match(action);
 return !!match&&allowedActions[match[1]]?.includes(match[2]);
}
export function query(method:"GET"|"POST",route:string,q:URLSearchParams):URLSearchParams|null{
 if(method==="POST")return q.size===0?new URLSearchParams():null;
 if(!tables.includes(route)||[...q.keys()].some(k=>k!=="limit")||[...q.keys()].length>1)return null;
 const copy=new URLSearchParams();
 if(q.has("limit")){
  const v=q.get("limit")!;
  if(!/^\d{1,3}$/.test(v)||Number(v)<1||Number(v)>100)return null;
  copy.set("limit",v);
 }
 return copy;
}
