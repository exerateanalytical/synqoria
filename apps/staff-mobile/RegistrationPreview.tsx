import {registrationReleasePresentation} from '@synqoria/ux-core';
import React from 'react';
import {duplicateStatus,registrationActionPresentation,intakeA11ySummary,accessibleLockedStatus,lockedRegistrationJourney} from '@synqoria/ux-core';
import {View,Text,Pressable,StyleSheet} from 'react-native';
/** P02-B0032 native STAFF intake source-only. No editable PHI before trusted session + approved server workflow. */
export function RegistrationPreview({locale}:{locale:'en'|'fr'}){
 const fr=locale==='fr';
 const review=duplicateStatus('NOT_CHECKED',locale);
 const release=accessibleLockedStatus(locale,'CLINICAL_RELEASE_LOCKED');
 const releaseState=registrationReleasePresentation(locale,'clinical');
 const action=registrationActionPresentation(locale,false,false);
 const summary=intakeA11ySummary(locale,['ACCESS','FACILITY','REVIEW','OFFLINE']);
 return <View style={s.panel} accessible accessibilityLabel={fr?'Enregistrement bloqué':'Registration locked'}>
  <Text accessibilityRole="header" style={s.heading}>{fr?'Enregistrement du patient':'Patient registration'}</Text>
  <Text style={s.text}>{fr?'La vérification du personnel, du site et des doublons est nécessaire avant toute saisie.':'Staff, facility and duplicate-review verification are required before intake.'}</Text>
  <Text nativeID={release.id} accessibilityRole="alert" style={s.warning}>{release.message}</Text>
  <Text accessibilityRole="text" style={s.warning}>{releaseState.title}: {releaseState.reason}</Text>
  <Text nativeID="intake-status" accessibilityRole="alert" style={s.warning}>{review.status}: {review.instruction}</Text>
  {lockedRegistrationJourney(locale).map(step=><Text key={step.id} accessibilityRole="text" style={s.text}>{step.label}: {fr?'Indisponible':'Unavailable'}</Text>)}
  {summary.items.map(issue=><Text key={issue.code} style={s.text} accessibilityRole="text">{issue.message}</Text>)}
  <Pressable disabled={true} focusable={false} accessibilityRole="button" accessibilityState={{disabled:true}} accessibilityLabel={`${action.buttonLabel}. ${action.reason}`} style={s.disabledAction}><Text style={s.actionText}>{action.buttonLabel}</Text></Pressable>
  <Text style={s.warning}>{fr?'Aucune donnée patient ne peut être saisie ou enregistrée.':'Patient data entry and saving are disabled.'}</Text>
 </View>;
}
const s=StyleSheet.create({panel:{backgroundColor:'#FFFFFF',padding:20,flexShrink:1,borderRadius:12,borderWidth:1,borderColor:'#DEDCD5'},heading:{fontSize:24,lineHeight:31,fontWeight:'700',color:'#172B4D'},text:{fontSize:16,lineHeight:24,color:'#475569',marginTop:12},warning:{fontSize:16,lineHeight:24,color:'#073656',marginTop:16},disabledAction:{marginTop:16,minHeight:48,backgroundColor:'#F0EEE7',borderWidth:1,borderColor:'#64748B',borderRadius:8,alignItems:'center',justifyContent:'center',paddingVertical:12,paddingHorizontal:16},actionText:{fontSize:16,lineHeight:24,color:'#172B4D',fontWeight:'700',textAlign:'center'}});
