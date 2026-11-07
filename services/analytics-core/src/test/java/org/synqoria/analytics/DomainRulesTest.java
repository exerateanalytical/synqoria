package org.synqoria.analytics;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.synqoria.analytics.domain.Domain;
import org.synqoria.analytics.domain.Domain.*;

/** Pure engine, disclosure-control and admission tests. All data is SYNTHETIC. */
class DomainRulesTest {
 static final ZoneId UTC=ZoneOffset.UTC;
 static final LocalDate D=LocalDate.parse("2026-11-07");
 static Map<String,Object> term(String agg,String field,Map<String,Object>...where){
  Map<String,Object> t=new LinkedHashMap<>();t.put("agg",agg);if(field!=null)t.put("field",field);if(where.length>0)t.put("where",List.of(where));return t;
 }
 static Map<String,Object> c(String f,String op,Object v){Map<String,Object> m=new HashMap<>();m.put("field",f);m.put("op",op);if(v!=null)m.put("value",v);return m;}
 static Fact fact(String id,Map<String,Object> data){return new Fact(id,D.atTime(8,0).toInstant(ZoneOffset.UTC),data);}
 static Map<String,Object> data(Object...kv){Map<String,Object> m=new HashMap<>();for(int i=0;i<kv.length;i+=2)m.put((String)kv[i],kv[i+1]);return m;}
 @SuppressWarnings("unchecked")
 static Map<String,Object> def(Map<String,Object> num,Map<String,Object> den,String groupBy,String cur,String... require){
  Map<String,Object> m=new LinkedHashMap<>();m.put("schemaVersion",1);m.put("source","beds");m.put("numerator",num);if(den!=null)m.put("denominator",den);
  if(groupBy!=null)m.put("groupBy",groupBy);if(cur!=null)m.put("currencyField",cur);if(require.length>0)m.put("requireFields",List.of(require));return m;
 }
 static List<Fact> beds(String ward,int total,int occupied){
  List<Fact> l=new ArrayList<>();
  for(int i=0;i<total;i++)l.add(fact(ward+i,data("ward",ward,"status",i<occupied?"OCCUPIED":"AVAILABLE")));
  return l;
 }
 static GroupResult row(List<GroupResult> r,String dim){return r.stream().filter(x->x.dimension().equals(dim)).findFirst().orElseThrow();}

 @Test void ratiosAreExactAndGroupedWithATotal(){
  var d=def(term("COUNT",null,c("status","eq","OCCUPIED")),term("COUNT",null),"ward",null);
  List<Fact> f=new ArrayList<>(beds("A",10,7));f.addAll(beds("B",8,2));
  var r=Domain.compute(d,f,D,D,UTC,5);
  assertEquals(new BigDecimal("0.700000"),row(r,"A").value());assertEquals(new BigDecimal("0.250000"),row(r,"B").value());
  assertEquals(new BigDecimal("9"),row(r,"ALL").numerator());assertEquals(new BigDecimal("18"),row(r,"ALL").denominator());
  assertEquals(new BigDecimal("0.500000"),row(r,"ALL").value());
  assertEquals("OK",row(r,"A").state());assertEquals(10,row(r,"A").cohort());
 }
 @Test void smallGroupsAreSuppressedAndNeverShownAsZero(){
  var d=def(term("COUNT",null,c("status","eq","OCCUPIED")),term("COUNT",null),"ward",null);
  List<Fact> f=new ArrayList<>(beds("A",10,0));f.addAll(beds("TINY",3,0));f.addAll(beds("C",9,4));
  var r=Domain.compute(d,f,D,D,UTC,5);
  GroupResult tiny=row(r,"TINY");
  assertEquals("SUPPRESSED",tiny.state());assertNull(tiny.value());assertNull(tiny.numerator());assertNull(tiny.denominator(),"suppressed means no numbers at all, not 0");
  assertEquals(new BigDecimal("0.000000"),row(r,"A").value(),"a real zero (10 beds, none occupied) is still a published zero");
 }
 @Test void complementarySuppressionStopsRecoveryBySubtraction(){
  var d=def(term("COUNT",null),null,"ward",null);
  List<Fact> f=new ArrayList<>(beds("A",12,0));f.addAll(beds("B",7,0));f.addAll(beds("TINY",2,0));
  var r=Domain.compute(d,f,D,D,UTC,5);
  assertEquals("SUPPRESSED",row(r,"TINY").state());
  assertEquals("SUPPRESSED",row(r,"B").state(),"the next smallest group is hidden too");
  assertEquals("OK",row(r,"A").state());assertEquals(new BigDecimal("21"),row(r,"ALL").numerator(),"the grand total stays published");
 }
 @Test void noFactsMeansNoDataNotZero(){
  var d=def(term("COUNT",null),null,null,null);
  var r=Domain.compute(d,List.of(),D,D,UTC,5);
  assertEquals(1,r.size());assertEquals("NO_DATA",r.getFirst().state());assertNull(r.getFirst().numerator());
  var outside=Domain.compute(d,beds("A",9,0),D.plusDays(5),D.plusDays(6),UTC,5);
  assertEquals("NO_DATA",outside.getFirst().state(),"facts outside the period are ignored");
 }
 @Test void missingFieldsAreUnknownAndMarkTheResultIncomplete(){
  var d=def(term("COUNT",null,c("status","eq","OCCUPIED")),term("COUNT",null),null,null,"status");
  List<Fact> f=new ArrayList<>(beds("A",9,3));f.add(fact("broken",data("ward","A")));
  var r=Domain.compute(d,f,D,D,UTC,5);
  GroupResult all=row(r,"ALL");
  assertEquals("INCOMPLETE",all.state());assertEquals(1,all.unknown());assertEquals(new BigDecimal("9"),all.denominator(),"the unknown bed is not counted as available");
 }
 @Test void sumsAreExactDecimalsSeparatedByCurrencyAndFloatsAreUnknown(){
  var d=def(term("SUM","amount"),null,null,"currency","amount");
  List<Fact> f=new ArrayList<>();
  for(int i=0;i<3;i++){f.add(fact("x"+i,data("amount","0.1","currency","XAF")));f.add(fact("y"+i,data("amount","0.2","currency","XAF")));}
  f.add(fact("e",data("amount","10.50","currency","EUR")));
  f.add(fact("fl",data("amount",1.1,"currency","XAF")));                                 // binary float => unknown
  var r=Domain.compute(d,f,D,D,UTC,1);
  GroupResult xaf=r.stream().filter(x->"XAF".equals(x.currency())).findFirst().orElseThrow(),eur=r.stream().filter(x->"EUR".equals(x.currency())).findFirst().orElseThrow();
  assertEquals(0,new BigDecimal("0.9").compareTo(xaf.numerator()),"0.1x3 + 0.2x3 is exactly 0.9");
  assertEquals(0,new BigDecimal("10.50").compareTo(eur.numerator()));
  assertEquals("INCOMPLETE",xaf.state());assertEquals(2,r.size(),"two currencies, never a combined total");
 }
 @Test void periodBoundariesUseTheFacilityZone(){
  var d=def(term("COUNT",null),null,null,null);
  Instant lateEvening=Instant.parse("2026-11-07T23:30:00Z");                               // already 7 Nov 00:30+... in UTC+1? use zone
  var z=ZoneId.of("Africa/Douala");                                                         // UTC+1
  var f=List.of(new Fact("a",lateEvening,data("x","1")));
  assertEquals("NO_DATA",Domain.compute(d,f,D,D,z,1).getFirst().state(),"23:30Z is 00:30 on the 8th in Douala");
  assertEquals("OK",Domain.compute(d,f,D.plusDays(1),D.plusDays(1),z,1).getFirst().state());
 }
 @Test void metricDefinitionsAreValidatedWithEveryProblemReported(){
  var ok=def(term("COUNT",null),null,"ward",null);
  assertEquals(List.of(),Domain.validateMetric(ok,"OPERATIONS","COUNT",5));
  assertTrue(Domain.validateMetric(ok,"CLINICAL","COUNT",3).contains("MIN_CELL_TOO_SMALL"));
  assertEquals(List.of(),Domain.validateMetric(def(term("SUM","amount"),null,null,"currency"),"FINANCIAL","AMOUNT",1));
  assertTrue(Domain.validateMetric(def(term("SUM","amount"),null,null,null),"FINANCIAL","AMOUNT",1).contains("AMOUNT_REQUIRES_CURRENCY_FIELD"));
  assertTrue(Domain.validateMetric(ok,"OPERATIONS","PERCENT",5).contains("RATE_REQUIRES_DENOMINATOR"));
  var bad=def(term("AVG",null),null,"Bad Field",null);
  var errs=Domain.validateMetric(bad,"NOPE","KILOS",5);
  for(String e:List.of("INVALID_DOMAIN","INVALID_UNIT","INVALID_AGG:NUMERATOR","INVALID_GROUP_BY"))assertTrue(errs.contains(e),e+" in "+errs);
  assertTrue(Domain.validateMetric(def(term("SUM",null),null,null,null),"OPERATIONS","COUNT",5).contains("SUM_REQUIRES_FIELD:NUMERATOR"));
 }
 @Test void eventValidationCatchesContractAndTypeProblems(){
  Instant now=Instant.parse("2026-11-07T06:00:00Z");
  assertEquals(List.of(),Domain.validateEvent(List.of("ward","status"),data("ward","A","status","OCCUPIED"),now.minusSeconds(60),now,60));
  var p=Domain.validateEvent(List.of("ward","status"),data("ward","A","amount",1.5,"nested",Map.of("a",1)),now.plusSeconds(3600),now,60);
  for(String e:List.of("MISSING_FIELD:status","FLOAT_NOT_ALLOWED:amount","NESTED_VALUE_NOT_ALLOWED:nested","OCCURRED_IN_FUTURE"))assertTrue(p.contains(e),e+" in "+p);
  assertEquals(List.of("EMPTY_PAYLOAD"),Domain.validateEvent(List.of("a"),Map.of(),now,now,60));
  assertTrue(Domain.isLate(now.minusSeconds(7200),now,60));assertFalse(Domain.isLate(now.minusSeconds(600),now,60));
 }
 @Test void crossFacilityAggregatesNeedAtLeastThreeFacilities(){
  UUID a=UUID.randomUUID(),b=UUID.randomUUID(),cc=UUID.randomUUID();
  var two=List.of(new Part(a,BigDecimal.TEN,BigDecimal.valueOf(20)),new Part(b,BigDecimal.TEN,BigDecimal.valueOf(20)));
  assertTrue(Domain.combine(two,3,true).isEmpty(),"two facilities could be inferred from each other");
  var three=new ArrayList<>(two);three.add(new Part(cc,BigDecimal.valueOf(10),BigDecimal.valueOf(60)));
  var g=Domain.combine(three,3,true).orElseThrow();
  assertEquals(new BigDecimal("0.300000"),g.value());assertEquals(3,g.cohort());
  assertTrue(Domain.combine(List.of(new Part(a,BigDecimal.ONE,null),new Part(a,BigDecimal.ONE,null),new Part(b,BigDecimal.ONE,null)),3,false).isEmpty(),"duplicates of one facility do not count twice");
 }
 @Test void csvNeutralisesFormulaInjection(){
  assertEquals("\"'=HYPERLINK(\"\"x\"\")\"",Domain.csvCell("=HYPERLINK(\"x\")"));
  for(String s:List.of("+1","-1","@SUM(A1)"))assertEquals("'"+s,Domain.csvCell(s));
  assertEquals("'\tx",Domain.csvCell("\tx"));assertEquals("\"a,b\"",Domain.csvCell("a,b"));assertEquals("plain",Domain.csvCell("plain"));assertEquals("",Domain.csvCell(null));
 }
 @Test void permissionVocabularyAndDomainGate(){
  assertEquals(Perm.READ_CLINICAL,Perm.readFor("CLINICAL"));
  assertEquals("invalid_domain",assertThrows(Violation.class,()->Perm.readFor("HR")).code());
  assertTrue(Perm.CHECKER.stream().noneMatch(p->List.of(Perm.SOURCE_MANAGE,Perm.METRIC_MANAGE,Perm.REPORT_MANAGE,Perm.EXPORT_REQUEST).contains(p)));
 }

 // ---- admission
 static final Instant NOW=Instant.parse("2026-11-07T06:00:00Z");
 static Jwt jwt(Map<String,Object> o){
  Map<String,Object> c=new HashMap<>(Map.of("sub",UUID.randomUUID().toString(),"synqoria_cell",UUID.randomUUID().toString(),"synqoria_facility",UUID.randomUUID().toString(),
   "synqoria_permissions",List.of("m28:read_operations"),"synqoria_grant_expires",NOW.plusSeconds(600).getEpochSecond()));
  c.putAll(o);return new Jwt("t",NOW.minusSeconds(5),NOW.plusSeconds(600),Map.of("alg","RS256"),c);
 }
 static String code(Jwt j){try{Platform.actorFrom(j,NOW);return "OK";}catch(Violation v){return v.code();}}
 @Test void admissionRejectsForgedOrStaleGrants(){
  assertEquals("OK",code(jwt(Map.of())));assertEquals("identity_denied",code(null));
  assertEquals("identity_denied",code(jwt(Map.of("synqoria_facility","x"))));
  assertEquals("grant_denied",code(jwt(Map.of("synqoria_permissions",List.of("m28:root")))));
  assertEquals("grant_denied",code(jwt(Map.of("synqoria_permissions",List.of("m24:comp_read")))));
  assertEquals("grant_denied",code(jwt(Map.of("synqoria_permissions",List.of("m28:ingest","m28:ingest")))));
  assertEquals("grant_expired",code(jwt(Map.of("synqoria_grant_expires",NOW.minusSeconds(1).getEpochSecond()))));
  assertEquals("grant_expired",code(jwt(Map.of("synqoria_grant_expires",NOW.plusSeconds(3600).getEpochSecond()))));
  assertEquals(403,Platform.Errors.status("verifier_must_be_independent").value());assertEquals(409,Platform.Errors.status("source_not_verified").value());
 }
}
