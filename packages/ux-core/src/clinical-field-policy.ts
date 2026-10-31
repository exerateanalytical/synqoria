/** P02-B0013 safety presentation policy. This does not validate clinical content. */
export type ClinicalFieldKind='medication'|'dose'|'route'|'allergy'|'identifier'|'clinical-instruction'|'noncritical-note';
export type FieldDisplay=Readonly<{allowEllipsis:boolean;wrap:true;minFontPx:16;needsAccessibleFullText:boolean}>;
const critical:ReadonlySet<ClinicalFieldKind>=new Set(['medication','dose','route','allergy','identifier','clinical-instruction']);
export function fieldDisplayPolicy(kind:ClinicalFieldKind,hasExpandedText:boolean):FieldDisplay{
 if(!critical.has(kind)&&kind!=='noncritical-note')throw new Error('Unknown field type');
 if(critical.has(kind))return {allowEllipsis:false,wrap:true,minFontPx:16,needsAccessibleFullText:false};
 return {allowEllipsis:hasExpandedText,wrap:true,minFontPx:16,needsAccessibleFullText:!hasExpandedText};
}
