import type {NativeAccessStatus} from './native-access-status';
import {nativeAccessStatus} from './native-access-status';
/** P02-B0072. Accessibility announcement for lifecycle lock; no patient identifiers. */
export function nativePrivacyAnnouncement(locale:'en'|'fr',covered:boolean,reason:NativeAccessStatus='NO_SESSION'){
 const status=nativeAccessStatus(locale,reason);
 const preface=locale==='fr'?'Affichage privé masqué.':'Private display hidden.';
 return {text:covered?`${preface} ${status.text}`:status.text,
  accessibilityRole:'alert' as const,accessibilityLiveRegion:'polite' as const,allowFontScaling:true,maxFontSizeMultiplier:3};
}
