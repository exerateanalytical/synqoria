/** Non-clinical appearance settings. No identifiers, PHI, sessions or permission grants. */
export const PREFERENCE_KEY="synqoria.ui.preferences.v1";
const defaults=Object.freeze({language:"en",timeFormat:"24h",density:"comfortable",contrast:"standard",motion:"system"});
const accepted={
 language:new Set(["en","fr"]),
 timeFormat:new Set(["12h","24h"]),
 density:new Set(["comfortable","compact"]),
 contrast:new Set(["standard","high"]),
 motion:new Set(["system","reduced"])
};
export function defaultPreferences(){return {...defaults};}
export function sanitizePreferences(raw){
 if(!raw||typeof raw!=="object"||Array.isArray(raw))return defaultPreferences();
 return Object.fromEntries(Object.keys(defaults).map(k=>[k,accepted[k].has(raw[k])?raw[k]:defaults[k]]));
}
export function encodePreferences(raw){
 return JSON.stringify(sanitizePreferences(raw));
}
export function decodePreferences(text){
 if(typeof text!=="string"||text.length>512)return defaultPreferences();
 try{return sanitizePreferences(JSON.parse(text));}catch{return defaultPreferences();}
}
export function formatTime(raw,locale,format){
 const d=new Date(raw);
 if(!Number.isFinite(d.valueOf()))return "—";
 return d.toLocaleTimeString(locale==="fr"?"fr-CM":"en-CM",{hour:"2-digit",minute:"2-digit",hour12:format==="12h"});
}
