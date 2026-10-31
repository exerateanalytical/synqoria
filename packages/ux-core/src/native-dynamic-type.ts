/** P02-B0059. Critical clinical labels may never be truncated to a fixed line count. */
export type NativeTextKind='body'|'heading'|'critical'|'label';
export function nativeTextSafety(kind:NativeTextKind){
 const minTouchTarget=kind==='critical'?48:44;
 return {allowFontScaling:true,maxFontSizeMultiplier:3,numberOfLines:undefined,
  style:{flexShrink:1,flexWrap:'wrap' as const},minTouchTarget};
}
export function nativeBottomDestinationCount(items:readonly {id:string}[]):boolean{
 return items.length>0&&items.length<=4&&new Set(items.map(i=>i.id)).size===items.length&&items.some(i=>i.id==='more');
}
