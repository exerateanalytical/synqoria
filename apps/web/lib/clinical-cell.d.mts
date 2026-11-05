export function configuredCellOrigin(env?:Record<string,string|undefined>):string|null;
export function validUuid(value:unknown):boolean;
export function verifyPatient(dto:unknown,id:string):boolean;
export function verifyTriageCase(dto:unknown,id:string):boolean;
export function reject(status:number,code:string):Response;
export function ok(value:unknown):Response;
export type ReadOptions={env?:Record<string,string|undefined>;fetcher?:typeof fetch};
export function readFacility(request:Request,options?:ReadOptions):Promise<Response>;
export function readPatient(request:Request,patientId:string,options?:ReadOptions):Promise<Response>;
export function readTriageCase(request:Request,patientId:string,caseId:string,options?:ReadOptions):Promise<Response>;
export function readCellHealth(request:Request,options?:ReadOptions):Promise<Response>;
export type TriageWorklistInput={view:string;caseIds:readonly string[];stationOrClinician?:string;after?:string};
export function validateTriageViewRequest(value:unknown):TriageWorklistInput|null;
export function readTriageWorklist(request:Request,patientId:string,options?:ReadOptions):Promise<Response>;

export interface TriageAuditRecord{revision:number;operation:string;occurredAt:string;digest:string}
export interface VerifiedTriageAuditPage{history:TriageAuditRecord[];nextAfterRevision:number|null}
export function validateTriageAuditPage(raw:unknown,afterRevision:number):VerifiedTriageAuditPage|null;
export function readTriageAudit(request:Request,patientId:string,caseId:string,options?:ReadOptions):Promise<Response>;
