import { Routes } from '@angular/router';
import { accueil, connecte, moduleRecouvrement, roles } from './core/gardes';
import { Shell } from './shell/shell';

const DIRECTION = ['ADMIN', 'DIR_RECOUVREMENT', 'DIR_AGENCE'];
const PILOTAGE = [...DIRECTION, 'DG', 'DIR_COMMERCIAL'];

export const routes: Routes = [
  { path: 'connexion', loadComponent: () => import('./pages/connexion/connexion').then(m => m.Connexion), title: 'Connexion' },
  {
    path: '', component: Shell, canActivate: [connecte], canActivateChild: [moduleRecouvrement],
    children: [
      { path: '', pathMatch: 'full', canActivate: [accueil], children: [] },
      { path: 'marketing', loadComponent: () => import('./pages/marketing/marketing').then(m => m.Marketing), title: 'Marketing' },
      { path: 'ma-journee', loadComponent: () => import('./pages/ma-journee/ma-journee').then(m => m.MaJournee), title: 'Ma journée' },
      { path: 'factures', loadComponent: () => import('./pages/factures/factures').then(m => m.Factures), title: 'Factures' },
      { path: 'clients', loadComponent: () => import('./pages/clients/clients').then(m => m.Clients), title: 'Clients' },
      { path: 'clients/:id', loadComponent: () => import('./pages/fiche-client/fiche-client').then(m => m.FicheClient), title: 'Fiche client' },
      { path: 'imports', canActivate: [roles(...DIRECTION)], loadComponent: () => import('./pages/imports/imports').then(m => m.Imports), title: 'Imports' },
      { path: 'imports/nouveau', canActivate: [roles(...DIRECTION)], loadComponent: () => import('./pages/assistant-import/assistant-import').then(m => m.AssistantImport), title: 'Assistant d\'import' },
      { path: 'portefeuilles', canActivate: [roles(...PILOTAGE)], loadComponent: () => import('./pages/portefeuilles/portefeuilles').then(m => m.Portefeuilles), title: 'Portefeuilles' },
      { path: 'campagnes', loadComponent: () => import('./pages/campagnes/campagnes').then(m => m.Campagnes), title: 'Campagnes' },
      { path: 'campagnes/nouvelle', canActivate: [roles(...DIRECTION, 'AGENT')], loadComponent: () => import('./pages/campagne-edition/campagne-edition').then(m => m.CampagneEdition), title: 'Nouvelle campagne' },
      { path: 'campagnes/:id/modifier', canActivate: [roles(...DIRECTION, 'AGENT')], loadComponent: () => import('./pages/campagne-edition/campagne-edition').then(m => m.CampagneEdition), title: 'Modifier la campagne' },
      { path: 'campagnes/:id', loadComponent: () => import('./pages/campagne-suivi/campagne-suivi').then(m => m.CampagneSuivi), title: 'Campagne' },
      { path: 'modeles', loadComponent: () => import('./pages/modeles/modeles').then(m => m.Modeles), title: 'Modèles de messages' },
      { path: 'numeros', loadComponent: () => import('./pages/numeros/numeros').then(m => m.Numeros), title: 'Numéros WhatsApp' },
      { path: 'reponses', loadComponent: () => import('./pages/reponses/reponses').then(m => m.Reponses), title: 'Réponses des clients' }
    ]
  },
  { path: '**', redirectTo: '' }
];
