/** P02-B0029. No personal information in analytics, URL params, localStorage or diagnostic traces. */
export function safeRegistrationTelemetry(input:{state:string;correlationId?:string;patientName?:string;birthDate?:string;identifier?:string}):Readonly<{state:string}>{
 const allowed=new Set(['locked','review','unavailable','pending','committed','unknown']);
 return Object.freeze({state:allowed.has(input.state)?input.state:'unknown'});
}
export function canStoreIntakeOffline():false{return false;}
