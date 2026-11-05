export type UiPreferences={language:"en"|"fr";timeFormat:"12h"|"24h";density:"comfortable"|"compact";contrast:"standard"|"high";motion:"system"|"reduced"};
export const PREFERENCE_KEY:string;
export function defaultPreferences():UiPreferences;
export function sanitizePreferences(raw:unknown):UiPreferences;
export function encodePreferences(raw:unknown):string;
export function decodePreferences(text:unknown):UiPreferences;
export function formatTime(raw:string,locale:"en"|"fr",format:"12h"|"24h"):string;
