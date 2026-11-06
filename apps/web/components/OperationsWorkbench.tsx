"use client";
import {useCallback,useEffect,useMemo,useState} from "react";
import Link from "next/link";
import styles from "../app/workforce/operations/operations.module.css";
import screenFlows from "../lib/operations-screen-flows.json";

type Item={id:string;cell:string;facility:string;module:string;recordType:string;status:string;version:number;
 createdBy:string;lastActor:string;fields:Record<string,string|number>};
type Step={from:string;action:string;to:string;permission:string;makerChecker:boolean;evidence:string};
type Module={id:string;name:string;recordTypes:string[];allowedFields:string[];steps:Step[]};
type Balance={lot:string;bin:string;onHand:number;reserved:number;available:number;version:number};
type Field={key:string;label:string;kind?:"number"|"datetime"|"email"|"text";required?:boolean};
const D=(key:string,label:string,kind:Field["kind"]="text",required=false):Field=>({key,label,kind,required});
const SHAPES:Record<string,{recordTypes:string[];fields:Field[]}>={
 M21:{recordTypes:["REQUISITION","SUPPLIER"],fields:[
  D("title","Requisition / supplier name","text",true),D("supplierRef","Qualified supplier UUID"),D("costCentre","Cost centre"),
  D("amountMinor","Amount (minor currency units)","number"),D("currency","Currency (ISO code)"),
  D("purchaseRef","Purchase order reference"),D("receiptRef","Goods receipt reference"),
  D("invoiceRef","Invoice reference"),D("registrationRef","Supplier registration"),
  D("taxRef","Tax identification"),D("qualificationRef","Qualification certificate"),
  D("phone","Contact phone"),D("email","Supplier email","email"),D("address","Supplier address"),D("description","Details")]},
 M22:{recordTypes:["STOCK_ITEM","STOCK_REQUEST"],fields:[
  D("title","Item or request title","text",true),D("sku","SKU"),D("lot","Lot/batch code"),
  D("bin","Storage bin"),D("unit","Stock unit"),D("quantity","Requested quantity","number"),
  D("receiptRef","Verified goods receipt"),D("issueRef","Stock issue reference"),D("warehouseRef","Warehouse reference"),
  D("requestedByRef","Requester reference"),D("adjustmentReason","Count adjustment reason"),D("description","Details")]},
 M23:{recordTypes:["ASSET","WORK_ORDER"],fields:[
  D("title","Asset / work order","text",true),D("serial","Serial number"),D("manufacturer","Manufacturer"),
  D("model","Equipment model"),D("serviceRef","Service identifier"),D("locationRef","Facility location"),
  D("safetyClass","Biomedical safety class"),D("assetRef","Asset UUID"),D("technicianRef","Assigned technician"),
  D("workOrderRef","Work order reference"),D("inspectionRef","Independent inspection"),D("conditionCode","Condition"),
  D("dueAt","Maintenance due date","datetime"),D("description","Maintenance notes")]},
 M25:{recordTypes:["TRIP","VEHICLE"],fields:[
  D("title","Transport request / vehicle","text",true),D("vehicleRef","Qualified vehicle UUID"),
  D("driverRef","Driver identifier"),D("originRef","Origin facility/location"),
  D("destinationRef","Destination facility/location"),D("requestPriority","Dispatch priority"),
  D("handoverRef","Approved handover reference"),D("plate","Vehicle registration"),
  D("inspectionRef","Vehicle safety certificate"),D("vehicleType","Ambulance / transport type"),
  D("capacity","Passenger capacity","number"),D("description","Route notes")]},
 M29:{recordTypes:["CONFIGURATION","SERVICE"],fields:[
  D("title","Department / service","text",true),D("locationRef","Location identifier"),
  D("departmentRef","Department identifier"),D("serviceCode","Service catalogue code"),
  D("schemaVersion","Configuration schema version"),D("changeImpact","Reviewed impact summary"),
  D("parentRef","Parent hierarchy node"),D("effectiveAt","Effective date","datetime"),
  D("retiredReason","Retirement reason"),D("description","Change notes")]},
 M31:{recordTypes:["TEMPLATE","DOCUMENT"],fields:[
  D("title","Template / document title","text",true),D("templateRef","Approved template UUID"),
  D("schemaVersion","Template schema version"),D("documentRef","Document reference"),
  D("contentDigest","SHA-256 content digest"),D("storageRef","Immutable object-storage reference"),
  D("signatureRef","Independently verified signature reference"),D("previousRef","Previous signed version"),
  D("retentionCode","Legal retention class"),D("language","Document language"),
  D("amendmentReason","Amendment reason"),D("description","Description")]},
 M37:{recordTypes:["MESSAGE","ROUTE"],fields:[
  D("title","Notification / route title","text",true),D("recipientRef","Authorized recipient reference"),
  D("templateRef","Approved message template"),D("channel","internal / email / sms / push"),
  D("deliveryRef","Verified delivery receipt"),D("dueAt","Delivery deadline","datetime"),
  D("severity","Escalation severity"),D("retryPolicy","Bounded retry policy"),
  D("escalationRef","Escalation route"),D("routingRef","Route identifier"),D("description","Nonclinical message description")]}
};
const SCREENS:Record<string,string>={
 M21:"Procurement and supplier qualification",M22:"General stores and non-medication stock",
 M23:"Biomedical assets and maintenance",M25:"Ambulance and transport operations",
 M29:"Facility and service configuration",M31:"Governed forms, documents and signatures",
 M37:"Notifications, communication and escalation"
};
const endpoint="/api/operations/proxy/";
function csrf(){
 if(typeof document==="undefined")return "";
 const pair=document.cookie.split("; ").find(x=>x.startsWith("__Host-synq_ops_csrf="));
 return pair?decodeURIComponent(pair.split("=").slice(1).join("=")):"";
}
async function call<T>(path:string,init?:{method?:"POST";body?:unknown}):Promise<T>{
 const res=await fetch(endpoint+path,{
  method:init?.method||"GET",credentials:"same-origin",cache:"no-store",
  headers:{"Accept":"application/json",...(init?.method?{"Content-Type":"application/json",
   "x-synq-ops-csrf":csrf()}: {})},
  ...(init?.method?{body:JSON.stringify(init.body)}:{})
 });
 let value:unknown;
 try{value=await res.json();}catch{throw new Error("Response unavailable or invalid.");}
 if(!res.ok){
  const code=value&&typeof value==="object"&&"error" in value?
   String((value as {error:unknown}).error):"operations_unavailable";
  const error=new Error(code) as Error&{status?:number};
  error.status=res.status;throw error;
 }
 return value as T;
}
function readForm(values:Record<string,string>,fields:Field[]){
 const typed:Record<string,string|number>={};
 for(const {key,kind} of fields){
  const value=(values[key]||"").trim();
  if(!value)continue;
  if(kind==="number"){
   const num=Number(value);
   if(!Number.isSafeInteger(num)||num<0)throw Error("Invalid quantity or amount: "+key);
   typed[key]=num;
  }else if(kind==="datetime"){
   const date=new Date(value);
   if(Number.isNaN(date.getTime()))throw Error("Invalid date: "+key);
   typed[key]=date.toISOString();
  }else typed[key]=value;
 }
 return typed;
}
function friendly(error:unknown){
 if(error instanceof Error)return error.message.replaceAll("_"," ");
 return "Request failed";
}
type Props={moduleId:string;screenId:string;name:string;nameFr:string;action:string;inputGroups:string};
export function OperationsWorkbench({moduleId,screenId,name,nameFr,action,inputGroups}:Props){
 const definition=SHAPES[moduleId];
 const flow=screenFlows.flows[screenId as keyof typeof screenFlows.flows];
 const allowedTypes:string[]=flow.recordTypes;
 const allowedActions:Set<string>=new Set(flow.actions);
 const allowedMovements:string[]=flow.movements;
 const displayedFields=new Set<string>(flow.fields);
 const canCreate=new Set(["M21-S01","M21-S02","M21-S06","M22-S01","M22-S02","M22-S03",
  "M23-S01","M23-S03","M25-S01","M25-S02","M29-S02","M31-S02","M31-S04",
  "M37-S01","M37-S03"]).has(screenId);
 const [moduleSpec,setSpec]=useState<Module|null>(null);
 const [items,setItems]=useState<Item[]>([]);
 const [selected,setSelected]=useState<Item|null>(null);
 const [balances,setBalances]=useState<Balance[]>([]);
 const [recordType,setRecordType]=useState(allowedTypes[0]);
 const [newValues,setNewValues]=useState<Record<string,string>>({});
 const [changeValues,setChangeValues]=useState<Record<string,string>>({});
 const [movement,setMovement]=useState(allowedMovements[0]||"RECEIVE");
 const [stockValues,setStockValues]=useState<Record<string,string>>({});
 const [busy,setBusy]=useState(false);
 const [state,setState]=useState<"loading"|"authorized"|"signin"|"unavailable">("loading");
 const [notice,setNotice]=useState("");
 const moduleRoute=moduleId+"/items";
 const refresh=useCallback(async()=>{
  const [catalog,records]=await Promise.all([
   call<Module[]>("catalog"),call<Item[]>(moduleRoute+"?limit=50")
  ]);
  setSpec(catalog.find(m=>m.id===moduleId)||null);
  setItems(records);
  setState("authorized");
  setSelected(previous=>previous?records.find(it=>it.id===previous.id)||previous:null);
 },[moduleId,moduleRoute]);
 useEffect(()=>{
  let current=true;
  Promise.all([call<Module[]>("catalog"),call<Item[]>(moduleRoute+"?limit=50")])
   .then(([catalog,records])=>{if(!current)return;
    setSpec(catalog.find(m=>m.id===moduleId)||null);
    setItems(records);setState("authorized");
   })
   .catch(err=>{if(!current)return;
    setState((err as {status?:number}).status===401?"signin":"unavailable");
    setNotice(friendly(err));
   });
  return ()=>{current=false;};
 },[moduleId,moduleRoute]);
 const actions=useMemo(()=>moduleSpec?.steps.filter(x=>
  x.from===selected?.status&&allowedActions.has(x.action))||[],
  [moduleSpec,selected?.status,allowedActions]);
 const fields=definition.fields.filter(f=>displayedFields.has(f.key));
 const viewItems=items.filter(item=>allowedTypes.includes(item.recordType));
 const fieldsForScreen=fields;
 const setField=(key:string,value:string,change=false)=>{
  const setter=change?setChangeValues:setNewValues;
  setter(old=>({...old,[key]:value}));
 };
 const execute=async(operation:()=>Promise<unknown>,success:string)=>{
  setBusy(true);setNotice("");
  try{await operation();await refresh();setChangeValues({});setNewValues({});
   setNotice(success);
  }catch(err){setNotice("Request not completed: "+friendly(err));}
  finally{setBusy(false);}
 };
 async function open(item:Item){
  setSelected(item);setBalances([]);setChangeValues({});
  if(moduleId==="M22"&&item.recordType==="STOCK_ITEM"){
   try{setBalances(await call<Balance[]>(moduleRoute+"/"+item.id+"/balances"));}
   catch(err){setNotice("Stock balances unavailable: "+friendly(err));}
  }
 }
 const printField=(field:Field,value:string,handler:(key:string,v:string)=>void,prefix:string)=>{
  const id=prefix+"-"+moduleId+"-"+field.key;
  return <label className={styles.opsField} htmlFor={id} key={field.key}>
   <span>{field.label}</span>
   <input id={id} name={field.key} type={field.kind==="number"?"number":
    field.kind==="datetime"?"datetime-local":field.kind==="email"?"email":"text"}
    min={field.kind==="number"?"0":undefined}
    step={field.kind==="number"?"1":undefined}
    value={value} onChange={e=>handler(field.key,e.target.value)}
    maxLength={field.kind==="number"?undefined:2048} autoComplete="off"
    placeholder={field.kind==="text"?"Enter "+field.label.toLowerCase():undefined}/>
  </label>;
 };
 return <section className={styles.opsWorkbench} aria-labelledby="workbench-heading">
  <div className={styles.opsHeader}>
   <div>
    <p className={styles.eyebrow}>{moduleId} · {screenId}</p>
    <h2 id="workbench-heading">{name}</h2>
    <p lang="fr" className={styles.fr}>{nameFr}</p>
    <p className={styles.caption}>Canonical action: {action}. Required information: {inputGroups}.</p>
    <p className={styles.caption}>Workflow gate: {flow.safety.replaceAll("_"," ")}. Server-side role and independent evidence controls apply.</p>
   </div>
   <div className={styles.opsToolbar}>
    <button type="button" onClick={()=>refresh().catch(err=>{
     setState((err as {status?:number}).status===401?"signin":"unavailable");
     setNotice(friendly(err));
    })} disabled={busy}>Refresh</button>
    <button type="button" onClick={()=>execute(async()=>{
     await call("catalog");
     const res=await fetch("/api/operations/auth/logout",{method:"POST",
      credentials:"same-origin",headers:{"x-synq-ops-csrf":csrf()}});
     if(!res.ok)throw Error("logout_failed");
     setItems([]);setSelected(null);setState("signin");
    },"Signed out")} disabled={busy||state!=="authorized"}>Sign out</button>
   </div>
  </div>
  {notice&&<p role="alert" className={styles.opsAlert}>{notice}</p>}
  {state==="loading"&&<p role="status">Loading authorized hospital records…</p>}
  {state==="signin"&&<div className={styles.opsGate}>
   <h3>Verified staff sign-in required</h3>
   <p>Use your authorized hospital identity. No patient or facility information is displayed before approval.</p>
   <a href="/api/operations/auth/start" className={styles.opsLink}>Sign in with hospital identity</a>
  </div>}
  {state==="unavailable"&&<div className={styles.opsGate} role="status">
   <h3>Hospital operations temporarily unavailable</h3>
   <p>This workspace requires an HTTPS identity provider, a protected facility API, a trusted workforce roster,
    and an approved staff shift. Operations remain blocked if any requirement is missing.</p>
  </div>}
  {state==="authorized"&&<>
   <div className={canCreate?styles.opsColumns:styles.opsSingle}>
    <section className={styles.opsPanel} aria-label="Records">
     <h3>Records · Dossiers</h3>
     <p className={styles.caption}>{viewItems.length} scoped records of the screen-specific record types (up to 50 shown)</p>
     {viewItems.length===0?<p>No authorized records are available for this workstation.</p>:
      <ul className={styles.opsRecords}>{viewItems.map(item=><li key={item.id}>
       <button type="button" onClick={()=>open(item)} className={styles.opsRecord}
        aria-pressed={selected?.id===item.id}>
        <strong>{String(item.fields.title||item.recordType)}</strong>
        <small>{item.recordType} · {item.status} · v{item.version}</small>
       </button>
      </li>)}</ul>}
    </section>
    {canCreate&&<section className={styles.opsPanel} aria-labelledby="create-record-heading">
     <h3 id="create-record-heading">Create authorized record</h3>
     <p className={styles.caption}>Complete the relevant fields. Approvals and external evidence cannot be self-attested.</p>
     <label className={styles.opsField}>Record type
      <select value={recordType} onChange={e=>setRecordType(e.target.value)}>
       {allowedTypes.map(t=><option key={t} value={t}>{t}</option>)}
      </select>
     </label>
     <form onSubmit={e=>{e.preventDefault();execute(async()=>{
      const parsed=readForm(newValues,fields);
      const created=await call<Item>(moduleRoute,{method:"POST",body:{
       recordType,fields:parsed,commandId:crypto.randomUUID()}});
      setSelected(created);
     },"Record created. Audit and outbox transaction recorded.");}}>
      <div className={styles.opsFields}>
       {fields.filter(f=>!(moduleId==="M31"&&recordType==="DOCUMENT"&&f.key==="signatureRef"))
         .map(f=>printField(f,newValues[f.key]||"",(k,v)=>setField(k,v),"create"))}
      </div>
      <button type="submit" disabled={busy||!newValues.title?.trim()} className={styles.opsPrimary}>
       {busy?"Processing…":"Create record"}
      </button>
     </form>
    </section>}
   </div>
   {selected&&<section className={styles.opsPanel} aria-labelledby="record-detail-heading">
    <div className={styles.opsHeader}>
     <div><h3 id="record-detail-heading">{String(selected.fields.title||selected.recordType)}</h3>
      <p className={styles.caption}>{selected.id} · {selected.status} · version {selected.version}</p></div>
     <button type="button" onClick={()=>setSelected(null)}>Close detail</button>
    </div>
    <dl className={styles.opsDetails}>
     {Object.entries(selected.fields).map(([key,value])=><div key={key}><dt>{key}</dt><dd>{String(value)}</dd></div>)}
    </dl>
    <h4>Update details for the next authorized action</h4>
    <p className={styles.caption}>Provide additional evidence references or values; unchanged fields are preserved.</p>
    <div className={styles.opsFields}>
     {fields.filter(f=>f.key!=="title"||selected.status.endsWith("DRAFT"))
      .map(f=>printField(f,changeValues[f.key]||"",(k,v)=>setField(k,v,true),"change"))}
    </div>
    <h4>Available lifecycle transitions</h4>
    {actions.length===0?<p>No further transition is defined for this record state.</p>:
     <div className={styles.opsActions}>
      {actions.map(step=><div className={styles.opsAction} key={step.action}>
       <button type="button" disabled={busy} onClick={()=>execute(async()=>{
        const fields=readForm(changeValues,fieldsForScreen);
        const updated=await call<Item>(moduleRoute+"/"+selected.id+"/transitions",{
         method:"POST",body:{action:step.action,expectedVersion:selected.version,
          commandId:crypto.randomUUID(),fields}});
        setSelected(updated);
       },step.action+" recorded. Transactional state updated.")}>{step.action.replaceAll("_"," ")}</button>
       <small>{step.to}{step.makerChecker?" · independent reviewer":""}
        {step.evidence?" · verified "+step.evidence:""}</small>
      </div>)}
     </div>}
    {moduleId==="M22"&&selected.recordType==="STOCK_ITEM"&&allowedMovements.length>0&&
     <section className={styles.opsStock} aria-label="Inventory movements">
      <h4>Lot/bin stock ledger</h4>
      {balances.length===0?<p>No stock balance recorded yet.</p>:
       <div className={styles.opsTableWrap}><table className={styles.opsTable}><thead><tr>
        <th>Lot</th><th>Bin</th><th>On hand</th><th>Reserved</th><th>Available</th></tr></thead>
        <tbody>{balances.map(b=><tr key={b.lot+"/"+b.bin}><td>{b.lot}</td><td>{b.bin}</td>
         <td>{b.onHand}</td><td>{b.reserved}</td><td>{b.available}</td></tr>)}</tbody></table></div>}
      <form onSubmit={e=>{e.preventDefault();execute(async()=>{
       const itemId=selected.id;
       const result=await call<Balance>(moduleRoute+"/"+itemId+"/movements",{
        method:"POST",body:{movement,lot:stockValues.lot,bin:stockValues.bin,
         quantity:Number(stockValues.quantity),referenceCode:stockValues.referenceCode,
         expectedVersion:selected.version,commandId:crypto.randomUUID()}});
       setBalances(old=>[...old.filter(b=>!(b.lot===result.lot&&b.bin===result.bin)),result]);
      },"Stock movement recorded atomically.");}}>
       <div className={styles.opsFields}>
        <label className={styles.opsField}>Movement
         <select value={movement} onChange={e=>setMovement(e.target.value)}>
          {allowedMovements.map(v=><option key={v}>{v}</option>)}
         </select>
        </label>
        {["lot","bin","quantity","referenceCode"].map(k=><label className={styles.opsField} key={k}>
         <span>{k}</span>
         <input type={k==="quantity"?"number":"text"} min={k==="quantity"?"1":undefined}
          value={stockValues[k]||""} onChange={e=>setStockValues(old=>({...old,[k]:e.target.value}))}
          required maxLength={160}/></label>)}
       </div>
       <p className={styles.caption}>Goods receipt and returns require independently attested references.
        Adjustments cannot be performed through this interface.</p>
       <button disabled={busy||!stockValues.lot||!stockValues.bin||!stockValues.referenceCode||
        !(Number(stockValues.quantity)>0)} type="submit">Record stock movement</button>
      </form>
     </section>}
   </section>}
   <p className={styles.caption}>All commands require a signed hospital session, a current facility/shift grant,
    module capability, transactional PostgreSQL isolation and an immutable audit/outbox event.
    No action executes without backend verification.</p>
  </>}
  <nav className={styles.opsBack} aria-label="Operations navigation">
   <Link href="/workforce/operations">All seven modules / Tous les modules</Link>
  </nav>
 </section>;
}
