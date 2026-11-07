// M38 English / French dictionary. Both languages must define exactly the same keys (tested).
export type Lang = "en" | "fr";

const en = {
 "app.title": "Offline synchronization and ownership", "app.subtitle": "M38 · Offline sync",
 "nav.label": "Offline synchronization workstations", "nav.status": "Synchronization status", "nav.queue": "Pending changes queue", "nav.conflicts": "Conflict review",
 "nav.transfers": "Ownership transfers", "nav.history": "Reconciliation history", "lang.label": "Language", "skip": "Skip to content",
 "hub.title": "Offline synchronization and ownership reconciliation",
 "hub.intro": "What was done offline, what reached the authoritative record, and what needs a person to decide. Nothing clinical is overwritten silently.", "hub.open": "Open",
 "common.refresh": "Refresh", "common.retry": "Try again", "common.reload": "Reload latest version", "common.cancel": "Cancel",
 "common.previous": "Previous page", "common.next": "Next page", "common.page": "Page {page} of {pages}", "common.total": "{total} records",
 "common.none": "None", "common.any": "Any", "common.version": "Version", "common.status": "Status", "common.actions": "Actions", "common.details": "Details",
 "common.reason": "Reason", "common.note": "Note", "common.record": "Record", "common.device": "Device", "common.operation": "Operation", "common.at": "Time", "common.close": "Close",
 "state.loading": "Loading records…", "state.empty": "No records match the current filters.", "state.emptyHint": "Change the filters or refresh once new records arrive.",
 "state.error": "The service could not be reached or returned an error.", "state.errorHint": "No data is shown as current. Check the connection, then try again.",
 "state.offline": "You are offline. Displayed data may be out of date and actions are disabled until the connection returns.",
 "state.unauthorized": "Sign in is required.", "state.unauthorizedHint": "Your session is missing or has expired. Sign in with your hospital identity to continue.",
 "state.signIn": "Sign in with hospital identity", "state.forbidden": "You are not authorized for this action or record.",
 "state.forbiddenHint": "Your role does not include this permission. Ask your supervisor if you need access.",
 "state.notFound": "This record does not exist or is outside your hospital and facility.", "state.notConfigured": "The offline synchronization service is not configured for this environment.",
 "disabled.status": "Not available in the current status ({status}).", "disabled.select": "Select a record first.", "disabled.offline": "Unavailable while offline.", "disabled.input": "Complete the required fields first.",
 "err.generic": "The action was refused ({code}).", "err.permission_denied": "You do not have permission for this action.",
 "err.approver_must_be_independent": "A different person must approve this. You cannot approve what you proposed.",
 "err.resolver_must_be_independent": "A different person must resolve this conflict. You cannot resolve a change you authored.",
 "err.stale_version": "The record changed. Reload it and try again.", "err.idempotency_collision": "This submission was already used with different content. Review and submit again.",
 "err.invalid_transition": "This action is not allowed in the record's current status.", "err.duplicate_record": "A matching request is already open for this record.",
 "err.reason_required": "A reason (and a note when the reason is Other) is required.", "err.record_frozen": "This record is frozen for an ownership transfer. Try again after it finishes.",
 "err.epoch_changed": "Ownership changed since this was detected. Re-check the record before deciding.", "err.head_moved": "The authoritative record changed since you reviewed it. Reload and review again.",
 "err.fence_version_mismatch": "The confirmed last version does not match the authoritative record. That is not valid fencing evidence.", "err.evidence_required": "Fencing evidence is required.",
 "err.already_owner": "That cell already owns this record.", "err.invalid_transfer": "Choose a record, a destination and give a reason.", "err.invalid_resolution": "Choose a valid resolution.",
 "err.device_revoked": "This device was revoked.", "err.invalid_pagination": "Invalid page request.", "err.invalid_status": "Unknown status filter.",
 "err.malformed_request": "The request was not understood.", "err.offline_unavailable": "The offline synchronization service is temporarily unavailable.",
 "enum.cmd.APPLIED": "Applied", "enum.cmd.CONFLICT": "Needs review", "enum.cmd.REJECTED": "Rejected", "enum.cmd.EXPIRED": "Expired", "enum.cmd.RESOLVED": "Resolved",
 "enum.conflict.OPEN": "Open", "enum.conflict.RESOLVED": "Resolved",
 "enum.kind.STALE_BASE": "Record changed since the device last saw it", "enum.kind.INCOMPATIBLE": "Overlapping changes to the same fields", "enum.kind.OWNER_FENCED": "Written under an outdated owner (fenced)",
 "enum.res.ACCEPT_LOCAL": "Accept the offline change", "enum.res.KEEP_AUTHORITATIVE": "Keep the authoritative record",
 "enum.reason.CLINICAL_REVIEW": "Clinical review", "enum.reason.DUPLICATE_ENTRY": "Duplicate entry", "enum.reason.SUPERSEDED_BY_NEWER": "Superseded by newer information", "enum.reason.DATA_ERROR": "Data error", "enum.reason.OTHER": "Other (note required)",
 "enum.device.ACTIVE": "Active", "enum.device.REVOKED": "Revoked",
 "enum.transfer.PROPOSED": "Proposed", "enum.transfer.FROZEN": "Frozen", "enum.transfer.FENCED": "Fenced", "enum.transfer.APPROVED": "Approved", "enum.transfer.COMPLETED": "Completed", "enum.transfer.ABORTED": "Aborted",
 "enum.outcome.APPLIED": "Applied", "enum.outcome.APPLIED_MERGED": "Applied (merged)", "enum.outcome.REJECTED": "Rejected", "enum.outcome.EXPIRED": "Expired",
 "enum.outcome.RESOLVED_LOCAL": "Resolved: offline change accepted", "enum.outcome.RESOLVED_AUTHORITATIVE": "Resolved: authoritative kept", "enum.outcome.OWNERSHIP_TRANSFERRED": "Ownership transferred",
 "code.applied": "Applied", "code.applied_merged": "Applied after safe merge", "code.stale_base": "Stale base version", "code.incompatible_changes": "Incompatible changes", "code.stale_writer_fenced": "Outdated owner fenced",
 "code.command_expired": "Command expired", "code.queue_age_exceeded": "Queued too long", "code.clock_skew": "Device clock ahead of server", "code.no_offline_contract": "No approved offline contract",
 "code.safety_gate_required": "Needs an online safety gate", "code.operation_not_offline": "Not allowed offline", "code.unknown_record": "Unknown record", "code.invalid_epoch": "Unknown ownership epoch",
 "code.invalid_base": "Base version from the future", "code.resolved_local": "Offline change accepted", "code.resolved_authoritative": "Authoritative record kept",
 // S01
 "st.title": "Offline synchronization status", "st.intro": "Which devices are registered, when they last synchronized, and what is waiting for review.",
 "st.openConflicts": "Open conflicts", "st.frozen": "Frozen records", "st.transfers": "Open ownership transfers", "st.devices": "Devices", "st.noDevices": "No devices registered.",
 "st.lastSeen": "Last synchronized", "st.never": "Never", "st.cursor": "Cursor", "st.received": "Commands received", "st.session": "Session expires", "st.stale": "No recent sync", "st.sessionExpired": "Session expired",
 "st.revoke": "Revoke device", "st.revokeReason": "Reason for revoking", "st.revoked": "Device revoked.", "st.localNote": "Offline availability on a device is decided by approved operation contracts, not by this screen.",
 "st.bound": "Bound user",
 // S02
 "q.title": "Pending changes queue", "q.intro": "Every change received from a device, with its idempotency key, base version and outcome.", "q.filterStatus": "Status", "q.filterDevice": "Device",
 "q.key": "Idempotency key", "q.base": "Base version", "q.epoch": "Base epoch", "q.applied": "Applied version", "q.created": "Created on device", "q.expires": "Expires", "q.received": "Received", "q.payload": "Content as sent",
 "q.fields": "Fields", "q.select": "Select a change to read its detail.", "q.outcome": "Outcome",
 // S03
 "cf.title": "Synchronization conflict review", "cf.intro": "A person decides, with a reason. The offline version is kept either way and no clinical record is overwritten silently.",
 "cf.list": "Conflicts", "cf.none": "No conflicts.", "cf.pick": "Select a conflict to review.", "cf.local": "Offline change", "cf.authoritative": "Authoritative record",
 "cf.baseSeen": "Device saw version", "cf.headNow": "Authoritative version now", "cf.epoch": "Ownership epoch", "cf.differing": "Fields changed meanwhile", "cf.author": "Authored by",
 "cf.resolution": "Decision", "cf.reasonCode": "Reason", "cf.note": "Note", "cf.resolve": "Record decision", "cf.resolved": "Decision recorded.", "cf.decidedBy": "Decided by", "cf.finalVersion": "Resulting version",
 "cf.acceptWarn": "Accepting writes the offline change as the next authoritative version. Confirm you have reviewed version {v}.",
 // S04
 "tr.title": "Ownership transfer review", "tr.intro": "Move a record to another cell only after freezing writers, confirming the last version, and an independent approval.",
 "tr.propose": "Propose a transfer", "tr.record": "Record", "tr.pickRecord": "Choose a record", "tr.destination": "Destination cell id", "tr.reason": "Reason for the transfer", "tr.proposeAction": "Propose transfer",
 "tr.list": "Transfers", "tr.none": "No transfers.", "tr.from": "From", "tr.to": "To", "tr.epoch": "Epoch", "tr.freeze": "Freeze writers", "tr.fence": "Record fencing evidence", "tr.approve": "Approve", "tr.rejectAction": "Reject",
 "tr.complete": "Complete transfer", "tr.abort": "Abort transfer", "tr.evidence": "Evidence reference", "tr.lastVersion": "Confirmed last version", "tr.abortReason": "Reason", "tr.proposed": "Transfer proposed.",
 "tr.step.PROPOSED": "Freeze writers next", "tr.step.FROZEN": "Waiting for fencing evidence", "tr.step.FENCED": "Waiting for independent approval", "tr.step.APPROVED": "Ready to complete", "tr.step.COMPLETED": "Ownership moved", "tr.step.ABORTED": "Stopped",
 "tr.frozenNow": "Writers frozen", "tr.noHeads": "No records are registered.", "tr.completedEpoch": "New epoch",
 // S05
 "hs.title": "Reconciliation history", "hs.intro": "What was applied, rejected, resolved or transferred, who reviewed it and where the record ended.", "hs.filter": "Outcome", "hs.none": "No history yet.",
 "hs.reviewer": "Reviewer", "hs.automatic": "Automatic", "hs.final": "Final version", "hs.finalEpoch": "Epoch", "hs.evidence": "Evidence",
 // This device (packages 04, 14-16, 20-22)
 "nav.device": "This device",
 "dv.title": "This device", "dv.intro": "The offline queue on this device: what is waiting, what the server decided, and the controls to sync, lock or wipe. Clinical actions are never decided here; the server is the authority.",
 "dv.session": "Signed-in session", "dv.noSession": "No valid session. Sign in to use this device.", "dv.scope": "Hospital scope", "dv.user": "User", "dv.expires": "Session ends",
 "dv.binding": "Device binding", "dv.deviceId": "Device id (registered by an administrator)", "dv.verify": "Verify with the server and bind", "dv.bound": "Bound to this user and hospital scope.", "dv.notBound": "This browser is not bound to a registered device.",
 "dv.forget": "Forget binding", "dv.unlock": "Unlock local storage", "dv.locked": "Local storage is locked.", "dv.unlocked": "Local storage is unlocked.", "dv.suspended": "Synchronization is suspended: {reason}",
 "dv.queue": "Local queue", "dv.entries": "Entries", "dv.empty": "Nothing queued on this device.", "dv.operation": "Operation", "dv.syncNow": "Sync now", "dv.refresh": "Refresh approved operations", "dv.refreshed": "Approved operations and record versions refreshed.",
 "dv.lock": "Lock device", "dv.wipe": "Wipe device (destroys the key)", "dv.wipeWarn": "Unsent changes are lost and cannot be recovered.", "dv.export": "Export queue metadata (no clinical content)", "dv.exported": "Metadata exported.",
 "dv.report": "Last sync: sent {sent}, applied {applied}, duplicate {duplicate}, conflicts {conflicted}, denied {denied}, expired {expired}, requeued {requeued}.", "dv.offline": "Offline: nothing is lost, sync resumes when the connection returns.",
 "dv.retry": "Will retry in {s} s.", "dv.wiped": "Device wiped.", "dv.locknote": "Locked.",
 "enum.local.QUEUED": "Waiting to send", "enum.local.SUBMITTED": "Sent, awaiting answer", "enum.local.APPLIED": "Applied", "enum.local.DUPLICATE": "Already applied (duplicate)", "enum.local.CONFLICTED": "Conflict: needs review",
 "enum.local.DENIED": "Denied by the server", "enum.local.EXPIRED": "Expired",
 "err.device_not_found": "The server does not know this device in your hospital scope.", "err.device_not_active": "This device is not active (revoked devices cannot be used).", "err.storage_unavailable": "This browser cannot store the binding.",
 "err.session_expired": "Your session ended. Sign in again; queued changes are kept.", "err.store_locked": "Local storage is locked.", "err.cross_scope_denied": "This session belongs to a different hospital scope than the device.",
 "err.retry_budget_spent": "Too many failed attempts; synchronization stopped. Check the connection, then resume.", "err.protocol_error": "The server answered in an unexpected way; synchronization stopped.", "err.device_user_mismatch": "This device is bound to a different user.",
 "err.no_session": "No valid session.", "err.contracts_not_loaded": "Approved operations have not been loaded yet.", "err.session_not_valid": "Sign in again before resuming."
} as const;

export type Key = keyof typeof en;

const fr: Record<Key, string> = {
 "app.title": "Synchronisation hors ligne et propriété", "app.subtitle": "M38 · Synchro hors ligne",
 "nav.label": "Postes de synchronisation hors ligne", "nav.status": "État de la synchronisation", "nav.queue": "File des changements en attente", "nav.conflicts": "Revue des conflits",
 "nav.transfers": "Transferts de propriété", "nav.history": "Historique de réconciliation", "lang.label": "Langue", "skip": "Passer au contenu",
 "hub.title": "Synchronisation hors ligne et réconciliation de propriété",
 "hub.intro": "Ce qui a été fait hors ligne, ce qui a atteint le dossier faisant autorité et ce qui exige la décision d’une personne. Rien de clinique n’est écrasé en silence.", "hub.open": "Ouvrir",
 "common.refresh": "Actualiser", "common.retry": "Réessayer", "common.reload": "Recharger la dernière version", "common.cancel": "Annuler",
 "common.previous": "Page précédente", "common.next": "Page suivante", "common.page": "Page {page} sur {pages}", "common.total": "{total} enregistrements",
 "common.none": "Aucun", "common.any": "Tous", "common.version": "Version", "common.status": "État", "common.actions": "Actions", "common.details": "Détails",
 "common.reason": "Motif", "common.note": "Note", "common.record": "Dossier", "common.device": "Appareil", "common.operation": "Opération", "common.at": "Heure", "common.close": "Fermer",
 "state.loading": "Chargement des enregistrements…", "state.empty": "Aucun enregistrement ne correspond aux filtres.", "state.emptyHint": "Modifiez les filtres ou actualisez à l’arrivée de nouveaux enregistrements.",
 "state.error": "Le service est injoignable ou a renvoyé une erreur.", "state.errorHint": "Aucune donnée n’est présentée comme à jour. Vérifiez la connexion puis réessayez.",
 "state.offline": "Vous êtes hors ligne. Les données affichées peuvent être périmées et les actions sont désactivées jusqu’au retour de la connexion.",
 "state.unauthorized": "Une connexion est requise.", "state.unauthorizedHint": "Votre session est absente ou expirée. Connectez-vous avec votre identité hospitalière pour continuer.",
 "state.signIn": "Se connecter avec l’identité hospitalière", "state.forbidden": "Vous n’êtes pas autorisé pour cette action ou cet enregistrement.",
 "state.forbiddenHint": "Votre rôle n’inclut pas cette autorisation. Demandez l’accès à votre responsable.",
 "state.notFound": "Cet enregistrement n’existe pas ou est hors de votre hôpital et de votre établissement.", "state.notConfigured": "Le service de synchronisation hors ligne n’est pas configuré pour cet environnement.",
 "disabled.status": "Indisponible dans l’état actuel ({status}).", "disabled.select": "Sélectionnez d’abord un enregistrement.", "disabled.offline": "Indisponible hors ligne.", "disabled.input": "Renseignez d’abord les champs obligatoires.",
 "err.generic": "L’action a été refusée ({code}).", "err.permission_denied": "Vous n’avez pas l’autorisation pour cette action.",
 "err.approver_must_be_independent": "Une autre personne doit approuver. Vous ne pouvez pas approuver ce que vous avez proposé.",
 "err.resolver_must_be_independent": "Une autre personne doit résoudre ce conflit. Vous ne pouvez pas résoudre un changement dont vous êtes l’auteur.",
 "err.stale_version": "L’enregistrement a changé. Rechargez-le et réessayez.", "err.idempotency_collision": "Cet envoi a déjà été utilisé avec un contenu différent. Vérifiez puis renvoyez.",
 "err.invalid_transition": "Cette action n’est pas permise dans l’état actuel de l’enregistrement.", "err.duplicate_record": "Une demande identique est déjà ouverte pour ce dossier.",
 "err.reason_required": "Un motif (et une note lorsque le motif est Autre) est requis.", "err.record_frozen": "Ce dossier est gelé pour un transfert de propriété. Réessayez après la fin du transfert.",
 "err.epoch_changed": "La propriété a changé depuis la détection. Vérifiez à nouveau le dossier avant de décider.", "err.head_moved": "Le dossier faisant autorité a changé depuis votre revue. Rechargez et relisez.",
 "err.fence_version_mismatch": "La dernière version confirmée ne correspond pas au dossier faisant autorité. Ce n’est pas une preuve de blocage valide.", "err.evidence_required": "Une preuve de blocage est requise.",
 "err.already_owner": "Cette cellule possède déjà ce dossier.", "err.invalid_transfer": "Choisissez un dossier, une destination et indiquez un motif.", "err.invalid_resolution": "Choisissez une décision valide.",
 "err.device_revoked": "Cet appareil a été révoqué.", "err.invalid_pagination": "Demande de page invalide.", "err.invalid_status": "Filtre d’état inconnu.",
 "err.malformed_request": "La requête n’a pas été comprise.", "err.offline_unavailable": "Le service de synchronisation hors ligne est temporairement indisponible.",
 "enum.cmd.APPLIED": "Appliqué", "enum.cmd.CONFLICT": "À examiner", "enum.cmd.REJECTED": "Refusé", "enum.cmd.EXPIRED": "Expiré", "enum.cmd.RESOLVED": "Résolu",
 "enum.conflict.OPEN": "Ouvert", "enum.conflict.RESOLVED": "Résolu",
 "enum.kind.STALE_BASE": "Dossier modifié depuis la dernière lecture de l’appareil", "enum.kind.INCOMPATIBLE": "Modifications qui se chevauchent sur les mêmes champs", "enum.kind.OWNER_FENCED": "Écrit sous un propriétaire périmé (bloqué)",
 "enum.res.ACCEPT_LOCAL": "Accepter le changement hors ligne", "enum.res.KEEP_AUTHORITATIVE": "Conserver le dossier faisant autorité",
 "enum.reason.CLINICAL_REVIEW": "Revue clinique", "enum.reason.DUPLICATE_ENTRY": "Saisie en double", "enum.reason.SUPERSEDED_BY_NEWER": "Remplacé par une information plus récente", "enum.reason.DATA_ERROR": "Erreur de données", "enum.reason.OTHER": "Autre (note requise)",
 "enum.device.ACTIVE": "Actif", "enum.device.REVOKED": "Révoqué",
 "enum.transfer.PROPOSED": "Proposé", "enum.transfer.FROZEN": "Gelé", "enum.transfer.FENCED": "Bloqué", "enum.transfer.APPROVED": "Approuvé", "enum.transfer.COMPLETED": "Terminé", "enum.transfer.ABORTED": "Abandonné",
 "enum.outcome.APPLIED": "Appliqué", "enum.outcome.APPLIED_MERGED": "Appliqué (fusionné)", "enum.outcome.REJECTED": "Refusé", "enum.outcome.EXPIRED": "Expiré",
 "enum.outcome.RESOLVED_LOCAL": "Résolu : changement hors ligne accepté", "enum.outcome.RESOLVED_AUTHORITATIVE": "Résolu : dossier faisant autorité conservé", "enum.outcome.OWNERSHIP_TRANSFERRED": "Propriété transférée",
 "code.applied": "Appliqué", "code.applied_merged": "Appliqué après fusion sûre", "code.stale_base": "Version de base périmée", "code.incompatible_changes": "Modifications incompatibles", "code.stale_writer_fenced": "Ancien propriétaire bloqué",
 "code.command_expired": "Commande expirée", "code.queue_age_exceeded": "Resté trop longtemps en file", "code.clock_skew": "Horloge de l’appareil en avance sur le serveur", "code.no_offline_contract": "Aucun contrat hors ligne approuvé",
 "code.safety_gate_required": "Exige un contrôle de sécurité en ligne", "code.operation_not_offline": "Non permis hors ligne", "code.unknown_record": "Dossier inconnu", "code.invalid_epoch": "Époque de propriété inconnue",
 "code.invalid_base": "Version de base venue du futur", "code.resolved_local": "Changement hors ligne accepté", "code.resolved_authoritative": "Dossier faisant autorité conservé",
 "st.title": "État de la synchronisation hors ligne", "st.intro": "Quels appareils sont enregistrés, quand ils ont synchronisé pour la dernière fois et ce qui attend une revue.",
 "st.openConflicts": "Conflits ouverts", "st.frozen": "Dossiers gelés", "st.transfers": "Transferts de propriété ouverts", "st.devices": "Appareils", "st.noDevices": "Aucun appareil enregistré.",
 "st.lastSeen": "Dernière synchronisation", "st.never": "Jamais", "st.cursor": "Curseur", "st.received": "Commandes reçues", "st.session": "Fin de session", "st.stale": "Aucune synchro récente", "st.sessionExpired": "Session expirée",
 "st.revoke": "Révoquer l’appareil", "st.revokeReason": "Motif de la révocation", "st.revoked": "Appareil révoqué.", "st.localNote": "La disponibilité hors ligne d’un appareil dépend de contrats d’opération approuvés, pas de cet écran.",
 "st.bound": "Utilisateur lié",
 "q.title": "File des changements en attente", "q.intro": "Chaque changement reçu d’un appareil, avec sa clé d’idempotence, sa version de base et son résultat.", "q.filterStatus": "État", "q.filterDevice": "Appareil",
 "q.key": "Clé d’idempotence", "q.base": "Version de base", "q.epoch": "Époque de base", "q.applied": "Version appliquée", "q.created": "Créé sur l’appareil", "q.expires": "Expire", "q.received": "Reçu", "q.payload": "Contenu tel qu’envoyé",
 "q.fields": "Champs", "q.select": "Sélectionnez un changement pour lire son détail.", "q.outcome": "Résultat",
 "cf.title": "Revue des conflits de synchronisation", "cf.intro": "Une personne décide, avec un motif. La version hors ligne est conservée dans tous les cas et aucun dossier clinique n’est écrasé en silence.",
 "cf.list": "Conflits", "cf.none": "Aucun conflit.", "cf.pick": "Sélectionnez un conflit à examiner.", "cf.local": "Changement hors ligne", "cf.authoritative": "Dossier faisant autorité",
 "cf.baseSeen": "L’appareil avait vu la version", "cf.headNow": "Version faisant autorité actuelle", "cf.epoch": "Époque de propriété", "cf.differing": "Champs modifiés entre-temps", "cf.author": "Auteur",
 "cf.resolution": "Décision", "cf.reasonCode": "Motif", "cf.note": "Note", "cf.resolve": "Enregistrer la décision", "cf.resolved": "Décision enregistrée.", "cf.decidedBy": "Décidé par", "cf.finalVersion": "Version résultante",
 "cf.acceptWarn": "Accepter inscrit le changement hors ligne comme prochaine version faisant autorité. Confirmez avoir examiné la version {v}.",
 "tr.title": "Revue des transferts de propriété", "tr.intro": "Ne déplacez un dossier vers une autre cellule qu’après gel des écritures, confirmation de la dernière version et approbation indépendante.",
 "tr.propose": "Proposer un transfert", "tr.record": "Dossier", "tr.pickRecord": "Choisissez un dossier", "tr.destination": "Identifiant de la cellule de destination", "tr.reason": "Motif du transfert", "tr.proposeAction": "Proposer le transfert",
 "tr.list": "Transferts", "tr.none": "Aucun transfert.", "tr.from": "De", "tr.to": "Vers", "tr.epoch": "Époque", "tr.freeze": "Geler les écritures", "tr.fence": "Enregistrer la preuve de blocage", "tr.approve": "Approuver", "tr.rejectAction": "Refuser",
 "tr.complete": "Terminer le transfert", "tr.abort": "Abandonner le transfert", "tr.evidence": "Référence de la preuve", "tr.lastVersion": "Dernière version confirmée", "tr.abortReason": "Motif", "tr.proposed": "Transfert proposé.",
 "tr.step.PROPOSED": "Geler les écritures ensuite", "tr.step.FROZEN": "En attente de la preuve de blocage", "tr.step.FENCED": "En attente d’approbation indépendante", "tr.step.APPROVED": "Prêt à terminer", "tr.step.COMPLETED": "Propriété déplacée", "tr.step.ABORTED": "Arrêté",
 "tr.frozenNow": "Écritures gelées", "tr.noHeads": "Aucun dossier n’est enregistré.", "tr.completedEpoch": "Nouvelle époque",
 "hs.title": "Historique de réconciliation", "hs.intro": "Ce qui a été appliqué, refusé, résolu ou transféré, qui l’a examiné et où le dossier s’est arrêté.", "hs.filter": "Résultat", "hs.none": "Aucun historique pour le moment.",
 "hs.reviewer": "Réviseur", "hs.automatic": "Automatique", "hs.final": "Version finale", "hs.finalEpoch": "Époque", "hs.evidence": "Preuve",
 "nav.device": "Cet appareil",
 "dv.title": "Cet appareil", "dv.intro": "La file hors ligne de cet appareil : ce qui attend, ce que le serveur a décidé et les commandes pour synchroniser, verrouiller ou effacer. Aucune action clinique n’est décidée ici ; le serveur fait autorité.",
 "dv.session": "Session ouverte", "dv.noSession": "Aucune session valide. Connectez-vous pour utiliser cet appareil.", "dv.scope": "Périmètre hospitalier", "dv.user": "Utilisateur", "dv.expires": "Fin de session",
 "dv.binding": "Liaison de l’appareil", "dv.deviceId": "Identifiant de l’appareil (enregistré par un administrateur)", "dv.verify": "Vérifier auprès du serveur et lier", "dv.bound": "Lié à cet utilisateur et à ce périmètre hospitalier.", "dv.notBound": "Ce navigateur n’est lié à aucun appareil enregistré.",
 "dv.forget": "Oublier la liaison", "dv.unlock": "Déverrouiller le stockage local", "dv.locked": "Le stockage local est verrouillé.", "dv.unlocked": "Le stockage local est déverrouillé.", "dv.suspended": "La synchronisation est suspendue : {reason}",
 "dv.queue": "File locale", "dv.entries": "Entrées", "dv.empty": "Rien en file sur cet appareil.", "dv.operation": "Opération", "dv.syncNow": "Synchroniser maintenant", "dv.refresh": "Actualiser les opérations approuvées", "dv.refreshed": "Opérations approuvées et versions des dossiers actualisées.",
 "dv.lock": "Verrouiller l’appareil", "dv.wipe": "Effacer l’appareil (détruit la clé)", "dv.wipeWarn": "Les changements non envoyés sont perdus et irrécupérables.", "dv.export": "Exporter les métadonnées de la file (sans contenu clinique)", "dv.exported": "Métadonnées exportées.",
 "dv.report": "Dernière synchro : envoyés {sent}, appliqués {applied}, doublons {duplicate}, conflits {conflicted}, refusés {denied}, expirés {expired}, remis en file {requeued}.", "dv.offline": "Hors ligne : rien n’est perdu, la synchro reprend au retour de la connexion.",
 "dv.retry": "Nouvel essai dans {s} s.", "dv.wiped": "Appareil effacé.", "dv.locknote": "Verrouillé.",
 "enum.local.QUEUED": "En attente d’envoi", "enum.local.SUBMITTED": "Envoyé, en attente de réponse", "enum.local.APPLIED": "Appliqué", "enum.local.DUPLICATE": "Déjà appliqué (doublon)", "enum.local.CONFLICTED": "Conflit : à examiner",
 "enum.local.DENIED": "Refusé par le serveur", "enum.local.EXPIRED": "Expiré",
 "err.device_not_found": "Le serveur ne connaît pas cet appareil dans votre périmètre hospitalier.", "err.device_not_active": "Cet appareil n’est pas actif (un appareil révoqué est inutilisable).", "err.storage_unavailable": "Ce navigateur ne peut pas enregistrer la liaison.",
 "err.session_expired": "Votre session est terminée. Reconnectez-vous ; les changements en file sont conservés.", "err.store_locked": "Le stockage local est verrouillé.", "err.cross_scope_denied": "Cette session appartient à un autre périmètre hospitalier que l’appareil.",
 "err.retry_budget_spent": "Trop d’échecs ; la synchronisation est arrêtée. Vérifiez la connexion puis reprenez.", "err.protocol_error": "Le serveur a répondu de façon inattendue ; la synchronisation est arrêtée.", "err.device_user_mismatch": "Cet appareil est lié à un autre utilisateur.",
 "err.no_session": "Aucune session valide.", "err.contracts_not_loaded": "Les opérations approuvées ne sont pas encore chargées.", "err.session_not_valid": "Reconnectez-vous avant de reprendre."
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
/** ui.tsx expects this name; here it labels machine codes in plain words. */
export function safetyLabel(lang: Lang, code: string): string {
 const key = `code.${code}` as Key;
 return key in en ? translate(lang, key) : code;
}
export function enumLabel(lang: Lang, group: string, value: string | null | undefined): string {
 if (!value) return translate(lang, "common.none");
 const key = `enum.${group}.${value}` as Key;
 return key in en ? translate(lang, key) : value;
}
