export type Ops001Operation="login"|"callback"|"logout"|"patient-lookup"|"facility";
export function ops001ServerAction(action:Ops001Operation,request:Request):Promise<Response>;
export function ops001PublicStatusResponse():Response;
export function ops001RuntimeStatus():Readonly<{ready:boolean}>;
export function installOps001VerifiedRuntime(runtime:unknown,env:Record<string,string>,options?:{
 fetcher?:typeof fetch;now?:()=>number;
}):Readonly<{ready:boolean}>;
