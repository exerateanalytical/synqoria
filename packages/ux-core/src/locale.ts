/** Locale is an interface choice, never evidence of identity, consent or authorization. */
export type Locale = 'en' | 'fr';
export type UiMessageKey =
  | 'brand' | 'workforce' | 'portal' | 'platform' | 'signInNeeded'
  | 'accessBlocked' | 'noClinicalData' | 'noFacility' | 'noConnection'
  | 'serviceStatus' | 'tasksUnavailable' | 'engineeringOnly' | 'backToEntry'
  | 'language' | 'navigation' | 'mainContent' | 'skipToContent'
  | 'home' | 'tasks' | 'more' | 'selected' | 'accessReason'
  | 'staffContext' | 'patientContext' | 'pendingVerification';
const en: Record<UiMessageKey,string> = {
  brand:'SYNQORIA', workforce:'Clinical workforce', portal:'Patient portal', platform:'Platform operations',
  signInNeeded:'Sign in required', accessBlocked:'No hospital or patient information is available before verified authentication and authorization.',
  noClinicalData:'No clinical records, patient lists, work queues or offline drafts have been loaded.',
  noFacility:'Facility not selected', noConnection:'Connectivity not verified', serviceStatus:'Access status',
  tasksUnavailable:'Work queue unavailable', engineeringOnly:'Engineering foundation — not a hospital-ready application.',
  backToEntry:'Return to entry', language:'Language', navigation:'Navigation', mainContent:'Main content',
  skipToContent:'Skip to main content',home:'Home', tasks:'Tasks', more:'More',selected:'Selected',
  accessReason:'Available only after identity, facility, shift and patient scope have been verified.',
  staffContext:'Staff workspace',patientContext:'My record',pendingVerification:'Access verification pending',
};
const fr: Record<UiMessageKey,string> = {
  brand:'SYNQORIA', workforce:'Équipe soignante', portal:'Portail patient', platform:'Exploitation de la plateforme',
  signInNeeded:'Connexion requise',accessBlocked:'Aucune information hospitalière ou patient n’est accessible sans authentification et autorisation vérifiées.',
  noClinicalData:'Aucun dossier clinique, liste de patients, file de tâches ou brouillon hors ligne n’a été chargé.',
  noFacility:'Établissement non sélectionné',noConnection:'Connectivité non vérifiée',serviceStatus:'État de l’accès',
  tasksUnavailable:'File de travail indisponible',engineeringOnly:'Fondation technique — application non prête pour un hôpital.',
  backToEntry:'Retour à l’accueil',language:'Langue',navigation:'Navigation',mainContent:'Contenu principal',
  skipToContent:'Aller au contenu principal',home:'Accueil',tasks:'Tâches',more:'Plus',selected:'Sélectionné',
  accessReason:'Disponible seulement après vérification de l’identité, de l’établissement, du service et des droits sur le patient.',
  staffContext:'Espace du personnel',patientContext:'Mon dossier',pendingVerification:'Vérification des droits en attente',
};
export const messages: Readonly<Record<Locale,Readonly<Record<UiMessageKey,string>>>> = Object.freeze({en:Object.freeze(en),fr:Object.freeze(fr)});
export function selectLocale(input: unknown):Locale { return input === 'fr' ? 'fr' : 'en'; }
export function translate(locale:Locale, key:UiMessageKey):string { return messages[locale][key]; }
export function workspaceLabel(locale:Locale, item:{labelEn:string;labelFr:string}):string {
  return locale === 'fr' ? item.labelFr : item.labelEn;
}
