/** P02-B0018: explicitly invalidates every clinical data surface before patient reauthorization.
 * The caller owns encrypted storage and UI caches; failures MUST deny the switch.
 */
export interface SensitiveClearPorts {
 abortPatientRequests():void;
 clearMemoryQueries():Promise<void>;
 clearPersistedDraftIndex():Promise<void>;
 clearTelemetryContext():void;
}
export function createSensitiveStateClear(ports:SensitiveClearPorts):()=>Promise<void>{
 return async()=>{
  ports.abortPatientRequests();
  let failed=false;
  try{await ports.clearMemoryQueries();}catch{failed=true;}
  try{await ports.clearPersistedDraftIndex();}catch{failed=true;}
  try{ports.clearTelemetryContext();}catch{failed=true;}
  if(failed)throw new Error('Sensitive state not cleared');
 };
}
