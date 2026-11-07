// M39 English / French dictionary. Both languages must define exactly the same keys (tested).
export type Lang = "en" | "fr";

const en = {
 "app.title": "Tenant and hospital cell lifecycle", "app.subtitle": "M39 · Fleet",
 "nav.label": "Fleet management workstations", "nav.fleet": "Tenant and cell fleet", "nav.provisioning": "Tenant provisioning", "nav.qualification": "Cell qualification",
 "nav.releases": "Fleet release rollout", "nav.migration": "Cell migration and routing", "lang.label": "Language", "skip": "Skip to content",
 "hub.title": "Tenant and hospital cell lifecycle", "hub.intro": "The management plane for hospital tenants and cells. It never holds clinical data; production changes are made by approved executors, and this console records and governs them.", "hub.open": "Open",
 "common.refresh": "Refresh", "common.retry": "Try again", "common.reload": "Reload latest version", "common.cancel": "Cancel",
 "common.previous": "Previous page", "common.next": "Next page", "common.page": "Page {page} of {pages}", "common.total": "{total} records",
 "common.none": "None", "common.any": "Any", "common.version": "Version", "common.status": "Status", "common.actions": "Actions", "common.details": "Details",
 "common.reason": "Reason", "common.region": "Region", "common.tier": "Tier", "common.name": "Name", "common.select": "Select",
 "state.loading": "Loading records…", "state.empty": "No records match the current filters.", "state.emptyHint": "Change the filters or refresh once new records arrive.",
 "state.error": "The service could not be reached or returned an error.", "state.errorHint": "No data is shown as current. Check the connection, then try again.",
 "state.offline": "You are offline. Displayed data may be out of date and actions are disabled until the connection returns.",
 "state.unauthorized": "Sign in is required.", "state.unauthorizedHint": "Your session is missing or has expired. Sign in with your hospital identity to continue.",
 "state.signIn": "Sign in with hospital identity", "state.forbidden": "You are not authorized for this action or record.",
 "state.forbiddenHint": "Your role does not include this permission. Ask your supervisor if you need access.",
 "state.notFound": "This record does not exist or is outside your tenant scope.", "state.notConfigured": "The fleet service is not configured for this environment.",
 "disabled.status": "Not available in the current status ({status}).", "disabled.select": "Select a record first.", "disabled.offline": "Unavailable while offline.", "disabled.input": "Complete the required fields first.",
 "err.generic": "The action was refused ({code}).", "err.permission_denied": "You do not have permission for this action.", "err.tenant_scope_denied": "This action is outside your tenant scope.",
 "err.approver_must_be_independent": "A different person must approve this. You cannot approve what you requested.", "err.completer_must_be_independent": "A different person must complete the qualification.",
 "err.stale_version": "The record changed. Reload it and try again.", "err.idempotency_collision": "This submission was already used with different content. Review and submit again.",
 "err.invalid_transition": "This action is not allowed in the record's current status.", "err.duplicate_record": "A matching request already exists.",
 "err.reason_required": "A reason of at least five characters is required.", "err.not_qualified": "The cell has not passed qualification.", "err.qualification_incomplete": "Not every qualification check has been recorded.",
 "err.stale_placement_epoch": "The placement epoch changed. Reload and review before switching.", "err.incompatible_release": "The release is not compatible with the schema of at least one chosen cell.",
 "err.evidence_mismatch": "The reported artifact does not match the approved release. It was not recorded as deployed.", "err.verification_failed": "Source and target digests differ. Nothing was switched.",
 "err.irreversible": "This step can no longer be reversed.", "err.rollout_stopped": "The rollout is stopped or finished.", "err.stage_not_open": "That cell belongs to a later stage.",
 "err.stage_incomplete": "Every cell of the current stage must be deployed first.", "err.health_gate_failed": "Healthy observations after deployment are required first.",
 "err.migration_in_progress": "A migration is in progress for this cell.", "err.residency_violation": "The target region breaks the tenant's residency policy.",
 "err.route_unavailable": "The target cell is not routable (not active or not healthy).", "err.route_in_migration": "This route is being migrated.", "err.lag_too_high": "Replication lag is too high to continue.",
 "err.evidence_required": "Evidence is required.", "err.invalid_provisioning": "The provisioning request is not valid.", "err.invalid_check": "The check result is not valid.", "err.invalid_step": "Unknown migration step.",
 "err.invalid_pagination": "Invalid page request.", "err.malformed_request": "The request was not understood.", "err.fleet_unavailable": "The fleet service is temporarily unavailable.",
 "enum.cell.PROVISIONING": "Provisioning", "enum.cell.QUALIFIED": "Qualified", "enum.cell.ACTIVE": "Active", "enum.cell.DRAINING": "Draining", "enum.cell.FENCED": "Fenced", "enum.cell.MIGRATING": "Migrating", "enum.cell.RETIRED": "Retired", "enum.cell.FAILED": "Failed",
 "enum.prov.REQUESTED": "Requested", "enum.prov.APPROVED": "Approved", "enum.prov.APPLIED": "Applied", "enum.prov.REJECTED": "Rejected", "enum.prov.FAILED": "Failed", "enum.prov.ROLLED_BACK": "Rolled back",
 "enum.health.HEALTHY": "Healthy", "enum.health.DEGRADED": "Degraded", "enum.health.UNHEALTHY": "Unhealthy", "enum.health.UNKNOWN": "No recent observation",
 "enum.qual.OPEN": "Open", "enum.qual.PASSED": "Passed", "enum.qual.FAILED": "Failed", "enum.result.PASS": "Pass", "enum.result.FAIL": "Fail",
 "enum.check.DATABASE": "Database", "enum.check.NETWORK": "Network", "enum.check.IDENTITY": "Identity", "enum.check.STORAGE": "Storage", "enum.check.CAPACITY": "Capacity", "enum.check.MONITORING": "Monitoring", "enum.check.BACKUP": "Backup", "enum.check.MIGRATION_COMPAT": "Migration compatibility",
 "enum.manifest.DRAFT": "Draft", "enum.manifest.APPROVED": "Approved", "enum.manifest.RETIRED": "Retired", "enum.manifest.REJECTED": "Rejected",
 "enum.rollout.CANARY": "Canary", "enum.rollout.PROGRESSING": "In progress", "enum.rollout.COMPLETED": "Completed", "enum.rollout.STOPPED": "Stopped",
 "enum.dep.PENDING": "Pending", "enum.dep.DEPLOYED": "Deployed", "enum.dep.FAILED": "Failed", "enum.dep.SKIPPED": "Skipped",
 "enum.mig.PREPARED": "Prepared", "enum.mig.COPYING": "Copying", "enum.mig.CAUGHT_UP": "Caught up", "enum.mig.FROZEN": "Frozen", "enum.mig.FENCED": "Fenced", "enum.mig.VERIFIED": "Verified", "enum.mig.SWITCHED": "Switched",
 "enum.mig.OBSERVING": "Observing", "enum.mig.RETIRED": "Source retired", "enum.mig.ROLLED_BACK": "Rolled back", "enum.mig.FAILED": "Failed",
 "enum.step.COPY": "Record copy", "enum.step.CATCH_UP": "Record catch-up", "enum.step.FREEZE": "Freeze writers", "enum.step.FENCE": "Record fencing", "enum.step.VERIFY": "Verify digests", "enum.step.SWITCH": "Switch route", "enum.step.OBSERVE": "Start observation", "enum.step.RETIRE": "Retire source",
 // S01
 "fl.title": "Tenant and cell fleet", "fl.intro": "Every tenant and hospital cell with its lifecycle state, placement epoch, release and latest health observation.",
 "fl.tenants": "Tenants", "fl.cells": "Cells", "fl.none": "No cells.", "fl.noTenants": "No tenants.", "fl.filterState": "Lifecycle state", "fl.epoch": "Placement epoch", "fl.release": "Release", "fl.health": "Health", "fl.environment": "Environment",
 "fl.activeCells": "Active cells", "fl.noRelease": "No release recorded", "fl.transition": "Change lifecycle state", "fl.to": "New state", "fl.transitionAction": "Apply state change", "fl.transitioned": "State changed.",
 "fl.stale": "Observation older than five minutes: not routable.", "fl.pick": "Select a cell to change its state.",
 // S02
 "pr.title": "Tenant provisioning", "pr.intro": "A declarative, idempotent request that a different person approves. This console never creates infrastructure; it records the executor's result.",
 "pr.request": "New provisioning request", "pr.requestKey": "Request key", "pr.tenantKey": "Tenant key", "pr.tenantName": "Hospital name", "pr.capacity": "Capacity units", "pr.services": "Service profile (comma separated)",
 "pr.isolation": "Storage isolation", "pr.residency": "Residency policy", "pr.rollbackNote": "Rollback note", "pr.submit": "Submit request", "pr.list": "Requests", "pr.none": "No requests.",
 "pr.approve": "Approve", "pr.reject": "Reject", "pr.apply": "Record executor result: OK", "pr.applyFail": "Record executor result: failed", "pr.failureCode": "Failure code", "pr.rollback": "Roll back (never served traffic)",
 "pr.submitted": "Request submitted.", "pr.DEDICATED_DATABASE": "Dedicated database", "pr.DEDICATED_CLUSTER": "Dedicated cluster", "pr.IN_REGION_ONLY": "In region only", "pr.IN_COUNTRY_ONLY": "In country only", "pr.SAME_CONTINENT": "Same continent",
 "pr.SMALL": "Small", "pr.STANDARD": "Standard", "pr.LARGE": "Large", "pr.pick": "Select a request.", "pr.rejectReason": "Reason (needed to reject or roll back)",
 // S03
 "ql.title": "Cell qualification", "ql.intro": "A cell cannot go live until all eight checks have passed with evidence and a different person completes the qualification.",
 "ql.cell": "Cell", "ql.pickCell": "Choose a cell", "ql.start": "Start qualification", "ql.checks": "Checks", "ql.record": "Record check", "ql.checkKey": "Check", "ql.result": "Result", "ql.evidence": "Evidence reference",
 "ql.detail": "Detail", "ql.complete": "Complete qualification", "ql.none": "No qualification yet.", "ql.missing": "Not recorded", "ql.started": "Qualification started.", "ql.recorded": "Check recorded.", "ql.completed": "Qualification completed.",
 "ql.history": "Qualifications of this cell",
 // S04
 "rl.title": "Fleet release rollout", "rl.intro": "Approved manifests, a compatibility gate, a canary stage, evidence of what was actually deployed and automatic stops.",
 "rl.manifests": "Release manifests", "rl.manifestNone": "No manifests.", "rl.key": "Release key", "rl.digest": "Artifact digest (SHA-256)", "rl.schemas": "Schema range", "rl.create": "Create manifest", "rl.decide": "Approve", "rl.rejectIt": "Reject",
 "rl.rollouts": "Rollouts", "rl.rolloutNone": "No rollouts.", "rl.start": "Start rollout", "rl.canary": "Canary cells (stage 1)", "rl.rest": "Remaining cells (stage 2)", "rl.stage": "Stage", "rl.advance": "Advance to next stage", "rl.stop": "Stop rollout",
 "rl.evidenceRef": "Deployment evidence reference", "rl.evidenceDigest": "Reported artifact digest", "rl.deployed": "Record deployed", "rl.failedDeploy": "Record failed deployment", "rl.stopReason": "Stop reason", "rl.created": "Manifest created.",
 "rl.started": "Rollout started.", "rl.autoStop": "Stopped:", "rl.pickRollout": "Select a rollout.", "rl.pickManifest": "Choose an approved manifest", "rl.cellsHelp": "Select cells for each stage.", "rl.currentStage": "Current stage",
 // S05
 "mg.title": "Cell migration and routing", "mg.intro": "Move a tenant between cells with evidence at every step. The switch needs an independent approver and the current epoch; rollback is limited once the source is retired.",
 "mg.routes": "Routes", "mg.routeNone": "No routes.", "mg.directory": "Directory version", "mg.routeKey": "Route", "mg.target": "Serving cell", "mg.plan": "Plan migration", "mg.source": "Source cell", "mg.targetCell": "Target cell",
 "mg.route": "Route", "mg.planAction": "Plan migration", "mg.list": "Migrations", "mg.none": "No migrations.", "mg.evidence": "Evidence reference", "mg.lag": "Replication lag (ms)", "mg.sourceDigest": "Source digest", "mg.targetDigest": "Target digest",
 "mg.epochSeen": "Route epoch you reviewed", "mg.next": "Next step", "mg.rollback": "Roll back", "mg.rollbackReason": "Reason for rollback", "mg.planned": "Migration planned.", "mg.irreversible": "Source retired: cannot be rolled back.",
 "mg.pick": "Select a migration.", "mg.switchNote": "The switch must be done by someone other than the planner.", "mg.done": "Step recorded.", "mg.moveRoute": "Failover route"
} as const;

export type Key = keyof typeof en;

const fr: Record<Key, string> = {
 "app.title": "Cycle de vie des tenants et des cellules hospitalières", "app.subtitle": "M39 · Flotte",
 "nav.label": "Postes de gestion de la flotte", "nav.fleet": "Flotte de tenants et de cellules", "nav.provisioning": "Provisionnement de tenant", "nav.qualification": "Qualification de cellule",
 "nav.releases": "Déploiement de versions", "nav.migration": "Migration et routage de cellule", "lang.label": "Langue", "skip": "Passer au contenu",
 "hub.title": "Cycle de vie des tenants et des cellules hospitalières", "hub.intro": "Le plan de gestion des tenants et cellules hospitalières. Il ne contient aucune donnée clinique ; les changements en production sont faits par des exécuteurs approuvés, et cette console les enregistre et les encadre.", "hub.open": "Ouvrir",
 "common.refresh": "Actualiser", "common.retry": "Réessayer", "common.reload": "Recharger la dernière version", "common.cancel": "Annuler",
 "common.previous": "Page précédente", "common.next": "Page suivante", "common.page": "Page {page} sur {pages}", "common.total": "{total} enregistrements",
 "common.none": "Aucun", "common.any": "Tous", "common.version": "Version", "common.status": "État", "common.actions": "Actions", "common.details": "Détails",
 "common.reason": "Motif", "common.region": "Région", "common.tier": "Niveau", "common.name": "Nom", "common.select": "Sélectionner",
 "state.loading": "Chargement des enregistrements…", "state.empty": "Aucun enregistrement ne correspond aux filtres.", "state.emptyHint": "Modifiez les filtres ou actualisez à l’arrivée de nouveaux enregistrements.",
 "state.error": "Le service est injoignable ou a renvoyé une erreur.", "state.errorHint": "Aucune donnée n’est présentée comme à jour. Vérifiez la connexion puis réessayez.",
 "state.offline": "Vous êtes hors ligne. Les données affichées peuvent être périmées et les actions sont désactivées jusqu’au retour de la connexion.",
 "state.unauthorized": "Une connexion est requise.", "state.unauthorizedHint": "Votre session est absente ou expirée. Connectez-vous avec votre identité hospitalière pour continuer.",
 "state.signIn": "Se connecter avec l’identité hospitalière", "state.forbidden": "Vous n’êtes pas autorisé pour cette action ou cet enregistrement.",
 "state.forbiddenHint": "Votre rôle n’inclut pas cette autorisation. Demandez l’accès à votre responsable.",
 "state.notFound": "Cet enregistrement n’existe pas ou est hors de votre périmètre de tenant.", "state.notConfigured": "Le service de flotte n’est pas configuré pour cet environnement.",
 "disabled.status": "Indisponible dans l’état actuel ({status}).", "disabled.select": "Sélectionnez d’abord un enregistrement.", "disabled.offline": "Indisponible hors ligne.", "disabled.input": "Renseignez d’abord les champs obligatoires.",
 "err.generic": "L’action a été refusée ({code}).", "err.permission_denied": "Vous n’avez pas l’autorisation pour cette action.", "err.tenant_scope_denied": "Cette action est hors de votre périmètre de tenant.",
 "err.approver_must_be_independent": "Une autre personne doit approuver. Vous ne pouvez pas approuver ce que vous avez demandé.", "err.completer_must_be_independent": "Une autre personne doit terminer la qualification.",
 "err.stale_version": "L’enregistrement a changé. Rechargez-le et réessayez.", "err.idempotency_collision": "Cet envoi a déjà été utilisé avec un contenu différent. Vérifiez puis renvoyez.",
 "err.invalid_transition": "Cette action n’est pas permise dans l’état actuel de l’enregistrement.", "err.duplicate_record": "Une demande correspondante existe déjà.",
 "err.reason_required": "Un motif d’au moins cinq caractères est requis.", "err.not_qualified": "La cellule n’a pas réussi sa qualification.", "err.qualification_incomplete": "Toutes les vérifications de qualification n’ont pas été enregistrées.",
 "err.stale_placement_epoch": "L’époque de placement a changé. Rechargez et vérifiez avant de basculer.", "err.incompatible_release": "La version n’est pas compatible avec le schéma d’au moins une cellule choisie.",
 "err.evidence_mismatch": "L’artefact signalé ne correspond pas à la version approuvée. Il n’a pas été enregistré comme déployé.", "err.verification_failed": "Les empreintes source et cible diffèrent. Rien n’a été basculé.",
 "err.irreversible": "Cette étape ne peut plus être annulée.", "err.rollout_stopped": "Le déploiement est arrêté ou terminé.", "err.stage_not_open": "Cette cellule appartient à une étape ultérieure.",
 "err.stage_incomplete": "Chaque cellule de l’étape en cours doit d’abord être déployée.", "err.health_gate_failed": "Des observations saines après le déploiement sont d’abord requises.",
 "err.migration_in_progress": "Une migration est en cours pour cette cellule.", "err.residency_violation": "La région cible enfreint la politique de résidence du tenant.",
 "err.route_unavailable": "La cellule cible n’est pas routable (inactive ou non saine).", "err.route_in_migration": "Cette route est en cours de migration.", "err.lag_too_high": "Le retard de réplication est trop élevé pour continuer.",
 "err.evidence_required": "Une preuve est requise.", "err.invalid_provisioning": "La demande de provisionnement n’est pas valide.", "err.invalid_check": "Le résultat de la vérification n’est pas valide.", "err.invalid_step": "Étape de migration inconnue.",
 "err.invalid_pagination": "Demande de page invalide.", "err.malformed_request": "La requête n’a pas été comprise.", "err.fleet_unavailable": "Le service de flotte est temporairement indisponible.",
 "enum.cell.PROVISIONING": "Provisionnement", "enum.cell.QUALIFIED": "Qualifiée", "enum.cell.ACTIVE": "Active", "enum.cell.DRAINING": "En drainage", "enum.cell.FENCED": "Isolée", "enum.cell.MIGRATING": "En migration", "enum.cell.RETIRED": "Retirée", "enum.cell.FAILED": "En échec",
 "enum.prov.REQUESTED": "Demandé", "enum.prov.APPROVED": "Approuvé", "enum.prov.APPLIED": "Appliqué", "enum.prov.REJECTED": "Refusé", "enum.prov.FAILED": "En échec", "enum.prov.ROLLED_BACK": "Annulé",
 "enum.health.HEALTHY": "Saine", "enum.health.DEGRADED": "Dégradée", "enum.health.UNHEALTHY": "Défaillante", "enum.health.UNKNOWN": "Aucune observation récente",
 "enum.qual.OPEN": "Ouverte", "enum.qual.PASSED": "Réussie", "enum.qual.FAILED": "Échouée", "enum.result.PASS": "Réussi", "enum.result.FAIL": "Échec",
 "enum.check.DATABASE": "Base de données", "enum.check.NETWORK": "Réseau", "enum.check.IDENTITY": "Identité", "enum.check.STORAGE": "Stockage", "enum.check.CAPACITY": "Capacité", "enum.check.MONITORING": "Supervision", "enum.check.BACKUP": "Sauvegarde", "enum.check.MIGRATION_COMPAT": "Compatibilité de migration",
 "enum.manifest.DRAFT": "Brouillon", "enum.manifest.APPROVED": "Approuvé", "enum.manifest.RETIRED": "Retiré", "enum.manifest.REJECTED": "Refusé",
 "enum.rollout.CANARY": "Canari", "enum.rollout.PROGRESSING": "En cours", "enum.rollout.COMPLETED": "Terminé", "enum.rollout.STOPPED": "Arrêté",
 "enum.dep.PENDING": "En attente", "enum.dep.DEPLOYED": "Déployé", "enum.dep.FAILED": "En échec", "enum.dep.SKIPPED": "Ignoré",
 "enum.mig.PREPARED": "Préparée", "enum.mig.COPYING": "Copie", "enum.mig.CAUGHT_UP": "Rattrapée", "enum.mig.FROZEN": "Gelée", "enum.mig.FENCED": "Isolée", "enum.mig.VERIFIED": "Vérifiée", "enum.mig.SWITCHED": "Basculée",
 "enum.mig.OBSERVING": "En observation", "enum.mig.RETIRED": "Source retirée", "enum.mig.ROLLED_BACK": "Annulée", "enum.mig.FAILED": "En échec",
 "enum.step.COPY": "Enregistrer la copie", "enum.step.CATCH_UP": "Enregistrer le rattrapage", "enum.step.FREEZE": "Geler les écritures", "enum.step.FENCE": "Enregistrer l’isolement", "enum.step.VERIFY": "Vérifier les empreintes", "enum.step.SWITCH": "Basculer la route", "enum.step.OBSERVE": "Démarrer l’observation", "enum.step.RETIRE": "Retirer la source",
 "fl.title": "Flotte de tenants et de cellules", "fl.intro": "Chaque tenant et cellule hospitalière avec son état de cycle de vie, son époque de placement, sa version et sa dernière observation de santé.",
 "fl.tenants": "Tenants", "fl.cells": "Cellules", "fl.none": "Aucune cellule.", "fl.noTenants": "Aucun tenant.", "fl.filterState": "État du cycle de vie", "fl.epoch": "Époque de placement", "fl.release": "Version", "fl.health": "Santé", "fl.environment": "Environnement",
 "fl.activeCells": "Cellules actives", "fl.noRelease": "Aucune version enregistrée", "fl.transition": "Changer l’état du cycle de vie", "fl.to": "Nouvel état", "fl.transitionAction": "Appliquer le changement d’état", "fl.transitioned": "État modifié.",
 "fl.stale": "Observation de plus de cinq minutes : non routable.", "fl.pick": "Sélectionnez une cellule pour changer son état.",
 "pr.title": "Provisionnement de tenant", "pr.intro": "Une demande déclarative et idempotente qu’une autre personne approuve. Cette console ne crée aucune infrastructure ; elle enregistre le résultat de l’exécuteur.",
 "pr.request": "Nouvelle demande de provisionnement", "pr.requestKey": "Clé de demande", "pr.tenantKey": "Clé du tenant", "pr.tenantName": "Nom de l’hôpital", "pr.capacity": "Unités de capacité", "pr.services": "Profil de services (séparés par des virgules)",
 "pr.isolation": "Isolation du stockage", "pr.residency": "Politique de résidence", "pr.rollbackNote": "Note de retour arrière", "pr.submit": "Envoyer la demande", "pr.list": "Demandes", "pr.none": "Aucune demande.",
 "pr.approve": "Approuver", "pr.reject": "Refuser", "pr.apply": "Enregistrer le résultat de l’exécuteur : OK", "pr.applyFail": "Enregistrer le résultat de l’exécuteur : échec", "pr.failureCode": "Code d’échec", "pr.rollback": "Annuler (n’a jamais servi de trafic)",
 "pr.submitted": "Demande envoyée.", "pr.DEDICATED_DATABASE": "Base de données dédiée", "pr.DEDICATED_CLUSTER": "Grappe dédiée", "pr.IN_REGION_ONLY": "Dans la région uniquement", "pr.IN_COUNTRY_ONLY": "Dans le pays uniquement", "pr.SAME_CONTINENT": "Même continent",
 "pr.SMALL": "Petit", "pr.STANDARD": "Standard", "pr.LARGE": "Grand", "pr.pick": "Sélectionnez une demande.", "pr.rejectReason": "Motif (requis pour refuser ou annuler)",
 "ql.title": "Qualification de cellule", "ql.intro": "Une cellule ne peut être mise en service qu’après réussite des huit vérifications avec preuve et achèvement par une autre personne.",
 "ql.cell": "Cellule", "ql.pickCell": "Choisissez une cellule", "ql.start": "Démarrer la qualification", "ql.checks": "Vérifications", "ql.record": "Enregistrer une vérification", "ql.checkKey": "Vérification", "ql.result": "Résultat", "ql.evidence": "Référence de la preuve",
 "ql.detail": "Détail", "ql.complete": "Terminer la qualification", "ql.none": "Aucune qualification pour le moment.", "ql.missing": "Non enregistrée", "ql.started": "Qualification démarrée.", "ql.recorded": "Vérification enregistrée.", "ql.completed": "Qualification terminée.",
 "ql.history": "Qualifications de cette cellule",
 "rl.title": "Déploiement de versions de la flotte", "rl.intro": "Manifestes approuvés, contrôle de compatibilité, étape canari, preuve de ce qui a réellement été déployé et arrêts automatiques.",
 "rl.manifests": "Manifestes de version", "rl.manifestNone": "Aucun manifeste.", "rl.key": "Clé de version", "rl.digest": "Empreinte de l’artefact (SHA-256)", "rl.schemas": "Plage de schémas", "rl.create": "Créer le manifeste", "rl.decide": "Approuver", "rl.rejectIt": "Refuser",
 "rl.rollouts": "Déploiements", "rl.rolloutNone": "Aucun déploiement.", "rl.start": "Démarrer le déploiement", "rl.canary": "Cellules canari (étape 1)", "rl.rest": "Cellules restantes (étape 2)", "rl.stage": "Étape", "rl.advance": "Passer à l’étape suivante", "rl.stop": "Arrêter le déploiement",
 "rl.evidenceRef": "Référence de la preuve de déploiement", "rl.evidenceDigest": "Empreinte d’artefact signalée", "rl.deployed": "Enregistrer comme déployé", "rl.failedDeploy": "Enregistrer un déploiement en échec", "rl.stopReason": "Motif d’arrêt", "rl.created": "Manifeste créé.",
 "rl.started": "Déploiement démarré.", "rl.autoStop": "Arrêté :", "rl.pickRollout": "Sélectionnez un déploiement.", "rl.pickManifest": "Choisissez un manifeste approuvé", "rl.cellsHelp": "Sélectionnez les cellules de chaque étape.", "rl.currentStage": "Étape en cours",
 "mg.title": "Migration et routage de cellule", "mg.intro": "Déplacez un tenant entre cellules avec une preuve à chaque étape. La bascule exige un approbateur indépendant et l’époque actuelle ; l’annulation est limitée une fois la source retirée.",
 "mg.routes": "Routes", "mg.routeNone": "Aucune route.", "mg.directory": "Version de l’annuaire", "mg.routeKey": "Route", "mg.target": "Cellule servante", "mg.plan": "Planifier une migration", "mg.source": "Cellule source", "mg.targetCell": "Cellule cible",
 "mg.route": "Route", "mg.planAction": "Planifier la migration", "mg.list": "Migrations", "mg.none": "Aucune migration.", "mg.evidence": "Référence de la preuve", "mg.lag": "Retard de réplication (ms)", "mg.sourceDigest": "Empreinte source", "mg.targetDigest": "Empreinte cible",
 "mg.epochSeen": "Époque de route examinée", "mg.next": "Étape suivante", "mg.rollback": "Annuler", "mg.rollbackReason": "Motif de l’annulation", "mg.planned": "Migration planifiée.", "mg.irreversible": "Source retirée : annulation impossible.",
 "mg.pick": "Sélectionnez une migration.", "mg.switchNote": "La bascule doit être faite par une autre personne que le planificateur.", "mg.done": "Étape enregistrée.", "mg.moveRoute": "Basculer la route (reprise)"
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
/** ui.tsx expects this name; qualification failures list the missing check keys in plain words. */
export function safetyLabel(lang: Lang, code: string): string {
 const key = `enum.check.${code}` as Key;
 return key in en ? translate(lang, key) : code;
}
export function enumLabel(lang: Lang, group: string, value: string | null | undefined): string {
 if (!value) return translate(lang, "common.none");
 const key = `enum.${group}.${value}` as Key;
 return key in en ? translate(lang, key) : value;
}
