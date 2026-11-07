// M23 preventive maintenance - web integration guards (Issue #68, package 26). Static + behavioural checks of the proxy allow-list,
// the read-only panel and the mounting on the EXISTING canonical screens (no new route, no regenerated screen).
import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync,existsSync,readdirSync} from "node:fs";
const root=new URL("../",import.meta.url);
const read=p=>readFileSync(new URL(p,root),"utf8");
const proxy=read("apps/web/app/api/operations/proxy/[...segments]/route.ts");
const panel=read("apps/web/components/maintenance/MaintenancePanel.tsx");
const page=read("apps/web/app/workforce/clinical/[screenId]/page.tsx");
const regex=name=>{const m=proxy.match(new RegExp("const "+name+"=/(.*)/;"));assert.ok(m,name);return new RegExp(m[1]);};

test("proxy exposes only the five M23 maintenance READ paths and never a maintenance write",()=>{
 const reads=regex("m23Reads");
 for(const ok of ["M23/maintenance/policies","M23/maintenance/plans","M23/maintenance/calendar","M23/maintenance/planner/health","M23/maintenance/inspections/pending"])
  assert.ok(reads.test(ok),ok);
 for(const bad of ["M23/maintenance/planner/generate","M23/maintenance/planner/escalate","M23/maintenance/plans/1","M23/maintenance/occurrences/x/completion",
  "M23/maintenance/assets/00000000-0000-4000-8000-000000000000/calibrations","M23/maintenance/work-orders/x/costs","M23/maintenance/policies/HIGH/versions/1/decision",
  "M21/maintenance/calendar","M23/maintenance/calendar/extra","M23/maintenance/../catalog","m23/maintenance/calendar"])
  assert.ok(!reads.test(bad),bad);
 const allowLine=proxy.split("\n").find(l=>l.includes('if(method==="GET")return'));
 assert.ok(allowLine.includes("m23Reads.test(path)"));
 const postLine=proxy.split("\n").find(l=>l.trim().startsWith("return listing.test(path)||transitions.test(path)"));
 assert.ok(postLine&&!postLine.includes("m23"),"no maintenance route is writable through the browser proxy");
});
test("calendar range and thresholds are validated before they reach the service",()=>{
 const iso=regex("isoInstant");
 assert.ok(iso.test("2026-11-07T12:00:00Z")&&iso.test("2026-11-07T12:00:00.123Z"));
 for(const bad of ["2026-11-07","2026-11-07T12:00:00+01:00","' OR 1=1","2026-11-07T12:00:00Zx"])assert.ok(!iso.test(bad),bad);
 assert.ok(proxy.includes('return send("invalid_range",400)')&&proxy.includes('return send("invalid_threshold",400)'));
 assert.ok(/k!=="limit"&&!extra\.includes\(k\)/.test(proxy),"unknown query keys are still rejected");
 assert.ok(proxy.includes('url.searchParams.set("from",from)')&&!/searchParams\.set\("(?!limit|from|to|staleAfterMinutes)/.test(proxy.split("M23/maintenance/calendar")[2]||""));
});
test("the panel is read-only, escapes by construction and states loading, denied and unavailable",()=>{
 assert.ok(!/method\s*:\s*["']POST|["']PUT["']|["']PATCH["']|["']DELETE["']/.test(panel),"no mutating fetch");
 assert.ok(!panel.includes("dangerouslySetInnerHTML")&&!panel.includes("innerHTML"));
 assert.ok(!/localStorage|sessionStorage|document\.cookie/.test(panel),"no client-side storage of maintenance data");
 for(const s of ["loading","denied","unavailable"])assert.ok(panel.includes('"'+s+'"'));
 assert.ok(panel.includes('credentials:"same-origin"')&&panel.includes('cache:"no-store"'));
 assert.ok(panel.includes("/api/operations/proxy/M23/maintenance/"));
 for(const needle of ["Servicing calendar · Calendrier","Planner health · État","Awaiting independent verification · En attente","Overdue · En retard","Blocked · Bloqué"])
  assert.ok(panel.includes(needle),"EN/FR label: "+needle);
 assert.ok(panel.includes("UNKNOWN is never shown as healthy"));
 assert.ok(panel.includes('role="alert"')&&panel.includes('role="status"')&&panel.includes('scope="col"'),"accessible states and table headers");
 assert.ok(!/glow|box-shadow/.test(panel),"no decorative effects");
});
test("the panel is mounted only for the existing M23 screens and no screen route is added",()=>{
 assert.ok(page.includes("MaintenancePanel")&&/operationsModule\.id==="M23"/.test(page));
 const body=panel.slice(panel.indexOf("export function MaintenancePanel"));
 assert.deepEqual([...body.matchAll(/"(M\d\d-S\d\d)"/g)].map(m=>m[1]).sort(),["M23-S01","M23-S03","M23-S05"]);
 const canonical=JSON.parse(read("registers/canonical-screens.json")).screen_register.filter(s=>s.module==="M23");
 assert.deepEqual(canonical.map(s=>s.id),["M23-S01","M23-S02","M23-S03","M23-S04","M23-S05"],"the five canonical M23 screens are unchanged");
 const flows=JSON.parse(read("apps/web/lib/operations-screen-flows.json")).flows;
 assert.deepEqual(Object.keys(flows).filter(k=>k.startsWith("M23")),["M23-S01","M23-S02","M23-S03","M23-S04","M23-S05"]);
 const created=readdirSync(new URL("apps/web/components/maintenance/",root));
 assert.deepEqual(created,["MaintenancePanel.tsx"],"one component, no duplicate screens");
 assert.ok(!existsSync(new URL("apps/web/app/workforce/maintenance",root)),"no parallel module URL");
});
test("the technician field now states it is a workforce member, matching the server-side shift check",()=>{
 assert.ok(read("apps/web/components/OperationsWorkbench.tsx").includes("Assigned technician (workforce member UUID on shift)"));
});
