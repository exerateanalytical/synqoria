package org.synqoria.registration;
import java.time.Duration;import java.time.Instant;
/** P05-B0032: bounds replay exposure; actual reviewer grant validation is external. */
public final class ReviewFreshnessWindow {
 private static final Duration MAX_AGE=Duration.ofSeconds(15);
 private ReviewFreshnessWindow(){}
 public static boolean fresh(Instant issued,Instant expires,Instant observed,Instant now){
  if(issued==null||expires==null||observed==null||now==null||!issued.isBefore(expires)||!now.isBefore(expires)
   ||now.isBefore(issued)||observed.isAfter(now)||observed.isBefore(issued))return false;
  Duration age=Duration.between(observed,now);
  return !age.isNegative()&&age.compareTo(MAX_AGE)<=0;
 }
}
