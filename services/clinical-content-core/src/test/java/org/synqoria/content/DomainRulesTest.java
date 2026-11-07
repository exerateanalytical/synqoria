package org.synqoria.content;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.synqoria.content.domain.Domain;
import org.synqoria.content.domain.Domain.*;

/** Pure domain and admission tests. All fixtures are SYNTHETIC; no licensed terminology content is embedded. */
class DomainRulesTest {
 static Map<String,Object> cond(String f,String op,Object v){Map<String,Object> m=new HashMap<>();m.put("field",f);m.put("op",op);if(v!=null)m.put("value",v);return m;}
 static Map<String,Object> rule(String id,String mode,List<Map<String,Object>> conds,String code){
  return Map.of("id",id,"when",Map.of(mode,conds),"advise",Map.of("code",code,"severity","WARNING","text",Map.of("en","Check","fr","Vérifier")));
 }
 static Map<String,Object> def(Map<String,Object>...rules){return Map.of("schemaVersion",1,"rules",List.of(rules));}

 @SuppressWarnings("unchecked")
 @Test void ruleEngineIsDeterministicThreeValuedAndOrderedById(){
  var d=def(rule("B2","all",List.of(cond("patient.age_years","gte",65),cond("order.drug","in",List.of("DRUGX","DRUGY"))),"ELDERLY_DRUG"),
            rule("A1","any",List.of(cond("patient.pregnant","eq",true),cond("labs.egfr","lt",30)),"RENAL_OR_PREG"));
  assertEquals(List.of(),Domain.validateDefinition(d));
  var facts=Map.<String,Object>of("patient",Map.of("age_years",70,"pregnant",false),"order",Map.of("drug","DRUGX"));
  var out=Domain.evaluate(d,facts);
  assertEquals(List.of("A1","B2"),out.stream().map(Advice::ruleId).toList());
  assertEquals(Verdict.INDETERMINATE,out.get(0).verdict(),"missing egfr must not become false/zero");
  assertEquals(Verdict.FIRED,out.get(1).verdict());
  assertEquals(out,Domain.evaluate(d,facts),"pure function");
  var firesAny=Domain.evaluate(d,Map.of("patient",Map.of("pregnant",true),"order",Map.of("drug","Z")));
  assertEquals(Verdict.FIRED,firesAny.get(0).verdict());
  assertEquals(Verdict.NOT_FIRED,firesAny.get(1).verdict(),"one definite false decides an all-rule");
  var nonNumeric=Domain.evaluate(def(rule("N","all",List.of(cond("labs.egfr","lt",30)),"X")),Map.of("labs",Map.of("egfr","low")));
  assertEquals(Verdict.NOT_FIRED,nonNumeric.get(0).verdict());
  assertEquals(Verdict.FIRED,Domain.evaluate(def(rule("C","all",List.of(cond("a.tags","contains","x"),cond("a.n","eq",1.0)),"X")),Map.of("a",Map.of("tags",List.of("x"),"n",1))).get(0).verdict());
 }
 @SuppressWarnings("unchecked")
 @Test void invalidDefinitionsReportEveryProblem(){
  assertEquals(List.of("MISSING_DEFINITION"),Domain.validateDefinition(null));
  assertTrue(Domain.validateDefinition(Map.of("schemaVersion",2,"rules",List.of())).contains("UNSUPPORTED_SCHEMA_VERSION"));
  var bad=Map.<String,Object>of("schemaVersion",1,"rules",List.of(
   Map.of("id","R1","when",Map.of("all",List.of(cond("Bad Field","zz",1))),"advise",Map.of("code","C","severity","LOUD","text",Map.of("en","x"))),
   Map.of("id","R1","when",Map.of("all",List.of(cond("a","in","notalist"))),"advise",Map.of("code","C","severity","INFO","text",Map.of("en","x","fr","y")))));
  var errs=Domain.validateDefinition(bad);
  for(String e:List.of("DUPLICATE_RULE_ID:R1","INVALID_FIELD:R1","INVALID_OP:R1","INVALID_ADVICE:R1","IN_REQUIRES_LIST:R1"))assertTrue(errs.contains(e),e+" in "+errs);
  assertEquals("invalid_definition",assertThrows(Violation.class,()->Domain.evaluate(bad,Map.of())).code());
  assertTrue(Domain.validateDefinition(def(Map.of("id","R","when",Map.of("all",List.of(cond("a","eq",1)),"any",List.of(cond("a","eq",1))),
   "advise",Map.of("code","C","severity","INFO","text",Map.of("en","x","fr","y"))))).contains("INVALID_WHEN:R"));
  assertTrue(Domain.validateDefinition(def(Map.of("id","R","when",Map.of("all",List.of(cond("a","eq",1))),
   "advise",Map.of("code","C","severity","INFO","text",Map.of("en","only english"))))).contains("ADVICE_TEXT_REQUIRES_EN_AND_FR:R"));
 }
 @Test void importValidationFindsDuplicatesCyclesDanglingParentsAndSilentRemovals(){
  var ok=List.of(new ImportConcept("A","ACTIVE","Alpha","Alpha fr",null,List.of("a")),new ImportConcept("B","ACTIVE","Beta",null,"A",null));
  assertEquals(List.of(),Domain.validateImport(ok,Set.of("A")));
  var bad=List.of(new ImportConcept("A","ACTIVE","x",null,"B",null),new ImportConcept("B","ACTIVE","y",null,"A",null),
   new ImportConcept("A","ACTIVE","dup",null,null,null),new ImportConcept("C","BOGUS","z",null,"MISSING",null),
   new ImportConcept("bad code","ACTIVE","q",null,null,null),new ImportConcept("D","ACTIVE"," ",null,null,null));
  var errs=Domain.validateImport(bad,Set.of("A","GONE"));
  for(String e:List.of("DUPLICATE_CODE:A","CYCLE:B","DANGLING_PARENT:C","INVALID_STATUS:C","INVALID_CODE","INVALID_DISPLAY_EN:D","REMOVED_WITHOUT_DEPRECATION:GONE"))
   assertTrue(errs.contains(e),e+" in "+errs);
  assertEquals(List.of("EMPTY_IMPORT"),Domain.validateImport(List.of(),null));
 }
 @Test void checksumIsOrderIndependentAndContentSensitive(){
  var a=new ImportConcept("A","ACTIVE","Alpha",null,null,null);var b=new ImportConcept("B","ACTIVE","Beta",null,"A",null);
  assertEquals(Domain.contentChecksum(List.of(a,b)),Domain.contentChecksum(List.of(b,a)));
  assertNotEquals(Domain.contentChecksum(List.of(a,b)),Domain.contentChecksum(List.of(a,new ImportConcept("B","ACTIVE","Beta2",null,"A",null))));
  assertTrue(Domain.contentChecksum(List.of(a)).matches("[0-9a-f]{64}"));
 }
 @Test void stateMachinesAllowOnlyDeclaredTransitions(){
  assertTrue(PackageStatus.VALIDATED.canMoveTo(PackageStatus.IN_REVIEW));
  assertFalse(PackageStatus.DRAFT.canMoveTo(PackageStatus.APPROVED),"cannot skip validation and review");
  assertFalse(PackageStatus.VALIDATION_FAILED.canMoveTo(PackageStatus.VALIDATED));
  assertFalse(PackageStatus.REVOKED.canMoveTo(PackageStatus.APPROVED));
  assertTrue(PackageStatus.APPROVED.canMoveTo(PackageStatus.REVOKED));
  assertFalse(FormularyStatus.DRAFT.canMoveTo(FormularyStatus.APPROVED));
  assertFalse(FormularyStatus.SUPERSEDED.canMoveTo(FormularyStatus.APPROVED));
  assertTrue(FormularyStatus.SUBMITTED.canMoveTo(FormularyStatus.REJECTED));
 }
 @Test void clinicalPermissionsAreSeparateFromEditorRoles(){
  Set<String> editor=Set.of(Perm.TERM_IMPORT,Perm.MAP_PROPOSE,Perm.FORM_EDIT,Perm.PKG_AUTHOR,Perm.PKG_VALIDATE,Perm.ACT_MANAGE,Perm.TERM_READ);
  assertTrue(editor.stream().noneMatch(Perm.CLINICAL_ONLY::contains));
  var a=new Actor(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),editor);
  assertEquals("permission_denied",assertThrows(Violation.class,()->a.require(Perm.REVIEW_CLINICAL)).code());
 }

 // ---- admission
 static final Instant NOW=Instant.parse("2026-11-07T06:00:00Z");
 static Jwt jwt(Map<String,Object> o){
  Map<String,Object> c=new HashMap<>(Map.of("sub",UUID.randomUUID().toString(),"synqoria_cell",UUID.randomUUID().toString(),"synqoria_facility",UUID.randomUUID().toString(),
   "synqoria_permissions",List.of("m30:terminology_read"),"synqoria_grant_expires",NOW.plusSeconds(600).getEpochSecond()));
  c.putAll(o);return new Jwt("t",NOW.minusSeconds(5),NOW.plusSeconds(600),Map.of("alg","RS256"),c);
 }
 static String code(Jwt j){try{Platform.actorFrom(j,NOW);return "OK";}catch(Violation v){return v.code();}}
 @Test void admissionRejectsForgedOrStaleGrants(){
  assertEquals("OK",code(jwt(Map.of())));
  assertEquals("identity_denied",code(null));
  assertEquals("identity_denied",code(jwt(Map.of("synqoria_facility","nope"))));
  assertEquals("grant_denied",code(jwt(Map.of("synqoria_permissions",List.of("m30:root")))));
  assertEquals("grant_denied",code(jwt(Map.of("synqoria_permissions",List.of("m26:order_read")))));
  assertEquals("grant_denied",code(jwt(Map.of("synqoria_permissions",List.of("m30:rollback","m30:rollback")))));
  assertEquals("grant_expired",code(jwt(Map.of("synqoria_grant_expires",NOW.minusSeconds(1).getEpochSecond()))));
  assertEquals("grant_expired",code(jwt(Map.of("synqoria_grant_expires",NOW.plusSeconds(3600).getEpochSecond()))));
  assertEquals(403,Platform.Errors.status("reviewer_must_be_independent").value());
  assertEquals(409,Platform.Errors.status("activation_conflict").value());
  assertEquals(404,Platform.Errors.status("not_found").value());
 }
}
