/**
 * Sections du menu de gauche repliables (parametre charte.menu_repliable) : une seule section ouverte a la fois.
 * ouverte : section ouverte par un clic, null = celle de l'ecran affiche, '' = aucune.
 */
export function sectionOuverte(repliable: boolean, ouverte: string | null, cle: string, active: boolean): boolean {
  if (!repliable) return true;
  return ouverte === null ? active : ouverte === cle;
}

/** Nouvelle section ouverte apres un clic sur le titre d'une section. */
export function apresClic(repliable: boolean, ouverte: string | null, cle: string, active: boolean): string | null {
  if (!repliable) return ouverte;
  return sectionOuverte(true, ouverte, cle, active) ? '' : cle;
}
