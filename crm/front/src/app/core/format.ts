import { Pipe, PipeTransform } from '@angular/core';

/** Montant en FCFA sans decimales inutiles : 1 500 000. */
@Pipe({ name: 'montant' })
export class MontantPipe implements PipeTransform {
  transform(valeur: number | null | undefined): string {
    if (valeur === null || valeur === undefined) return '—';
    return new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 2 }).format(valeur);
  }
}

/** Date ISO (2026-10-18 ou 2026-10-18T09:30:00) en 18/10/2026, avec l'heure si demandee. */
@Pipe({ name: 'jour' })
export class JourPipe implements PipeTransform {
  transform(valeur: string | null | undefined, avecHeure = false): string {
    if (!valeur) return '—';
    const [date, heure] = valeur.split('T');
    const [a, m, j] = date.split('-');
    const texte = `${j}/${m}/${a}`;
    return avecHeure && heure ? `${texte} ${heure.substring(0, 5)}` : texte;
  }
}

/** Libelles des codes renvoyes par l'API. */
export const LIBELLES = {
  statutFacture: {
    A_RELANCER: 'À relancer', RELANCE_PROGRAMMEE: 'Relance programmée', RELANCE_ENVOYEE: 'Relance envoyée',
    MESSAGE_DISTRIBUE: 'Message distribué', MESSAGE_LU: 'Message lu', REPONSE_RECUE: 'Réponse reçue', SOLDEE: 'Soldée'
  } as Partial<Record<string, string>>,
  couleurFacture: {
    A_RELANCER: '#9DB0BF', RELANCE_PROGRAMMEE: '#F4B740', RELANCE_ENVOYEE: '#7FA8C9', MESSAGE_DISTRIBUE: '#0B5C8E',
    MESSAGE_LU: '#05253D', REPONSE_RECUE: '#51D78E', SOLDEE: '#157A47'
  } as Partial<Record<string, string>>,
  suiviFacture: { PROMESSE: 'Promesse de paiement', LITIGE: 'Litige', CONTENTIEUX: 'Contentieux' } as Partial<Record<string, string>>,
  canal: { WHATSAPP: 'WhatsApp', EMAIL: 'E-mail', SMS: 'SMS' } as Partial<Record<string, string>>,
  typeCampagne: {
    AVANT_ECHEANCE: 'Avant échéance', JOUR_ECHEANCE: "Jour d'échéance", APRES_ECHEANCE: 'Après échéance', PERSONNALISEE: 'Personnalisée',
    PROMOTION: 'Promotion'
  } as Partial<Record<string, string>>,
  recurrence: { QUOTIDIENNE: 'chaque jour', HEBDOMADAIRE: 'chaque semaine', MENSUELLE: 'chaque mois' } as Partial<Record<string, string>>,
  statutCampagne: {
    BROUILLON: 'Brouillon', A_VALIDER: 'En attente de validation', VALIDEE: 'Programmée', EN_COURS: 'En cours',
    TERMINEE: 'Exécutée', ANNULEE: 'Annulée'
  } as Partial<Record<string, string>>,
  couleurCampagne: {
    BROUILLON: '#9DB0BF', A_VALIDER: '#F4B740', VALIDEE: '#0B5C8E', EN_COURS: '#51D78E', TERMINEE: '#05253D', ANNULEE: '#C2410C'
  } as Partial<Record<string, string>>,
  statutMessage: {
    EXCLU: 'Exclu', A_ENVOYER: 'À envoyer', ENVOI: "En cours d'envoi", ENVOYE: 'Envoyé', DISTRIBUE: 'Distribué',
    LU: 'Lu', ECHEC: 'Échec', ANNULE: 'Annulé'
  } as Partial<Record<string, string>>,
  couleurMessage: {
    EXCLU: '#9DB0BF', A_ENVOYER: '#F4B740', ENVOI: '#F4B740', ENVOYE: '#7FA8C9', DISTRIBUE: '#0B5C8E', LU: '#51D78E',
    ECHEC: '#C2410C', ANNULE: '#9DB0BF'
  } as Partial<Record<string, string>>,
  statutSession: {
    DECONNECTE: 'Non connecté', CONNEXION: 'Connexion en cours', QR: 'QR à scanner', CONNECTE: 'Session connectée'
  } as Partial<Record<string, string>>,
  couleurSession: { DECONNECTE: '#C2410C', CONNEXION: '#F4B740', QR: '#F4B740', CONNECTE: '#51D78E' } as Partial<Record<string, string>>,
  actionImport: {
    CREATION: 'Création', MISE_A_JOUR: 'Mise à jour', IDENTIQUE: 'Identique', REJET: 'Rejet'
  } as Partial<Record<string, string>>
};

/** Date du jour au format ISO (heure d'Abidjan = UTC). */
export function aujourdhui(decalageJours = 0): string {
  const d = new Date();
  d.setUTCDate(d.getUTCDate() + decalageJours);
  return d.toISOString().substring(0, 10);
}

/** Resume lisible des criteres d'une campagne, pour les puces et le panneau de validation. */
export function resumeCriteres(c: import('./modeles').CriteresCampagne | null | undefined): string[] {
  if (!c) return [];
  const r: string[] = [];
  const intervalle = (min?: number | null, max?: number | null) =>
    min != null && max != null ? `${min} à ${max}` : min != null ? `${min} ou plus` : `jusqu'à ${max}`;
  if (c.joursAvantMin != null || c.joursAvantMax != null) r.push(`Échéance dans ${intervalle(c.joursAvantMin, c.joursAvantMax)} j`);
  if (c.joursRetardMin != null || c.joursRetardMax != null) r.push(`Échue depuis ${intervalle(c.joursRetardMin, c.joursRetardMax)} j`);
  if (c.echeanceDu || c.echeanceAu) {
    const j = (d?: string | null) => d ? d.split('-').reverse().join('/') : '…';
    r.push(`Échéance du ${j(c.echeanceDu)} au ${j(c.echeanceAu)}`);
  }
  const fmt = (n: number) => new Intl.NumberFormat('fr-FR').format(n);
  if (c.soldeMin != null) r.push(`Solde ≥ ${fmt(c.soldeMin)}`);
  if (c.soldeMax != null) r.push(`Solde ≤ ${fmt(c.soldeMax)}`);
  if (c.agenceIds?.length) r.push(`${c.agenceIds.length} agence${c.agenceIds.length > 1 ? 's' : ''}`);
  if (c.agentIds?.length) r.push(`${c.agentIds.length} agent${c.agentIds.length > 1 ? 's' : ''}`);
  if (c.clientIds?.length) r.push(`${c.clientIds.length} client${c.clientIds.length > 1 ? 's' : ''} choisi${c.clientIds.length > 1 ? 's' : ''}`);
  if (c.sousTypeIds?.length) r.push(`${c.sousTypeIds.length} sous-type${c.sousTypeIds.length > 1 ? 's' : ''}`);
  if (c.segments?.length) r.push(`Segment ${c.segments.join(', ')}`);
  return r;
}

/** Numero WhatsApp normalise (2250708123456) affiche en +225 07 08 12 34 56. */
@Pipe({ name: 'telephone' })
export class TelephonePipe implements PipeTransform {
  transform(valeur: string | null | undefined): string {
    if (!valeur) return '';
    const chiffres = valeur.replace(/\D/g, '');
    if (chiffres.length === 13 && chiffres.startsWith('225')) {
      return '+225 ' + chiffres.substring(3).match(/.{2}/g)!.join(' ');
    }
    return chiffres.length > 8 ? '+' + chiffres : valeur;
  }
}

/** Alphabet GSM 7 bits d'un SMS ; les caracteres de GSM_DOUBLE comptent pour deux, tout autre caractere passe le SMS en Unicode. */
const GSM = '@£$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞÆæßÉ !"#¤%&\'()*+,-./0123456789:;<=>?¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà';
const GSM_DOUBLE = '^{}\\[~]|€';

/** Longueur d'un SMS et nombre de SMS factures (160 / 153 caracteres, 70 / 67 en Unicode). */
export function compterSms(texte: string): { caracteres: number; sms: number; unicode: boolean } {
  const unicode = [...texte].some(c => !GSM.includes(c) && !GSM_DOUBLE.includes(c));
  const caracteres = unicode ? [...texte].length : [...texte].reduce((n, c) => n + (GSM_DOUBLE.includes(c) ? 2 : 1), 0);
  const [seul, partie] = unicode ? [70, 67] : [160, 153];
  return { caracteres, sms: caracteres === 0 ? 0 : caracteres <= seul ? 1 : Math.ceil(caracteres / partie), unicode };
}
