/** P02-B0067. No queued clinical mutation without independently approved offline protocol. */
export type NativeAction='view_shell'|'view_cached_nonclinical'|'patient_register'|'prescribe'|'administer_medication'|'sign_note'|'review_approve';
export function nativeOfflineAction(action:NativeAction,online:boolean,serverAuthorized:boolean){
 if(action==='view_shell')return {allowed:true as const,reason:'PUBLIC_SHELL' as const};
 if(action==='view_cached_nonclinical')return {allowed:false as const,reason:'UNVERIFIED_CACHE_POLICY' as const};
 return {allowed:false as const,reason:online&&serverAuthorized?'CLINICAL_RELEASE_LOCKED' as const:'AUTHORITY_OR_CONNECTIVITY_MISSING' as const};
}
