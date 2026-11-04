package org.synqoria.longitudinal.inboxops;
import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
/** P07-B0156: Immutable state transition circuit breaker prevents unsafe broker/DB retries. Pure fail-closed policy only; caller supplies trusted context and transactional state. */
public final class InboxDependencyCircuitBreaker {
 private InboxDependencyCircuitBreaker(){}
 private static void require(boolean condition){if(!condition)throw new SecurityException("clinical event unavailable");}

 public enum State{CLOSED,OPEN,HALF_OPEN}
 public record Circuit(State state,int consecutiveFailures,Instant retryAfter){
  public Circuit{Objects.requireNonNull(state);Objects.requireNonNull(retryAfter);require(consecutiveFailures>=0&&consecutiveFailures<=20);}
 }
 public static Circuit fail(Circuit c,Instant now){require(c!=null&&now!=null);
  int n=Math.min(20,c.consecutiveFailures()+1);if(n>=3)return new Circuit(State.OPEN,n,now.plusSeconds(30));
  return new Circuit(State.CLOSED,n,now);
 }
 public static Circuit probe(Circuit c,Instant now){require(c!=null&&now!=null&&c.state()==State.OPEN&&!now.isBefore(c.retryAfter()));return new Circuit(State.HALF_OPEN,c.consecutiveFailures(),now);}
 public static Circuit succeed(Circuit c){require(c!=null&&c.state()!=State.OPEN);return new Circuit(State.CLOSED,0,c.retryAfter());}

}
