package org.synqoria.scheduling;

import java.sql.*;
import java.lang.reflect.*;
import java.util.*;

public final class AppointmentIdempotencyStoreTest {
    static int checks;
    static void ok(boolean b){checks++;if(!b)throw new AssertionError("check "+checks);}
    static Object zero(Class<?> t){if(t==int.class)return 0;if(t==long.class)return 0L;if(t==boolean.class)return false;return null;}
    static Connection connection(List<String> statements, UUID patient, UUID appointment, String digest,
                                 int inserted, String state, UUID event, long revision) {
        return connection(statements,patient,appointment,digest,inserted,state,event,revision,revision==0&&event==null);
    }
    static Connection connection(List<String> statements, UUID patient, UUID appointment, String digest,
                                 int inserted, String state, UUID event, long revision, boolean revisionNull) {
        return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class[]{Connection.class},(p,m,a)->switch(m.getName()){
            case "getAutoCommit" -> false;
            case "getTransactionIsolation" -> Connection.TRANSACTION_SERIALIZABLE;
            case "prepareStatement" -> {
                String sql=(String)a[0]; statements.add(sql);
                ResultSet rs=(ResultSet)Proxy.newProxyInstance(ResultSet.class.getClassLoader(),new Class[]{ResultSet.class},new InvocationHandler(){int n=0;
                    public Object invoke(Object p2,Method m2,Object[] a2){return switch(m2.getName()){
                        case "next" -> ++n==1;
                        case "getObject" -> switch((Integer)a2[0]){case 1->patient;case 4->appointment;case 6->event;default->null;};
                        case "getString" -> (Integer)a2[0]==2?digest:state;
                        case "getLong" -> revision;
                        case "wasNull" -> revisionNull;
                        default -> zero(m2.getReturnType());};}});
                PreparedStatement ps=(PreparedStatement)Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),new Class[]{PreparedStatement.class},(p2,m2,a2)->{
                    if(m2.getName().startsWith("set"))return null;
                    if(m2.getName().equals("executeUpdate"))return sql.startsWith("INSERT")?inserted:1;
                    if(m2.getName().equals("executeQuery"))return rs;
                    return zero(m2.getReturnType());});
                yield ps;
            }
            default -> zero(m.getReturnType());});
    }
    public static void main(String[] args)throws Exception{
        UUID t=UUID.randomUUID(),f=UUID.randomUUID(),actor=UUID.randomUUID(),pat=UUID.randomUUID(),app=UUID.randomUUID(),event=UUID.randomUUID();
        var scope=new AppointmentTenantContext(t,f,actor,pat);
        String key="aLongUniqueKey123456789",digest="b".repeat(64);
        ok(AppointmentIdempotencyStore.keyDigest(key).matches("[0-9a-f]{64}"));
        ok(AppointmentIdempotencyStore.keyDigest(key).equals(AppointmentIdempotencyStore.keyDigest(key)));
        ok(!AppointmentIdempotencyStore.keyDigest(key).equals(AppointmentIdempotencyStore.keyDigest(key+"1")));
        List<String> statements=new ArrayList<>();
        var first=AppointmentIdempotencyStore.acquire(connection(statements,pat,app,digest,1,"PENDING",null,0),scope,app,key,digest);
        ok(first.kind()==AppointmentIdempotencyStore.Kind.NEW);ok(statements.size()==2);
        ok(statements.get(0).contains("ON CONFLICT"));ok(statements.get(1).contains("FOR UPDATE"));
        statements.clear();
        var replay=AppointmentIdempotencyStore.acquire(connection(statements,pat,app,digest,0,"COMMITTED",event,3),scope,app,key,digest);
        ok(replay.kind()==AppointmentIdempotencyStore.Kind.REPLAY);ok(replay.eventId().equals(event));ok(replay.resultRevision()==3);
        try{AppointmentIdempotencyStore.acquire(connection(new ArrayList<>(),UUID.randomUUID(),app,digest,0,"COMMITTED",event,3),scope,app,key,digest);throw new AssertionError();}
        catch(SQLException ex){ok("23505".equals(ex.getSQLState()));}
        try{AppointmentIdempotencyStore.acquire(connection(new ArrayList<>(),pat,app,"c".repeat(64),0,"COMMITTED",event,3),scope,app,key,digest);throw new AssertionError();}
        catch(SQLException ex){ok("23505".equals(ex.getSQLState()));}
        try{AppointmentIdempotencyStore.acquire(connection(new ArrayList<>(),pat,app,digest,0,"PENDING",null,0),scope,app,key,digest);throw new AssertionError();}
        catch(SQLException ex){ok("40001".equals(ex.getSQLState()));}
        try{AppointmentIdempotencyStore.acquire(connection(new ArrayList<>(),pat,app,digest,1,"PENDING",null,0,false),scope,app,key,digest);throw new AssertionError("non-null zero revision accepted");}
        catch(SQLException ex){ok("40001".equals(ex.getSQLState()));}
        statements.clear();
        AppointmentIdempotencyStore.complete(connection(statements,pat,app,digest,1,"PENDING",null,0),scope,app,key,digest,4,event);
        ok(statements.size()==1);ok(statements.get(0).contains("state='PENDING'"));
        try{AppointmentIdempotencyStore.keyDigest("bad");throw new AssertionError();}catch(IllegalArgumentException ex){ok(true);}
        System.out.println("PASS AppointmentIdempotencyStoreTest assertions="+checks);
    }
}
