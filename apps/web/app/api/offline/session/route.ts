import {NextRequest,NextResponse} from "next/server";
import {OPS_COOKIE} from "../../../../lib/operations-auth";

/**
 * Tells the installed device WHO the signed-in user is and for which hospital scope, without ever exposing the token.
 * The claims are read from the HttpOnly session cookie but are NOT verified here: they are used only for local admission and for
 * refusing to mix scopes or users in one device queue. The M38 service verifies the token on every call and is the only authority.
 */
function send(error:string,status:number){return NextResponse.json({error},{status,headers:{"Cache-Control":"no-store"}});}
const UUID=/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
export async function GET(request:NextRequest){
 const token=request.cookies.get(OPS_COOKIE.token)?.value;
 if(!token||token.length>16000||token.length<80)return send("identity_denied",401);
 const part=token.split(".")[1];
 if(!part)return send("identity_denied",401);
 let claims:Record<string,unknown>;
 try{claims=JSON.parse(Buffer.from(part,"base64url").toString("utf8"));}catch{return send("identity_denied",401);}
 const sub=claims.sub,cell=claims.synqoria_cell,facility=claims.synqoria_facility,exp=claims.exp;
 if(typeof sub!=="string"||!UUID.test(sub)||typeof cell!=="string"||!UUID.test(cell)||typeof facility!=="string"||!UUID.test(facility)||typeof exp!=="number")return send("identity_denied",401);
 if(exp*1000<=Date.now())return send("session_expired",401);
 return NextResponse.json({userId:sub,scope:`${cell}/${facility}`,expiresAt:exp*1000},{headers:{"Cache-Control":"no-store","X-Content-Type-Options":"nosniff"}});
}
