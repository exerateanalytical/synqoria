// M28 English / French dictionary. Both languages must define exactly the same keys (tested).
export type Lang = "en" | "fr";

const en = {
 "app.title": "Operations and business intelligence", "app.subtitle": "M28 · Analytics",
 "nav.label": "Analytics workstations", "nav.dashboard": "Operations dashboard", "nav.clinical": "Clinical service analytics", "nav.financial": "Financial analytics",
 "nav.reports": "Report catalogue", "nav.exports": "Exports and history", "lang.label": "Language", "skip": "Skip to content",
 "hub.title": "Operations and business intelligence",
 "hub.intro": "Verified-source numbers with their freshness and limits. Unknown is shown as unknown, small groups are hidden to protect patients, and nothing is shown from another hospital.",
 "hub.open": "Open",
 "common.refresh": "Refresh", "common.retry": "Try again", "common.reload": "Reload latest version", "common.cancel": "Cancel", "common.submit": "Submit",
 "common.previous": "Previous page", "common.next": "Next page", "common.page": "Page {page} of {pages}", "common.total": "{total} records",
 "common.none": "None", "common.any": "Any", "common.version": "Version", "common.updated": "Updated", "common.reason": "Reason", "common.status": "Status",
 "common.from": "From", "common.to": "To", "common.actions": "Actions", "common.approve": "Approve", "common.reject": "Reject", "common.details": "Details",
 "state.loading": "Loading records…", "state.empty": "No records match the current filters.", "state.emptyHint": "Change the filters or refresh once new records arrive.",
 "state.error": "The service could not be reached or returned an error.", "state.errorHint": "No data is shown as current. Check the connection, then try again.",
 "state.offline": "You are offline. Displayed data may be out of date and actions are disabled until the connection returns.",
 "state.unauthorized": "Sign in is required.", "state.unauthorizedHint": "Your session is missing or has expired. Sign in with your hospital identity to continue.",
 "state.signIn": "Sign in with hospital identity", "state.forbidden": "You are not authorized for this action or record.",
 "state.forbiddenHint": "Your role does not include this permission. Ask your supervisor if you need access.",
 "state.notFound": "This record does not exist or is outside your hospital and facility.", "state.notConfigured": "The analytics service is not configured for this environment.",
 "disabled.status": "Not available in the current status ({status}).", "disabled.select": "Select a record first.", "disabled.offline": "Unavailable while offline.",
 "disabled.input": "Complete the required fields first.",
 "err.generic": "The action was refused ({code}).", "err.permission_denied": "You do not have permission for this action.",
 "err.approver_must_be_independent": "A different person must approve this. You cannot approve what you requested.",
 "err.stale_version": "The record changed. Reload it and try again.", "err.idempotency_collision": "This submission was already used with different content. Review and submit again.",
 "err.invalid_transition": "This action is not allowed in the record's current status.", "err.duplicate_record": "A matching request is already pending.",
 "err.reason_required": "A reason is required.", "err.report_not_approved": "This report is not approved for use.", "err.metric_not_approved": "This metric is not approved.",
 "err.source_not_verified": "The data source is not verified.", "err.export_not_ready": "This export is not ready to download.", "err.export_expired": "This export has expired. Request a new one.",
 "err.unsupported_format": "Only CSV exports are available. PDF and Excel are not supported.", "err.invalid_period": "The period is not valid for this report (check the dates and the maximum length).",
 "err.invalid_export": "Describe the purpose of the export.", "err.invalid_domain": "Unknown analytics domain.", "err.invalid_query": "The search is not valid.",
 "err.evidence_mismatch": "The stored export no longer matches its fingerprint. It was not released.", "err.invalid_pagination": "Invalid page request.",
 "err.malformed_request": "The request was not understood.", "err.analytics_unavailable": "The analytics service is temporarily unavailable.",
 "enum.state.OK": "Complete", "enum.state.INCOMPLETE": "Incomplete", "enum.state.SUPPRESSED": "Hidden: group too small", "enum.state.NO_DATA": "No data",
 "enum.state.NOT_COMPUTED": "Not computed", "enum.state.NOT_AVAILABLE": "Not available", "enum.state.AVAILABLE": "Available",
 "explain.OK": "All inputs were known.", "explain.INCOMPLETE": "{n} records had missing or unusable fields and were left out. The figure covers known records only.",
 "explain.SUPPRESSED": "Fewer than {n} people or records. The value is hidden to protect individuals.", "explain.NO_DATA": "No verified data for this period. This is not zero.",
 "explain.stale": "The source has not reported recently. Treat this figure as possibly out of date.",
 "enum.run.QUEUED": "Queued", "enum.run.RUNNING": "Running", "enum.run.SUCCEEDED": "Succeeded", "enum.run.FAILED": "Failed",
 "enum.export.REQUESTED": "Awaiting approval", "enum.export.READY": "Ready", "enum.export.REJECTED": "Rejected", "enum.export.EXPIRED": "Expired",
 "enum.report.DRAFT": "Draft", "enum.report.APPROVED": "Approved", "enum.report.RETIRED": "Retired", "enum.report.REJECTED": "Rejected",
 "enum.source.PENDING": "Awaiting verification", "enum.source.VERIFIED": "Verified", "enum.source.SUSPENDED": "Suspended",
 "enum.trigger.MANUAL": "Manual", "enum.trigger.SCHEDULE": "Scheduled",
 // S01
 "db.title": "Hospital operations dashboard", "db.intro": "Capacity, flow and activity from verified sources. Each tile shows when its source last reported.",
 "db.noTiles": "No operations metrics have been computed yet.", "db.freshness": "Source freshness", "db.watermark": "Last reported", "db.neverReported": "Never reported",
 "db.quarantineCol": "Quarantined", "db.stale": "Stale", "db.fresh": "Up to date", "db.quarantined": "{n} events quarantined", "db.period": "Period", "db.computed": "Computed",
 // S02 / S03
 "an.clinicalTitle": "Clinical service analytics", "an.clinicalIntro": "Service-level trends and quality indicators. Groups below the minimum size are hidden, and missing data is never shown as zero.",
 "an.financialTitle": "Financial analytics", "an.financialIntro": "Revenue, invoicing and receivables from authoritative financial records. Each currency is shown separately and never combined.",
 "an.metric": "Metric", "an.from": "From", "an.to": "To", "an.series": "Trend", "an.breakdown": "By group", "an.table": "Data table", "an.dimension": "Group", "an.currency": "Currency",
 "an.cohort": "Records", "an.unknown": "Unknown inputs", "an.chartLabel": "Trend of {metric}, {n} points", "an.chartEmpty": "No points to draw.", "an.noMetrics": "No approved metrics in this domain.",
 "an.pickMetric": "Choose a metric.", "an.suppressedNote": "Hidden groups protect individuals and are not zero.", "an.numerator": "Numerator", "an.denominator": "Denominator", "an.source": "Source watermark",
 "an.cellAggregate": "Across facilities of this hospital", "an.cellNote": "Combined only when at least 3 facilities contribute. No single facility is shown.", "an.cellHidden": "Hidden: fewer than 3 facilities",
 "an.cellLoad": "Show combined figure", "an.facilities": "{n} facilities",
 // S04
 "rp.title": "Report catalogue and execution", "rp.intro": "Run approved reports for a period. Reports read stored, already-protected figures and run in the background.",
 "rp.catalogue": "Approved reports", "rp.noReports": "No approved reports.", "rp.metrics": "Metrics", "rp.maxDays": "Up to {n} days", "rp.run": "Run report", "rp.periodStart": "Period start", "rp.periodEnd": "Period end",
 "rp.requires": "Needs permission {perm}", "rp.history": "Run history", "rp.noRuns": "No runs yet.", "rp.process": "Process queued runs", "rp.result": "Result", "rp.queuedNote": "Queued. Process the queue or wait for the scheduler, then refresh.",
 "rp.section": "Metric {key} (version {v})", "rp.requested": "Run requested.", "rp.pick": "Select a run to read its result.", "rp.error": "Failure code",
 // S05
 "ex.title": "Report export and history", "ex.intro": "Exports are CSV only, need approval from someone else, carry a watermark and expire. Every download is recorded.",
 "ex.request": "Request an export", "ex.run": "Finished run", "ex.purpose": "Purpose of the export", "ex.format": "Format", "ex.csvOnly": "CSV (PDF and Excel are not available)", "ex.requestAction": "Request export",
 "ex.history": "Export history", "ex.noExports": "No exports yet.", "ex.download": "Download", "ex.downloads": "{n} downloads", "ex.expires": "Expires", "ex.expireDue": "Purge expired exports",
 "ex.approveAction": "Approve and generate", "ex.rejectAction": "Reject export", "ex.fingerprint": "SHA-256", "ex.size": "{n} bytes", "ex.downloaded": "Download recorded.", "ex.pickRun": "Select a finished run.",
 "ex.rejectReason": "Reason for rejection"
} as const;

export type Key = keyof typeof en;

const fr: Record<Key, string> = {
 "app.title": "Exploitation et intelligence décisionnelle", "app.subtitle": "M28 · Analytique",
 "nav.label": "Postes d’analytique", "nav.dashboard": "Tableau de bord opérationnel", "nav.clinical": "Analytique des services cliniques", "nav.financial": "Analytique financière",
 "nav.reports": "Catalogue de rapports", "nav.exports": "Exports et historique", "lang.label": "Langue", "skip": "Passer au contenu",
 "hub.title": "Exploitation et intelligence décisionnelle",
 "hub.intro": "Des chiffres issus de sources vérifiées, avec leur fraîcheur et leurs limites. L’inconnu est affiché comme inconnu, les petits groupes sont masqués pour protéger les patients, et rien n’est montré d’un autre hôpital.",
 "hub.open": "Ouvrir",
 "common.refresh": "Actualiser", "common.retry": "Réessayer", "common.reload": "Recharger la dernière version", "common.cancel": "Annuler", "common.submit": "Envoyer",
 "common.previous": "Page précédente", "common.next": "Page suivante", "common.page": "Page {page} sur {pages}", "common.total": "{total} enregistrements",
 "common.none": "Aucun", "common.any": "Tous", "common.version": "Version", "common.updated": "Mis à jour", "common.reason": "Motif", "common.status": "État",
 "common.from": "Du", "common.to": "Au", "common.actions": "Actions", "common.approve": "Approuver", "common.reject": "Refuser", "common.details": "Détails",
 "state.loading": "Chargement des enregistrements…", "state.empty": "Aucun enregistrement ne correspond aux filtres.", "state.emptyHint": "Modifiez les filtres ou actualisez à l’arrivée de nouveaux enregistrements.",
 "state.error": "Le service est injoignable ou a renvoyé une erreur.", "state.errorHint": "Aucune donnée n’est présentée comme à jour. Vérifiez la connexion puis réessayez.",
 "state.offline": "Vous êtes hors ligne. Les données affichées peuvent être périmées et les actions sont désactivées jusqu’au retour de la connexion.",
 "state.unauthorized": "Une connexion est requise.", "state.unauthorizedHint": "Votre session est absente ou expirée. Connectez-vous avec votre identité hospitalière pour continuer.",
 "state.signIn": "Se connecter avec l’identité hospitalière", "state.forbidden": "Vous n’êtes pas autorisé pour cette action ou cet enregistrement.",
 "state.forbiddenHint": "Votre rôle n’inclut pas cette autorisation. Demandez l’accès à votre responsable.",
 "state.notFound": "Cet enregistrement n’existe pas ou est hors de votre hôpital et de votre établissement.", "state.notConfigured": "Le service d’analytique n’est pas configuré pour cet environnement.",
 "disabled.status": "Indisponible dans l’état actuel ({status}).", "disabled.select": "Sélectionnez d’abord un enregistrement.", "disabled.offline": "Indisponible hors ligne.",
 "disabled.input": "Renseignez d’abord les champs obligatoires.",
 "err.generic": "L’action a été refusée ({code}).", "err.permission_denied": "Vous n’avez pas l’autorisation pour cette action.",
 "err.approver_must_be_independent": "Une autre personne doit approuver. Vous ne pouvez pas approuver ce que vous avez demandé.",
 "err.stale_version": "L’enregistrement a changé. Rechargez-le et réessayez.", "err.idempotency_collision": "Cet envoi a déjà été utilisé avec un contenu différent. Vérifiez puis renvoyez.",
 "err.invalid_transition": "Cette action n’est pas permise dans l’état actuel de l’enregistrement.", "err.duplicate_record": "Une demande identique est déjà en attente.",
 "err.reason_required": "Un motif est requis.", "err.report_not_approved": "Ce rapport n’est pas approuvé pour l’usage.", "err.metric_not_approved": "Cet indicateur n’est pas approuvé.",
 "err.source_not_verified": "La source de données n’est pas vérifiée.", "err.export_not_ready": "Cet export n’est pas prêt à être téléchargé.", "err.export_expired": "Cet export a expiré. Demandez-en un nouveau.",
 "err.unsupported_format": "Seuls les exports CSV sont disponibles. PDF et Excel ne sont pas pris en charge.", "err.invalid_period": "La période n’est pas valide pour ce rapport (vérifiez les dates et la durée maximale).",
 "err.invalid_export": "Décrivez l’objet de l’export.", "err.invalid_domain": "Domaine d’analytique inconnu.", "err.invalid_query": "La recherche n’est pas valide.",
 "err.evidence_mismatch": "L’export stocké ne correspond plus à son empreinte. Il n’a pas été remis.", "err.invalid_pagination": "Demande de page invalide.",
 "err.malformed_request": "La requête n’a pas été comprise.", "err.analytics_unavailable": "Le service d’analytique est temporairement indisponible.",
 "enum.state.OK": "Complet", "enum.state.INCOMPLETE": "Incomplet", "enum.state.SUPPRESSED": "Masqué : groupe trop petit", "enum.state.NO_DATA": "Aucune donnée",
 "enum.state.NOT_COMPUTED": "Non calculé", "enum.state.NOT_AVAILABLE": "Indisponible", "enum.state.AVAILABLE": "Disponible",
 "explain.OK": "Toutes les données d’entrée étaient connues.", "explain.INCOMPLETE": "{n} enregistrements avaient des champs manquants ou inutilisables et ont été écartés. Le chiffre ne couvre que les enregistrements connus.",
 "explain.SUPPRESSED": "Moins de {n} personnes ou enregistrements. La valeur est masquée pour protéger les personnes.", "explain.NO_DATA": "Aucune donnée vérifiée pour cette période. Ce n’est pas zéro.",
 "explain.stale": "La source n’a pas communiqué récemment. Considérez ce chiffre comme possiblement périmé.",
 "enum.run.QUEUED": "En file", "enum.run.RUNNING": "En cours", "enum.run.SUCCEEDED": "Réussi", "enum.run.FAILED": "Échoué",
 "enum.export.REQUESTED": "En attente d’approbation", "enum.export.READY": "Prêt", "enum.export.REJECTED": "Refusé", "enum.export.EXPIRED": "Expiré",
 "enum.report.DRAFT": "Brouillon", "enum.report.APPROVED": "Approuvé", "enum.report.RETIRED": "Retiré", "enum.report.REJECTED": "Refusé",
 "enum.source.PENDING": "En attente de vérification", "enum.source.VERIFIED": "Vérifiée", "enum.source.SUSPENDED": "Suspendue",
 "enum.trigger.MANUAL": "Manuel", "enum.trigger.SCHEDULE": "Planifié",
 "db.title": "Tableau de bord des opérations hospitalières", "db.intro": "Capacité, flux et activité issus de sources vérifiées. Chaque tuile indique quand sa source a communiqué pour la dernière fois.",
 "db.noTiles": "Aucun indicateur opérationnel n’a encore été calculé.", "db.freshness": "Fraîcheur des sources", "db.watermark": "Dernière communication", "db.neverReported": "Jamais communiqué",
 "db.quarantineCol": "En quarantaine", "db.stale": "Périmée", "db.fresh": "À jour", "db.quarantined": "{n} événements en quarantaine", "db.period": "Période", "db.computed": "Calculé",
 "an.clinicalTitle": "Analytique des services cliniques", "an.clinicalIntro": "Tendances par service et indicateurs de qualité. Les groupes sous la taille minimale sont masqués, et les données manquantes ne sont jamais affichées comme zéro.",
 "an.financialTitle": "Analytique financière", "an.financialIntro": "Recettes, facturation et créances issues des enregistrements financiers faisant autorité. Chaque devise est affichée séparément et jamais combinée.",
 "an.metric": "Indicateur", "an.from": "Du", "an.to": "Au", "an.series": "Tendance", "an.breakdown": "Par groupe", "an.table": "Tableau de données", "an.dimension": "Groupe", "an.currency": "Devise",
 "an.cohort": "Enregistrements", "an.unknown": "Données inconnues", "an.chartLabel": "Tendance de {metric}, {n} points", "an.chartEmpty": "Aucun point à tracer.", "an.noMetrics": "Aucun indicateur approuvé dans ce domaine.",
 "an.pickMetric": "Choisissez un indicateur.", "an.suppressedNote": "Les groupes masqués protègent les personnes et ne valent pas zéro.", "an.numerator": "Numérateur", "an.denominator": "Dénominateur", "an.source": "Dernière donnée de la source",
 "an.cellAggregate": "Sur l’ensemble des établissements de cet hôpital", "an.cellNote": "Combiné uniquement si au moins 3 établissements contribuent. Aucun établissement n’est montré seul.", "an.cellHidden": "Masqué : moins de 3 établissements",
 "an.cellLoad": "Afficher le chiffre combiné", "an.facilities": "{n} établissements",
 "rp.title": "Catalogue de rapports et exécution", "rp.intro": "Exécutez des rapports approuvés pour une période. Les rapports lisent des chiffres déjà protégés et s’exécutent en arrière-plan.",
 "rp.catalogue": "Rapports approuvés", "rp.noReports": "Aucun rapport approuvé.", "rp.metrics": "Indicateurs", "rp.maxDays": "Jusqu’à {n} jours", "rp.run": "Exécuter le rapport", "rp.periodStart": "Début de période", "rp.periodEnd": "Fin de période",
 "rp.requires": "Nécessite l’autorisation {perm}", "rp.history": "Historique des exécutions", "rp.noRuns": "Aucune exécution pour le moment.", "rp.process": "Traiter les exécutions en file", "rp.result": "Résultat", "rp.queuedNote": "En file. Traitez la file ou attendez le planificateur, puis actualisez.",
 "rp.section": "Indicateur {key} (version {v})", "rp.requested": "Exécution demandée.", "rp.pick": "Sélectionnez une exécution pour lire son résultat.", "rp.error": "Code d’échec",
 "ex.title": "Export de rapports et historique", "ex.intro": "Les exports sont en CSV uniquement, exigent l’approbation d’une autre personne, portent un filigrane et expirent. Chaque téléchargement est enregistré.",
 "ex.request": "Demander un export", "ex.run": "Exécution terminée", "ex.purpose": "Objet de l’export", "ex.format": "Format", "ex.csvOnly": "CSV (PDF et Excel indisponibles)", "ex.requestAction": "Demander l’export",
 "ex.history": "Historique des exports", "ex.noExports": "Aucun export pour le moment.", "ex.download": "Télécharger", "ex.downloads": "{n} téléchargements", "ex.expires": "Expire", "ex.expireDue": "Purger les exports expirés",
 "ex.approveAction": "Approuver et générer", "ex.rejectAction": "Refuser l’export", "ex.fingerprint": "SHA-256", "ex.size": "{n} octets", "ex.downloaded": "Téléchargement enregistré.", "ex.pickRun": "Sélectionnez une exécution terminée.",
 "ex.rejectReason": "Motif du refus"
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
/** ui.tsx expects this name; here it labels data states and codes. */
export function safetyLabel(lang: Lang, code: string): string {
 const key = `enum.state.${code}` as Key;
 return key in en ? translate(lang, key) : code;
}
export function enumLabel(lang: Lang, group: string, value: string | null | undefined): string {
 if (!value) return translate(lang, "common.none");
 const key = `enum.${group}.${value}` as Key;
 return key in en ? translate(lang, key) : value;
}
