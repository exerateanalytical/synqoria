import {NextRequest,NextResponse} from "next/server";
import {OPS_COOKIE,opsCallback,opsConfig,opsCookieDefaults,
 opsDiscovery,opsEqual,opsRandom} from "../../../../../lib/operations-auth";

export async function GET(request:NextRequest){
 const state=request.nextUrl.searchParams.get("state")||"";
 const code=request.nextUrl.searchParams.get("code")||"";
 const stored=request.cookies.get(OPS_COOKIE.state)?.value||"";
 const verifier=request.cookies.get(OPS_COOKIE.verifier)?.value||"";
 try{
  const config=opsConfig();
  if(!state||!stored||!opsEqual(state,stored)||!code||code.length>4096||
    !/^[A-Za-z0-9_~-]{40,128}$/.test(verifier))
   return NextResponse.json({error:"invalid_oauth_callback"},{status:400});
  const discovery=await opsDiscovery(config);
  const params=new URLSearchParams({
   grant_type:"authorization_code",client_id:config.clientId,code,
   redirect_uri:opsCallback(config),code_verifier:verifier
  });
  if(config.clientSecret)params.set("client_secret",config.clientSecret);
  const exchange=await fetch(discovery.token_endpoint,{
   method:"POST",headers:{"Content-Type":"application/x-www-form-urlencoded"},
   body:params.toString(),cache:"no-store",signal:AbortSignal.timeout(8000)
  });
  if(!exchange.ok)return NextResponse.json({error:"oauth_exchange_rejected"},{status:401});
  const result=await exchange.json() as Record<string,unknown>;
  if(result.token_type!=="Bearer"||typeof result.access_token!=="string"||
     result.access_token.length>16000||result.access_token.length<80 ||
     !Number.isFinite(Number(result.expires_in))||Number(result.expires_in)<60)
   return NextResponse.json({error:"invalid_oidc_response"},{status:401});
  const maxAge=Math.min(Math.floor(Number(result.expires_in)),900);
  const response=NextResponse.redirect(config.webOrigin+"/workforce/operations",303);
  response.cookies.set(OPS_COOKIE.token,result.access_token,{
   ...opsCookieDefaults,sameSite:"strict",maxAge
  });
  response.cookies.set(OPS_COOKIE.csrf,opsRandom(),{
   path:"/",secure:true,sameSite:"strict",httpOnly:false,maxAge
  });
  response.cookies.set(OPS_COOKIE.state,"",{...opsCookieDefaults,maxAge:0});
  response.cookies.set(OPS_COOKIE.verifier,"",{...opsCookieDefaults,maxAge:0});
  return response;
 }catch{return NextResponse.json({error:"operations_identity_unavailable"},{status:503});}
}
