package org.synqoria.longitudinal;
/** Compare durable DB inbox checkpoint against broker partitions without skipping gaps. */
public final class LongitudinalInboxCheckpointReconcile {
 private LongitudinalInboxCheckpointReconcile(){}
 public enum Action {START_AT_DB_NEXT,REPLAY_FROM_DB_NEXT,PAUSE_FOR_REVIEW}
 public record Decision(Action action,long nextOffset){
   public Decision {if(nextOffset<0)throw new IllegalArgumentException("offset");}
 }
 public static Decision reconcile(long dbCommitted,long brokerCommitted,long brokerEarliest,long brokerLatest){
   if(dbCommitted<-1||brokerCommitted<-1||brokerEarliest<0||brokerLatest<brokerEarliest||
      dbCommitted==Long.MAX_VALUE||brokerCommitted==Long.MAX_VALUE)throw new IllegalArgumentException("checkpoint");
   long next=dbCommitted+1;
   if(next<brokerEarliest||next>brokerLatest+1||brokerCommitted>dbCommitted)
      return new Decision(Action.PAUSE_FOR_REVIEW,next);
   if(brokerCommitted==dbCommitted)return new Decision(Action.START_AT_DB_NEXT,next);
   return new Decision(Action.REPLAY_FROM_DB_NEXT,next);
 }
}
