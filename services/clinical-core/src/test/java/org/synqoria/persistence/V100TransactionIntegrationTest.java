package org.synqoria.persistence;
import java.lang.reflect.*;import java.sql.*;import java.time.*;import java.util.*;
import org.synqoria.security.*;import org.synqoria.facility.*;
/** JDBC proxy checks only: transaction semantics must also run on a live PostgreSQL role/RLS environment. */
public final class V100TransactionIntegrationTest {
 static int checks=0;static void ok(boolean b){if(!b)throw new AssertionError("v100 check "+checks);checks++;}
 static Object def(Class<?> type){if(type==boolean.class)return false;if(type==int.class)return 0;if(type==long.class)return 0L;return null;}
 @SuppressWarnings("unchecked")static <T>T proxy(Class<T> type,InvocationHandler inv){return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},inv);}
 static final class Db {
  boolean auto=false,readonly=false,committed=false,rolled=false,closed=false,failAudit=false,failCommit=false,allowedRow=true,wrongFacility=false;
  String lastSql="";int inserts=0,queries=0; final Map<Integer,Object> bind=new HashMap<>();
  final UUID tenant,facility,patient;
  Db(UUID t,UUID f,UUID p){tenant=t;facility=f;patient=p;}
  Connection open(){return proxy(Connection.class,(self,m,a)->switch(m.getName()){
   case "getAutoCommit"->auto;case "isReadOnly"->readonly;
   case "prepareStatement"->{lastSql=(String)a[0];yield statement(lastSql);}
   case "commit"->{if(failCommit)throw new SQLException("simulated failure");committed=true;yield null;}
   case "rollback"->{rolled=true;yield null;}
   case "close"->{closed=true;yield null;}
   default->def(m.getReturnType());});}
  PreparedStatement statement(String sql){return proxy(PreparedStatement.class,(self,m,a)->switch(m.getName()){
   case "setObject","setString","setTimestamp","setQueryTimeout","setMaxRows"->{if(a.length>1&&a[0] instanceof Integer)bind.put((Integer)a[0],a[1]);yield null;}
   case "executeQuery"->{queries++;yield results();}
   case "executeUpdate"->{inserts++;if(failAudit)throw new SQLException("write unavailable");yield 1;}
   case "close"->null;
   default->def(m.getReturnType());});}
  ResultSet results(){return proxy(ResultSet.class,new InvocationHandler(){int n=0;
    public Object invoke(Object p,Method m,Object[] a){return switch(m.getName()){
     case "next"->{n++;yield allowedRow&&n==1;}
     case "getObject"->switch((String)a[0]){case "tenant_id"->tenant;case "facility_id"->wrongFacility?UUID.randomUUID():facility;case "patient_id"->patient;default->null;};
     case "getString"->"verified";case "getLong"->1L;case "close"->null;default->def(m.getReturnType());};}
  });}
 }
 public static void main(String[] args){
  UUID t=UUID.randomUUID(),f=UUID.randomUUID(),p=UUID.randomUUID(),cell=UUID.randomUUID(),actor=UUID.randomUUID();
  var access=new TrustedAccessContext(t,f,actor,Set.of("patient:read"),Set.of(p));Instant now=Instant.parse("2026-10-30T00:00:00Z");UUID correlation=UUID.randomUUID();
  Db good=new Db(t,f,p);var svc=new TransactionalPatientReadJdbc(good::open,(a,b,c)->true);
  var r=svc.read(access,p,correlation,now);ok(r.status()==TransactionalPatientReadJdbc.Status.OK&&r.patient()!=null);
  ok(good.committed&&good.inserts==1&&good.queries==1&&good.closed);ok(PatientReadReleaseFence.released(r)!=null);
  Db noProof=new Db(t,f,p);var denied=new TransactionalPatientReadJdbc(noProof::open,(a,b,c)->false).read(access,p,correlation,now);
  ok(denied.status()==TransactionalPatientReadJdbc.Status.NOT_DISCLOSED&&noProof.rolled&&!noProof.committed&&noProof.inserts==0);
  Db missing=new Db(t,f,p);missing.allowedRow=false;ok(new TransactionalPatientReadJdbc(missing::open,(a,b,c)->true).read(access,p,correlation,now).status()==TransactionalPatientReadJdbc.Status.NOT_DISCLOSED&&missing.rolled);
  Db cross=new Db(t,f,p);cross.wrongFacility=true;ok(new TransactionalPatientReadJdbc(cross::open,(a,b,c)->true).read(access,p,correlation,now).status()==TransactionalPatientReadJdbc.Status.NOT_DISCLOSED&&cross.rolled);
  Db noAudit=new Db(t,f,p);noAudit.failAudit=true;var failed=new TransactionalPatientReadJdbc(noAudit::open,(a,b,c)->true).read(access,p,correlation,now);
  ok(failed.status()==TransactionalPatientReadJdbc.Status.UNAVAILABLE&&noAudit.rolled&&!noAudit.committed);ok(PatientReadReleaseFence.released(failed)==null);
  Db noCommit=new Db(t,f,p);noCommit.failCommit=true;ok(new TransactionalPatientReadJdbc(noCommit::open,(a,b,c)->true).read(access,p,correlation,now).status()==TransactionalPatientReadJdbc.Status.UNAVAILABLE&&noCommit.rolled);
  Db auto=new Db(t,f,p);auto.auto=true;ok(new TransactionalPatientReadJdbc(auto::open,(a,b,c)->true).read(access,p,correlation,now).status()==TransactionalPatientReadJdbc.Status.UNAVAILABLE&&!auto.committed);
  Db readOnly=new Db(t,f,p);readOnly.readonly=true;ok(new TransactionalPatientReadJdbc(readOnly::open,(a,b,c)->true).read(access,p,correlation,now).status()==TransactionalPatientReadJdbc.Status.UNAVAILABLE);
  var unlisted=new TrustedAccessContext(t,f,actor,Set.of("patient:read"),Set.of());Db skip=new Db(t,f,p);
  ok(new TransactionalPatientReadJdbc(skip::open,(a,b,c)->true).read(unlisted,p,correlation,now).status()==TransactionalPatientReadJdbc.Status.NOT_DISCLOSED&&!skip.committed&&!skip.rolled&&!skip.closed);
  ok(FacilityAdmissionFence.mayServe(access,new FacilityBinding(cell,t,f,"ACTIVE",7),cell,7));
  ok(!FacilityAdmissionFence.mayServe(access,new FacilityBinding(cell,t,f,"SUSPENDED",7),cell,7));
  ok(!FacilityAdmissionFence.mayServe(access,new FacilityBinding(cell,t,f,"ACTIVE",8),cell,7));
  ok(SessionLifetimeFence.active(now,now.minusSeconds(30),now.plusSeconds(60),now.minusSeconds(5),10));
  ok(!SessionLifetimeFence.active(now,now.minusSeconds(30),now.plusSeconds(60),now.minusSeconds(11),10));
  ok(!SessionLifetimeFence.active(now,now.minusSeconds(30),now,now,10));
  try {new ClinicalAuditEntry(null,correlation,actor,t,f,p,"patient:read",now);throw new AssertionError();}catch(IllegalArgumentException ok){checks++;}
  System.out.println("PASS v1.0.0: "+checks+" transaction/audit/scope/session assertions (JDBC mocks only)");
 }
}
