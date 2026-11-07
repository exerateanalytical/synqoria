package org.synqoria.longitudinal;
import java.time.*;import java.util.*;
public class LongitudinalInboxEpochFenceTest {
 public static void main(String[] args){UUID c=UUID.randomUUID(),e=UUID.randomUUID(),t=UUID.randomUUID();Instant n=Instant.now();
 var x=new LongitudinalInboxEpochFence.Claim(c,e,t,42,n.plusSeconds(20));
 LongitudinalInboxEpochFence.require(x,c,e,t,42,n);
 int rejected=0;for(int i=0;i<5;i++){try{LongitudinalInboxEpochFence.require(x,i==0?UUID.randomUUID():c,
 i==1?UUID.randomUUID():e,i==2?UUID.randomUUID():t,i==3?43:42,i==4?n.plusSeconds(20):n);
 throw new AssertionError("allowed stale claim "+i);}catch(SecurityException ok){rejected++;}}
 if(rejected!=5)throw new AssertionError("insufficient failures");System.out.println("PASS B0122 epoch claim 6 checks");}
}
