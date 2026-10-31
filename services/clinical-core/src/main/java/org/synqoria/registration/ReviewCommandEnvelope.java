package org.synqoria.registration;
import java.util.UUID;
/** P05-B0033: validated immutable command identity; no approval authority. */
public record ReviewCommandEnvelope(UUID tenant,UUID facility,UUID command,UUID candidate,UUID registrar,UUID reviewer,long revision,String digest){
 public ReviewCommandEnvelope {
  if(tenant==null||facility==null||command==null||candidate==null||registrar==null||reviewer==null||registrar.equals(reviewer)||revision<0||digest==null||!digest.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Invalid reviewer command");
 }
 public boolean sameScope(ReviewCommandEnvelope other){return other!=null&&tenant.equals(other.tenant)&&facility.equals(other.facility)&&candidate.equals(other.candidate)&&revision==other.revision;}
}
