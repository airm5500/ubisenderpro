/** Contrats de l'API Spring Boot (DTO de rest.service.dto). */

export interface Page<T> { elements: T[]; total: number; page: number; taille: number; }
export interface Erreur { statut: number; message: string; reference?: string; champs?: { champ: string; message: string }[]; }

export interface Utilisateur {
  id: number; login: string; nomComplet: string; roles: string[]; modules: string[];
  visionGlobale: boolean; agenceIds: number[]; motDePasseProvisoire: boolean;
  /** Ecrans et actions permis par ses roles. */
  droits: string[]; profil: string;
}

export interface Droit { code: string; libelle: string; description: string; groupe: string; module: string | null; moduleLibelle: string | null; }
export interface RoleGestion {
  id: number; code: string; libelle: string; description: string | null; visionGlobale: boolean; actif: boolean; systeme: boolean;
  nbUtilisateurs: number; droits: string[]; modifiePar: string | null; modifieLe: string | null;
}

export interface Habilitation { roleCode: string; roleLibelle?: string; moduleCode: string | null; moduleLibelle?: string; }
export interface CompteUtilisateur {
  id: number; login: string; nomComplet: string; email: string | null; numeroWhatsapp: string | null; statut: string;
  motDePasseProvisoire: boolean; echecsConnexion: number; derniereConnexion: string | null; createdAt: string;
  habilitations: Habilitation[]; agences: { agenceId: number; libelle: string; depuis: string }[];
}
export interface CompteProvisoire { utilisateur: CompteUtilisateur; motDePasseProvisoire: string; }
export interface ReferentielHabilitation {
  roles: { code: string; libelle: string; description: string; visionGlobale: boolean }[];
  modules: { code: string; libelle: string }[];
}

export interface Agence { id: number; code: string; libelle: string; }
export interface SousType { id: number; code: string; libelle: string; }

export interface Client {
  id: number; code: string; raisonSociale: string; segmentation: string; segmentationLibelle: string;
  nomPharmacien: string; ville: string; agenceId: number; agenceLibelle: string; sousAgence: string;
  adresse: string; email: string; commentaire: string; numeroPrincipal: string; numeroSecondaire: string; statut: string;
  /** Numeros actifs par role, renseignes sur la fiche client seulement. */
  numeros?: Record<string, string>;
}
export interface Segment { code: string; libelle: string; }

export interface Facture {
  id: number; reference: string; clientId: number; clientCode: string; clientRaisonSociale: string;
  agenceId: number; agenceLibelle: string; sousTypeId: number; sousTypeLibelle: string; nature: string;
  dateFacture: string; dateEcheance: string; montant: number; solde: number; statut: string; joursRetard: number;
}

export interface Import {
  id: number; type: string; nomFichier: string; statut: string; nbLignes: number; nbCreations: number;
  nbMisesAJour: number; nbIdentiques: number; nbRejets: number; nbCrees: number | null; nbMisAJour: number | null;
  nbIgnores: number | null; auteur: string; creeLe: string; validePar: string | null; valideLe: string | null;
}
export interface ImportLigne { id: number; numeroLigne: number; cle: string; action: string; message: string; resultat: string; donnees: Record<string, string>; }
export interface ChampImport { code: string; libelle: string; obligatoire: boolean; exemple: string; alias: string[]; }
export interface ColonneApercu { cle: string; libelle: string; exemples: string[]; }
export interface ApercuImport {
  nomFichier: string; nbLignes: number; colonnes: ColonneApercu[]; champs: ChampImport[];
  correspondance: Record<string, string>; champsManquants: string[];
}
export interface ModeleImport { id: number; type: string; nom: string; correspondance: Record<string, string>; auteur: string; modifieLe: string; }

export interface Agent {
  id: number; login: string; nomComplet: string; numeroWhatsapp: string | null; agences: string;
  nbClients: number; totalSolde: number; remplacant: string | null;
}
export interface PortefeuilleClient {
  clientId: number; code: string; raisonSociale: string; nomPharmacien: string; agenceId: number; agenceLibelle: string;
  segmentation: string; numeroPrincipal: string; agentId: number | null; agentNom: string | null; affecteLe: string | null;
  nbFacturesOuvertes: number; totalSolde: number; retardMax: number;
}
export interface ResultatAffectation { nbTraites: number; nbInchanges: number; refus: string[]; }
export interface Affectation {
  id: number; clientId: number; agentId: number; agentNom: string; dateDebut: string; dateFin: string | null;
  motifDebut: string; motifFin: string | null; affectePar: string; retirePar: string | null;
}
export interface Delegation {
  id: number; titulaireId: number; titulaireNom: string; titulaireNumero: string; remplacantId: number; remplacantNom: string;
  dateDebut: string; dateFin: string; motif: string; statut: string; enCours: boolean; autorisePar: string;
}

export interface Variable { id: number; code: string; libelle: string; source: string; exemple: string; statut: string; }
export interface SourceVariable { source: string; libelle: string; }
export interface ModeleMessage {
  id: number; nom: string; typeCampagne: string; canal: string; objet: string | null; contenu: string; statut: string; variables: string[];
  creePar: string; modifiePar: string; createdAt: string; updatedAt: string;
}

export interface CriteresCampagne {
  agenceIds?: number[]; agentIds?: number[]; clientIds?: number[]; sousTypeIds?: number[]; segments?: string[];
  soldeMin?: number | null; soldeMax?: number | null; echeanceDu?: string | null; echeanceAu?: string | null;
  joursAvantMin?: number | null; joursAvantMax?: number | null; joursRetardMin?: number | null; joursRetardMax?: number | null;
}
export interface CampagneDemande { nom: string; typeCampagne: string; modeleId: number | null; criteres: CriteresCampagne; dateExecution: string | null; }
export interface Campagne {
  id: number; nom: string; typeCampagne: string; modeleId: number; modeleNom: string; canal: string; criteres: CriteresCampagne;
  dateExecution: string | null; statut: string; nbClients: number; nbFactures: number; nbMessages: number; nbExclus: number;
  nbEnvoyes: number; nbEchecs: number; creeParId: number; creePar: string; createdAt: string; soumiseLe: string | null;
  validePar: string | null; valideLe: string | null; motifRejet: string | null; rejetePar: string | null;
  lanceLe: string | null; termineLe: string | null; annulePar: string | null; annuleLe: string | null; motifAnnulation: string | null;
}
export interface MessageCampagne {
  id: number | null; factureId: number; factureReference: string; dateEcheance: string; solde: number; clientId: number;
  clientCode: string; clientRaisonSociale: string; agenceId: number; canal: string; numeroDestinataire: string | null;
  emailDestinataire: string | null; agentId: number | null; agentNom: string | null; numeroExpediteur: string | null;
  emailReponse: string | null; objet: string | null; contenu: string | null; statut: string; motif: string | null;
  envoyeLe: string | null; distribueLe: string | null; luLe: string | null;
}
export interface Parametre {
  cle: string; valeur: string | null; typeValeur: string; minimum: number | null; maximum: number | null; obligatoire: boolean;
  description: string; categorie: string; modifiePar: string | null; modifieLe: string | null;
}

export interface EtatSms {
  configure: boolean; fournisseur: string | null; expediteur: string | null; enAttente: number; envoyesAujourdhui: number;
  heureDebut: number; heureFin: number; alerte: string | null;
}

export interface FournisseurSms {
  id: number; code: string; libelle: string; actif: boolean; parDefaut: boolean; expediteur: string | null; cleRenseignee: boolean;
  formatNumero: string; urlEnvoi: string; methodeEnvoi: string; typeContenu: string; entetes: string | null; corps: string | null;
  cheminIdentifiant: string | null; cheminNbSms: string | null; cheminErreur: string | null; urlStatut: string | null; cheminStatut: string | null;
  champIdentifiantAccuse: string | null; champStatutAccuse: string | null; correspondanceStatuts: string | null;
  urlSolde: string | null; cheminSolde: string | null; urlAccuse: string | null; modifiePar: string | null; modifieLe: string | null;
}

export interface ResultatAppelSms {
  reussi: boolean; statutHttp: number | null; identifiant: string | null; nbSms: number | null; solde: string | null;
  erreur: string | null; reponse: string | null;
}

export interface EtatEmail { configure: boolean; expediteur: string; enAttente: number; envoyesAujourdhui: number; heureDebut: number; heureFin: number; }

export interface Recapitulatif {
  nbClients: number; nbFactures: number; nbMessages: number; nbExclus: number; totalSolde: number;
  agents: { agentId: number; agentNom: string; numeroExpediteur: string; nbClients: number; nbMessages: number }[];
  exclusions: { motif: string; nb: number }[];
  exemples: MessageCampagne[];
}

export interface SessionAgent {
  agentId: number; agentNom: string; numero: string | null; statut: string; sante: string | null; raison: string | null;
  connecteLe: string | null; envoyesAujourdhui: number; enAttente: number;
}
export interface EtatSession { statut: string; sante: string | null; raison: string | null; qr: string | null; }
export interface MessageRecu {
  id: number; numeroAgent: string; agentNom: string; numeroClient: string; nomAffiche: string | null; clientId: number | null;
  clientCode: string | null; clientRaisonSociale: string | null; factureReference: string | null; campagneNom: string | null;
  type: string; contenu: string; recuLe: string;
}

/** Marketing : liste de diffusion, par criteres et/ou clients choisis un par un. */
export interface ListeDiffusion {
  id: number; nom: string; description: string | null; parCriteres: boolean; agenceIds: number[]; sousAgences: string[]; villes: string[];
  segments: string[]; fonctions: string[]; statut: 'ACTIF' | 'ARCHIVE'; nbAjouts: number; nbExclusions: number; nbClients: number;
  nbJoignables: number; creePar: string | null; creeLe: string; modifieLe: string | null;
}
export interface MembreListe {
  clientId: number; code: string; raisonSociale: string; nomPharmacien: string | null; agenceLibelle: string | null; segmentation: string | null;
  ville: string | null; origine: 'CRITERES' | 'AJOUT' | 'EXCLUSION'; numeros: string[]; email: string | null;
  motifExclusion: 'DESABONNE' | 'SANS_COORDONNEES' | null; desabonneLe: string | null;
}
export interface ApercuListe { nbClients: number; nbJoignables: number; nbDesabonnes: number; nbSansCoordonnees: number; membres: Page<MembreListe>; }
export interface ValeursCriteres { sousAgences: string[]; villes: string[]; }

/** Marketing : campagne promotionnelle sur une liste de diffusion. */
export interface CampagneMarketing {
  id: number; nom: string; listeId: number; listeNom: string; modeleId: number; modeleNom: string; canal: string; dateExecution: string | null;
  statut: string; nbClients: number; nbMessages: number; nbExclus: number; nbEnvoyes: number; nbEchecs: number; creeParId: number; creePar: string;
  numeroExpediteur: string | null; agencesCibles: number[] | null; createdAt: string; soumiseLe: string | null; validePar: string | null;
  valideLe: string | null; motifRejet: string | null; rejetePar: string | null; lanceLe: string | null; termineLe: string | null;
  annulePar: string | null; annuleLe: string | null; motifAnnulation: string | null;
}
export interface RecapitulatifMarketing {
  nbClients: number; nbMessages: number; nbExclus: number; expediteur: string; numeroExpediteur: string | null; emailReponse: string | null;
  exclusions: { motif: string; nb: number }[]; exemples: MessageCampagne[];
}
export interface Produit {
  id: number; code: string; libelle: string; categorie: string | null; laboratoire: string | null; conditionnement: string | null;
  prix: number | null; description: string | null; statut: 'ACTIF' | 'ARCHIVE'; importe: boolean; creePar: string | null; creeLe: string;
  modifiePar: string | null; modifieLe: string | null;
}
export interface FiltresCatalogue { categories: string[]; laboratoires: string[]; }
