"use client";

import {useCallback,useEffect,useMemo,useState} from "react";
import Link from "next/link";
import styles from "../app/workforce/mortuary/mortuary.module.css";

type CaseView={
 caseId:string;decedentRef:string;originatingUnitRef:string;belongingsSealRef:string;
 intakeReceivingStaff:string;state:string;version:number;
 currentStorage:string|null;receivedAt:string;releasedAt:string|null
};
type Custody={version:number;eventType:string;fromStorage:string|null;toStorage:string|null;
 actorId:string;receivingStaffId:string|null;evidenceRef:string;occurredAt:string;
 sealRef:string|null;conditionCode:string|null;witnessStaffId:string|null;belongingsRef:string|null};
type Approval={stage:string;actorId:string;caseVersion:number;createdAt:string};
type Audit={version:number;action:string;fromState:string|null;toState:string;actorId:string;occurredAt:string};
type Screen="M27-S01"|"M27-S02"|"M27-S03"|"M27-S04"|"M27-S05";
type Action="IDENTIFY"|"STORE"|"TRANSFER"|"REVIEW_RELEASE"|"AUTHORIZE_RELEASE"|"HANDOVER";
type FieldKey="evidenceRef"|"storageCode"|"receivingStaffId"|"recipientRef"|"authorityRef"
 |"sealRef"|"conditionCode"|"witnessStaffId"|"belongingsReleaseRef";
type Field={key:FieldKey;label:string;labelFr:string;hint:string};
type State="loading"|"authorized"|"signin"|"forbidden"|"unavailable";
const apiPrefix="/api/operations/proxy/M27/";
const emailLessCode=/^[A-Za-z0-9_.:/-]{1,160}$/;
const uuid=/^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$/;
const fields:Record<Action,Field[]>={
 IDENTIFY:[{key:"evidenceRef",label:"Independent identity verification reference",
  labelFr:"Référence de vérification d'identité",hint:"Must be issued by a trusted identity authority"}],
 STORE:[
  {key:"storageCode",label:"Secure storage code",labelFr:"Emplacement sécurisé",hint:"Validated mortuary bay"},
  {key:"receivingStaffId",label:"Receiving staff UUID",labelFr:"Identifiant de l'agent réceptionnaire",hint:"Staff must have an active custody shift"},
  {key:"sealRef",label:"Tamper-evident custody seal reference",labelFr:"Référence du scellé de garde",hint:"Verified sealed inventory"},
  {key:"conditionCode",label:"Recorded custody condition code",labelFr:"Code d'état à la réception",hint:"Authorized controlled code, no free-text narrative"},
  {key:"evidenceRef",label:"Storage safety evidence reference",labelFr:"Preuve de conformité du stockage",hint:"Independently certified storage record"}],
 TRANSFER:[
  {key:"storageCode",label:"New storage location",labelFr:"Nouvel emplacement",hint:"Different from current location"},
  {key:"receivingStaffId",label:"Receiving staff UUID",labelFr:"Identifiant de l'agent réceptionnaire",hint:"Must be a different active employee"},
  {key:"sealRef",label:"New custody seal reference",labelFr:"Nouveau scellé de garde",hint:"Tamper-evident documented seal"},
  {key:"conditionCode",label:"Received condition code",labelFr:"Code de l'état reçu",hint:"Explicit controlled value"},
  {key:"evidenceRef",label:"Independent custody acceptance",labelFr:"Attestation indépendante de transfert",hint:"Externally verified signed custody evidence"}],
 REVIEW_RELEASE:[
  {key:"authorityRef",label:"Legal release authority reference",labelFr:"Référence de l'autorité légale",hint:"Independently signed release eligibility"},
  {key:"recipientRef",label:"Verified recipient reference",labelFr:"Référence du destinataire vérifié",hint:"Recipient identity authority evidence"}],
 AUTHORIZE_RELEASE:[
  {key:"authorityRef",label:"Previously reviewed legal authority",labelFr:"Autorité légale déjà examinée",hint:"Must match independent release review"},
  {key:"recipientRef",label:"Previously verified recipient",labelFr:"Destinataire préalablement vérifié",hint:"Must match review and be freshly reverified"}],
 HANDOVER:[
  {key:"authorityRef",label:"Approved release authority",labelFr:"Autorité de remise approuvée",hint:"Must match both reviewers"},
  {key:"recipientRef",label:"Verified handover recipient",labelFr:"Destinataire vérifié",hint:"Fresh identity verification required"},
  {key:"witnessStaffId",label:"Independent witness staff UUID",labelFr:"Identifiant du témoin indépendant",hint:"Currently authorized receiving officer, separate from all release actors"},
  {key:"belongingsReleaseRef",label:"Verified belongings return receipt",labelFr:"Récépissé de remise des effets",hint:"Independently attested belongings release"},
  {key:"evidenceRef",label:"Signed handover receipt reference",labelFr:"Référence du récépissé signé",hint:"Externally verified handover acknowledgement"}]
};
const applicable:Record<Screen,Partial<Record<string,Action[]>>>={
 "M27-S01":{},
 "M27-S02":{RECEIVED:["IDENTIFY"]},
 "M27-S03":{IDENTIFIED:["STORE"],STORED:["TRANSFER"]},
 "M27-S04":{STORED:["REVIEW_RELEASE"],RELEASE_REVIEWED:["AUTHORIZE_RELEASE"]},
 "M27-S05":{RELEASE_AUTHORIZED:["HANDOVER"]}
};
const descriptions:Record<Action,{en:string;fr:string}>={
 IDENTIFY:{en:"Confirm identity using evidence from an independent authority.",
  fr:"Confirmer l'identité à partir d'une preuve émise par une autorité indépendante."},
 STORE:{en:"Move into a verified safe storage location with an authorized receiving custodian.",
  fr:"Enregistrer la mise en conservation avec un agent autorisé."},
 TRANSFER:{en:"Preserve the previous location and append a new custody event.",
  fr:"Conserver l'emplacement précédent et ajouter un événement de garde."},
 REVIEW_RELEASE:{en:"First independent release review; an active legal hold blocks this step.",
  fr:"Premier examen indépendant ; une opposition légale active bloque cette étape."},
 AUTHORIZE_RELEASE:{en:"Second approver must differ from the first reviewer and intake officer.",
  fr:"Le deuxième approbateur doit être distinct du premier et de l'agent d'admission."},
 HANDOVER:{en:"Final handover requires a third independent employee and a verified receipt.",
  fr:"La remise finale exige un troisième agent indépendant et un récépissé vérifié."}
};
const csrf=()=>{
 if(typeof document==="undefined")return "";
 const entry=document.cookie.split("; ").find(x=>x.startsWith("__Host-synq_ops_csrf="));
 return entry?decodeURIComponent(entry.slice("__Host-synq_ops_csrf=".length)):"";
};
async function request<T>(path:string,body?:object):Promise<T>{
 const response=await fetch(apiPrefix+path,{
  method:body?"POST":"GET",cache:"no-store",credentials:"same-origin",
  headers:body?{"Content-Type":"application/json","x-synq-ops-csrf":csrf()}:
   {"Accept":"application/json"},
  ...(body?{body:JSON.stringify(body)}:{})
 });
 let value:unknown;
 try{value=await response.json();}catch{throw new Error("invalid_server_response");}
 if(!response.ok){
  const code=value&&typeof value==="object"&&"error" in value?
   String((value as {error:unknown}).error):"operation_failed";
  const err=new Error(code) as Error&{status?:number};
  err.status=response.status;throw err;
 }
 return value as T;
}
function message(error:unknown){
 if(error instanceof Error)return error.message.replaceAll("_"," ");
 return "The request was not completed";
}
function readableDate(value:string|null){
 if(!value)return "—";
 const date=new Date(value);
 return Number.isFinite(date.getTime())?date.toLocaleString():"—";
}
function personRef(id:string){return id.length>18?id.slice(0,8)+"…"+id.slice(-5):id;}
export function MortuaryWorkbench({screenId,name,nameFr,primaryAction}:{
 screenId:string;name:string;nameFr:string;primaryAction:string
}){
 const screen=screenId as Screen;
 const [language,setLanguage]=useState<"en"|"fr">("en");
 const [state,setState]=useState<State>("loading");
 const [records,setRecords]=useState<CaseView[]>([]);
 const [selectedId,setSelectedId]=useState("");
 const [custody,setCustody]=useState<Custody[]>([]);
 const [approvals,setApprovals]=useState<Approval[]>([]);
 const [audits,setAudits]=useState<Audit[]>([]);
 const [query,setQuery]=useState("");
 const [form,setForm]=useState<Record<string,string>>({});
 const [pendingCommand,setPendingCommand]=useState<string|null>(null);
 const [busy,setBusy]=useState(false);
 const [notice,setNotice]=useState("");
 const selected=records.find(x=>x.caseId===selectedId)||null;
 const available=selected?(applicable[screen][selected.state]||[]):[];
 const say=(en:string,fr:string)=>language==="fr"?fr:en;
 const load=useCallback(async()=>{
  const data=await request<CaseView[]>("cases?limit=100");
  setRecords(data);setState("authorized");
 },[]);
 useEffect(()=>{
  let alive=true;
  request<CaseView[]>("cases?limit=100")
   .then(data=>{if(alive){setRecords(data);setState("authorized");}})
   .catch(err=>{if(alive){
    setState((err as {status?:number}).status===401?"signin":
      (err as {status?:number}).status===403?"forbidden":"unavailable");
    setNotice(message(err));
   }});
  return ()=>{alive=false;};
 },[]);
 useEffect(()=>{
  if(!selectedId){setCustody([]);setApprovals([]);setAudits([]);return;}
  let active=true;
  request<Custody[]>(`cases/${selectedId}/custody`)
   .then(rows=>{if(active)setCustody(rows);})
   .catch(()=>{if(active)setCustody([]);});
  if(screen==="M27-S04"||screen==="M27-S05"){
   request<Approval[]>(`cases/${selectedId}/approvals`)
    .then(rows=>{if(active)setApprovals(rows);})
    .catch(()=>{if(active)setApprovals([]);});
  }
  if(screen==="M27-S05"){
   request<Audit[]>(`cases/${selectedId}/audit`)
    .then(rows=>{if(active)setAudits(rows);})
    .catch(()=>{if(active)setAudits([]);});
  }
  return ()=>{active=false;};
 },[selectedId,screen,selected?.version]);
 const filtered=useMemo(()=>records.filter(c=>[c.caseId,c.decedentRef,c.state,
  c.currentStorage||""].some(s=>s.toLowerCase().includes(query.trim().toLowerCase()))),[records,query]);
 const choose=(id:string)=>{setSelectedId(id);setForm({});setPendingCommand(null);setNotice("");};
 const onChange=(key:string,value:string)=>{setForm(current=>({...current,[key]:value}));setPendingCommand(null);};
 async function execute(action:Action|"RECEIVE"){
  if(busy)return;
  const data=form;
  try{
   const required=action==="RECEIVE"?
    ["decedentRef","intakeRef","deathRef","originatingUnitRef","authorizationRef",
     "belongingsSealRef","receivingStaffId"]:fields[action].map(f=>f.key);
   for(const key of required){
    const value=data[key]?.trim();
    if(!value)throw new Error("required_"+key);
    if(key==="decedentRef"||key==="receivingStaffId"||key==="witnessStaffId"){
     if(!uuid.test(value))throw new Error("invalid_"+key);
    }else if(!emailLessCode.test(value))throw new Error("invalid_"+key);
   }
   if(action!=="RECEIVE"&&!selected)throw new Error("select_a_case");
   setBusy(true);setNotice("");
   const cmd=pendingCommand||crypto.randomUUID();
   setPendingCommand(cmd);
   let updated:CaseView;
   if(action==="RECEIVE"){
    updated=await request<CaseView>("cases",{decedentRef:data.decedentRef.trim(),
     intakeRef:data.intakeRef.trim(),deathRef:data.deathRef.trim(),
     originatingUnitRef:data.originatingUnitRef.trim(),
     authorizationRef:data.authorizationRef.trim(),
     belongingsSealRef:data.belongingsSealRef.trim(),
     receivingStaffId:data.receivingStaffId.trim(),commandId:cmd});
   }else{
    updated=await request<CaseView>(`cases/${selected!.caseId}/actions`,{
     action,expectedVersion:selected!.version,commandId:cmd,
     evidenceRef:data.evidenceRef?.trim()||null,
     storageCode:data.storageCode?.trim()||null,
     receivingStaffId:data.receivingStaffId?.trim()||null,
     recipientRef:data.recipientRef?.trim()||null,
     authorityRef:data.authorityRef?.trim()||null,
     sealRef:data.sealRef?.trim()||null,
     conditionCode:data.conditionCode?.trim()||null,
     witnessStaffId:data.witnessStaffId?.trim()||null,
     belongingsReleaseRef:data.belongingsReleaseRef?.trim()||null
    });
   }
   setForm({});setPendingCommand(null);
   await load();
   setSelectedId(updated.caseId);
   setNotice(say("The authorized transition was recorded. Confirm the new state and custody history.",
    "L'opération autorisée est enregistrée. Vérifiez le nouvel état et la chaîne de garde."));
  }catch(err){setNotice(say("Action denied or not completed: ","Action refusée ou non achevée : ")+message(err));}
  finally{setBusy(false);}
 }
 const fieldInput=(key:string,label:string,hint:string,kind:"text"|"uuid"="text")=>(
  <label className={styles.field} key={key}>
   <span>{label}</span>
   <input type="text" maxLength={160} autoComplete="off" inputMode="text"
    aria-describedby={screenId+"-"+key+"-hint"}
    value={form[key]||""} onChange={e=>onChange(key,e.target.value)} required
    placeholder={kind==="uuid"?"xxxxxxxx-xxxx-4xxx-8xxx-xxxxxxxxxxxx":"Validated reference"}/>
   <small id={screenId+"-"+key+"-hint"}>{hint}</small>
  </label>);
 const activeAction=available[0]||null;
 return <section className={styles.root} aria-labelledby="mortuary-workstation">
  <header className={styles.intro}>
   <div>
    <p className={styles.eyebrow}>SYNQORIA · M27 · {screenId}</p>
    <h1 id="mortuary-workstation">{say(name,nameFr)}</h1>
    <p className={styles.subtitle}>{say(primaryAction,"Traçabilité, sécurité et double validation")}</p>
    <p className={styles.hint}>
     {say("No free-text patient names are collected. Every action requires a current staff shift and independently issued evidence.",
      "Aucun nom libre n'est recueilli. Chaque action nécessite une habilitation en cours et des preuves indépendantes.")}
    </p>
   </div>
   <div className={styles.controls}>
    <button type="button" onClick={()=>setLanguage(v=>v==="en"?"fr":"en")}
     className={styles.quiet}>{language==="en"?"Français":"English"}</button>
    <button type="button" onClick={()=>load().catch(err=>setNotice(message(err)))}
     disabled={busy} className={styles.quiet}>{say("Refresh","Actualiser")}</button>
   </div>
  </header>
  {notice&&<p role="alert" className={styles.notice}>{notice}</p>}
  {state==="loading"&&<p role="status">{say("Verifying hospital identity and loading records…",
   "Vérification des autorisations et chargement…")}</p>}
  {state==="signin"&&<section className={styles.gate}>
   <h2>{say("Verified hospital sign-in required","Connexion hospitalière vérifiée requise")}</h2>
   <p>{say("Access is blocked until the authorized OIDC session is established.",
    "L'accès reste bloqué sans une session institutionnelle valide.")}</p>
   <a className={styles.primaryLink} href="/api/operations/auth/start">
    {say("Sign in with hospital identity","Se connecter avec l'identité institutionnelle")}</a>
  </section>}
  {(state==="unavailable"||state==="forbidden")&&<section className={styles.gate}>
   <h2>{say("Access not available","Accès indisponible")}</h2>
   <p>{say("Hospital identity, roster revision, shift, operational grants and registry evidence must be configured and verified.",
    "L'identité, les habilitations, les gardes et les justificatifs institutionnels doivent être vérifiés.")}</p>
  </section>}
  {state==="authorized"&&<div className={styles.columns}>
   <section className={styles.panel} aria-label="Authorized mortuary register">
    <h2>{say("Case register","Registre des dossiers")}</h2>
    <label className={styles.field}><span>{say("Find case","Rechercher un dossier")}</span>
     <input value={query} onChange={e=>setQuery(e.target.value)}
      placeholder={say("Case ID, identifier or state","Identifiant ou état")}
      maxLength={160}/>
    </label>
    <p className={styles.hint}>{filtered.length} {say("authorized records","dossiers autorisés")}</p>
    {filtered.length===0?<p>{say("No authorized records in this facility.",
     "Aucun dossier autorisé dans cet établissement.")}</p>:
     <ul className={styles.list}>{filtered.map(c=><li key={c.caseId}>
      <button type="button" aria-pressed={selectedId===c.caseId}
       className={styles.caseButton} onClick={()=>choose(c.caseId)}>
       <strong>{say("Case","Dossier")} {personRef(c.caseId)}</strong>
       <span>{c.state.replaceAll("_"," ")} · v{c.version}</span>
       <small>{c.currentStorage||say("Not in storage","Hors stockage")}</small>
      </button>
     </li>)}</ul>}
   </section>
   <div className={styles.mainColumn}>
    {screen==="M27-S02"&&<section className={styles.panel}>
     <h2>{say("Verified intake","Admission avec preuves vérifiées")}</h2>
     <p className={styles.hint}>{say("The death record and identity attestations must already exist in the independent authority source.",
      "L'acte de décès et les preuves d'identité doivent déjà figurer dans le registre compétent.")}</p>
     <form onSubmit={e=>{e.preventDefault();void execute("RECEIVE");}}>
      <div className={styles.fields}>
       {fieldInput("decedentRef",say("Existing decedent identity UUID","Identifiant du défunt"),"Authoritatively verified registry identity","uuid")}
       {fieldInput("intakeRef",say("Identity intake attestation reference","Attestation d'admission"),"Registered incoming custody evidence")}
       {fieldInput("deathRef",say("Verified death record reference","Référence de l'acte de décès"),"Death confirmation from independent authority")}
       {fieldInput("originatingUnitRef",say("Originating clinical unit","Service d'origine"),"Verified origin unit code")}
       {fieldInput("authorizationRef",say("Intake authorization document","Document autorisant l'admission"),"Independent institutional intake authorization")}
       {fieldInput("belongingsSealRef",say("Sealed belongings reference","Référence des effets personnels scellés"),"Independent sealed-belongings inventory")}
       {fieldInput("receivingStaffId",say("Receiving mortuary officer UUID","Identifiant de l'agent réceptionnaire"),"Active shift, distinct from requesting intake officer","uuid")}
      </div>
      <button className={styles.primary} type="submit" disabled={busy}>
       {busy?say("Submitting…","Enregistrement…"):say("Record independently verified intake","Enregistrer l'admission vérifiée")}</button>
     </form>
    </section>}
    {selected?<section className={styles.panel}>
      <div className={styles.cardHeading}>
       <h2>{say("Case status and custody","État du dossier et chaîne de garde")}</h2>
       <span className={styles.state}>{selected.state.replaceAll("_"," ")}</span>
      </div>
      <dl className={styles.facts}>
       <div><dt>{say("Case ID","Référence du dossier")}</dt><dd>{selected.caseId}</dd></div>
       <div><dt>{say("Decedent identity reference","Référence du défunt")}</dt><dd>{selected.decedentRef}</dd></div>
       <div><dt>{say("Originating unit","Service d'origine")}</dt><dd>{selected.originatingUnitRef}</dd></div>
       <div><dt>{say("Sealed belongings","Effets personnels scellés")}</dt><dd>{selected.belongingsSealRef}</dd></div>
       <div><dt>{say("Receiving officer","Agent réceptionnaire")}</dt><dd>{personRef(selected.intakeReceivingStaff)}</dd></div>
       <div><dt>{say("Current storage","Emplacement actuel")}</dt><dd>{selected.currentStorage||"—"}</dd></div>
       <div><dt>{say("Version","Version")}</dt><dd>{selected.version}</dd></div>
       <div><dt>{say("Received","Date de réception")}</dt><dd>{readableDate(selected.receivedAt)}</dd></div>
       <div><dt>{say("Released","Date de remise")}</dt><dd>{readableDate(selected.releasedAt)}</dd></div>
      </dl>
      {screen!=="M27-S01"&&activeAction&&<section className={styles.actionPanel}>
       <h3>{activeAction.replaceAll("_"," ")}</h3>
       <p className={styles.hint}>{say(descriptions[activeAction].en,descriptions[activeAction].fr)}</p>
       <form key={selected.caseId+"-"+activeAction} onSubmit={e=>{
        e.preventDefault();void execute(activeAction);
       }}>
        <div className={styles.fields}>
         {fields[activeAction].map(f=>fieldInput(f.key,say(f.label,f.labelFr),f.hint,
           f.key==="receivingStaffId"?"uuid":"text"))}
        </div>
        <button className={styles.primary} type="submit" disabled={busy}>
         {busy?say("Validating…","Vérification…"):
          say("Submit authorized "+activeAction.replaceAll("_"," ").toLowerCase(),
           "Soumettre la transition autorisée")}</button>
       </form>
      </section>}
      {screen!=="M27-S01"&&!activeAction&&<p role="status" className={styles.hint}>
       {say("No action on this screen is permitted from the current state.",
        "Aucune action n'est autorisée sur cet écran depuis l'état actuel.")}</p>}
      <h3>{say("Immutable custody timeline","Historique de garde non modifiable")}</h3>
      {custody.length===0?<p className={styles.hint}>{say("No custody records visible.",
       "Aucun événement de garde visible.")}</p>:
       <div className={styles.tableWrap}><table className={styles.table}>
        <thead><tr><th>{say("Version","Version")}</th><th>{say("Event","Événement")}</th>
         <th>{say("From","Origine")}</th><th>{say("To","Destination")}</th>
         <th>{say("Seal","Scellé")}</th><th>{say("Condition","État")}</th>
         <th>{say("Time","Date")}</th></tr></thead>
        <tbody>{custody.map(e=><tr key={e.version}><td>{e.version}</td>
         <td>{e.eventType}</td><td>{e.fromStorage||"—"}</td><td>{e.toStorage||"—"}</td>
         <td>{e.sealRef||e.belongingsRef||"—"}</td>
         <td>{e.conditionCode||"—"}</td>
         <td>{readableDate(e.occurredAt)}</td></tr>)}</tbody></table></div>}
      {(screen==="M27-S04"||screen==="M27-S05")&&<>
       <h3>{say("Independent release approvals","Approbations indépendantes")}</h3>
       {approvals.length===0?<p className={styles.hint}>
        {say("No approved release reviews visible to this role.","Aucune approbation accessible à ce rôle.")}</p>:
        <ul className={styles.eventList}>{approvals.map(x=><li key={x.stage}>
         {x.stage} · {personRef(x.actorId)} · v{x.caseVersion} · {readableDate(x.createdAt)}</li>)}</ul>}
      </>}
      {screen==="M27-S05"&&<>
       <h3>{say("Authorized audit history","Historique d'audit autorisé")}</h3>
       {audits.length===0?<p className={styles.hint}>{say("Audit privilege required to display the log.",
        "Le journal est réservé aux agents habilités.")}</p>:
        <ul className={styles.eventList}>{audits.map(x=><li key={x.version}>
         v{x.version} · {x.action} · {readableDate(x.occurredAt)}</li>)}</ul>}
       {selected.state==="RELEASED"&&<p className={styles.legal}>
        {say("Release recorded with immutable custody and two independent approvals. A legally signed printable certificate remains subject to the approved hospital document/signature integration.",
         "Remise enregistrée avec historique de garde et double approbation. Le certificat officiel signé dépend de l'intégration documentaire autorisée.")}
       </p>}
      </>}
     </section>:<section className={styles.panel}>
      <h2>{say("Choose a record","Sélectionner un dossier")}</h2>
      <p>{say("Open a verified case from the register. Intake is available only on M27-S02.",
       "Ouvrez un dossier vérifié. Les nouvelles admissions sont réservées à M27-S02.")}</p>
     </section>}
   </div>
  </div>}
  <nav className={styles.links} aria-label="Mortuary module navigation">
   <Link href="/workforce/mortuary">{say("All mortuary workstations","Tous les écrans du module")}</Link>
   <Link href="/workforce/operations">{say("Hospital operations","Opérations hospitalières")}</Link>
  </nav>
 </section>;
}
