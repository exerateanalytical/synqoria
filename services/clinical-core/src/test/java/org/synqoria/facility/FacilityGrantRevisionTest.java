package org.synqoria.facility;
import org.synqoria.security.TrustedAccessContext;import java.time.Instant;import java.util.*;
public class FacilityGrantRevisionTest {
 static int n;static void yes(boolean b){if(!b)throw new AssertionError();n++;}
 public static void main(String[] args){
  var t=UUID.randomUUID();var f=UUID.randomUUID();var a=UUID.randomUUID();var now=Instant.parse("2026-10-30T00:00:00Z");
  var ctx=new TrustedAccessContext(t,f,a,Set.of("facility:read"),Set.of());
  var grant=new FacilityGrantRevision.Snapshot(t,f,a,2,Set.of("facility:read"),now.minusSeconds(5),now.plusSeconds(120),true,true);
  yes(FacilityGrantRevision.permits(ctx,grant,"facility:read",2,now));
  yes(!FacilityGrantRevision.permits(ctx,grant,"facility:read",3,now));
  yes(!FacilityGrantRevision.permits(ctx,grant,"facility:read",2,now.plusSeconds(121)));
  yes(!FacilityGrantRevision.permits(ctx,grant,"facility:write",2,now));
  yes(!FacilityGrantRevision.permits(new TrustedAccessContext(t,UUID.randomUUID(),a,Set.of("facility:read"),Set.of()),grant,"facility:read",2,now));
  var shifted=new FacilityGrantRevision.Snapshot(t,f,a,2,Set.of("facility:read"),now,now.plusSeconds(100),true,false);
  yes(!FacilityGrantRevision.permits(ctx,shifted,"facility:read",2,now));
  System.out.println("PASS: "+n+" facility grant assertions");
 }
}
