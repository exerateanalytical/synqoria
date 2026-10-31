/** P02-B0027. Demographic validation only; never infer names or birth dates. */
export type BirthInformation='known'|'estimated'|'unknown';
export type RegistrationDraft={name:string;birthInformation:BirthInformation;dateOfBirth:string;estimatedAge:string;language:'en'|'fr';duplicateReviewCleared:boolean};
export type IntakeError='name_too_long'|'invalid_date'|'future_birth'|'invalid_estimated_age'|'conflicting_birth_fields'|'review_required';
export function validateRegistrationDraft(d:RegistrationDraft,todayISO:string):IntakeError[]{
 const issues:IntakeError[]=[];
 const name=d.name.trim();if(name.length>200||/[\x00-\x1F\x7F]/.test(name))issues.push('name_too_long');
 const birth=d.dateOfBirth.trim(),age=d.estimatedAge.trim();
 if(d.birthInformation==='known'){
  const valid=/^\d{4}-\d{2}-\d{2}$/.test(birth)&&!Number.isNaN(Date.parse(birth))&&new Date(birth).toISOString().slice(0,10)===birth;
  if(!valid)issues.push('invalid_date');else if(birth>todayISO)issues.push('future_birth');
  if(age!=='')issues.push('conflicting_birth_fields');
 }else if(d.birthInformation==='estimated'){
  if(!/^(0|[1-9]\d{0,2})$/.test(age)||Number(age)>130)issues.push('invalid_estimated_age');
  if(birth!=='')issues.push('conflicting_birth_fields');
 }else if(birth!==''||age!=='')issues.push('conflicting_birth_fields');
 if(!d.duplicateReviewCleared)issues.push('review_required');
 return issues;
}
