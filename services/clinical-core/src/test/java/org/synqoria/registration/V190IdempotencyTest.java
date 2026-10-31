package org.synqoria.registration;
import java.util.UUID;
public final class V190IdempotencyTest {
 public static void main(String[] a){UUID tenant=UUID.randomUUID(),facility=UUID.randomUUID(),command=UUID.randomUUID(),candidate=UUID.randomUUID();String hash="a".repeat(64);
  var k=new ReviewIdempotencyFence.Key(tenant,facility,command,candidate,1,hash);
  assert ReviewIdempotencyFence.compare(k,null)==ReviewIdempotencyFence.Outcome.NEW;
  assert ReviewIdempotencyFence.compare(k,k)==ReviewIdempotencyFence.Outcome.REPLAY_SAME_REQUEST;
  assert ReviewIdempotencyFence.compare(new ReviewIdempotencyFence.Key(tenant,facility,command,candidate,1,"b".repeat(64)),k)==ReviewIdempotencyFence.Outcome.CONFLICT;
  assert ReviewIdempotencyFence.compare(new ReviewIdempotencyFence.Key(tenant,facility,command,candidate,2,hash),k)==ReviewIdempotencyFence.Outcome.CONFLICT;
  assert ReviewIdempotencyFence.compare(new ReviewIdempotencyFence.Key(UUID.randomUUID(),facility,command,candidate,1,hash),k)==ReviewIdempotencyFence.Outcome.WRONG_SCOPE;
  assert ReviewIdempotencyFence.compare(new ReviewIdempotencyFence.Key(tenant,facility,UUID.randomUUID(),candidate,1,hash),k)==ReviewIdempotencyFence.Outcome.NEW;
  assert ReviewIdempotencyFence.compare(null,k)==ReviewIdempotencyFence.Outcome.CONFLICT;
  try{new ReviewIdempotencyFence.Key(tenant,facility,command,candidate,1,"bad");throw new AssertionError();}catch(IllegalArgumentException expected){}
  System.out.println("PASS P05-B0029 8 idempotency checks");
 }
}
