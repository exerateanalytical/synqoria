/** SYNQORIA UI v1.1: screen-specific identity/registration design contracts.
 * All fields are unapproved specimens until domain, terminology and permission review.
 * These contracts must never be interpreted as approval to collect real credentials or PHI.
 */
export type IdentityScreenId =
 | "G-S01" | "G-S02" | "G-S03"
 | "M01-S01" | "M01-S02" | "M01-S03" | "M01-S04" | "M01-S05" | "M01-S06";
export type IdentityFieldType = "text"|"select"|"date"|"number"|"tel"|"email"|"textarea"|"password";
export type IdentityField = {
 key:string; en:string; fr:string; type:IdentityFieldType; section:string; required?:boolean;
 locked?:boolean; options?:ReadonlyArray<{value:string;en:string;fr:string}>;
};
export type IdentityScreenSpec = {
 id:IdentityScreenId; tabs:readonly string[]; description:string; fields:readonly IdentityField[];
 controls:readonly string[]; workflow:readonly string[];
 readonlyServer:true; clinicalMutationLocked:true;
};
const field=(key:string,en:string,fr:string,type:IdentityFieldType,section:string,required=false,locked=false,options?:IdentityField["options"]):IdentityField=>({key,en,fr,type,section,required,locked,options});
export const IDENTITY_SCREEN_SPECS:Record<IdentityScreenId,IdentityScreenSpec>={
 "G-S01":{id:"G-S01",tabs:["Access","Service status","Help"],description:"Organization-scoped sign in with protected identity provider handoff.",fields:[
 field("organization","Organization / hospital","Organisation / hôpital","text","account",true,true),
 field("account","Staff account identifier","Identifiant du personnel","text","account",true,true),
 field("authenticationMethod","Authentication method","Méthode d'authentification","select","account",true,true,[{value:"oidc",en:"Approved identity provider",fr:"Fournisseur d'identité approuvé"},{value:"password",en:"Password (policy-dependent)",fr:"Mot de passe (selon la politique)"}]),
 field("password","Password","Mot de passe","password","account",false,true),
 field("availability","Authentication service","Service d'authentification","text","service",false,true)
 ],controls:["Identity-provider entry","Organization selection","Password policy","Help","Service status","Unavailable-state explanation"],workflow:["Select verified hospital domain","Redirect using approved OIDC with CSRF/state and PKCE","Complete approved MFA","Receive server-confirmed scoped session"],readonlyServer:true,clinicalMutationLocked:true},
 "G-S02":{id:"G-S02",tabs:["Verify challenge","Recovery","Help"],description:"MFA challenge and recovery never execute within a prototype.",fields:[
 field("challenge","Challenge type","Type de défi","select","challenge",true,true,[{value:"totp",en:"Authenticator",fr:"Application d'authentification"},{value:"webauthn",en:"Passkey or security key",fr:"Clé de sécurité"},{value:"recovery",en:"Approved recovery",fr:"Récupération approuvée"}]),
 field("code","Verification code","Code de vérification","password","challenge",true,true),
 field("expires","Challenge expiry","Expiration du défi","text","challenge",false,true),
 field("recoveryMethod","Recovery method","Mode de récupération","select","recovery",false,true,[{value:"approved",en:"Organization-approved recovery",fr:"Récupération autorisée"}]),
 field("recoveryStatus","Safe retry status","État de nouvelle tentative","text","recovery",false,true)
 ],controls:["Challenge entry","Expiry","Retry limit","Recovery","Cancellation","Error boundary"],workflow:["Validate server-issued challenge","Apply rate limits and expiry","Verify with approved IdP","Enter authorized facility selection"],readonlyServer:true,clinicalMutationLocked:true},
 "G-S03":{id:"G-S03",tabs:["Facility","Role and shift","Scope review"],description:"Enter a verified facility, role and shift without manufacturing authorization.",fields:[
 field("facility","Permitted facility","Établissement autorisé","select","scope",true,true),
 field("role","Permitted role","Rôle autorisé","select","scope",true,true),
 field("shift","Current shift","Service en cours","select","scope",true,true),
 field("activeScope","Authorized scope","Périmètre autorisé","text","scope",true,true)
 ],controls:["Facility selector","Role selector","Shift selector","Scope summary","Confirm locked"],workflow:["Fetch permitted facilities from policy service","Verify current role and shift","Review active scope and expiry","Enter the server-authorized workspace"],readonlyServer:true,clinicalMutationLocked:true},
 "M01-S01":{id:"M01-S01",tabs:["Search","Advanced filters","Review results"],description:"Search-first patient discovery with patient existence protected until access is verified.",fields:[
 field("identifier","Identifier","Identifiant","text","query"),
 field("issuer","Identifier issuer","Émetteur de l'identifiant","text","query"),
 field("name","Name","Nom","text","query"),
 field("birthDate","Known birth date","Date de naissance connue","date","query"),
 field("age","Estimated age (years)","Âge estimé (années)","number","query"),
 field("contact","Contact reference","Référence de contact","tel","query"),
 field("matchConfidence","Match confidence","Fiabilité de correspondance","select","filters",false,false,[{value:"all",en:"All permitted levels",fr:"Tous niveaux autorisés"},{value:"review",en:"Needs reviewer attention",fr:"Révision requise"}]),
 field("verification","Identity verification","Vérification d'identité","select","filters",false,false,[{value:"all",en:"All permitted statuses",fr:"Tous statuts autorisés"},{value:"provisional",en:"Provisional",fr:"Provisoire"},{value:"verified",en:"Verified",fr:"Vérifié"}])
 ],controls:["Identifier/name input","Birth age distinction","Contact","Advanced filter toggle","Match confidence","Verification status","Clear filters","Search locked","Matching table","New registration link"],workflow:["Establish hospital and patient-search permission","Search identifiers before registration","Show only authorized match details and confidence","Independently review ambiguous matches before opening or registering"],readonlyServer:true,clinicalMutationLocked:true},
 "M01-S02":{id:"M01-S02",tabs:["Identity","Contacts & address","Review & duplicates"],description:"Synthetic in-memory registration specimen; independent duplicate review always outstanding.",fields:[
 field("name","Reported name","Nom déclaré","text","identity"),
 field("preferredName","Preferred display name","Nom d'usage","text","identity"),
 field("identifierType","Identifier type","Type d'identifiant","select","identity",false,false,[{value:"",en:"Select after policy review",fr:"Choisir après examen de la politique"},{value:"local",en:"Local facility identifier",fr:"Identifiant local"},{value:"external",en:"Other issuer-defined identifier",fr:"Autre identifiant d'un émetteur"}]),
 field("identifier","External identifier (if supplied)","Identifiant externe (si fourni)","text","identity"),
 field("issuer","Identifier issuer","Émetteur de l'identifiant","text","identity"),
 field("birthInformation","Birth information","Information de naissance","select","identity",true,false,[{value:"unknown",en:"Unknown",fr:"Inconnue"},{value:"known",en:"Known birth date",fr:"Date connue"},{value:"estimated",en:"Estimated age",fr:"Âge estimé"}]),
 field("dateOfBirth","Date of birth (if known)","Date de naissance (si connue)","date","identity"),
 field("estimatedAge","Estimated age (years)","Âge estimé (années)","number","identity"),
 field("language","Preferred language","Langue préférée","select","identity",true,false,[{value:"en",en:"English",fr:"Anglais"},{value:"fr",en:"French",fr:"Français"}]),
 field("demographics","Other demographic attributes","Autres données démographiques","text","identity",false,true),
 field("phone","Contact phone","Téléphone","tel","contact"),
 field("email","Contact email","Courriel","email","contact"),
 field("addressLine","Address","Adresse","text","contact"),
 field("city","Town / locality","Ville / localité","text","contact"),
 field("country","Country","Pays","text","contact"),
 field("contactSource","Information source","Source des informations","text","contact")
 ],controls:["Identity fields","Issuer and identifier","Known/estimated/unknown birth state","Demographics approval placeholder","Contacts and address","Draft review","Duplicate-review state","Validation summary","Clear volatile draft","Register action locked"],workflow:["Verify staff and facility scope","Search existing patient identifiers","Complete identity and contact data with known provenance","Request independent possible-duplicate review","Submit only through authorized audit and server idempotency after release approval"],readonlyServer:true,clinicalMutationLocked:true},
 "M01-S03":{id:"M01-S03",tabs:["Identity profile","Verification","History"],description:"Identity values and duplicate flags remain unavailable until a scoped patient read is authorized.",fields:[
 field("identifiers","Identifiers and issuers","Identifiants et émetteurs","text","summary",false,true),
 field("demographics","Demographic sources","Sources démographiques","text","summary",false,true),
 field("contacts","Contacts","Contacts","text","summary",false,true),
 field("verification","Identity verification status","Statut de vérification","text","verification",false,true),
 field("duplicateFlags","Duplicate-review flags","Indicateurs de doublons","text","verification",false,true)
 ],controls:["Patient context","Identifiers","Verification status","Duplicate flags","History","Request correction locked"],workflow:["Verify patient context with two identifiers","Read authorized identity versions","Display provenance and corrections","Request reviewed correction only"],readonlyServer:true,clinicalMutationLocked:true},
 "M01-S04":{id:"M01-S04",tabs:["Current & proposed","Source and reason","Review"],description:"Demographic corrections are versioned requests, not silent overwrites.",fields:[
 field("current","Current value (server-sourced)","Valeur actuelle (source serveur)","text","compare",false,true),
 field("proposed","Proposed value","Valeur proposée","text","compare"),
 field("source","Supporting source","Source justificative","text","reason"),
 field("reason","Correction reason","Motif de correction","textarea","reason"),
 field("effective","Effective date/time","Date et heure de prise d'effet","text","reason")
 ],controls:["Current-value read","Proposed value","Reason","Provenance","Effective time","Review","Submit locked"],workflow:["Fetch current identity with revision and patient scope","Record changed fields and source","Require reason and safe review","Request versioned correction via approved transaction"],readonlyServer:true,clinicalMutationLocked:true},
 "M01-S05":{id:"M01-S05",tabs:["Candidates","Impact","Proposal"],description:"Duplicate identity review cannot disclose candidates or propose merges without authorization.",fields:[
 field("candidateRecords","Candidate records","Dossiers candidats","text","candidates",false,true),
 field("distinguishing","Distinguishing identifiers","Identifiants distinctifs","text","candidates",false,true),
 field("encounters","Encounter references","Références des épisodes de soins","text","impact",false,true),
 field("mergeImpact","Potential merge impact","Impact de fusion potentiel","text","impact",false,true),
 field("source","Review provenance","Provenance de la revue","text","proposal",false,true)
 ],controls:["Candidate comparison","Distinguishing identifiers","Encounter impact","Reviewer acknowledgment","Propose merge locked"],workflow:["Obtain independent duplicate candidates","Compare identifiers and encounter histories","Record reviewer evidence without silent merge","Forward proposal for separate approval"],readonlyServer:true,clinicalMutationLocked:true},
 "M01-S06":{id:"M01-S06",tabs:["Merge lineage","Independent approval","Reversal history"],description:"Merge approvals must preserve source identities, source record lineage and independent review.",fields:[
 field("survivor","Survivor record","Dossier survivant","text","lineage",false,true),
 field("absorbed","Absorbed record","Dossier absorbé","text","lineage",false,true),
 field("references","Reference changes","Changements de références","text","lineage",false,true),
 field("approval","Independent approvals","Approbations indépendantes","text","approval",false,true),
 field("reversal","Reversal / correction history","Historique de réversion / correction","text","reversal",false,true)
 ],controls:["Survivor/absorbed comparison","Downstream reference impact","Independent approval","Reversal lineage","Approve merge locked"],workflow:["Verify identity-steward authorization and maker/checker separation","Recheck candidate identities and downstream references","Obtain independent attestation","Commit only through versioned merge and lineage service"],readonlyServer:true,clinicalMutationLocked:true}
};
export const IDENTITY_PREVIEW_IDS=Object.freeze(Object.keys(IDENTITY_SCREEN_SPECS) as IdentityScreenId[]);
