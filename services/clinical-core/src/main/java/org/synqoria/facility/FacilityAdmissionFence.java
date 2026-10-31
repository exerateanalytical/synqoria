package org.synqoria.facility;
import org.synqoria.security.TrustedAccessContext;
import java.util.*;
/** P04-B0010. Independent authoritative hospital binding, not a client-selected facility. */
public final class FacilityAdmissionFence {
 private FacilityAdmissionFence(){}
 public static boolean mayServe(TrustedAccessContext actor,FacilityBinding actual,UUID currentCell,long expectedRevision){
  return actor!=null&&actual!=null&&currentCell!=null&&expectedRevision>0
   &&currentCell.equals(actual.cellId())&&actor.tenantId().equals(actual.tenantId())
   &&actor.facilityId().equals(actual.facilityId())&&actual.revision()==expectedRevision
   &&"ACTIVE".equals(actual.status());
 }
}
