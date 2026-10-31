package org.synqoria.scheduling;
import java.util.UUID;
public class AppointmentScopeFenceTest {public static void main(String[] a){UUID t=UUID.randomUUID(),f=UUID.randomUUID(),p=UUID.randomUUID(),other=UUID.randomUUID();AppointmentScopeFence.require(t,f,p,t,f,p);
 UUID[][] bad={{other,f,p},{t,other,p},{t,f,other},{null,f,p}};
 for(UUID[] b:bad){try{AppointmentScopeFence.require(t,f,p,b[0],b[1],b[2]);throw new AssertionError();}catch(SecurityException expected){}}
 System.out.println("AppointmentScopeFenceTest PASS 5 checks");}}
