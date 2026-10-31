package org.synqoria.reliability;
/** P01-B0013: retry classification. A timeout is UNKNOWN, never a successful business effect. */
public final class DeliveryRetryPolicy {
 private DeliveryRetryPolicy(){}
 public enum Outcome { ACKNOWLEDGED, RETRY_LATER, RECONCILE_FIRST, PERMANENT_FAILURE }
 public record Advice(Outcome outcome,long delayMillis){}
 public static Advice classify(Integer responseCode,boolean idempotentOperation,int attempt,long retryAfterMillis){
  if(attempt<0||attempt>100||retryAfterMillis<0)throw new IllegalArgumentException("invalid attempt");
  if(responseCode==null)return new Advice(Outcome.RECONCILE_FIRST,0);
  int status=responseCode;
  if(status>=200&&status<300)return new Advice(Outcome.ACKNOWLEDGED,0);
  if(status==409||status==412||status==422||status==400||status==401||status==403||status==404)
   return new Advice(Outcome.PERMANENT_FAILURE,0);
  if(status==408||status==425||status==429||status>=500&&status<=599){
   if(!idempotentOperation)return new Advice(Outcome.RECONCILE_FIRST,0);
   if(attempt>=8)return new Advice(Outcome.PERMANENT_FAILURE,0);
   long exp=Math.min(30000,250L*(1L<<Math.min(7,attempt)));
   return new Advice(Outcome.RETRY_LATER,Math.min(60000,Math.max(exp,retryAfterMillis)));
  }
  return new Advice(Outcome.PERMANENT_FAILURE,0);
 }
}
