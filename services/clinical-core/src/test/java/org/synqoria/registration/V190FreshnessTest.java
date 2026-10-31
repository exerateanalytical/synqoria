package org.synqoria.registration;
import java.time.Instant;
public final class V190FreshnessTest {
 public static void main(String[] a){Instant issued=Instant.parse("2026-10-30T00:00:00Z"),exp=issued.plusSeconds(60),now=issued.plusSeconds(20);
  assert ReviewFreshnessWindow.fresh(issued,exp,now.minusSeconds(15),now);
  assert !ReviewFreshnessWindow.fresh(issued,exp,now.minusSeconds(16),now);
  assert !ReviewFreshnessWindow.fresh(issued,exp,now.plusSeconds(1),now);
  assert !ReviewFreshnessWindow.fresh(issued,exp,issued.minusSeconds(1),now);
  assert !ReviewFreshnessWindow.fresh(issued,exp,now,exp);
  assert !ReviewFreshnessWindow.fresh(issued,exp,now,issued.minusSeconds(1));
  assert !ReviewFreshnessWindow.fresh(issued,issued,issued,issued);
  assert !ReviewFreshnessWindow.fresh(null,exp,now,now);
  System.out.println("PASS P05-B0032 8 freshness checks");
 }
}
