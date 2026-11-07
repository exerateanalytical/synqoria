package org.synqoria.longitudinal;
public class LongitudinalInboxPartitionOffsetTest {
 public static void main(String[] args){var p=new LongitudinalInboxPartitionOffset.Partition("synqoria.clinical.events.v1",2);
 var w=new LongitudinalInboxPartitionOffset.Window(p,10);
 if(w.completed(p,12)!=10||w.pending()!=1)throw new AssertionError("gap jumped");
 if(w.completed(p,11)!=12||w.pending()!=0)throw new AssertionError("contiguous commit");
 if(w.completed(p,12)!=12)throw new AssertionError("duplicate");
 try{w.completed(new LongitudinalInboxPartitionOffset.Partition(p.topic(),3),13);throw new AssertionError("other partition");}
 catch(SecurityException expected){}
 try{w.completed(p,2000);throw new AssertionError("unbounded offset");}catch(SecurityException expected){}
 System.out.println("PASS B0123 offset watermark 5 checks");}
}
