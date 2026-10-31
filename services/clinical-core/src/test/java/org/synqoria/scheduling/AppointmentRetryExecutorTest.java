package org.synqoria.scheduling;
import java.sql.SQLException;import java.util.concurrent.atomic.AtomicInteger;
public class AppointmentRetryExecutorTest{
 public static void main(String[] a)throws Exception {int n=0;AtomicInteger c=new AtomicInteger();
  String v=AppointmentRetryExecutor.execute(()->{if(c.incrementAndGet()<3)throw new SQLException("serialization","40001");return "ok";});
  if(!"ok".equals(v)||c.get()!=3)throw new AssertionError();n++;
  c.set(0);try{AppointmentRetryExecutor.execute(()->{c.incrementAndGet();throw new SQLException("denied","42501");});throw new AssertionError();}catch(SQLException e){if(c.get()!=1)throw new AssertionError();n++;}
  c.set(0);try{AppointmentRetryExecutor.execute(()->{c.incrementAndGet();throw new SQLException("deadlock","40P01");});throw new AssertionError();}catch(SQLException e){if(c.get()!=3)throw new AssertionError();n++;}
  try{AppointmentRetryExecutor.execute(null);throw new AssertionError();}catch(IllegalArgumentException expected){n++;}
  System.out.println("PASS P06-B0038 assertions="+n);
 }
}