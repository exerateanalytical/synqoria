import type {PatientSwitchPorts} from './patient-switch-coordinator';
import type {ClinicalApiClient} from './clinical-api-client';
/** Only backend 200 + identity match counts as authorized; no patient facts go into telemetry. */
export function patientHttpPorts(client:ClinicalApiClient,token:()=>string|null,
 clearSensitiveState:()=>Promise<void>):PatientSwitchPorts {
 return {
  async clearSensitivePatientState(){await clearSensitiveState();},
  async authorizePatient(id,signal){
   const bearer=token();
   if(!bearer)return false;
   const result=await client.readPatient(id,bearer,signal);
   return result.status==='ok'&&result.patientId===id;
  }
 };
}
