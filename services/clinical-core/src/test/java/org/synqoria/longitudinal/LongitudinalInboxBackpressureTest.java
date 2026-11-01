package org.synqoria.longitudinal;
import java.util.*;
public class LongitudinalInboxBackpressureTest {
 public static void main(String[] a){var w=new LongitudinalInboxBackpressure.Window(2);
 UUID one=w.acquire().orElseThrow(),two=w.acquire().orElseThrow();
 if(w.acquire().isPresent()||w.outstanding()!=2)throw new AssertionError("overflow");
 w.release(one);if(w.acquire().isEmpty())throw new AssertionError("not reusable");
 try{w.release(one);throw new AssertionError("double release");}catch(SecurityException expected){}
 w.revokeAll();if(w.outstanding()!=0)throw new AssertionError("not revoked");
 try{new LongitudinalInboxBackpressure.Window(257);throw new AssertionError("unbounded");}catch(IllegalArgumentException expected){}
 System.out.println("PASS B0125 consumer backpressure 5 checks");}
}
