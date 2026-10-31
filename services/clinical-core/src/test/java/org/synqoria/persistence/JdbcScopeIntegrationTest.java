package org.synqoria.persistence;
import java.sql.*;import java.lang.reflect.*;import java.util.*;
/** JDBC API contract checks without PostgreSQL: validates scoped SQL and bind/close behavior only. */
public final class JdbcScopeIntegrationTest{
 static int checks=0;static void ok(boolean yes){if(!yes)throw new AssertionError("JDBC scope "+checks);checks++;}
 static Object d(Class<?> t){if(t==boolean.class)return false;if(t==int.class)return 0;if(t==long.class)return 0L;return null;}
 @SuppressWarnings("unchecked")static <T>T prox(Class<T> type,InvocationHandler h){return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},h);}
 static final class Mock {
  String sql;final Map<Integer,Object> bindings=new HashMap<>();boolean closed,stmtClosed,rsClosed,readonly=true,duplicate=false;Map<String,Object> row;
  Connection connection(){return prox(Connection.class,(p,m,args)->{
   return switch(m.getName()){
    case "isReadOnly"->readonly;
    case "prepareStatement"->{sql=(String)args[0];yield statement();}
    case "close"->{closed=true;yield null;}
    default ->d(m.getReturnType());
   };
  });}
  PreparedStatement statement(){return prox(PreparedStatement.class,(p,m,args)->{
   return switch(m.getName()){
    case "setObject"->{bindings.put((Integer)args[0],args[1]);yield null;}
    case "setMaxRows"->{ok(((Integer)args[0])==2);yield null;}
    case "setQueryTimeout"->{ok(((Integer)args[0])==3);yield null;}
    case "executeQuery"->results();
    case "close"->{stmtClosed=true;yield null;}
    default->d(m.getReturnType());
   };
  });}
  ResultSet results(){return prox(ResultSet.class,new InvocationHandler(){int position=0;
   public Object invoke(Object p,Method m,Object[] args){return switch(m.getName()){
    case "next"->{position++;yield row!=null && position<=(duplicate?2:1);}
    case "getObject"->row.get((String)args[0]);
    case "getString"->row.get((String)args[0]);
    case "getLong"->row.get((String)args[0]);
    case "close"->{rsClosed=true;yield null;}
    default->d(m.getReturnType());
   };}
  });}
 }
 public static void main(String[] args){
  var tenant=UUID.randomUUID();var facility=UUID.randomUUID();var cell=UUID.randomUUID();var patient=UUID.randomUUID();
  Mock m=new Mock();m.row=Map.of("tenant_id",tenant,"facility_id",facility,"patient_id",patient,"identity_status","verified","revision",5L);
  var repository=new TenantScopedPatientJdbc(m::connection,facility);
  var found=repository.getForTenant(tenant,patient);
  ok(found.isPresent()&&found.get().revision()==5);ok(m.sql.contains("tenant_id = ? AND home_facility_id = ? AND patient_id = ?"));
  ok(m.bindings.get(1).equals(tenant)&&m.bindings.get(2).equals(facility)&&m.bindings.get(3).equals(patient));
  ok(m.closed&&m.stmtClosed&&m.rsClosed);
  Mock cross=new Mock();cross.row=Map.of("tenant_id",tenant,"facility_id",UUID.randomUUID(),"patient_id",patient,"identity_status","verified","revision",5L);
  try{new TenantScopedPatientJdbc(cross::connection,facility).getForTenant(tenant,patient);throw new AssertionError("cross facility leaked");}catch(IllegalStateException e){checks++;}
  Mock writable=new Mock();writable.readonly=false;
  try{new TenantScopedPatientJdbc(writable::connection,facility).getForTenant(tenant,patient);throw new AssertionError("write pool allowed");}catch(IllegalStateException e){checks++;}
  Mock f=new Mock();f.row=Map.of("tenant_id",tenant,"facility_id",facility,"cell_id",cell,"status","ACTIVE","revision",3L);
  var resolver=new TenantScopedFacilityJdbc(f::connection);ok(resolver.resolve(tenant,cell,facility).isPresent());
  ok(f.sql.contains("tenant_id = ? AND cell_id = ? AND facility_id = ?"));ok(f.bindings.get(1).equals(tenant)&&f.bindings.get(2).equals(cell)&&f.bindings.get(3).equals(facility));
  ok(f.closed&&f.stmtClosed&&f.rsClosed);
  Mock inactive=new Mock();inactive.row=Map.of("tenant_id",tenant,"facility_id",facility,"cell_id",cell,"status","SUSPENDED","revision",3L);
  ok(new TenantScopedFacilityJdbc(inactive::connection).resolve(tenant,cell,facility).isEmpty());
  Mock duplicate=new Mock();duplicate.row=f.row;duplicate.duplicate=true;
  try{new TenantScopedFacilityJdbc(duplicate::connection).resolve(tenant,cell,facility);throw new AssertionError("duplicated location accepted");}catch(IllegalStateException e){checks++;}
  System.out.println("PASS P04-B0008/B0009: "+checks+" JDBC scoped-query assertions (mock JDBC, not PostgreSQL)");
 }
}
