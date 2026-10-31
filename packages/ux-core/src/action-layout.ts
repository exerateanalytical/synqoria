/** P02-B0012: deterministic layout policy; do not shrink fonts or clip high-priority actions. */
export type Action=Readonly<{id:string;label:string;priority:'critical'|'primary'|'secondary'}>;
export type ActionLayout=Readonly<{columns:1|2;alwaysVisible:readonly Action[];overflow:readonly Action[];wrapLabels:true;minTargetPx:48}>;
export function planActions(actions:readonly Action[],viewportWidth:number,scale=1):ActionLayout{
 if(!Number.isFinite(viewportWidth)||viewportWidth<240||viewportWidth>10000||!Number.isFinite(scale)||scale<1||scale>3)throw new Error('Unsupported layout dimensions');
 const ids=new Set<string>();for(const a of actions){if(!/^[a-z][a-z0-9-]{0,50}$/.test(a.id)||ids.has(a.id)||!a.label.trim())throw new Error('Invalid action');ids.add(a.id);}
 const columns:1|2=viewportWidth>=720*scale?2:1;
 const critical=actions.filter(a=>a.priority==='critical'||a.priority==='primary');
 const secondary=actions.filter(a=>a.priority==='secondary');
 // On compact layouts, critical/primary actions always remain visible; only secondary actions move into More.
 return {columns,alwaysVisible:columns===1?critical:[...critical,...secondary],overflow:columns===1?secondary:[],wrapLabels:true,minTargetPx:48};
}
