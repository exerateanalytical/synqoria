// M40 English / French dictionary. Both languages must define exactly the same keys (tested).
export type Lang = "en" | "fr";

const en = {
 "app.title": "Reliability, backup and recovery", "app.subtitle": "M40 · Reliability",
 "nav.label": "Reliability workstations", "nav.health": "Service health and capacity", "nav.incidents": "Incident workspace", "nav.backups": "Backup catalogue and verification",
 "nav.recovery": "Recovery plan and execution", "nav.validation": "Recovery validation and reopening", "nav.telemetry": "Telemetry explorer", "lang.label": "Language", "skip": "Skip to content",
 "hub.title": "Reliability, backup and recovery", "hub.intro": "What is healthy, what is broken, which backups can really be restored, and what must be true before a cell reopens. Unknown is shown as unknown.", "hub.open": "Open",
 "common.refresh": "Refresh", "common.retry": "Try again", "common.reload": "Reload latest version", "common.cancel": "Cancel",
 "common.previous": "Previous page", "common.next": "Next page", "common.page": "Page {page} of {pages}", "common.total": "{total} records",
 "common.none": "None", "common.any": "Any", "common.version": "Version", "common.status": "Status", "common.actions": "Actions", "common.details": "Details",
 "common.reason": "Reason", "common.select": "Select", "common.at": "Time", "common.evidence": "Evidence reference", "common.service": "Service", "common.from": "From", "common.to": "To",
 "state.loading": "Loading records…", "state.empty": "No records match the current filters.", "state.emptyHint": "Change the filters or refresh once new records arrive.",
 "state.error": "The service could not be reached or returned an error.", "state.errorHint": "No data is shown as current. Check the connection, then try again.",
 "state.offline": "You are offline. Displayed data may be out of date and actions are disabled until the connection returns.",
 "state.unauthorized": "Sign in is required.", "state.unauthorizedHint": "Your session is missing or has expired. Sign in with your hospital identity to continue.",
 "state.signIn": "Sign in with hospital identity", "state.forbidden": "You are not authorized for this action or record.",
 "state.forbiddenHint": "Your role does not include this permission. Ask your supervisor if you need access.",
 "state.notFound": "This record does not exist or is outside your hospital and facility.", "state.notConfigured": "The reliability service is not configured for this environment.",
 "disabled.status": "Not available in the current status ({status}).", "disabled.select": "Select a record first.", "disabled.offline": "Unavailable while offline.", "disabled.input": "Complete the required fields first.",
 "err.generic": "The action was refused ({code}).", "err.permission_denied": "You do not have permission for this action.",
 "err.approver_must_be_independent": "A different person must authorize this. You cannot authorize what you planned.", "err.verifier_must_be_independent": "A different person must verify this. You cannot verify what you recorded or own.",
 "err.reopener_must_be_independent": "Someone who neither planned nor authorized this recovery must approve reopening.",
 "err.stale_version": "The record changed. Reload it and try again.", "err.idempotency_collision": "This submission was already used with different content. Review and submit again.",
 "err.invalid_transition": "This action is not allowed in the record's current status.", "err.duplicate_record": "A matching record already exists (only one real recovery may be open at a time).",
 "err.reason_required": "A reason of at least five characters is required.", "err.evidence_required": "Evidence is required.",
 "err.backup_not_verified": "The base backup is not verified, or has expired.", "err.wal_gap": "The WAL archive has a gap before the target position.", "err.target_out_of_range": "The target position is before the base backup ended.",
 "err.fencing_not_confirmed": "Fencing was not confirmed: egress must be disabled and writers stopped.", "err.restore_target_mismatch": "The restored position does not equal the target position.",
 "err.objects_unreconciled": "Some objects are still missing.", "err.effects_unreconciled": "External side effects are still unreconciled.", "err.validation_incomplete": "Not every validation item has passed.",
 "err.invalid_verification": "All five verification checks must be submitted with evidence.", "err.invalid_recovery": "The recovery request is not valid.", "err.invalid_incident": "The incident request is not valid.",
 "err.invalid_query": "The search is not valid.", "err.invalid_batch": "Too many or no events.", "err.invalid_pagination": "Invalid page request.", "err.malformed_request": "The request was not understood.",
 "err.reliability_unavailable": "The reliability service is temporarily unavailable.", "err.not_found": "Not found.",
 "enum.health.HEALTHY": "Healthy", "enum.health.DEGRADED": "Degraded", "enum.health.DOWN": "Down", "enum.health.UNKNOWN": "Unknown (no fresh signal)",
 "enum.tier.CRITICAL": "Critical", "enum.tier.IMPORTANT": "Important", "enum.tier.SUPPORT": "Support",
 "enum.sev.SEV1": "SEV1 · critical", "enum.sev.SEV2": "SEV2 · major", "enum.sev.SEV3": "SEV3 · minor", "enum.sev.SEV4": "SEV4 · low",
 "enum.inc.OPEN": "Open", "enum.inc.ACKNOWLEDGED": "Acknowledged", "enum.inc.MITIGATING": "Mitigating", "enum.inc.MITIGATED": "Mitigated", "enum.inc.RESOLVED": "Resolved and verified",
 "enum.bk.RECORDED": "Recorded, not verified", "enum.bk.VERIFIED": "Verified", "enum.bk.VERIFICATION_FAILED": "Verification failed", "enum.kind.FULL": "Full", "enum.kind.INCREMENTAL": "Incremental", "enum.kind.WAL_ARCHIVE": "WAL archive",
 "enum.check.INVENTORY": "Inventory", "enum.check.INTEGRITY": "Integrity (digest)", "enum.check.ENCRYPTION_KEY": "Encryption key available", "enum.check.COMPLETENESS": "Completeness", "enum.check.RESTORE_READINESS": "Restore readiness",
 "enum.result.PASS": "Pass", "enum.result.FAIL": "Fail",
 "enum.rec.PLANNED": "Planned", "enum.rec.AUTHORIZED": "Authorized", "enum.rec.FENCED": "Fenced", "enum.rec.DB_RESTORED": "Database restored", "enum.rec.OBJECTS_RECONCILED": "Objects reconciled", "enum.rec.AUDIT_RECOVERED": "Audit recovered",
 "enum.rec.EFFECTS_RECONCILED": "External effects reconciled", "enum.rec.VALIDATING": "Validating", "enum.rec.REOPENED": "Reopened", "enum.rec.ABORTED": "Aborted",
 "enum.step.FENCE": "Record fencing", "enum.step.RESTORE_DB": "Record database restore", "enum.step.RECONCILE_OBJECTS": "Record object reconciliation", "enum.step.RECOVER_AUDIT": "Record audit recovery",
 "enum.step.RECONCILE_EFFECTS": "Record external-effect reconciliation", "enum.step.BEGIN_VALIDATION": "Begin validation",
 "enum.scenario.DATABASE_FAILURE": "Database failure", "enum.scenario.APPLICATION_FAILURE": "Application failure", "enum.scenario.INFRASTRUCTURE_LOSS": "Infrastructure loss", "enum.scenario.CONNECTIVITY_INTERRUPTION": "Connectivity interruption", "enum.scenario.ISOLATED_CELL_FAILURE": "Isolated cell failure",
 "enum.item.SMOKE_TESTS": "Smoke tests", "enum.item.IDENTITY_CONTINUITY": "Identity continuity", "enum.item.CLINICAL_RECONCILIATION": "Clinical reconciliation", "enum.item.FINANCIAL_RECONCILIATION": "Financial reconciliation",
 "enum.item.AUDIT_CONTINUITY": "Audit continuity", "enum.item.EGRESS_STATE": "Egress state",
 "enum.level.DEBUG": "Debug", "enum.level.INFO": "Info", "enum.level.WARN": "Warning", "enum.level.ERROR": "Error", "enum.source.EXERCISE": "Measured in an exercise", "enum.source.REAL": "Measured in a real recovery",
 // S01
 "hl.title": "Service health and capacity", "hl.intro": "Health comes only from fresh signals. A service that has stopped reporting is Unknown, not healthy. Hard dependencies can cap a service at Degraded.",
 "hl.services": "Services", "hl.none": "No services registered.", "hl.owner": "Owner", "hl.boundary": "Recovery boundary", "hl.dependency": "Degraded by a dependency", "hl.signals": "Latest signals", "hl.noSignals": "No signals",
 "hl.saturation": "Saturation", "hl.backlog": "Backlog", "hl.latency": "Latency p95 (ms)", "hl.errors": "Error rate (%)", "hl.openIncidents": "Open incidents", "hl.freshness": "Signals older than {n} s are ignored.", "hl.slo": "Target availability",
 // S02
 "in.title": "Incident workspace", "in.intro": "Declare, acknowledge, escalate and mitigate. An incident is resolved only when someone other than its owner verifies the recovery.",
 "in.declare": "Declare incident", "in.titleField": "Title", "in.severity": "Severity", "in.service": "Affected service", "in.impact": "Impact", "in.declareAction": "Declare", "in.list": "Incidents", "in.none": "No incidents.",
 "in.ack": "Acknowledge", "in.assign": "Assign owner (user id)", "in.assignAction": "Assign", "in.note": "Timeline entry", "in.noteKind": "Type", "in.noteNote": "Note", "in.noteAction": "Action taken", "in.escalate": "Escalate", "in.escalateReason": "Escalation reason",
 "in.mitigating": "Mark mitigating", "in.mitigated": "Mark mitigated", "in.statusText": "Mitigation detail", "in.verify": "Verify recovery and resolve", "in.verifyEvidence": "Verification evidence", "in.timeline": "Timeline",
 "in.overdue": "Acknowledgement overdue", "in.sla": "Acknowledge within {n} minutes", "in.level": "Escalation level", "in.owner": "Owner", "in.pick": "Select an incident.", "in.declared": "Incident declared.", "in.add": "Add entry",
 // S03
 "bk.title": "Backup catalogue and verification", "bk.intro": "A backup is Verified only after five independent checks by a different person, including a recomputed digest. A successful backup job is not a verified backup.",
 "bk.catalogue": "Backups", "bk.none": "No backups.", "bk.filterKind": "Kind", "bk.filterStatus": "Status", "bk.snapshot": "Snapshot", "bk.wal": "WAL range", "bk.digest": "Recorded digest", "bk.key": "Key reference", "bk.retention": "Retained until",
 "bk.expired": "Expired", "bk.size": "{n} bytes", "bk.verification": "Verification", "bk.verifyAction": "Submit verification", "bk.observed": "Digest recomputed from the artifact", "bk.walCheck": "WAL continuity check",
 "bk.baseBackup": "Base (full) backup", "bk.target": "Target WAL position", "bk.walAction": "Check continuity", "bk.continuous": "Continuous up to the target.", "bk.gap": "Gap between {a} and {b}.", "bk.pick": "Select a backup.",
 "bk.noChecks": "Not yet verified.", "bk.verified": "Verification recorded.", "bk.objects": "Object references",
 // S04
 "rc.title": "Recovery plan and execution", "rc.intro": "Plan from a verified backup with continuous WAL, have someone else authorize it, fence the cell, restore, reconcile and recover the audit trail. Each step needs recorded evidence.",
 "rc.plan": "Plan a recovery", "rc.scenario": "Scenario", "rc.exercise": "This is an exercise in a disposable environment", "rc.base": "Verified base backup", "rc.targetLsn": "Target WAL position", "rc.targetTime": "Target time",
 "rc.outage": "Outage began", "rc.planAction": "Plan recovery", "rc.list": "Recoveries", "rc.none": "No recoveries.", "rc.authorize": "Authorize", "rc.abort": "Abort recovery", "rc.abortReason": "Abort reason", "rc.evidence": "Evidence for this step",
 "rc.egress": "Egress disabled", "rc.writers": "Writers stopped", "rc.restoredLsn": "Restored WAL position", "rc.missing": "Objects still missing", "rc.auditDigest": "Audit chain digest (SHA-256)", "rc.pending": "External effects still pending",
 "rc.next": "Next step", "rc.steps": "Recorded steps", "rc.pick": "Select a recovery.", "rc.real": "Real recovery", "rc.planned": "Recovery planned.", "rc.done": "Step recorded.", "rc.base0": "No verified full backup available.",
 // S05
 "va.title": "Recovery validation and reopening", "va.intro": "Reopening is scoped, independently approved and needs six passing validations. The numbers shown are what was measured, never a promise.",
 "va.pick": "Recoveries being validated", "va.none": "No recovery is being validated.", "va.items": "Validation items", "va.record": "Record validation", "va.item": "Item", "va.result": "Result", "va.residual": "Residual risk",
 "va.reopen": "Reopen services", "va.scope": "Services to reopen", "va.missing": "Not recorded", "va.measure": "Measured recovery times", "va.rto": "Time to reopen", "va.rpo": "Data lost window", "va.noMeasure": "Nothing has been measured yet.",
 "va.noGuarantee": "These are measurements of specific recoveries, not guarantees.", "va.recorded": "Validation recorded.", "va.reopened": "Services reopened.", "va.minutes": "{n} min", "va.history": "Measurements",
 // S06
 "tl.title": "Operational telemetry explorer", "tl.intro": "Logs and traces are redacted before they are stored. Every search you run here is itself recorded.",
 "tl.filters": "Search", "tl.correlation": "Correlation id", "tl.level": "Level", "tl.search": "Search", "tl.results": "Events", "tl.none": "No events.", "tl.redactions": "{n} redactions", "tl.trace": "Trace", "tl.access": "Who searched what",
 "tl.accessNone": "No searches recorded.", "tl.returned": "{n} returned", "tl.attributes": "Attributes", "tl.message": "Message", "tl.event": "Event"
} as const;

export type Key = keyof typeof en;

const fr: Record<Key, string> = {
 "app.title": "Fiabilité, sauvegarde et reprise", "app.subtitle": "M40 · Fiabilité",
 "nav.label": "Postes de fiabilité", "nav.health": "Santé et capacité des services", "nav.incidents": "Espace incidents", "nav.backups": "Catalogue et vérification des sauvegardes",
 "nav.recovery": "Plan et exécution de reprise", "nav.validation": "Validation et réouverture", "nav.telemetry": "Explorateur de télémétrie", "lang.label": "Langue", "skip": "Passer au contenu",
 "hub.title": "Fiabilité, sauvegarde et reprise", "hub.intro": "Ce qui est sain, ce qui est en panne, quelles sauvegardes sont réellement restaurables et ce qui doit être vrai avant la réouverture d’une cellule. L’inconnu est affiché comme inconnu.", "hub.open": "Ouvrir",
 "common.refresh": "Actualiser", "common.retry": "Réessayer", "common.reload": "Recharger la dernière version", "common.cancel": "Annuler",
 "common.previous": "Page précédente", "common.next": "Page suivante", "common.page": "Page {page} sur {pages}", "common.total": "{total} enregistrements",
 "common.none": "Aucun", "common.any": "Tous", "common.version": "Version", "common.status": "État", "common.actions": "Actions", "common.details": "Détails",
 "common.reason": "Motif", "common.select": "Sélectionner", "common.at": "Heure", "common.evidence": "Référence de la preuve", "common.service": "Service", "common.from": "Du", "common.to": "Au",
 "state.loading": "Chargement des enregistrements…", "state.empty": "Aucun enregistrement ne correspond aux filtres.", "state.emptyHint": "Modifiez les filtres ou actualisez à l’arrivée de nouveaux enregistrements.",
 "state.error": "Le service est injoignable ou a renvoyé une erreur.", "state.errorHint": "Aucune donnée n’est présentée comme à jour. Vérifiez la connexion puis réessayez.",
 "state.offline": "Vous êtes hors ligne. Les données affichées peuvent être périmées et les actions sont désactivées jusqu’au retour de la connexion.",
 "state.unauthorized": "Une connexion est requise.", "state.unauthorizedHint": "Votre session est absente ou expirée. Connectez-vous avec votre identité hospitalière pour continuer.",
 "state.signIn": "Se connecter avec l’identité hospitalière", "state.forbidden": "Vous n’êtes pas autorisé pour cette action ou cet enregistrement.",
 "state.forbiddenHint": "Votre rôle n’inclut pas cette autorisation. Demandez l’accès à votre responsable.",
 "state.notFound": "Cet enregistrement n’existe pas ou est hors de votre hôpital et de votre établissement.", "state.notConfigured": "Le service de fiabilité n’est pas configuré pour cet environnement.",
 "disabled.status": "Indisponible dans l’état actuel ({status}).", "disabled.select": "Sélectionnez d’abord un enregistrement.", "disabled.offline": "Indisponible hors ligne.", "disabled.input": "Renseignez d’abord les champs obligatoires.",
 "err.generic": "L’action a été refusée ({code}).", "err.permission_denied": "Vous n’avez pas l’autorisation pour cette action.",
 "err.approver_must_be_independent": "Une autre personne doit autoriser. Vous ne pouvez pas autoriser ce que vous avez planifié.", "err.verifier_must_be_independent": "Une autre personne doit vérifier. Vous ne pouvez pas vérifier ce que vous avez enregistré ou dont vous êtes responsable.",
 "err.reopener_must_be_independent": "Quelqu’un qui n’a ni planifié ni autorisé cette reprise doit approuver la réouverture.",
 "err.stale_version": "L’enregistrement a changé. Rechargez-le et réessayez.", "err.idempotency_collision": "Cet envoi a déjà été utilisé avec un contenu différent. Vérifiez puis renvoyez.",
 "err.invalid_transition": "Cette action n’est pas permise dans l’état actuel de l’enregistrement.", "err.duplicate_record": "Un enregistrement correspondant existe déjà (une seule reprise réelle peut être ouverte à la fois).",
 "err.reason_required": "Un motif d’au moins cinq caractères est requis.", "err.evidence_required": "Une preuve est requise.",
 "err.backup_not_verified": "La sauvegarde de base n’est pas vérifiée ou a expiré.", "err.wal_gap": "L’archive WAL présente un trou avant la position cible.", "err.target_out_of_range": "La position cible précède la fin de la sauvegarde de base.",
 "err.fencing_not_confirmed": "L’isolement n’a pas été confirmé : la sortie doit être désactivée et les écritures arrêtées.", "err.restore_target_mismatch": "La position restaurée n’est pas égale à la position cible.",
 "err.objects_unreconciled": "Des objets sont encore manquants.", "err.effects_unreconciled": "Des effets externes ne sont pas encore réconciliés.", "err.validation_incomplete": "Tous les éléments de validation n’ont pas réussi.",
 "err.invalid_verification": "Les cinq vérifications doivent être soumises avec preuve.", "err.invalid_recovery": "La demande de reprise n’est pas valide.", "err.invalid_incident": "La demande d’incident n’est pas valide.",
 "err.invalid_query": "La recherche n’est pas valide.", "err.invalid_batch": "Trop d’événements ou aucun.", "err.invalid_pagination": "Demande de page invalide.", "err.malformed_request": "La requête n’a pas été comprise.",
 "err.reliability_unavailable": "Le service de fiabilité est temporairement indisponible.", "err.not_found": "Introuvable.",
 "enum.health.HEALTHY": "Sain", "enum.health.DEGRADED": "Dégradé", "enum.health.DOWN": "En panne", "enum.health.UNKNOWN": "Inconnu (aucun signal récent)",
 "enum.tier.CRITICAL": "Critique", "enum.tier.IMPORTANT": "Important", "enum.tier.SUPPORT": "Support",
 "enum.sev.SEV1": "SEV1 · critique", "enum.sev.SEV2": "SEV2 · majeur", "enum.sev.SEV3": "SEV3 · mineur", "enum.sev.SEV4": "SEV4 · faible",
 "enum.inc.OPEN": "Ouvert", "enum.inc.ACKNOWLEDGED": "Pris en compte", "enum.inc.MITIGATING": "En atténuation", "enum.inc.MITIGATED": "Atténué", "enum.inc.RESOLVED": "Résolu et vérifié",
 "enum.bk.RECORDED": "Enregistrée, non vérifiée", "enum.bk.VERIFIED": "Vérifiée", "enum.bk.VERIFICATION_FAILED": "Vérification échouée", "enum.kind.FULL": "Complète", "enum.kind.INCREMENTAL": "Incrémentale", "enum.kind.WAL_ARCHIVE": "Archive WAL",
 "enum.check.INVENTORY": "Inventaire", "enum.check.INTEGRITY": "Intégrité (empreinte)", "enum.check.ENCRYPTION_KEY": "Clé de chiffrement disponible", "enum.check.COMPLETENESS": "Complétude", "enum.check.RESTORE_READINESS": "Aptitude à la restauration",
 "enum.result.PASS": "Réussi", "enum.result.FAIL": "Échec",
 "enum.rec.PLANNED": "Planifiée", "enum.rec.AUTHORIZED": "Autorisée", "enum.rec.FENCED": "Isolée", "enum.rec.DB_RESTORED": "Base restaurée", "enum.rec.OBJECTS_RECONCILED": "Objets réconciliés", "enum.rec.AUDIT_RECOVERED": "Audit récupéré",
 "enum.rec.EFFECTS_RECONCILED": "Effets externes réconciliés", "enum.rec.VALIDATING": "En validation", "enum.rec.REOPENED": "Rouverte", "enum.rec.ABORTED": "Abandonnée",
 "enum.step.FENCE": "Enregistrer l’isolement", "enum.step.RESTORE_DB": "Enregistrer la restauration de la base", "enum.step.RECONCILE_OBJECTS": "Enregistrer la réconciliation des objets", "enum.step.RECOVER_AUDIT": "Enregistrer la récupération de l’audit",
 "enum.step.RECONCILE_EFFECTS": "Enregistrer la réconciliation des effets externes", "enum.step.BEGIN_VALIDATION": "Commencer la validation",
 "enum.scenario.DATABASE_FAILURE": "Panne de base de données", "enum.scenario.APPLICATION_FAILURE": "Panne applicative", "enum.scenario.INFRASTRUCTURE_LOSS": "Perte d’infrastructure", "enum.scenario.CONNECTIVITY_INTERRUPTION": "Interruption de connectivité", "enum.scenario.ISOLATED_CELL_FAILURE": "Défaillance d’une cellule isolée",
 "enum.item.SMOKE_TESTS": "Tests de fumée", "enum.item.IDENTITY_CONTINUITY": "Continuité d’identité", "enum.item.CLINICAL_RECONCILIATION": "Réconciliation clinique", "enum.item.FINANCIAL_RECONCILIATION": "Réconciliation financière",
 "enum.item.AUDIT_CONTINUITY": "Continuité de l’audit", "enum.item.EGRESS_STATE": "État de la sortie",
 "enum.level.DEBUG": "Débogage", "enum.level.INFO": "Info", "enum.level.WARN": "Avertissement", "enum.level.ERROR": "Erreur", "enum.source.EXERCISE": "Mesuré lors d’un exercice", "enum.source.REAL": "Mesuré lors d’une reprise réelle",
 "hl.title": "Santé et capacité des services", "hl.intro": "La santé provient uniquement de signaux récents. Un service qui ne communique plus est Inconnu, pas sain. Les dépendances fortes peuvent le plafonner à Dégradé.",
 "hl.services": "Services", "hl.none": "Aucun service enregistré.", "hl.owner": "Responsable", "hl.boundary": "Périmètre de reprise", "hl.dependency": "Dégradé par une dépendance", "hl.signals": "Derniers signaux", "hl.noSignals": "Aucun signal",
 "hl.saturation": "Saturation", "hl.backlog": "File d’attente", "hl.latency": "Latence p95 (ms)", "hl.errors": "Taux d’erreur (%)", "hl.openIncidents": "Incidents ouverts", "hl.freshness": "Les signaux de plus de {n} s sont ignorés.", "hl.slo": "Disponibilité visée",
 "in.title": "Espace incidents", "in.intro": "Déclarez, prenez en compte, escaladez et atténuez. Un incident n’est résolu que lorsqu’une autre personne que son responsable vérifie la reprise.",
 "in.declare": "Déclarer un incident", "in.titleField": "Titre", "in.severity": "Gravité", "in.service": "Service concerné", "in.impact": "Impact", "in.declareAction": "Déclarer", "in.list": "Incidents", "in.none": "Aucun incident.",
 "in.ack": "Prendre en compte", "in.assign": "Affecter un responsable (id utilisateur)", "in.assignAction": "Affecter", "in.note": "Entrée de chronologie", "in.noteKind": "Type", "in.noteNote": "Note", "in.noteAction": "Action menée", "in.escalate": "Escalader", "in.escalateReason": "Motif d’escalade",
 "in.mitigating": "Marquer en atténuation", "in.mitigated": "Marquer atténué", "in.statusText": "Détail de l’atténuation", "in.verify": "Vérifier la reprise et résoudre", "in.verifyEvidence": "Preuve de vérification", "in.timeline": "Chronologie",
 "in.overdue": "Prise en compte en retard", "in.sla": "Prise en compte sous {n} minutes", "in.level": "Niveau d’escalade", "in.owner": "Responsable", "in.pick": "Sélectionnez un incident.", "in.declared": "Incident déclaré.", "in.add": "Ajouter l’entrée",
 "bk.title": "Catalogue et vérification des sauvegardes", "bk.intro": "Une sauvegarde n’est Vérifiée qu’après cinq contrôles indépendants par une autre personne, dont une empreinte recalculée. Une tâche de sauvegarde réussie n’est pas une sauvegarde vérifiée.",
 "bk.catalogue": "Sauvegardes", "bk.none": "Aucune sauvegarde.", "bk.filterKind": "Type", "bk.filterStatus": "État", "bk.snapshot": "Instantané", "bk.wal": "Plage WAL", "bk.digest": "Empreinte enregistrée", "bk.key": "Référence de clé", "bk.retention": "Conservée jusqu’au",
 "bk.expired": "Expirée", "bk.size": "{n} octets", "bk.verification": "Vérification", "bk.verifyAction": "Soumettre la vérification", "bk.observed": "Empreinte recalculée à partir de l’artefact", "bk.walCheck": "Contrôle de continuité WAL",
 "bk.baseBackup": "Sauvegarde de base (complète)", "bk.target": "Position WAL cible", "bk.walAction": "Vérifier la continuité", "bk.continuous": "Continue jusqu’à la cible.", "bk.gap": "Trou entre {a} et {b}.", "bk.pick": "Sélectionnez une sauvegarde.",
 "bk.noChecks": "Pas encore vérifiée.", "bk.verified": "Vérification enregistrée.", "bk.objects": "Références d’objets",
 "rc.title": "Plan et exécution de reprise", "rc.intro": "Planifiez à partir d’une sauvegarde vérifiée avec WAL continu, faites autoriser par une autre personne, isolez la cellule, restaurez, réconciliez et récupérez la piste d’audit. Chaque étape exige une preuve enregistrée.",
 "rc.plan": "Planifier une reprise", "rc.scenario": "Scénario", "rc.exercise": "Il s’agit d’un exercice en environnement jetable", "rc.base": "Sauvegarde de base vérifiée", "rc.targetLsn": "Position WAL cible", "rc.targetTime": "Heure cible",
 "rc.outage": "Début de la panne", "rc.planAction": "Planifier la reprise", "rc.list": "Reprises", "rc.none": "Aucune reprise.", "rc.authorize": "Autoriser", "rc.abort": "Abandonner la reprise", "rc.abortReason": "Motif d’abandon", "rc.evidence": "Preuve de cette étape",
 "rc.egress": "Sortie désactivée", "rc.writers": "Écritures arrêtées", "rc.restoredLsn": "Position WAL restaurée", "rc.missing": "Objets encore manquants", "rc.auditDigest": "Empreinte de la chaîne d’audit (SHA-256)", "rc.pending": "Effets externes encore en attente",
 "rc.next": "Étape suivante", "rc.steps": "Étapes enregistrées", "rc.pick": "Sélectionnez une reprise.", "rc.real": "Reprise réelle", "rc.planned": "Reprise planifiée.", "rc.done": "Étape enregistrée.", "rc.base0": "Aucune sauvegarde complète vérifiée disponible.",
 "va.title": "Validation et réouverture", "va.intro": "La réouverture est ciblée, approuvée indépendamment et exige six validations réussies. Les chiffres affichés sont ceux qui ont été mesurés, jamais une promesse.",
 "va.pick": "Reprises en cours de validation", "va.none": "Aucune reprise n’est en cours de validation.", "va.items": "Éléments de validation", "va.record": "Enregistrer la validation", "va.item": "Élément", "va.result": "Résultat", "va.residual": "Risque résiduel",
 "va.reopen": "Rouvrir les services", "va.scope": "Services à rouvrir", "va.missing": "Non enregistré", "va.measure": "Durées de reprise mesurées", "va.rto": "Délai de réouverture", "va.rpo": "Fenêtre de données perdues", "va.noMeasure": "Rien n’a encore été mesuré.",
 "va.noGuarantee": "Ce sont des mesures de reprises précises, pas des garanties.", "va.recorded": "Validation enregistrée.", "va.reopened": "Services rouverts.", "va.minutes": "{n} min", "va.history": "Mesures",
 "tl.title": "Explorateur de télémétrie opérationnelle", "tl.intro": "Les journaux et traces sont masqués avant d’être stockés. Chaque recherche faite ici est elle-même enregistrée.",
 "tl.filters": "Recherche", "tl.correlation": "Identifiant de corrélation", "tl.level": "Niveau", "tl.search": "Rechercher", "tl.results": "Événements", "tl.none": "Aucun événement.", "tl.redactions": "{n} masquages", "tl.trace": "Trace", "tl.access": "Qui a cherché quoi",
 "tl.accessNone": "Aucune recherche enregistrée.", "tl.returned": "{n} renvoyés", "tl.attributes": "Attributs", "tl.message": "Message", "tl.event": "Événement"
};

export const dictionaries: Record<Lang, Record<Key, string>> = { en, fr };
export function format(template: string, values?: Record<string, string | number>): string {
 if (!values) return template;
 return template.replace(/\{(\w+)\}/g, (_, k: string) => (k in values ? String(values[k]) : `{${k}}`));
}
export function translate(lang: Lang, key: Key, values?: Record<string, string | number>): string {
 return format(dictionaries[lang][key] ?? dictionaries.en[key] ?? key, values);
}
export function detectLang(stored: string | null | undefined, navigatorLang: string | undefined): Lang {
 if (stored === "en" || stored === "fr") return stored;
 return navigatorLang?.toLowerCase().startsWith("fr") ? "fr" : "en";
}
export function errorKey(code: string): Key {
 const key = `err.${code}` as Key;
 return key in en ? key : "err.generic";
}
/** ui.tsx expects this name; validation failures list the item keys in plain words. */
export function safetyLabel(lang: Lang, code: string): string {
 for (const g of ["item", "check"]) {
  const key = `enum.${g}.${code}` as Key;
  if (key in en) return translate(lang, key);
 }
 return code;
}
export function enumLabel(lang: Lang, group: string, value: string | null | undefined): string {
 if (!value) return translate(lang, "common.none");
 const key = `enum.${group}.${value}` as Key;
 return key in en ? translate(lang, key) : value;
}
