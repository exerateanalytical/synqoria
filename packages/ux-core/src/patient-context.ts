/** A UI transition guard; server authorization is mandatory after every switch. */
export type PatientSwitchDecision =
  | { kind: 'confirm-unsaved'; choices: readonly ['save-draft', 'discard', 'cancel'] }
  | { kind: 'reauthorize'; clearPatientCaches: true; clearSensitiveTelemetry: true };

export function requestPatientSwitch(args: { hasUnsavedWork: boolean }): PatientSwitchDecision {
  if (args.hasUnsavedWork) {
    return { kind: 'confirm-unsaved', choices: ['save-draft', 'discard', 'cancel'] };
  }
  return { kind: 'reauthorize', clearPatientCaches: true, clearSensitiveTelemetry: true };
}

export type SyncOutcome = 'draft-local' | 'saved-server' | 'pending-sync' | 'synchronized' | 'outcome-unknown';
export const syncOutcomeLabels: Readonly<Record<SyncOutcome, Readonly<{ en: string; fr: string }>>> = {
  'draft-local': { en: 'Draft saved locally', fr: 'Brouillon enregistré localement' },
  'saved-server': { en: 'Saved to hospital server', fr: 'Enregistré sur le serveur hospitalier' },
  'pending-sync': { en: 'Pending synchronization', fr: 'Synchronisation en attente' },
  synchronized: { en: 'Synchronized', fr: 'Synchronisé' },
  'outcome-unknown': { en: 'Outcome unknown — verify before retrying', fr: 'Résultat inconnu — vérifier avant de réessayer' },
};
