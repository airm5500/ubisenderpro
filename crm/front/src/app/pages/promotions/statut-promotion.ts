/** Libelle et couleur de pastille d'un statut de promotion. */
export const STATUTS_PROMOTION: Record<string, { libelle: string; couleur: string }> = {
  BROUILLON: { libelle: 'Brouillon', couleur: '#F2B84B' },
  PROGRAMMEE: { libelle: 'Programmée', couleur: '#0B5C8E' },
  ACTIVE: { libelle: 'Active', couleur: '#51D78E' },
  SUSPENDUE: { libelle: 'Désactivée', couleur: '#E5484D' },
  TERMINEE: { libelle: 'Terminée', couleur: '#5B8DEF' },
  ARCHIVEE: { libelle: 'Archivée', couleur: '#9AA5AE' }
};
