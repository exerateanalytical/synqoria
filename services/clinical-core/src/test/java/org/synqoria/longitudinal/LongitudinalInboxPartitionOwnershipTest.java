package org.synqoria.longitudinal;
import java.time.*;import java.util.*;
public class LongitudinalInboxPartitionOwnershipTest {
 public static void main(String[] args){UUID owner=UUID.randomUUID();Instant now=Instant.now();
 var a=new LongitudinalInboxPartitionOwnership.Assignment("synqoria.clinical.events.v1",4,owner,3,now.plusSeconds(20));
 a.require(owner,3,4,now);int failures=0;
 for(int n=0;n<4;n++){try{a.require(n==0?UUID.randomUUID():owner,n==1?4:3,n==2?5:4,n==3?now.plusSeconds(20):now);
 throw new AssertionError("stale partition");}catch(SecurityException x){failures++;}}
 if(failures!=4)throw new AssertionError("missing gate");System.out.println("PASS B0126 partition ownership 5 checks");}
}
