import React, { useState, useEffect } from 'react';
import { mobileShellTabs, MobileVisibilityFence, MobilePrivacyLatch, nativeTextSafety, mobileScopeDisclosure, nativeLockedAnnouncement, nativeOfflineAction, lockedNavigationAccessibility, PatientProxyContext, NativePrivacyCurtain, nativePrivacyAnnouncement, nativeAccessStatus, nativeProxyGrantStatus, nativeDeepLinkGate, NativeResponseEpoch, NativeOfflineWriteQueue, nativeMoreItems, nativeMoreCanOpen } from '@synqoria/ux-core';
import { View, Text, Pressable, ScrollView, StyleSheet, StatusBar, AppState, KeyboardAvoidingView, Platform } from 'react-native';
import { SafeAreaProvider, SafeAreaView } from 'react-native-safe-area-context';
/** Native presentation-only foundation; verified API authentication is not connected. */
const colors={navy:'#073656',canvas:'#F7F5F0',surface:'#FFFFFF',text:'#172B4D',muted:'#475569',border:'#DEDCD5',blue:'#1664D4',subtle:'#F0EEE7'};
const tabs=[{id:'home',en:'Home',fr:'Accueil'},{id:'appointments',en:'Appointments',fr:'Rendez-vous'},{id:'records',en:'Records',fr:'Dossiers'},{id:'more',en:'More',fr:'Plus'}] as const;
type Tab=typeof tabs[number]['id'];
type Locale='en'|'fr';
const copy={
 en:{context:'My record · Not signed in',intro:'Your health record is private. Verified identity, current access rights and valid proxy grants are required.',status:'ACCESS STATUS',notConnected:'Not connected',content:'No health records, appointments or proxy data have been loaded.',unavailable:'Requires verified sign-in and authorization',language:'Language',prototype:'Engineering foundation — not ready for clinical use.'},
 fr:{context:'Mon dossier · Non connecté',intro:'Votre dossier médical est privé. Une identité vérifiée, des droits valides et un mandat de représentation actif sont nécessaires.',status:'ÉTAT DE L’ACCÈS',notConnected:'Non connecté',content:'Aucun dossier de santé, rendez-vous ou donnée de représentation n’a été chargé.',unavailable:'Authentification et autorisation vérifiées nécessaires',language:'Langue',prototype:'Fondation technique — non prête pour un usage clinique.'}
} as const;
export default function App(){
 const [active,setActive]=useState<Tab>(tabs[0].id);
 const [visibility]=useState(()=>new MobileVisibilityFence());
 const [privacy]=useState(()=>new MobilePrivacyLatch());
 const [curtain]=useState(()=>new NativePrivacyCurtain());
 const [privacyCovered,setPrivacyCovered]=useState(true);
 const [responseEpoch]=useState(()=>new NativeResponseEpoch());
 const [offlineQueue]=useState(()=>new NativeOfflineWriteQueue());
 const [proxyContext]=useState(()=>new PatientProxyContext());
 useEffect(()=>{const sub=AppState.addEventListener('change',state=>{if(state==='active'){privacy.foreground();curtain.resume();setPrivacyCovered(true);}else{curtain.background();setPrivacyCovered(true);responseEpoch.invalidate();privacy.hide();visibility.hide();proxyContext.background();setActive(tabs[0].id);} });return ()=>sub.remove();},[privacy,visibility,proxyContext,curtain,responseEpoch]);
 const [locale,setLocale]=useState<Locale>('en');
 const t=copy[locale];
 const lockedAnnouncement=nativeLockedAnnouncement('patient',locale);
 const clinicalAction=nativeOfflineAction('patient_register',false,false);
 const deepLink=nativeDeepLinkGate('/home','patient',false);
 const queueStatus=offlineQueue.request('appointment',false,false);
 const scope=mobileScopeDisclosure('patient',locale,null,Date.now(),false);
 const proxyStatus=nativeProxyGrantStatus(null,'','',0,Date.now(),false);
 const safeStatus=nativeAccessStatus(locale,'NO_SESSION');
 const privateStatus=nativePrivacyAnnouncement(locale,privacyCovered,'NO_SESSION');
 const safeTabLabels=mobileShellTabs('patient',locale,{kind:'locked'},false,0);
 const lockedTabA11y=lockedNavigationAccessibility('patient',locale);
 const selected=tabs.find(item=>item.id===active) ?? tabs[0];
 return <SafeAreaProvider><SafeAreaView edges={['top','bottom','left','right']} style={s.safe}><KeyboardAvoidingView style={s.keyboard} behavior={Platform.OS==='ios'?'padding':undefined}><StatusBar barStyle="dark-content" backgroundColor={colors.surface}/>
  <View style={s.header}>
   <View style={s.headingRow}><Text style={s.brand}>SYNQORIA</Text>
    <View accessible accessibilityLabel={t.language} style={s.languageRow}>
     <Pressable accessibilityRole="button" accessibilityLabel="English" accessibilityState={{selected:locale==='en'}} onPress={()=>setLocale('en')} style={[s.languageButton,locale==='en'&&s.languageActive]}><Text style={[s.languageLabel,locale==='en'&&s.languageSelected]}>EN</Text></Pressable>
     <Pressable accessibilityRole="button" accessibilityLabel="Français" accessibilityState={{selected:locale==='fr'}} onPress={()=>setLocale('fr')} style={[s.languageButton,locale==='fr'&&s.languageActive]}><Text style={[s.languageLabel,locale==='fr'&&s.languageSelected]}>FR</Text></Pressable>
    </View></View>
   <Text {...nativeTextSafety('label')} style={[s.context,nativeTextSafety('label').style]}>{scope.label}</Text>
  </View>
  <ScrollView style={s.scroller} contentContainerStyle={s.content} keyboardShouldPersistTaps="handled">
   <Text {...nativeTextSafety('heading')} accessibilityRole="header" style={[s.heading,nativeTextSafety('heading').style]}>{locale==='en'?selected.en:selected.fr}</Text>
   <Text {...nativeTextSafety('body')} style={[s.description,nativeTextSafety('body').style]}>{t.intro}</Text>
   <View style={s.card}><Text style={s.eyebrow}>{t.status}</Text><Text style={s.cardTitle}>{t.notConnected}</Text>
     <Text style={s.description}>{t.content}</Text></View>
   <View style={s.info}><Text style={s.infoText}>{safeStatus.text}</Text></View>
  {active==='more'&&<View accessibilityRole="summary" style={s.card}>
    <Text accessibilityRole="header" style={s.cardTitle}>{locale==='fr'?'Options locales':'Local options'}</Text>
    {nativeMoreItems('patient',locale).map(item=><View key={item.id} accessible accessibilityLabel={item.label+'. '+item.hint} style={s.moreRow}>
      <Text style={s.moreTitle}>{item.label}</Text><Text style={s.moreHint}>{item.hint}</Text>
    </View>)}
  </View>}

   {!proxyStatus.allowed&&<Text style={s.prototype}>{locale==='fr'?'Accès par représentant indisponible.':'Proxy access unavailable.'}</Text>}
   <Text accessibilityRole={privateStatus.accessibilityRole} accessibilityLiveRegion={privateStatus.accessibilityLiveRegion} allowFontScaling={privateStatus.allowFontScaling} maxFontSizeMultiplier={privateStatus.maxFontSizeMultiplier} style={s.prototype}>{privateStatus.text}</Text>
   <Text accessibilityRole={lockedAnnouncement.accessibilityRole} accessibilityLiveRegion={lockedAnnouncement.accessibilityLiveRegion} allowFontScaling={lockedAnnouncement.allowFontScaling} maxFontSizeMultiplier={lockedAnnouncement.maxFontSizeMultiplier} style={s.prototype}>{lockedAnnouncement.text}</Text><Text style={s.prototype}>{t.prototype} · {clinicalAction.reason} · {queueStatus.reason} · {deepLink.allowed?"":"NAVIGATION_LOCKED"}</Text>
  </ScrollView>
  <View accessibilityRole="tablist" style={s.tabs}>{tabs.map(tab=><Pressable key={tab.id} accessibilityRole="tab" accessibilityLabel={lockedTabA11y.find((_,i)=>tabs[i].id===tab.id)?.accessibilityLabel ?? (locale==='en'?tab.en:tab.fr)} accessibilityState={{selected:tab.id===active,disabled:!nativeMoreCanOpen(tab.id,false)}} accessibilityHint={safeTabLabels.find(t=>t.id===tab.id)?.reason ?? t.unavailable} disabled={!nativeMoreCanOpen(tab.id,false)} onPress={()=>{if(nativeMoreCanOpen(tab.id,false))setActive('more');}} style={s.tab}>
   <Text style={s.tabLabel}>{locale==='en'?tab.en:tab.fr}</Text></Pressable>)}</View>
 </KeyboardAvoidingView></SafeAreaView></SafeAreaProvider>;
}
const s=StyleSheet.create({
 safe:{flex:1,backgroundColor:colors.canvas},keyboard:{flex:1},scroller:{flex:1},header:{backgroundColor:colors.surface,paddingHorizontal:20,paddingVertical:12,borderBottomWidth:1,borderColor:colors.border},
 headingRow:{flexDirection:'row',flexWrap:'wrap',justifyContent:'space-between',alignItems:'center',gap:8},brand:{fontSize:20,fontWeight:'800',color:colors.navy},
 context:{fontSize:14,lineHeight:22,color:colors.muted,marginTop:4},content:{padding:20,paddingBottom:32,flexGrow:1},heading:{fontSize:24,lineHeight:31,fontWeight:'700',color:colors.text},
 description:{fontSize:16,lineHeight:24,color:colors.muted,marginTop:12},card:{backgroundColor:colors.surface,padding:20,borderRadius:12,borderWidth:1,borderColor:colors.border,marginTop:24},
 eyebrow:{fontSize:14,lineHeight:20,color:colors.muted,fontWeight:'700'},cardTitle:{fontSize:20,lineHeight:28,fontWeight:'700',color:colors.text,marginTop:8},
 info:{backgroundColor:colors.subtle,padding:16,marginTop:16,borderRadius:10},infoText:{color:colors.text,fontSize:16,lineHeight:24},prototype:{fontSize:14,color:colors.muted,marginTop:24,lineHeight:20},
 languageRow:{flexDirection:'row',flexWrap:'wrap',gap:6},languageButton:{minWidth:48,minHeight:48,borderWidth:1,borderColor:colors.border,justifyContent:'center',alignItems:'center',borderRadius:8},
 languageActive:{backgroundColor:colors.navy,borderColor:colors.navy},languageLabel:{fontSize:14,color:colors.text,fontWeight:'700'},languageSelected:{color:colors.surface},
 tabs:{flexDirection:'row',backgroundColor:colors.surface,borderTopWidth:1,borderColor:colors.border,paddingBottom:8},
 tab:{flex:1,minHeight:48,alignItems:'center',justifyContent:'center',paddingHorizontal:4,paddingVertical:8},active:{borderTopWidth:3,borderTopColor:colors.blue},
 moreRow:{paddingVertical:12,borderBottomWidth:1,borderColor:colors.border},moreTitle:{fontSize:16,lineHeight:24,fontWeight:'700',color:colors.text},moreHint:{fontSize:14,lineHeight:22,color:colors.muted},
 tabLabel:{fontSize:14,color:colors.muted,textAlign:'center',flexShrink:1,flexWrap:'wrap'},selected:{fontWeight:'700',color:colors.navy}
});
