package org.synqoria.scheduling;
import java.sql.SQLException;
public class AppointmentRetryPolicyTest {
 public static void main(String[] args){int n=0;
  for(String s:new String[]{"40001","40P01"})for(int a=1;a<=2;a++){if(!AppointmentRetryPolicy.retryable(new SQLException("",s),a))throw new AssertionError();n++;}
  for(String s:new String[]{"23505","42501","08006",null}){if(AppointmentRetryPolicy.retryable(new SQLException("",s),1))throw new AssertionError();n++;}
  if(AppointmentRetryPolicy.retryable(new SQLException("","40001"),3)||AppointmentRetryPolicy.retryable(null,1)||AppointmentRetryPolicy.retryable(new SQLException("","40001"),0))throw new AssertionError();n+=3;
  System.out.println("PASS P06-B0037 assertions="+n);
 }
}
