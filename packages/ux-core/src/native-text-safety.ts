/** P02-B0053. Critical content must not be hidden by font scaling or narrow screens. */
export type TextSafety={fontSize:number;lineHeight:undefined;minTouchTarget:number;allowFontScaling:true;numberOfLines:undefined;flexShrink:1,flexWrap:'wrap'};
export function criticalTextSafety(kind:'drug'|'dose'|'allergy'|'general',scale:number):TextSafety|null{
 if(!Number.isFinite(scale)||scale<1||scale>3)return null;
 const base=kind==='general'?16:16;
 return {fontSize:base,lineHeight:undefined,minTouchTarget:kind==='general'?44:48,allowFontScaling:true,numberOfLines:undefined,flexShrink:1,flexWrap:'wrap'};
}
