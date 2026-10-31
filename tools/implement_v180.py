from pathlib import Path
r=Path(__file__).resolve().parents[1]
def put(path,text):
 p=r/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text.strip()+'\n')
base='services/clinical-core/src/main/java/org/synqoria/security/'
put(base+'OidcTrustedRsaJwk.java',r'''
package org.synqoria.security;
import java.math.BigInteger;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** P03-B0024: construct a trusted, operator-pinned RSA JWK (not token-provided JWK). */
public final class OidcTrustedRsaJwk {
 private final String kid,thumbprint; private final RSAPublicKey key;
 public OidcTrustedRsaJwk(String kid, BigInteger modulus, BigInteger exponent, String expectedThumbprint){
  if(kid==null||!kid.matches("[A-Za-z0-9_-]{8,80}")||modulus==null||exponent==null||modulus.bitLength()<2048||modulus.bitLength()>4096||!exponent.equals(BigInteger.valueOf(65537)))throw new IllegalArgumentException("Invalid trusted RSA JWK");
  if(expectedThumbprint==null||!expectedThumbprint.matches("[A-Za-z0-9_-]{43}"))throw new IllegalArgumentException("Missing operator-pinned thumbprint");
  try{
   String e=Base64.getUrlEncoder().withoutPadding().encodeToString(unsigned(exponent));
   String n=Base64.getUrlEncoder().withoutPadding().encodeToString(unsigned(modulus));
   String canonical="{\"e\":\""+e+"\",\"kty\":\"RSA\",\"n\":\""+n+"\"}";
   String digest=Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
   if(!MessageDigest.isEqual(digest.getBytes(StandardCharsets.US_ASCII),expectedThumbprint.getBytes(StandardCharsets.US_ASCII)))throw new IllegalArgumentException("Trusted key thumbprint mismatch");
   key=(RSAPublicKey)KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(modulus,exponent));
   this.kid=kid;this.thumbprint=digest;
  }catch(GeneralSecurityException ex){throw new IllegalArgumentException("Invalid RSA public key",ex);}
 }
 private static byte[] unsigned(BigInteger v){byte[] a=v.toByteArray();return a.length>1&&a[0]==0?Arrays.copyOfRange(a,1,a.length):a;}
 public String kid(){return kid;} public String thumbprint(){return thumbprint;} public RSAPublicKey key(){return key;}
}
''')
put(base+'OidcProtectedHeaderFence.java',r'''
package org.synqoria.security;
import java.util.*;
/** P03-B0025: strict policy for a header decoded by a separately trusted duplicate-rejecting JSON parser. */
public final class OidcProtectedHeaderFence {
 private OidcProtectedHeaderFence(){}
 public static boolean allows(Map<String,Object> header,String expectedKid){
  if(header==null||header.size()!=3||expectedKid==null||!expectedKid.matches("[A-Za-z0-9_-]{8,80}"))return false;
  return "RS256".equals(header.get("alg"))&&"at+jwt".equals(header.get("typ"))&&expectedKid.equals(header.get("kid"));
 }
}
''')
put(base+'OidcSignedPayload.java',r'''
package org.synqoria.security;
import java.time.*;import java.util.*;import java.security.MessageDigest;
/** P03-B0026: verifies bytes and returns ONLY signed payload bytes, never an authenticated principal. */
public final class OidcSignedPayload {
 private OidcSignedPayload(){}
 public static Optional<byte[]> verify(String token,byte[] externallyParsedHeaderBytes,Map<String,Object> parsedHeader,
                                       OidcTrustedRsaJwk trustedKey,OidcKeyRotationFence rotation,Instant now){
  if(externallyParsedHeaderBytes==null||trustedKey==null||rotation==null||now==null||!rotation.active(trustedKey.kid(),now)||!OidcProtectedHeaderFence.allows(parsedHeader,trustedKey.kid()))return Optional.empty();
  var parts=OidcCompactEnvelope.parse(token);
  if(parts.isEmpty()||!MessageDigest.isEqual(parts.get().header(),externallyParsedHeaderBytes))return Optional.empty();
  if(!OidcRs256Primitive.verify(parts.get().signingInput(),parts.get().signature(),trustedKey.key()))return Optional.empty();
  return Optional.of(parts.get().payload());
 }
}
''')
put(base+'OidcTrustedClaimPolicy.java',r'''
package org.synqoria.security;
import java.time.*;import java.util.*;
/** P03-B0027: checks independently trusted, duplicate-free parsed claims. DOES NOT bind them to a JWS; not an admission API. */
public final class OidcTrustedClaimPolicy {
 public record Claims(String issuer,Set<String> audience,String authorizedParty,String subject,String sessionId,String tokenId,
                      Instant issuedAt,Instant notBefore,Instant expiresAt,Instant authenticatedAt){
  public Claims{audience=audience==null?Set.of():Set.copyOf(audience);}
 }
 private OidcTrustedClaimPolicy(){}
 public static boolean structurallyValid(Claims claims,OidcTrustPins pins,Instant now){
  if(claims==null||pins==null||now==null)return false;
  return pins.matches(claims.issuer(),claims.audience(),claims.authorizedParty())
   &&validOpaque(claims.subject(),8,128)&&validOpaque(claims.sessionId(),8,128)&&validOpaque(claims.tokenId(),12,128)
   &&OidcTemporalFence.current(claims.issuedAt(),claims.notBefore(),claims.expiresAt(),claims.authenticatedAt(),now);
 }
 private static boolean validOpaque(String s,int min,int max){return s!=null&&s.length()>=min&&s.length()<=max&&s.matches("[A-Za-z0-9_-]+");}
}
''')
put(base+'OidcSecurityAuditRecord.java',r'''
package org.synqoria.security;
import javax.crypto.Mac;import javax.crypto.spec.SecretKeySpec;import java.nio.charset.StandardCharsets;import java.security.*;import java.time.*;import java.util.*;
/** P03-B0028: PHI-free security decision metadata with HMAC-pseudonymous session reference. */
public final class OidcSecurityAuditRecord {
 public record Record(String event,Instant at,String sessionReference,String decision){
  public Record {if(!Set.of("TOKEN_REJECTED","KEY_REVOKED","GRANT_DENIED","SESSION_EXPIRED").contains(event)||at==null||sessionReference==null||!sessionReference.matches("[A-Za-z0-9_-]{43}")||!Set.of("DENY","LOCK").contains(decision))throw new IllegalArgumentException("Invalid security event");}
  public String safeLine(){return at+" event="+event+" session_hmac="+sessionReference+" decision="+decision;}
 }
 private OidcSecurityAuditRecord(){}
 public static Record denied(String event,Instant now,String opaqueSessionId,byte[] secret){
  if(now==null||opaqueSessionId==null||!opaqueSessionId.matches("[A-Za-z0-9_-]{8,128}")||secret==null||secret.length<32)throw new IllegalArgumentException("Invalid audit input");
  try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret,"HmacSHA256"));
   String ref=Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(("SYNQORIA-AUDIT-SESSION-v1:"+opaqueSessionId).getBytes(StandardCharsets.UTF_8)));
   return new Record(event,now,ref,"DENY");
  }catch(GeneralSecurityException e){throw new IllegalStateException("Audit cryptography unavailable",e);}
 }
}
''')
# UX modules
b='packages/ux-core/src/'
put(b+'safe-staff-route-intent.ts',r'''
/** P02-B0048: deny private IDs, query and redirects in staff navigation intents. This is NOT backend authorization. */
export type StaffIntent = 'work'|'patients'|'tasks'|'more';
const roots:Readonly<Record<StaffIntent,string>>=Object.freeze({work:'/workforce',patients:'/workforce/patients',tasks:'/workforce/tasks',more:'/workforce/more'});
export function safeStaffRouteIntent(intent:unknown,locked:boolean):{kind:'locked'|'navigate';href?:string}{
 if(locked||typeof intent!=='string'||!Object.prototype.hasOwnProperty.call(roots,intent))return {kind:'locked'};
 return {kind:'navigate',href:roots[intent as StaffIntent]};
}
export function safeStaffReturnPath(raw:unknown):string|null{
 if(typeof raw!=='string'||raw.length>80)return null;
 return (Object.values(roots) as string[]).includes(raw)?raw:null;
}
''')
put(b+'foreground-reauthorization.ts',r'''
/** P02-B0049: foreground entry is locked until a NEW server verification matches a new generation. */
export class ForegroundReauthorization {
 private epoch=0;private active=false;private lastVerified=-1;
 get generation(){return this.epoch;}
 get canDisplayProtectedData(){return this.active&&this.lastVerified===this.epoch;}
 background(){this.epoch++;this.active=false;this.lastVerified=-1;}
 revoke(){this.background();}
 resume(verification:{generation:number;online:boolean;serverVerified:boolean;grantFresh:boolean}):boolean{
  if(!verification||verification.generation!==this.epoch||!verification.online||!verification.serverVerified||!verification.grantFresh){this.active=false;return false;}
  this.active=true;this.lastVerified=this.epoch;return true;
 }
}
''')
put(b+'privacy-notification.ts',r'''
/** P02-B0050: public device notifications contain no patient, proxy, diagnosis or appointment details. */
export type SafeNoticeKind='new-task'|'appointment-update'|'access-expired'|'sync-review';
const copy:Readonly<Record<SafeNoticeKind,{en:string;fr:string}>>={
 'new-task':{en:'A work item requires secure sign-in.',fr:'Une tâche nécessite une connexion sécurisée.'},
 'appointment-update':{en:'An update is available in the secure app.',fr:'Une mise à jour est disponible dans l’application sécurisée.'},
 'access-expired':{en:'Your secure session has expired.',fr:'Votre session sécurisée a expiré.'},
 'sync-review':{en:'Open the app to review synchronization status.',fr:'Ouvrez l’application pour vérifier la synchronisation.'}
};
export function privacyNotification(kind:unknown,locale:'en'|'fr'):{title:string;body:string;data:Record<string,never>;containsClinicalData:false}|null{
 if(typeof kind!=='string'||!Object.prototype.hasOwnProperty.call(copy,kind)||!['en','fr'].includes(locale))return null;
 return {title:'SYNQORIA',body:copy[kind as SafeNoticeKind][locale],data:{},containsClinicalData:false};
}
''')
put(b+'locked-registration-journey.ts',r'''
/** P02-B0051: readonly step model, never permits clinical registration or editable patient data. */
export type IntakeStep='access'|'facility'|'duplicate-review'|'registration';
const steps:IntakeStep[]=['access','facility','duplicate-review','registration'];
const names={en:['Verify staff access','Confirm facility and shift','Review possible duplicates','Register patient'],fr:['Vérifier l’accès du personnel','Confirmer l’établissement et le service','Examiner les doublons possibles','Enregistrer le patient']};
export function lockedRegistrationJourney(locale:'en'|'fr'){
 return steps.map((id,i)=>({id,label:names[locale][i]!,status:'blocked' as const,canOpen:false,canSubmit:false,containsPatientData:false}));
}
''')
put(b+'locked-navigation-accessibility.ts',r'''
/** P02-B0052: accessible four-destination mobile navigation for signed-out state; labels never imply access. */
export type LockedApp='staff'|'patient';
const labels={
 staff:{en:['Work','Patients','Tasks','More'],fr:['Travail','Patients','Tâches','Plus']},
 patient:{en:['Home','Appointments','Records','More'],fr:['Accueil','Rendez-vous','Dossiers','Plus']}
} as const;
export function lockedNavigationAccessibility(app:LockedApp,locale:'en'|'fr'){
 return labels[app][locale].map((label,index)=>({id:index,accessibilityLabel:label,accessibilityHint:locale==='fr'?'Connexion et autorisation requises':'Verified sign-in and authorization required',selected:false,disabled:true,minTouchTarget:48}));
}
''')
idx=r/'packages/ux-core/src/index.ts'
idx.write_text(idx.read_text()+"\n"+"\n".join("export * from './"+s+"';" for s in ['safe-staff-route-intent','foreground-reauthorization','privacy-notification','locked-registration-journey','locked-navigation-accessibility'])+'\n')
# real native integration; read-only model only
app=r/'apps/staff-mobile/RegistrationPreview.tsx';t=app.read_text();t=t.replace('accessibleLockedStatus}', 'accessibleLockedStatus,lockedRegistrationJourney}')
t=t.replace('  {summary.items.map(issue=>', '  {lockedRegistrationJourney(locale).map(step=><Text key={step.id} accessibilityRole="text" style={s.text}>{step.label}: {fr?\'Indisponible\':\'Unavailable\'}</Text>)}\n  {summary.items.map(issue=>');app.write_text(t)
for appname in ['staff-mobile','patient-mobile']:
 p=r/f'apps/{appname}/App.tsx';t=p.read_text();t=t.replace('MobileVisibilityFence', 'MobileVisibilityFence, lockedNavigationAccessibility',1);t=t.replace('const selected=tabs.find(',f"const lockedTabA11y=lockedNavigationAccessibility('{ 'staff' if appname=='staff-mobile' else 'patient'}',locale);\n const selected=tabs.find(")
 t=t.replace('accessibilityLabel={safeTabLabels.find(t=>t.id===tab.id)?.label ?? (locale===\'en\'?tab.en:tab.fr)}','accessibilityLabel={lockedTabA11y.find((_,i)=>tabs[i].id===tab.id)?.accessibilityLabel ?? (locale===\'en\'?tab.en:tab.fr)}')
 p.write_text(t)
print('Wrote 10 independent components and read-only native integrations')
