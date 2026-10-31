package org.synqoria.scheduling;

import java.sql.*;
import java.lang.reflect.*;
import java.util.*;

public final class AppointmentLockedSnapshotTest {
    static int assertions;
    static void ok(boolean v){ assertions++;if(!v)throw new AssertionError("assertion " + assertions); }
    static Object zero(Class<?> t){ if(t==boolean.class)return false;if(t==long.class)return 0L;if(t==int.class)return 0;return null; }
    static Connection connection(List<Object> bound, boolean present, String state) {
        ResultSet rs=(ResultSet)Proxy.newProxyInstance(ResultSet.class.getClassLoader(),new Class[]{ResultSet.class},new InvocationHandler(){int row=0;
            public Object invoke(Object p,Method m,Object[] a){return switch(m.getName()){
                case "next" -> present && ++row==1;
                case "getString" -> state;
                case "getLong" -> 9L;
                case "wasNull" -> false;
                default -> zero(m.getReturnType());};}});
        PreparedStatement ps=(PreparedStatement)Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),new Class[]{PreparedStatement.class},(p,m,a)->{
            if(m.getName().startsWith("set")){bound.add(a[1]);return null;}
            if(m.getName().equals("executeQuery"))return rs;
            return zero(m.getReturnType());});
        return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class[]{Connection.class},(p,m,a)->switch(m.getName()){
            case "getAutoCommit" -> false;
            case "getTransactionIsolation" -> Connection.TRANSACTION_SERIALIZABLE;
            case "prepareStatement" -> {ok(((String)a[0]).contains("FOR UPDATE"));ok(((String)a[0]).contains("patient_id = ?"));yield ps;}
            default -> zero(m.getReturnType());});
    }
    public static void main(String[] args)throws Exception{
        UUID id=UUID.randomUUID(),t=UUID.randomUUID(),f=UUID.randomUUID(),a=UUID.randomUUID(),pat=UUID.randomUUID();
        var ctx=new AppointmentTenantContext(t,f,a,pat);
        List<Object> bound=new ArrayList<>();
        var snap=AppointmentLockedSnapshot.read(connection(bound,true,"CONFIRMED"),ctx,id);
        ok(snap.appointmentId().equals(id));ok(snap.revision()==9);ok("CONFIRMED".equals(snap.lifecycle()));
        ok(bound.equals(List.of(id,t,f,pat)));
        try{AppointmentLockedSnapshot.read(connection(new ArrayList<>(),false,"CONFIRMED"),ctx,id);throw new AssertionError();}
        catch(SQLException ex){ok("02000".equals(ex.getSQLState()));ok(!ex.getMessage().contains(id.toString()));}
        try{AppointmentLockedSnapshot.read(connection(new ArrayList<>(),true,"UNRECOGNIZED"),ctx,id);throw new AssertionError();}
        catch(SQLException ex){ok("23514".equals(ex.getSQLState()));}
        System.out.println("PASS AppointmentLockedSnapshotTest assertions="+assertions);
    }
}
