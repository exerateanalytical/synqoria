package org.synqoria.longitudinal;
public class LongitudinalInboxCheckpointReconcileTest {
 public static void main(String[] x){var c=LongitudinalInboxCheckpointReconcile.class;
 var a=LongitudinalInboxCheckpointReconcile.reconcile(12,12,0,20);
 if(a.action()!=LongitudinalInboxCheckpointReconcile.Action.START_AT_DB_NEXT||a.nextOffset()!=13)throw new AssertionError("start");
 if(LongitudinalInboxCheckpointReconcile.reconcile(12,10,0,20).action()!=LongitudinalInboxCheckpointReconcile.Action.REPLAY_FROM_DB_NEXT)throw new AssertionError("replay");
 if(LongitudinalInboxCheckpointReconcile.reconcile(12,14,0,20).action()!=LongitudinalInboxCheckpointReconcile.Action.PAUSE_FOR_REVIEW)throw new AssertionError("uncommitted ACK");
 if(LongitudinalInboxCheckpointReconcile.reconcile(4,4,7,20).action()!=LongitudinalInboxCheckpointReconcile.Action.PAUSE_FOR_REVIEW)throw new AssertionError("retention gap");
 try{LongitudinalInboxCheckpointReconcile.reconcile(-2,0,0,20);throw new AssertionError("negative");}catch(IllegalArgumentException expected){}
 System.out.println("PASS B0128 checkpoint reconcile 5 checks");}
}
