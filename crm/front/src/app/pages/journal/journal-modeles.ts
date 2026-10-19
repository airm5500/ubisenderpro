/** Journal des actions : lignes et listes de filtres renvoyees par /api/journal. */
export interface JournalLigne {
  cle: string; le: string; utilisateurId?: number; login?: string; utilisateurNom?: string;
  action: string; actionLibelle: string; nature: string; natureLibelle: string; type: string; typeLibelle: string;
  entite?: string; entiteId?: number; objet?: string; lien?: string; details?: string; adresseIp?: string;
}

export interface JournalOption { code: string; libelle: string; }

export interface JournalReferentiel { types: JournalOption[]; natures: JournalOption[]; utilisateurs: JournalOption[]; }

/** Auteur affiche : nom de l'utilisateur, sinon traitement automatique ou login saisi (connexion refusee). */
export function auteur(l: JournalLigne): string {
  if (l.utilisateurNom) return l.utilisateurNom;
  return l.login === 'systeme' ? 'Traitement automatique' : (l.login || '—');
}

/** Date du jour (AAAA-MM-JJ, heure locale), decalee de jours. */
export function jourIso(decalage = 0): string {
  const d = new Date();
  d.setDate(d.getDate() + decalage);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}
