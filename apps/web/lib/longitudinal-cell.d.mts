export type ClinicalSourceResource="summary"|"audit"|"note-index"|"observations"|"note"|"lineage";
export type ScopeIdentifiers={patientId:string;encounterId:string;memberId:string|null};
export type LongitudinalReadOptions={env?:Record<string,string|undefined>;fetcher?:typeof fetch};
export function parseLongitudinalParams(resource:string,query:Record<string,unknown>):Record<string,string|number>|null;
export function validateLongitudinalSource(resource:string,body:unknown,params:Record<string,string|number>,identifiers:ScopeIdentifiers):boolean;
export function readLongitudinal(request:Request,patientId:string,encounterId:string,resource:ClinicalSourceResource,memberId?:string|null,options?:LongitudinalReadOptions):Promise<Response>;
