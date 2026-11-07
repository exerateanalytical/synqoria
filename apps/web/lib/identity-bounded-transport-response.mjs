/**
 * P03 B0112–B0116: independent HTTP resource bound at the trusted IdP transport.
 * Token/roster consumers keep their domain-specific parsers; this boundary
 * rejects malformed, oversized, redirected, cacheable or compressed HTTP before
 * a downstream deserializer can allocate attacker-controlled memory.
 */
export class IdentityBoundedResponseError extends Error {
 constructor(){super("identity_upstream_response_denied");this.name="IdentityBoundedResponseError";}
}
const deny=()=>{throw new IdentityBoundedResponseError();};
const textEncoder=new TextEncoder();
const contentType=/^application\/json(?:;\s*charset=utf-8)?$/i;
function validHeaders(response,maxBytes){
 const h=response.headers;
 if(!(h instanceof Headers)||!contentType.test(h.get("content-type")||"")||
    !/(?:^|,)\s*no-store\s*(?:,|$)/i.test(h.get("cache-control")||"")||
    h.has("set-cookie")||h.has("location")||
    (h.has("content-encoding")&&h.get("content-encoding")!=="identity"))deny();
 const length=h.get("content-length");
 if(length!==null&&(!/^(?:0|[1-9][0-9]{0,5})$/.test(length)||Number(length)>maxBytes))deny();
 return length===null?null:Number(length);
}
export async function verifyBoundedIdentityResponse(response,{expectedUrl,maxBytes,signal}={}){
 if(!(response instanceof Response)||response.status!==200||response.redirected||
    response.type==="opaque"||response.type==="opaqueredirect"||!response.body||
    response.bodyUsed||typeof expectedUrl!=="string"||
    (response.url!==""&&response.url!==expectedUrl)||
    !Number.isSafeInteger(maxBytes)||maxBytes<1024||maxBytes>32768||
    !signal||typeof signal.aborted!=="boolean"||signal.aborted)deny();
 const declared=validHeaders(response,maxBytes);
 let reader,complete=false,total=0;
 const parts=[];
 try{
  reader=response.body.getReader();
  for(;;){
   if(signal.aborted)deny();
   const part=await reader.read();
   if(signal.aborted)deny();
   if(part.done){complete=true;break;}
   if(!(part.value instanceof Uint8Array)||part.value.length===0)deny();
   total+=part.value.byteLength;
   if(total>maxBytes||declared!==null&&total>declared)deny();
   parts.push(part.value);
  }
  if(!complete||total===0||declared!==null&&total!==declared)deny();
  const buffer=new Uint8Array(total);
  let offset=0;for(const part of parts){buffer.set(part,offset);offset+=part.byteLength;}
  const text=new TextDecoder("utf-8",{fatal:true}).decode(buffer);
  const decoded=JSON.parse(text);
  if(!decoded||typeof decoded!=="object"||Array.isArray(decoded))deny();
  // Canonical JSON bytes also strip any attacker-controlled response headers.
  const canonical=textEncoder.encode(JSON.stringify(decoded));
  if(canonical.byteLength>maxBytes)deny();
  return new Response(canonical,{status:200,headers:{
    "Content-Type":"application/json","Cache-Control":"no-store"
  }});
 }catch{deny();}
 finally{
  if(reader){
   if(!complete){try{await reader.cancel();}catch{/* denied */}}
   try{reader.releaseLock();}catch{/* denied */}
  }
 }
}
