import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
const read=p=>readFileSync(new URL("../"+p,import.meta.url),"utf8");
const v=JSON.parse(read("registers/stack-stable-2026-11-06.json"));
test("policy declares exact Java 25 LTS and separately checked latest stable Java 27",()=>{
 assert.equal(v.schema,"synqoria.release.latest-supported-stable.v1");
 assert.equal(v.productionJavaJdk,"25");
 assert.equal(v.latestStableJavaTestedInCi,"27");
 assert.equal(v.globalClinicalRelease,"DENIED");
 assert.equal(v.operationalModulesComplete,0);
 assert.equal(v.operationalScreensAccepted,0);
 const pom=read("services/operations-core/pom.xml");
 assert.match(pom,/<java.version>25<\/java.version>/);
 assert.match(pom,/<version>4.1.2<\/version>/);
 const workflow=read(".github/workflows/operations-seven-domain-ci.yml");
 assert.match(workflow,/java: \['25', '27'\]/);
});
test("stable stack manifest is deduplicated and does not promote unverified targets",()=>{
 assert.equal(v.versions.length,new Set(v.versions.map(x=>x.component)).size);
 const entries=new Map(v.versions.map(x=>[x.component,x]));
 for(const item of v.versions) {
  assert.match(item.source,/^https:\/\//);
  assert.ok(item.owner.length>3);
  assert.ok(item.status.length>7);
  if(item.version)assert.doesNotMatch(item.version,/alpha|beta|rc|snapshot|canary|nightly/i);
 }
 assert.equal(entries.get("PostgreSQL").version,"18.6");
 assert.equal(entries.get("Node.js").version,"26.13.0");
 assert.equal(entries.get("Next.js").version,"16.4.5");
 assert.equal(entries.get("Expo SDK").version,"58.0.23");
 assert.equal(entries.get("React Native").version,"0.88.1");
 assert.equal(entries.get("Keycloak").version,"26.8.2");
 assert.equal(entries.get("Apache Kafka").version,"4.4.0");
 assert.equal(entries.get("Redis").version,"8.10.3");
 assert.equal(entries.get("Kong Gateway").version,null);
 assert.equal(entries.get("Kong Gateway").status,"VERSION_NOT_VERIFIED");
 assert.equal(entries.get("Next.js").status,"TARGET_PENDING_COMPATIBILITY");
 assert.equal(entries.get("Expo SDK").status,"TARGET_PENDING_COMPATIBILITY");
});
test("primary disposable PostgreSQL18.6 and Java25 CI workflow pins are explicit",()=>{
 for(const path of [".github/workflows/operations-seven-jdbc-integration.yml",
  ".github/workflows/operations-seven-pg16-rls.yml"]){
  const s=read(path);assert.match(s,/postgres:18\.6/);
  assert.doesNotMatch(s,/postgres:16(?:\s|\n)/);
 }
 assert.match(read(".github/workflows/operations-seven-jdbc-integration.yml"),/java-version: '25'/);
 const policy=read("docs/authority/SYNQORIA_STABLE_RELEASE_POLICY_2026-11-06.md");
 assert.match(policy,/clinical acceptance/i);
 assert.match(policy,/PR #23\/24/);
});
