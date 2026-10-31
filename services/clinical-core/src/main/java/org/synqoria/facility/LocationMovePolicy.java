package org.synqoria.facility;
import java.util.*;
/** P04-B0006 administrative location-directory parent change, NOT patient ADT transfer. */
public final class LocationMovePolicy {
 private LocationMovePolicy(){}
 public enum Decision { ALLOW_REVISION_UPDATE, PRECONDITION_FAILED, INVALID_SCOPE, INVALID_STATE }
 public record Location(UUID tenant,UUID facility,UUID department,UUID location,long revision,boolean active){}
 public record Destination(UUID tenant,UUID facility,UUID department,boolean active){}
 public static Decision move(Location current,Destination next,long expectedRevision){
  if(current==null||next==null||current.tenant()==null||current.facility()==null||current.department()==null
   ||current.location()==null||next.tenant()==null||next.facility()==null||next.department()==null
   ||!current.tenant().equals(next.tenant())||!current.facility().equals(next.facility()))return Decision.INVALID_SCOPE;
  if(current.revision()<1||expectedRevision!=current.revision())return Decision.PRECONDITION_FAILED;
  if(!current.active()||!next.active()||current.department().equals(next.department()))return Decision.INVALID_STATE;
  if(current.revision()==Long.MAX_VALUE)return Decision.PRECONDITION_FAILED;
  return Decision.ALLOW_REVISION_UPDATE;
 }
}
