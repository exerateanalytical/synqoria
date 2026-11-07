export declare const CANONICAL_PRINT_CODES: ReadonlyArray<string>;
export declare const DOCUMENT_TABLE_COLUMNS: Readonly<Record<string,string[]>>;
export declare const DOCUMENT_STATUSES: ReadonlyArray<string>;
export declare const PRINT_MEDIA: ReadonlyArray<string>;
export declare const DOCUMENT_RELEASE: Readonly<{generation:"SAMPLE_ONLY";clinical:"DENIED";signatures:"NOT_LEGALLY_VALID";patientData:"NONE";realPrint:"NOT_AUTHORIZED";retention:"POLICY_PENDING"}>;
export declare function resolveTemplate(
 template:{id:string;groupKeys:readonly string[];[key:string]:unknown},
 library:Record<string,{id:string;title:string;fields:Array<{id:string;label:string;type:string;required:string;source:string;recordValue:boolean}>}>
):unknown;
export declare function validateHospitalCatalog(
 catalog:{templates:ReadonlyArray<{id:string;module:string;title:string;media:string;groupKeys:readonly string[];canonicalPrintCode:string|null;release:{mode:string;clinical:string;dataSource:string;authorization:string}}>},
 library:Record<string,{fields:Array<{id:string;label:string;type:string}>}>
):string[];
