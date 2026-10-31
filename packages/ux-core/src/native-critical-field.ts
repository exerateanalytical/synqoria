/** P02-B0076. Native drug/dose/allergy labels must scale and wrap; never ellipsize.
 * Declarative policy, not a substitute for 200% device layout verification. */
export type CriticalFieldKind='drug'|'dose'|'allergy';
export function nativeCriticalField(kind:CriticalFieldKind,locale:'en'|'fr'){
 const names={en:{drug:'Medication',dose:'Dose and instructions',allergy:'Allergy information'},fr:{drug:'Médicament',dose:'Dose et instructions',allergy:'Informations sur les allergies'}};
 if(!['drug','dose','allergy'].includes(kind)||!['en','fr'].includes(locale))throw new Error('Invalid field');
 return Object.freeze({label:names[locale][kind],allowFontScaling:true,maxFontSizeMultiplier:3,
  numberOfLines:undefined,ellipsizeMode:undefined,style:Object.freeze({fontSize:16,lineHeight:24,flexShrink:1,flexWrap:'wrap' as const,minHeight:48})});
}
