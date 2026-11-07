package org.synqoria.content;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.synqoria.content.Db.Reply;
import org.synqoria.content.PackageService.*;
import org.synqoria.content.TerminologyService.*;
import org.synqoria.content.domain.Domain;
import org.synqoria.content.domain.Domain.*;
import tools.jackson.databind.json.JsonMapper;

/**
 * Real PostgreSQL integration (disposable 18.x, restricted non-owner runtime role, forced RLS).
 * Opt-in: SYNQORIA_CONTENT_PG_TEST=1. All terminology, drugs and rules here are SYNTHETIC fixtures.
 */
class ContentPgIntegrationTest {
 static String url,adminUrl,adminUser,adminPassword,runtimeUser="synqoria_content_runtime",runtimePassword="m30-ci-only";
 static class MutableClock extends Clock {
  volatile Instant now=Instant.parse("2026-11-07T06:00:00Z");
  public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}public Instant instant(){return now;}
 }
 MutableClock clock;Db db;TerminologyService terms;FormularyService formulary;PackageService packages;ActivationService activations;
 JdbcTemplate admin;DataSource runtimeDs;
 final UUID cell=UUID.randomUUID(),facility=UUID.randomUUID();
 final UUID importerId=UUID.randomUUID(),authorId=UUID.randomUUID(),clinId=UUID.randomUUID(),policyId=UUID.randomUUID(),activatorId=UUID.randomUUID(),otherClinId=UUID.randomUUID();
 Actor importer,author,clinician,policy,activator,roller,reader;
 static final String SYS="urn:synthetic:drugs";

 @BeforeAll static void env(){
  assumeTrue("1".equals(System.getenv("SYNQORIA_CONTENT_PG_TEST")),"set SYNQORIA_CONTENT_PG_TEST=1 with disposable PostgreSQL");
  url=System.getenv("SYNQORIA_CONTENT_TEST_JDBC_URL");adminUrl=System.getenv().getOrDefault("SYNQORIA_CONTENT_TEST_ADMIN_JDBC_URL",url);
  adminUser=System.getenv("SYNQORIA_CONTENT_TEST_ADMIN_USER");adminPassword=System.getenv("SYNQORIA_CONTENT_TEST_ADMIN_PASSWORD");
 }
 @BeforeEach void setUp()throws Exception{
  DriverManagerDataSource ads=new DriverManagerDataSource(adminUrl,adminUser,adminPassword);
  admin=new JdbcTemplate(ads);
  admin.execute("DO $$ BEGIN IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='synqoria_content_runtime') THEN CREATE ROLE synqoria_content_runtime LOGIN NOINHERIT NOSUPERUSER NOCREATEROLE NOCREATEDB NOREPLICATION NOBYPASSRLS PASSWORD '"+runtimePassword+"'; END IF; END $$");
  admin.execute("DROP SCHEMA IF EXISTS synqoria_content CASCADE");
  try(Connection c=ads.getConnection();Statement s=c.createStatement()){s.execute(Files.readString(Path.of("db","migration","V001__m30_clinical_content.sql")));}
  DriverManagerDataSource rds=new DriverManagerDataSource(url,runtimeUser,runtimePassword);runtimeDs=rds;
  clock=new MutableClock();
  db=new Db(new JdbcTemplate(rds),JsonMapper.builder().build(),new DataSourceTransactionManager(rds),clock,"synqoria_content_runtime","UTC");
  terms=new TerminologyService(db);formulary=new FormularyService(db);packages=new PackageService(db);activations=new ActivationService(db);
  importer=a(importerId,Perm.TERM_IMPORT,Perm.TERM_READ,Perm.MAP_PROPOSE);
  author=a(authorId,Perm.PKG_AUTHOR,Perm.PKG_VALIDATE,Perm.PKG_READ,Perm.FORM_EDIT,Perm.FORM_READ,Perm.TERM_READ,Perm.ACT_READ,Perm.MAP_PROPOSE);
  clinician=a(clinId,Perm.REVIEW_CLINICAL,Perm.FORM_APPROVE,Perm.MAP_APPROVE,Perm.APPROVAL_REVOKE,Perm.PKG_READ,Perm.FORM_READ,Perm.TERM_READ);
  policy=a(policyId,Perm.REVIEW_POLICY,Perm.PKG_READ);
  activator=a(activatorId,Perm.ACT_MANAGE,Perm.ACT_READ,Perm.PKG_READ);
  roller=a(activatorId,Perm.ROLLBACK,Perm.ACT_MANAGE,Perm.ACT_READ);
  reader=a(UUID.randomUUID(),Perm.TERM_READ,Perm.FORM_READ,Perm.PKG_READ,Perm.ACT_READ);
 }
 Actor a(UUID id,String...p){return new Actor(id,cell,facility,Set.copyOf(Arrays.stream(p).filter(Objects::nonNull).toList()));}

 // ------------------------------------------------------------- helpers
 static UUID k(){return UUID.randomUUID();}
 @SuppressWarnings("unchecked") Map<String,Object> body(Reply r){return (Map<String,Object>)r.body();}
 Reply ok(Reply r){assertTrue(r.status()<300,r.status()+" "+r.body());return r;}
 static UUID id(Map<String,Object> m){return m.get("id") instanceof UUID u?u:UUID.fromString((String)m.get("id"));}
 static long ver(Map<String,Object> m){return ((Number)m.get("version")).longValue();}
 String code(Runnable r){try{r.run();return "NO_ERROR";}catch(Violation v){return v.code();}}
 static ConceptIn c(String code,String status,String en,String parent){return new ConceptIn(code,status,en,"fr "+en,parent,List.of("syn-"+code.toLowerCase()));}
 static List<ConceptIn> drugs(){return List.of(c("D1","ACTIVE","Synthetic analgesic",null),c("D1A","ACTIVE","Synthetic analgesic tablet","D1"),c("D2","ACTIVE","Synthetic antibiotic",null),c("D3","INACTIVE","Withdrawn synthetic",null));}
 String checksum(List<ConceptIn> l){return Domain.contentChecksum(l.stream().map(x->new ImportConcept(x.code(),x.status(),x.displayEn(),x.displayFr(),x.parent(),x.synonyms())).toList());}
 ImportRequest imp(String label,List<ConceptIn> l){return new ImportRequest(label,checksum(l),clock.instant().minusSeconds(60),"synthetic test fixture",l);}
 UUID system(String uri){
  return id(body(ok(terms.createSystem(importer,k(),new SystemRequest(uri,"Synthetic "+uri,"OPEN",null)))));
 }
 UUID publish(UUID system,String label,List<ConceptIn> l){return id(body(ok(terms.importVersion(importer,k(),system,imp(label,l)))));}
 UUID drugVersion(){return publish(system(SYS),"2026.1",drugs());}

 static Map<String,Object> rule(String id,String code){
  return Map.of("id",id,"when",Map.of("all",List.of(Map.of("field","patient.age_years","op","gte","value",65))),
   "advise",Map.of("code",code,"severity","WARNING","text",Map.of("en","Review dose","fr","Revoir la dose")));
 }
 static Map<String,Object> def(Map<String,Object>...rules){
  return Map.of("schemaVersion",1,"rules",List.of(rules),"codeRefs",List.of(Map.of("system",SYS,"code","D1")));
 }
 GoldenCase gc(String n,int age,boolean fires,String ruleId){
  return new GoldenCase(n,Map.of("patient",Map.of("age_years",age)),fires?Map.of("FIRED",List.of(ruleId)):Map.of());
 }
 @SuppressWarnings("unchecked")
 PackageRequest pkgReq(String key,Map<String,Object> d,GoldenCase...cases){
  return new PackageRequest(key,d,"synthetic source guideline v1",List.of(new Dependency("CODE_SYSTEM",SYS)),clock.instant().minusSeconds(60),null,List.of(cases));
 }
 Map<String,Object> validPackage(String key){
  Map<String,Object> p=body(ok(packages.create(author,k(),pkgReq(key,def(rule("R1","C1")),gc("old",70,true,"R1"),gc("young",30,false,"R1")))));
  Map<String,Object> v=body(ok(packages.validate(author,k(),id(p),new Transition(ver(p)))));
  assertEquals("VALIDATED",v.get("status"),v.toString());
  return v;
 }
 Map<String,Object> reviewed(Map<String,Object> v){
  Map<String,Object> s=body(ok(packages.submit(author,k(),id(v),new Transition(ver(v)))));
  s=body(ok(packages.review(clinician,k(),id(s),new ReviewRequest(ver(s),"CLINICAL",true,"clinically sound",30))));
  s=body(ok(packages.review(policy,k(),id(s),new ReviewRequest(ver(s),"POLICY",true,"policy ok",30))));
  assertEquals("APPROVED",s.get("status"));
  return s;
 }
 Map<String,Object> activeOf(String type,UUID subject,UUID expected){
  return body(ok(activations.activate(activator,k(),new ActivationService.ActivateRequest(type,subject,expected))));
 }
 String status(String table,UUID id){return admin.queryForObject("SELECT status FROM synqoria_content."+table+" WHERE id=?",String.class,id);}

 // ------------------------------------------------------------- terminology
 @Test void importPublishesSearchableHierarchicalMultilingualTerminology(){
  UUID s=system(SYS);UUID v=publish(s,"2026.1",drugs());
  var hit=terms.search(importer,s,null,"analgesic",null,false,0,25);
  assertEquals(2L,((Number)hit.get("total")).longValue());
  assertEquals(1L,((Number)terms.search(importer,s,null,"syn-d2",null,false,0,25).get("total")).longValue(),"synonym search");
  assertEquals(1L,((Number)terms.search(importer,s,null,"fr synthetic antib",null,false,0,25).get("total")).longValue(),"French label search");
  assertEquals(3L,((Number)terms.search(importer,s,null,null,null,false,0,25).get("total")).longValue(),"inactive hidden by default");
  assertEquals(4L,((Number)terms.search(importer,s,null,null,null,true,0,25).get("total")).longValue());
  var concept=terms.concept(importer,v,"D1A");
  assertEquals(List.of("D1"),((List<?>)concept.get("ancestors")).stream().map(x->((Map<?,?>)x).get("code")).toList());
  assertEquals(1,((List<?>)terms.concept(importer,v,"D1").get("children")).size());
  assertEquals(3L,((Number)terms.roots(importer,v,0,10).get("total")).longValue());
  assertEquals("2026.1",((Map<?,?>)((Map<?,?>)((List<?>)terms.systems(importer,0,10).get("items")).getFirst()).get("effectiveVersion")).get("versionLabel"));
  assertEquals("invalid_pagination",code(()->terms.search(importer,s,null,null,null,false,0,1000)));
 }
 @Test void effectiveVersionFollowsEffectiveDate(){
  UUID s=system(SYS);publish(s,"2026.1",drugs());
  var l2=new ArrayList<>(drugs());l2.add(c("D4","ACTIVE","Synthetic new product",null));
  ok(terms.importVersion(importer,k(),s,new ImportRequest("2027.1",checksum(l2),clock.instant().plus(Duration.ofDays(30)),"future release",l2)));
  assertEquals(3L,((Number)terms.search(importer,s,null,null,null,false,0,25).get("total")).longValue());
  clock.now=clock.now.plus(Duration.ofDays(31));
  assertEquals(4L,((Number)terms.search(importer,s,null,null,null,false,0,25).get("total")).longValue());
 }
 @Test void rejectedImportsAreRecordedWithEveryProblemAndPublishNothing(){
  UUID s=system(SYS);
  var broken=new ArrayList<>(drugs());broken.add(c("D1","ACTIVE","dup",null));broken.add(c("X","ACTIVE","dangling","NOPE"));
  Reply r=terms.importVersion(importer,k(),s,imp("bad.1",broken));
  assertEquals(409,r.status());assertEquals("import_rejected",body(r).get("error"));
  String d=body(r).get("details").toString();
  assertTrue(d.contains("DUPLICATE_CODE:D1")&&d.contains("DANGLING_PARENT:X"),d);
  assertEquals(0L,admin.queryForObject("SELECT count(*) FROM synqoria_content.concepts",Long.class));
  assertEquals("REJECTED",admin.queryForObject("SELECT status FROM synqoria_content.code_system_versions",String.class));
  var tampered=new ImportRequest("bad.2","0".repeat(64),clock.instant(),"synthetic",drugs());
  assertTrue(body(terms.importVersion(importer,k(),s,tampered)).get("details").toString().contains("CHECKSUM_MISMATCH"));
  // the same label can still be imported correctly later, and then becomes permanent
  ok(terms.importVersion(importer,k(),s,imp("bad.1",drugs())));
  assertEquals("version_exists",code(()->terms.importVersion(importer,k(),s,imp("bad.1",drugs()))));
  // silent removal of an active concept is refused: deprecate instead
  var removed=drugs().stream().filter(x->!x.code().equals("D2")).toList();
  assertTrue(body(terms.importVersion(importer,k(),s,imp("bad.3",removed))).get("details").toString().contains("REMOVED_WITHOUT_DEPRECATION:D2"));
  var deprecated=new ArrayList<>(removed);deprecated.add(c("D2","DEPRECATED","Synthetic antibiotic",null));
  ok(terms.importVersion(importer,k(),s,imp("bad.4",deprecated)));
 }
 @Test void restrictedTerminologyRequiresLicenseRecordAndPublishedContentIsImmutable(){
  assertEquals("license_reference_required",code(()->terms.createSystem(importer,k(),new SystemRequest("urn:synthetic:licensed","Licensed","LICENSED_RESTRICTED",null))));
  ok(terms.createSystem(importer,k(),new SystemRequest("urn:synthetic:licensed","Licensed","LICENSED_RESTRICTED","LIC-SYNTH-1")));
  assertThrows(DataAccessException.class,()->admin.update("INSERT INTO synqoria_content.code_systems(id,cell_id,facility_id,system_uri,name,licensing_class,created_by) VALUES (gen_random_uuid(),?,?,'urn:synthetic:x','x','LICENSED_RESTRICTED',gen_random_uuid())",cell,facility));
  assertEquals("duplicate_record",code(()->terms.createSystem(importer,k(),new SystemRequest("urn:synthetic:licensed","Again","OPEN",null))));
  publish(system(SYS),"2026.1",drugs());
  assertThrows(DataAccessException.class,()->admin.update("UPDATE synqoria_content.concepts SET display_en='tampered'"));
  assertThrows(DataAccessException.class,()->admin.update("DELETE FROM synqoria_content.concepts"));
  assertThrows(DataAccessException.class,()->admin.update("UPDATE synqoria_content.code_system_versions SET status='REJECTED'"));
  assertThrows(DataAccessException.class,()->admin.execute("TRUNCATE synqoria_content.concepts CASCADE"));
 }
 @Test void mappingsNeedIndependentApprovalAndCannotConflict(){
  UUID v1=publish(system(SYS),"2026.1",drugs());
  UUID other=publish(system("urn:synthetic:local"),"1",List.of(c("L1","ACTIVE","Local one",null),c("L2","ACTIVE","Local two",null)));
  Map<String,Object> m1=body(ok(terms.propose(importer,k(),new MappingRequest(v1,"D1",other,"L1","EQUIVALENT"))));
  assertEquals("permission_denied",code(()->terms.decide(importer,k(),id(m1),new MappingDecision(1,true))));
  Actor selfChecker=a(importerId,Perm.MAP_APPROVE);
  assertEquals("approver_must_be_independent",code(()->terms.decide(selfChecker,k(),id(m1),new MappingDecision(1,true))));
  ok(terms.decide(clinician,k(),id(m1),new MappingDecision(1,true)));
  Map<String,Object> m2=body(ok(terms.propose(importer,k(),new MappingRequest(v1,"D1",other,"L2","EQUIVALENT"))));
  assertEquals("mapping_conflict",code(()->terms.decide(clinician,k(),id(m2),new MappingDecision(1,true))));
  assertThrows(DataAccessException.class,()->admin.update("UPDATE synqoria_content.concept_mappings SET status='APPROVED',decided_by=gen_random_uuid(),decided_at=now() WHERE id=?",id(m2)));
  assertEquals("concept_not_found",code(()->terms.propose(importer,k(),new MappingRequest(v1,"NOPE",other,"L1","EQUIVALENT"))));
  var shown=terms.concept(importer,v1,"D1");
  assertEquals(1,((List<?>)shown.get("mappings")).size(),"only approved mappings are displayed");
 }

 // ------------------------------------------------------------- formulary
 @Test void formularyRequiresIndependentApprovalSupersedesAtomicallyAndRefusesInactiveProducts(){
  UUID v=drugVersion();
  var req=new FormularyService.EntryRequest("PARA-TAB",v,"D1A","tablet 500 mg","ORAL","adult analgesia, short courses",List.of("max 4 doses/day"));
  Map<String,Object> e=body(ok(formulary.create(author,k(),req)));
  assertEquals("duplicate_record",code(()->formulary.create(author,k(),req)));
  assertEquals("invalid_transition",code(()->formulary.decide(clinician,k(),id(e),new FormularyService.Decision(1,true,"x"))),"cannot approve a draft");
  Map<String,Object> up=body(ok(formulary.update(author,k(),id(e),new FormularyService.UpdateRequest(1,"tablet 500 mg","ORAL","adult analgesia",List.of()))));
  Map<String,Object> sub=body(ok(formulary.submit(author,k(),id(up),new FormularyService.Transition(ver(up)))));
  Actor selfApprover=a(authorId,Perm.FORM_APPROVE);
  assertEquals("approver_must_be_independent",code(()->formulary.decide(selfApprover,k(),id(sub),new FormularyService.Decision(ver(sub),true,null))));
  assertEquals("permission_denied",code(()->formulary.decide(author,k(),id(sub),new FormularyService.Decision(ver(sub),true,null))));
  Map<String,Object> ap=body(ok(formulary.decide(clinician,k(),id(sub),new FormularyService.Decision(ver(sub),true,null))));
  assertEquals("APPROVED",ap.get("status"));
  // second version: approval supersedes the first in one transaction
  Map<String,Object> e2=body(ok(formulary.create(author,k(),new FormularyService.EntryRequest("PARA-TAB",v,"D1A","tablet 1 g","ORAL","adult analgesia",List.of()))));
  assertEquals(id(ap).toString(),String.valueOf(e2.get("supersedesId")));
  Map<String,Object> s2=body(ok(formulary.submit(author,k(),id(e2),new FormularyService.Transition(ver(e2)))));
  ok(formulary.decide(clinician,k(),id(s2),new FormularyService.Decision(ver(s2),true,null)));
  assertEquals("SUPERSEDED",status("formulary_entries",id(ap)));
  assertEquals(1L,admin.queryForObject("SELECT count(*) FROM synqoria_content.formulary_entries WHERE status='APPROVED'",Long.class));
  assertEquals(1,((List<?>)formulary.get(author,id(e2)).get("history")).size()>0?1:0);
  assertEquals("product_not_active",code(()->formulary.create(author,k(),new FormularyService.EntryRequest("OLD-DRUG",v,"D3","tablet","ORAL","none",List.of()))));
  assertEquals("concept_not_found",code(()->formulary.create(author,k(),new FormularyService.EntryRequest("GHOST",v,"ZZZ","tablet","ORAL","none",List.of()))));
  assertEquals("invalid_route",code(()->formulary.create(author,k(),new FormularyService.EntryRequest("BAD",v,"D2","tablet","oral","none",List.of()))));
  assertEquals("reason_required",code(()->{
   Map<String,Object> e3=body(ok(formulary.create(author,k(),new FormularyService.EntryRequest("AMOX",v,"D2","capsule","ORAL","infection",List.of()))));
   Map<String,Object> s3=body(ok(formulary.submit(author,k(),id(e3),new FormularyService.Transition(ver(e3)))));
   formulary.decide(clinician,k(),id(s3),new FormularyService.Decision(ver(s3),false,""));}));
  assertThrows(DataAccessException.class,()->admin.update("UPDATE synqoria_content.formulary_entries SET approver_id=author_id WHERE id=?",id(e2)));
  assertEquals(1L,((Number)formulary.list(author,"APPROVED",null,0,10).get("total")).longValue());
 }

 // ------------------------------------------------------------- rule packages
 @Test void validationChecksSchemaGoldenCasesDependenciesTerminologyAndRegression(){
  drugVersion();
  Map<String,Object> good=validPackage("BEERS-LITE");
  assertEquals("VALIDATED",good.get("status"));
  // golden mismatch => VALIDATION_FAILED (terminal), reported, not silently passed
  Map<String,Object> p=body(ok(packages.create(author,k(),pkgReq("WRONG",def(rule("R1","C1")),gc("bad",30,true,"R1")))));
  Map<String,Object> f=body(ok(packages.validate(author,k(),id(p),new Transition(ver(p)))));
  assertEquals("VALIDATION_FAILED",f.get("status"));
  assertTrue(f.get("report").toString().contains("bad:FIRED"),f.get("report").toString());
  assertEquals("invalid_transition",code(()->packages.submit(author,k(),id(f),new Transition(ver(f)))));
  // unknown code, undeclared system, missing dependency
  var d=Map.<String,Object>of("schemaVersion",1,"rules",List.of(rule("R1","C1")),"codeRefs",List.of(Map.of("system",SYS,"code","NOPE"),Map.of("system","urn:other","code","X"),Map.of("system",SYS,"code","D3")));
  Map<String,Object> q=body(ok(packages.create(author,k(),new PackageRequest("INTEG",d,"src",List.of(new Dependency("CODE_SYSTEM",SYS),new Dependency("CODE_SYSTEM","urn:synthetic:absent")),clock.instant().minusSeconds(1),null,List.of(gc("c",70,true,"R1"))))));
  String rep=body(ok(packages.validate(author,k(),id(q),new Transition(ver(q))))).get("report").toString();
  for(String e:List.of("CODE_UNKNOWN:NOPE","UNDECLARED_SYSTEM:urn:other","CODE_NOT_ACTIVE:D3","DEPENDENCY_UNAVAILABLE:urn:synthetic:absent"))assertTrue(rep.contains(e),e+" in "+rep);
  // no golden cases at all is a failure
  Map<String,Object> n=body(ok(packages.create(author,k(),pkgReq("EMPTYCASES",def(rule("R1","C1"))))));
  assertTrue(body(ok(packages.validate(author,k(),id(n),new Transition(ver(n))))).get("report").toString().contains("NO_GOLDEN_CASES"));
 }
 @Test void newVersionReportsRegressionAgainstPreviousVersion(){
  drugVersion();
  Map<String,Object> v1=validPackage("RG");
  Map<String,Object> next=body(ok(packages.create(author,k(),pkgReq("RG",def(Map.of("id","R1","when",Map.of("all",List.of(Map.of("field","patient.age_years","op","gte","value",80))),
   "advise",Map.of("code","C1","severity","WARNING","text",Map.of("en","a","fr","b")))),gc("old",85,true,"R1"),gc("mid",70,false,"R1")))));
  assertEquals(2,((Number)next.get("versionNo")).intValue());
  assertEquals(id(v1).toString(),String.valueOf(next.get("previousId")));
  Map<String,Object> rep=body(ok(packages.validate(author,k(),id(next),new Transition(ver(next)))));
  assertEquals("VALIDATED",rep.get("status"));
  assertTrue(rep.get("report").toString().contains("regressionChanges=[{"),"threshold change must surface as a regression: "+rep.get("report"));
 }
 @Test void validatedPackageContentIsImmutableAndHashBound(){
  drugVersion();Map<String,Object> v=validPackage("IMM");
  assertThrows(DataAccessException.class,()->admin.update("UPDATE synqoria_content.rule_packages SET definition='{\"schemaVersion\":1,\"rules\":[]}'::jsonb WHERE id=?",id(v)));
  assertThrows(DataAccessException.class,()->admin.update("UPDATE synqoria_content.rule_packages SET status='APPROVED' WHERE id=?",id(v)),"cannot reach approved without review rows? status check must hold hash");
 }
 @Test void approvalRequiresIndependentClinicalAndPolicyReviewersAndSupportsRejection(){
  drugVersion();Map<String,Object> v=validPackage("APPR");
  assertEquals("invalid_transition",code(()->packages.review(clinician,k(),id(v),new ReviewRequest(ver(v),"CLINICAL",true,"x",30))),"must be submitted first");
  Map<String,Object> s=body(ok(packages.submit(author,k(),id(v),new Transition(ver(v)))));
  Actor authorAsReviewer=a(authorId,Perm.REVIEW_CLINICAL);
  assertEquals("reviewer_must_be_independent",code(()->packages.review(authorAsReviewer,k(),id(s),new ReviewRequest(ver(s),"CLINICAL",true,"self",30))));
  assertEquals("permission_denied",code(()->packages.review(policy,k(),id(s),new ReviewRequest(ver(s),"CLINICAL",true,"wrong role",30))));
  assertEquals("invalid_validity",code(()->packages.review(clinician,k(),id(s),new ReviewRequest(ver(s),"CLINICAL",true,"x",0))));
  Map<String,Object> s1=body(ok(packages.review(clinician,k(),id(s),new ReviewRequest(ver(s),"CLINICAL",true,"ok",30))));
  assertEquals("IN_REVIEW",s1.get("status"),"one sign-off is not enough");
  Actor both=a(clinId,Perm.REVIEW_POLICY);
  assertEquals("duplicate_record",code(()->packages.review(both,k(),id(s1),new ReviewRequest(ver(s1),"POLICY",true,"same person twice",30))));
  Map<String,Object> s2=body(ok(packages.review(policy,k(),id(s1),new ReviewRequest(ver(s1),"POLICY",true,"ok",30))));
  assertEquals("APPROVED",s2.get("status"));
  // a rejection ends the version
  Map<String,Object> v2=validPackage("APPR2");Map<String,Object> t=body(ok(packages.submit(author,k(),id(v2),new Transition(ver(v2)))));
  assertEquals("REJECTED",body(ok(packages.review(clinician,k(),id(t),new ReviewRequest(ver(t),"CLINICAL",false,"unsafe threshold",30)))).get("status"));
  assertEquals("stale_version",code(()->packages.review(policy,k(),id(t),new ReviewRequest(1,"POLICY",true,"late",30))));
 }

 // ------------------------------------------------------------- activation and rollback
 @Test void activationNeedsBothApprovalsIndependentActivatorAndIsAtomicAndExclusive(){
  drugVersion();Map<String,Object> v=validPackage("ACT");
  assertEquals("approvals_incomplete",code(()->activeOf("RULE_PACKAGE",id(v),null)));
  Map<String,Object> ap=reviewed(v);
  var dry=activations.impact(activator,"RULE_PACKAGE",id(ap));
  assertEquals(true,dry.get("canActivate"));
  assertEquals(0L,admin.queryForObject("SELECT count(*) FROM synqoria_content.activations",Long.class),"dry run writes nothing");
  Actor authorActivator=a(authorId,Perm.ACT_MANAGE);
  assertEquals("activator_must_be_independent",code(()->activations.activate(authorActivator,k(),new ActivationService.ActivateRequest("RULE_PACKAGE",id(ap),null))));
  assertEquals("permission_denied",code(()->activations.activate(reader,k(),new ActivationService.ActivateRequest("RULE_PACKAGE",id(ap),null))));
  Map<String,Object> act=activeOf("RULE_PACKAGE",id(ap),null);
  assertEquals("ACTIVE",act.get("status"));
  assertEquals("activation_conflict",code(()->activeOf("RULE_PACKAGE",id(ap),null)),"stale expectedActiveId / not newer");
  // v2 supersedes v1 atomically
  Map<String,Object> n=body(ok(packages.create(author,k(),pkgReq("ACT",def(rule("R1","C1")),gc("old",70,true,"R1")))));
  Map<String,Object> nv=body(ok(packages.validate(author,k(),id(n),new Transition(ver(n)))));
  Map<String,Object> na=reviewed(nv);
  assertEquals("activation_conflict",code(()->activeOf("RULE_PACKAGE",id(na),null)),"must name the version it replaces");
  Map<String,Object> act2=activeOf("RULE_PACKAGE",id(na),id(act));
  assertEquals(id(act).toString(),String.valueOf(act2.get("previousId")));
  assertEquals("SUPERSEDED",status("activations",id(act)));
  assertEquals(1L,admin.queryForObject("SELECT count(*) FROM synqoria_content.activations WHERE status='ACTIVE'",Long.class));
  assertThrows(DataAccessException.class,()->admin.update("UPDATE synqoria_content.activations SET status='ACTIVE' WHERE id=?",id(act)));
  assertThrows(DataAccessException.class,()->admin.update("DELETE FROM synqoria_content.activations"));
  assertThrows(DataAccessException.class,()->admin.update("INSERT INTO synqoria_content.activations(id,cell_id,facility_id,subject_type,subject_key,subject_id,status,activated_by,version) VALUES (gen_random_uuid(),?,?,'RULE_PACKAGE','ACT',gen_random_uuid(),'ACTIVE',gen_random_uuid(),1)",cell,facility),"second ACTIVE row for the same subject");
 }
 @Test void concurrentActivationsHaveExactlyOneWinner()throws Exception{
  drugVersion();Map<String,Object> ap=reviewed(validPackage("RACE"));
  Actor a2=a(UUID.randomUUID(),Perm.ACT_MANAGE);
  ExecutorService pool=Executors.newFixedThreadPool(2);CyclicBarrier gate=new CyclicBarrier(2);
  Callable<String> c1=()->{gate.await();try{return "OK"+activations.activate(activator,k(),new ActivationService.ActivateRequest("RULE_PACKAGE",id(ap),null)).status();}catch(Violation v){return v.code();}};
  Callable<String> c2=()->{gate.await();try{return "OK"+activations.activate(a2,k(),new ActivationService.ActivateRequest("RULE_PACKAGE",id(ap),null)).status();}catch(Violation v){return v.code();}};
  Future<String> f1=pool.submit(c1),f2=pool.submit(c2);
  List<String> res=List.of(f1.get(30,TimeUnit.SECONDS),f2.get(30,TimeUnit.SECONDS));pool.shutdown();
  assertEquals(1,res.stream().filter(x->x.equals("OK201")).count(),res.toString());
  assertEquals(1L,admin.queryForObject("SELECT count(*) FROM synqoria_content.activations WHERE status='ACTIVE'",Long.class));
 }
 @Test void expiredOrRevokedApprovalBlocksActivationAndSuspendsLiveContent(){
  drugVersion();Map<String,Object> ap=reviewed(validPackage("EXP"));
  clock.now=clock.now.plus(Duration.ofDays(31));
  assertEquals("approval_expired",code(()->activeOf("RULE_PACKAGE",id(ap),null)));
  clock.now=clock.now.minus(Duration.ofDays(31));
  Map<String,Object> act=activeOf("RULE_PACKAGE",id(ap),null);
  UUID clinApproval=admin.queryForObject("SELECT id FROM synqoria_content.approvals WHERE role='CLINICAL'",UUID.class);
  assertEquals("permission_denied",code(()->packages.revoke(policy,k(),clinApproval,new RevokeRequest("x"))));
  Map<String,Object> revoked=body(ok(packages.revoke(clinician,k(),clinApproval,new RevokeRequest("new safety signal"))));
  assertEquals("REVOKED",revoked.get("status"));
  assertEquals("SUSPENDED",status("activations",id(act)),"fail-safe: nothing active");
  assertEquals(0L,admin.queryForObject("SELECT count(*) FROM synqoria_content.activations WHERE status='ACTIVE'",Long.class));
  assertEquals("invalid_transition",code(()->packages.revoke(clinician,k(),clinApproval,new RevokeRequest("again"))));
  assertThrows(DataAccessException.class,()->admin.update("UPDATE synqoria_content.approvals SET decision='REJECT' WHERE id=?",clinApproval));
  assertThrows(DataAccessException.class,()->admin.update("DELETE FROM synqoria_content.approvals"));
 }
 @Test void rollbackRepinsPreviousVersionAndFailsSafeWhenItIsNoLongerEligible(){
  drugVersion();
  Map<String,Object> a1=reviewed(validPackage("RB"));
  Map<String,Object> act1=activeOf("RULE_PACKAGE",id(a1),null);
  Map<String,Object> n=body(ok(packages.create(author,k(),pkgReq("RB",def(rule("R1","C1")),gc("old",70,true,"R1")))));
  Map<String,Object> a2=reviewed(body(ok(packages.validate(author,k(),id(n),new Transition(ver(n))))));
  Map<String,Object> act2=activeOf("RULE_PACKAGE",id(a2),id(act1));
  assertEquals("invalid_transition",code(()->activations.rollback(roller,k(),id(act1),new ActivationService.RollbackRequest(2,"x"))),"superseded entries cannot be rolled back");
  assertEquals("permission_denied",code(()->activations.rollback(activator,k(),id(act2),new ActivationService.RollbackRequest(1,"x"))));
  assertEquals("reason_required",code(()->activations.rollback(roller,k(),id(act2),new ActivationService.RollbackRequest(1,""))));
  assertEquals("stale_version",code(()->activations.rollback(roller,k(),id(act2),new ActivationService.RollbackRequest(9,"x"))));
  Map<String,Object> back=body(ok(activations.rollback(roller,k(),id(act2),new ActivationService.RollbackRequest(1,"unexpected alerts"))));
  assertEquals(id(a1).toString(),String.valueOf(back.get("subjectId")));
  assertEquals("ROLLED_BACK",status("activations",id(act2)));
  assertEquals(1L,admin.queryForObject("SELECT count(*) FROM synqoria_content.activations WHERE status='ACTIVE'",Long.class));
  assertEquals(3L,admin.queryForObject("SELECT count(*) FROM synqoria_content.activations",Long.class),"history retained");
  // fail-safe: revoke the retained version, then a rollback target is ineligible and state is unchanged
  Map<String,Object> a3=reviewed(body(ok(packages.validate(author,k(),id(body(ok(packages.create(author,k(),pkgReq("RB",def(rule("R1","C1")),gc("old",70,true,"R1")))))),new Transition(1)))));
  Map<String,Object> act3=activeOf("RULE_PACKAGE",id(a3),id(back));
  for(UUID ap:admin.queryForList("SELECT id FROM synqoria_content.approvals WHERE package_id=?",UUID.class,id(a1)))packages.revoke(clinician,k(),ap,new RevokeRequest("withdrawn"));
  assertEquals("nothing_to_roll_back",code(()->activations.rollback(roller,k(),id(act3),new ActivationService.RollbackRequest(1,"try"))));
  assertEquals("ACTIVE",status("activations",id(act3)),"failed rollback must not change active content");
 }
 @Test void evaluationIsAdvisoryAndWritesNothing(){
  drugVersion();Map<String,Object> v=validPackage("EVAL");
  long before=admin.queryForObject("SELECT (SELECT count(*) FROM synqoria_content.audit_events)+(SELECT count(*) FROM synqoria_content.rule_packages)",Long.class);
  var out=packages.evaluate(author,id(v),new EvaluateRequest(Map.of("patient",Map.of("age_years",80))));
  assertEquals(true,out.get("advisoryOnly"));
  assertTrue(out.get("results").toString().contains("verdict=FIRED"));
  assertEquals(before,admin.queryForObject("SELECT (SELECT count(*) FROM synqoria_content.audit_events)+(SELECT count(*) FROM synqoria_content.rule_packages)",Long.class));
  assertEquals("invalid_facts",code(()->packages.evaluate(author,id(v),new EvaluateRequest(null))));
 }
 @Test void formularyAndTerminologyActivationUseTheSameExclusiveMechanism(){
  UUID v=drugVersion();
  Map<String,Object> e=body(ok(formulary.create(author,k(),new FormularyService.EntryRequest("ACT-F",v,"D1A","tablet","ORAL","analgesia",List.of()))));
  Map<String,Object> s=body(ok(formulary.submit(author,k(),id(e),new FormularyService.Transition(ver(e)))));
  assertEquals("approvals_incomplete",code(()->activeOf("FORMULARY",id(s),null)));
  Map<String,Object> ap=body(ok(formulary.decide(clinician,k(),id(s),new FormularyService.Decision(ver(s),true,null))));
  assertEquals("ACTIVE",activeOf("FORMULARY",id(ap),null).get("status"));
  Map<String,Object> cs=activeOf("CODE_SYSTEM",v,null);
  assertEquals(SYS,cs.get("subjectKey"));
  assertEquals("invalid_subject_type",code(()->activations.impact(activator,"PRESCRIPTION",v)));
  assertEquals(2,((List<?>)activations.active(activator).get("items")).size());
 }

 // ------------------------------------------------------------- security
 @Test void crossCellAndCrossFacilityAccessIsImpossible()throws Exception{
  UUID v=drugVersion();Map<String,Object> pk=validPackage("ISO");
  Map<String,Object> ap=reviewed(pk);
  Map<String,Object> act=activeOf("RULE_PACKAGE",id(ap),null);
  Actor otherCell=new Actor(UUID.randomUUID(),UUID.randomUUID(),facility,Set.copyOf(Perm.ALL));
  Actor otherFac=new Actor(UUID.randomUUID(),cell,UUID.randomUUID(),Set.copyOf(Perm.ALL));
  for(Actor f:List.of(otherCell,otherFac)){
   assertEquals("not_found",code(()->packages.get(f,id(ap))));
   assertEquals("not_found",code(()->terms.concept(f,v,"D1")));
   assertEquals("not_found",code(()->activations.impact(f,"RULE_PACKAGE",id(ap))));
   assertEquals("not_found",code(()->activations.rollback(f,k(),id(act),new ActivationService.RollbackRequest(1,"x"))));
   assertEquals(0L,((Number)terms.systems(f,0,10).get("total")).longValue());
   assertEquals(0,((List<?>)activations.active(f).get("items")).size());
   assertEquals("not_found",code(()->packages.evaluate(f,id(ap),new EvaluateRequest(Map.of()))));
  }
  try(Connection c=runtimeDs.getConnection()){
   c.setAutoCommit(false);
   try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT count(*) FROM synqoria_content.rule_packages")){r.next();assertEquals(0,r.getInt(1));}
   try(PreparedStatement ps=c.prepareStatement("SELECT set_config('synqoria.cell',?,true),set_config('synqoria.facility',?,true)")){ps.setString(1,UUID.randomUUID().toString());ps.setString(2,facility.toString());ps.execute();}
   try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT count(*) FROM synqoria_content.concepts")){r.next();assertEquals(0,r.getInt(1));}
   assertThrows(SQLException.class,()->{try(Statement s=c.createStatement()){s.execute("INSERT INTO synqoria_content.code_systems(id,cell_id,facility_id,system_uri,name,licensing_class,created_by) VALUES (gen_random_uuid(),'"+cell+"','"+facility+"','urn:forged:x','x','OPEN',gen_random_uuid())");}});
   c.rollback();
  }
 }
 @Test void runtimeRoleIsLeastPrivilegedRlsForcedAndOwnerLoginIsRefused(){
  assertEquals(List.of(),admin.queryForList("SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='synqoria_content' AND c.relkind='r' AND NOT (c.relrowsecurity AND c.relforcerowsecurity)",String.class));
  assertEquals(0L,admin.queryForObject("SELECT count(*) FROM information_schema.role_table_grants WHERE grantee='synqoria_content_runtime' AND table_schema='synqoria_content' AND privilege_type IN ('DELETE','TRUNCATE')",Long.class));
  assertEquals(0L,admin.queryForObject("SELECT count(*) FROM information_schema.role_table_grants WHERE grantee='synqoria_content_runtime' AND table_name IN ('audit_events','outbox','concepts','code_system_versions','golden_cases','validation_runs') AND privilege_type='UPDATE'",Long.class));
  assertEquals(0L,admin.queryForObject("SELECT count(*) FROM information_schema.role_table_grants WHERE grantee='PUBLIC' AND table_schema='synqoria_content'",Long.class));
  DriverManagerDataSource ads=new DriverManagerDataSource(adminUrl,adminUser,adminPassword);
  Db bad=new Db(new JdbcTemplate(ads),JsonMapper.builder().build(),new DataSourceTransactionManager(ads),clock,"synqoria_content_runtime","UTC");
  assertEquals("database_role_denied",code(()->new TerminologyService(bad).systems(importer,0,5)));
  assertThrows(IllegalStateException.class,()->new Db(admin,JsonMapper.builder().build(),new DataSourceTransactionManager(ads),clock,"postgres","UTC"));
 }
 @Test void idempotentReplayAndKeyReuseWithDifferentPayload(){
  UUID key=k();SystemRequest r=new SystemRequest("urn:synthetic:idem","Idem","OPEN",null);
  Reply first=terms.createSystem(importer,key,r),replay=terms.createSystem(importer,key,r);
  assertEquals(201,first.status());assertEquals(201,replay.status());
  assertEquals(id(body(first)).toString(),String.valueOf(body(replay).get("id")));
  assertEquals(1L,admin.queryForObject("SELECT count(*) FROM synqoria_content.code_systems",Long.class));
  assertEquals("idempotency_collision",code(()->terms.createSystem(importer,key,new SystemRequest("urn:synthetic:idem2","Idem","OPEN",null))));
  assertEquals("idempotency_key_required",code(()->terms.createSystem(importer,null,r)));
  // a committed import rejection replays identically and creates no second record
  UUID s=system(SYS);UUID ik=k();ImportRequest bad=new ImportRequest("x1","0".repeat(64),clock.instant(),"p",drugs());
  Reply r1=terms.importVersion(importer,ik,s,bad),r2=terms.importVersion(importer,ik,s,bad);
  assertEquals(409,r1.status());assertEquals(r1.body().toString(),r2.body().toString());
  assertEquals(1L,admin.queryForObject("SELECT count(*) FROM synqoria_content.code_system_versions WHERE status='REJECTED'",Long.class));
 }
 @Test void everyStateChangeHasAnAuditAndOutboxEvent(){
  drugVersion();Map<String,Object> ap=reviewed(validPackage("AUD"));
  activeOf("RULE_PACKAGE",id(ap),null);
  assertEquals(0L,admin.queryForObject("SELECT count(*) FROM synqoria_content.audit_events e LEFT JOIN synqoria_content.outbox o USING(event_id) WHERE o.event_id IS NULL",Long.class));
  assertEquals(List.of("CREATE","VALIDATE","SUBMIT","REVIEW_CLINICAL_APPROVE","REVIEW_POLICY_APPROVE"),
   admin.queryForList("SELECT action FROM synqoria_content.audit_events WHERE aggregate_type='PACKAGE' AND aggregate_id=? ORDER BY occurred_at,event_id",String.class,id(ap)));
  assertThrows(DataAccessException.class,()->admin.update("UPDATE synqoria_content.audit_events SET action='X'"));
 }
}
