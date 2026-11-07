package org.synqoria.longitudinal;
import java.time.*;
public class LongitudinalInboxReleaseReadinessTest {
 public static void main(String[] z){Instant n=Instant.now();
 if(LongitudinalInboxReleaseReadiness.ready((d,t)->false,n))throw new AssertionError("default open");
 if(!LongitudinalInboxReleaseReadiness.ready((d,t)->true,n))throw new AssertionError("all verified");
 for(var denied:LongitudinalInboxReleaseReadiness.Dependency.values()){
  if(LongitudinalInboxReleaseReadiness.ready((d,t)->d!=denied,n))throw new AssertionError("dependency missed "+denied);
 }
 if(LongitudinalInboxReleaseReadiness.ready((d,t)->{throw new IllegalStateException("secret");},n))throw new AssertionError("exception fail open");
 try{LongitudinalInboxReleaseReadiness.require((d,t)->false,n);throw new AssertionError("require fail open");}
 catch(SecurityException expected){}
 System.out.println("PASS B0129 dependency readiness 11 checks");}
}
