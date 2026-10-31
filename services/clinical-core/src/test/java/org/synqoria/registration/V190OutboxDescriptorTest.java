package org.synqoria.registration;
import java.time.Instant;import java.util.UUID;
public final class V190OutboxDescriptorTest {
 public static void main(String[] a){UUID tenant=UUID.randomUUID(),facility=UUID.randomUUID(),review=UUID.randomUUID(),command=UUID.randomUUID();
  var e=new ReviewOutboxDescriptor(tenant,facility,review,command,1,ReviewOutboxDescriptor.Kind.REVIEW_APPROVED,Instant.parse("2026-10-30T00:00:00Z"),"a".repeat(64));
  assert e.topic().equals("synqoria.review.decision.v1");assert e.partitionKey().equals(tenant+":"+review);assert e.eventType().equals("REVIEW_APPROVED");
  assert e.toString().indexOf("patientName")<0;assert e.version()==1;
  try{new ReviewOutboxDescriptor(tenant,facility,review,command,0,e.kind(),e.occurredAt(),e.auditDigest());throw new AssertionError();}catch(IllegalArgumentException expected){}
  try{new ReviewOutboxDescriptor(tenant,facility,review,command,1,e.kind(),e.occurredAt(),"not-a-digest");throw new AssertionError();}catch(IllegalArgumentException expected){}
  System.out.println("PASS P05-B0030 7 metadata checks");
 }
}
