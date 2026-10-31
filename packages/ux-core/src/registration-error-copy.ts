/** P02-B0031: bilingual privacy-safe accessible feedback, no values/PHI in errors. */
import type {IntakeError} from './registration-draft';
const copy:Record<IntakeError,{en:string;fr:string}>={
 name_too_long:{en:'Name must be at most 200 characters.',fr:'Le nom ne doit pas dépasser 200 caractères.'},
 invalid_date:{en:'Enter a valid date of birth.',fr:'Saisissez une date de naissance valide.'},
 future_birth:{en:'Date of birth cannot be in the future.',fr:'La date de naissance ne peut pas être future.'},
 invalid_estimated_age:{en:'Enter an estimated age from 0 to 130.',fr:'Saisissez un âge estimé de 0 à 130 ans.'},
 conflicting_birth_fields:{en:'Choose one birth-information method.',fr:'Choisissez une seule méthode pour la naissance.'},
 review_required:{en:'Identity review by authorized staff is required.',fr:'Une vérification d’identité par un agent autorisé est requise.'}
};
export function registrationErrorCopy(error:IntakeError,locale:'en'|'fr'):string{return copy[error][locale];}
export function registrationIssueSummary(errors:IntakeError[],locale:'en'|'fr'):{heading:string;messages:string[]}{
 const unique=[...new Set(errors)];return {heading:locale==='fr'?`${unique.length} vérifications en attente`:`${unique.length} checks pending`,messages:unique.map(e=>registrationErrorCopy(e,locale))};
}
