import {NextResponse} from "next/server";
import {OPS_COOKIE,opsCallback,opsChallenge,opsConfig,opsCookieDefaults,
 opsDiscovery,opsRandom} from "../../../../../lib/operations-auth";

export async function GET(){
 try{
  const config=opsConfig(),discovery=await opsDiscovery(config);
  const state=opsRandom(),verifier=opsRandom(),nonce=opsRandom();
  const url=new URL(discovery.authorization_endpoint);
  const entries={
   response_type:"code",client_id:config.clientId,redirect_uri:opsCallback(config),
   scope:"openid profile",state,nonce,code_challenge:opsChallenge(verifier),
   code_challenge_method:"S256",prompt:"login"
  };
  for(const [name,value] of Object.entries(entries))url.searchParams.set(name,value);
  const response=NextResponse.redirect(url,302);
  response.cookies.set(OPS_COOKIE.state,state,{...opsCookieDefaults,maxAge:300});
  response.cookies.set(OPS_COOKIE.verifier,verifier,{...opsCookieDefaults,maxAge:300});
  return response;
 }catch{return NextResponse.json({error:"operations_identity_unavailable"},{status:503});}
}
