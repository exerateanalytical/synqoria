package org.synqoria.security;
import java.util.*;
public final class SafeApiProblemTest {
 static int n=0;static void ok(boolean yes){if(!yes)throw new AssertionError("Problem "+n);n++;}
 public static void main(String[]args){var id=UUID.randomUUID();var statuses=Map.of(
  SafeApiProblem.Failure.NOT_DISCLOSED,404,SafeApiProblem.Failure.STALE_VERSION,412,
  SafeApiProblem.Failure.BUSINESS_CONFLICT,409,SafeApiProblem.Failure.INVALID_SEMANTICS,422,
  SafeApiProblem.Failure.UNAVAILABLE,503,SafeApiProblem.Failure.RATE_LIMIT,429);
  for(var e:statuses.entrySet()){var p=SafeApiProblem.of(e.getKey(),id);ok(p.httpStatus()==e.getValue());ok(p.correlationId().equals(id));ok(!p.title().contains("patient"));}
  ok(SafeApiProblem.of(SafeApiProblem.Failure.NOT_DISCLOSED,id).code().equals("RESOURCE_NOT_AVAILABLE"));
  boolean fail=false;try{SafeApiProblem.of(null,id);}catch(IllegalArgumentException x){fail=true;}ok(fail);
  System.out.println("PASS: "+n+" allowlisted API problem assertions");
 }
}
