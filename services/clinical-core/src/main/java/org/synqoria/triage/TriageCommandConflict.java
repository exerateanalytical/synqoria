package org.synqoria.triage;
/** Explicit application conflict. Storage failures MUST NOT be mapped to a patient-state conflict. */
public final class TriageCommandConflict extends IllegalStateException {
 private final boolean staleRevision;
 public TriageCommandConflict(boolean staleRevision){super(staleRevision?"triage revision conflict":"triage state conflict");this.staleRevision=staleRevision;}
 public boolean staleRevision(){return staleRevision;}
}
