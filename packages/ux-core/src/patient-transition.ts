/**
 * P02-B0004: presentation-only clinical context switching state machine.
 * Identifiers are opaque; no names/health information or URLs appear in this state.
 * `authorizeTarget` may only be dispatched after a separately trusted backend result;
 * the reducer cannot itself establish patient entitlement or server persistence.
 */
export type PatientContext = Readonly<
  | { stage:'active'; patientId:string|null; dirty:boolean }
  | { stage:'confirm-unsaved'; patientId:string; targetId:string }
  | { stage:'saving-draft'; patientId:string; targetId:string }
  | { stage:'reauthorizing'; patientId:null; targetId:string; clearPatientCaches:true; clearSensitiveTelemetry:true }
>;
export type PatientContextAction =
  | { type:'mark-dirty' }
  | { type:'request-switch'; targetId:string }
  | { type:'cancel' }
  | { type:'discard' }
  | { type:'request-save-draft' }
  | { type:'draft-save-confirmed'; confirmedByServer:boolean }
  | { type:'authorize-target'; targetId:string; backendAccessVerified:boolean }
  | { type:'authorization-denied' };

export const emptyPatientContext:PatientContext = Object.freeze({stage:'active',patientId:null,dirty:false});
function mayUseOpaqueId(value:string):boolean {
  // UI identifier only; never allow patient names, URLs or free text in navigation state.
  return /^[a-zA-Z0-9_-]{8,128}$/.test(value);
}
function reauthorize(targetId:string):PatientContext {
  return {stage:'reauthorizing',patientId:null,targetId,clearPatientCaches:true,clearSensitiveTelemetry:true};
}
export function transitionPatientContext(state:PatientContext,action:PatientContextAction):PatientContext {
  if(action.type==='request-switch') {
    if(!mayUseOpaqueId(action.targetId) || state.stage!=='active') return state;
    if(state.patientId===action.targetId) return state;
    return state.patientId!==null && state.dirty
      ? {stage:'confirm-unsaved',patientId:state.patientId,targetId:action.targetId}
      : reauthorize(action.targetId);
  }
  if(action.type==='mark-dirty') return state.stage==='active' && state.patientId!==null
    ? {...state,dirty:true} : state;
  if(action.type==='cancel') {
    if(state.stage==='confirm-unsaved' || state.stage==='saving-draft')
      return {stage:'active',patientId:state.patientId,dirty:true};
    return state;
  }
  if(action.type==='discard' && state.stage==='confirm-unsaved') return reauthorize(state.targetId);
  if(action.type==='request-save-draft' && state.stage==='confirm-unsaved')
    return {stage:'saving-draft',patientId:state.patientId,targetId:state.targetId};
  if(action.type==='draft-save-confirmed' && state.stage==='saving-draft')
    return action.confirmedByServer ? reauthorize(state.targetId) : state;
  if(action.type==='authorize-target' && state.stage==='reauthorizing'
      && action.targetId===state.targetId && action.backendAccessVerified)
    return {stage:'active',patientId:action.targetId,dirty:false};
  if(action.type==='authorization-denied' && state.stage==='reauthorizing') return emptyPatientContext;
  return state;
}
