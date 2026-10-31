package org.synqoria.reliability;
public final class DeliveryRetryPolicyTest{
 static int n;static void yes(boolean b){if(!b)throw new AssertionError();n++;}
 public static void main(String[] args){
  var O=DeliveryRetryPolicy.Outcome.ACKNOWLEDGED;yes(DeliveryRetryPolicy.classify(201,true,0,0).outcome()==O);
  yes(DeliveryRetryPolicy.classify(null,true,1,0).outcome()==DeliveryRetryPolicy.Outcome.RECONCILE_FIRST);
  yes(DeliveryRetryPolicy.classify(503,false,1,0).outcome()==DeliveryRetryPolicy.Outcome.RECONCILE_FIRST);
  yes(DeliveryRetryPolicy.classify(503,true,0,0).delayMillis()==250);
  yes(DeliveryRetryPolicy.classify(429,true,2,7000).delayMillis()==7000);
  yes(DeliveryRetryPolicy.classify(503,true,7,0).delayMillis()==30000);
  yes(DeliveryRetryPolicy.classify(503,true,8,0).outcome()==DeliveryRetryPolicy.Outcome.PERMANENT_FAILURE);
  for(int x:new int[]{400,401,403,404,409,412,422})yes(DeliveryRetryPolicy.classify(x,true,1,0).outcome()==DeliveryRetryPolicy.Outcome.PERMANENT_FAILURE);
  yes(DeliveryRetryPolicy.classify(408,true,1,0).outcome()==DeliveryRetryPolicy.Outcome.RETRY_LATER);
  System.out.println("PASS: "+n+" retry assertions");
 }
}
