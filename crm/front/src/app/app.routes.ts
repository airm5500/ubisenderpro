import { Routes } from '@angular/router';
import { accueil, connecte, droit, moduleRecouvrement, motDePasseAJour } from './core/gardes';
import { Shell } from './shell/shell';

export const routes: Routes = [
  { path: 'connexion', loadComponent: () => import('./pages/connexion/connexion').then(m => m.Connexion), title: 'Connexion' },
  {
    path: '', component: Shell, canActivate: [connecte], canActivateChild: [motDePasseAJour, moduleRecouvrement],
    children: [
      { path: '', pathMatch: 'full', canActivate: [accueil], children: [] },
      { path: 'marketing', loadComponent: () => import('./pages/marketing/marketing').then(m => m.Marketing), title: 'Marketing' },
      { path: 'marketing/listes', canActivate: [droit('LISTES')], loadComponent: () => import('./pages/listes-diffusion/listes-diffusion').then(m => m.ListesDiffusion), title: 'Listes de diffusion' },
      { path: 'marketing/listes/:id', canActivate: [droit('LISTES')], loadComponent: () => import('./pages/liste-diffusion/liste-diffusion').then(m => m.ListeDiffusionDetail), title: 'Liste de diffusion' },
      { path: 'ma-journee', canActivate: [droit('MA_JOURNEE')], loadComponent: () => import('./pages/ma-journee/ma-journee').then(m => m.MaJournee), title: 'Ma journée' },
      { path: 'factures', canActivate: [droit('FACTURES')], loadComponent: () => import('./pages/factures/factures').then(m => m.Factures), title: 'Factures' },
      { path: 'clients', canActivate: [droit('CLIENTS')], loadComponent: () => import('./pages/clients/clients').then(m => m.Clients), title: 'Clients' },
      { path: 'clients/:id', canActivate: [droit('CLIENTS', 'MA_JOURNEE')], loadComponent: () => import('./pages/fiche-client/fiche-client').then(m => m.FicheClient), title: 'Fiche client' },
      { path: 'imports', canActivate: [droit('IMPORTS')], loadComponent: () => import('./pages/imports/imports').then(m => m.Imports), title: 'Imports' },
      { path: 'imports/nouveau', canActivate: [droit('IMPORTS')], loadComponent: () => import('./pages/assistant-import/assistant-import').then(m => m.AssistantImport), title: 'Assistant d\'import' },
      { path: 'portefeuilles', canActivate: [droit('PORTEFEUILLES')], loadComponent: () => import('./pages/portefeuilles/portefeuilles').then(m => m.Portefeuilles), title: 'Portefeuilles' },
      { path: 'campagnes', canActivate: [droit('CAMPAGNES')], loadComponent: () => import('./pages/campagnes/campagnes').then(m => m.Campagnes), title: 'Campagnes' },
      { path: 'campagnes/nouvelle', canActivate: [droit('CAMPAGNES_PREPARER')], loadComponent: () => import('./pages/campagne-edition/campagne-edition').then(m => m.CampagneEdition), title: 'Nouvelle campagne' },
      { path: 'campagnes/:id/modifier', canActivate: [droit('CAMPAGNES_PREPARER', 'CAMPAGNES_VALIDER')], loadComponent: () => import('./pages/campagne-edition/campagne-edition').then(m => m.CampagneEdition), title: 'Modifier la campagne' },
      { path: 'campagnes/:id', canActivate: [droit('CAMPAGNES', 'MA_JOURNEE')], loadComponent: () => import('./pages/campagne-suivi/campagne-suivi').then(m => m.CampagneSuivi), title: 'Campagne' },
      { path: 'modeles', canActivate: [droit('MODELES', 'MODELES_GERER')], loadComponent: () => import('./pages/modeles/modeles').then(m => m.Modeles), title: 'Modèles de messages' },
      { path: 'numeros', canActivate: [droit('NUMEROS', 'NUMEROS_TOUS')], loadComponent: () => import('./pages/numeros/numeros').then(m => m.Numeros), title: 'Numéros WhatsApp' },
      { path: 'parametres', canActivate: [droit('PARAMETRES')], loadComponent: () => import('./pages/parametres/parametres').then(m => m.Parametres), title: 'Paramètres' },
      { path: 'fournisseurs-sms', canActivate: [droit('FOURNISSEURS_SMS')], loadComponent: () => import('./pages/fournisseurs-sms/fournisseurs-sms').then(m => m.FournisseursSms), title: 'Fournisseurs SMS' },
      { path: 'utilisateurs', canActivate: [droit('UTILISATEURS')], loadComponent: () => import('./pages/utilisateurs/utilisateurs').then(m => m.Utilisateurs), title: 'Utilisateurs et habilitations' },
      { path: 'roles', canActivate: [droit('ROLES')], loadComponent: () => import('./pages/roles/roles').then(m => m.Roles), title: 'Rôles et droits' },
      { path: 'mot-de-passe', loadComponent: () => import('./pages/mot-de-passe/mot-de-passe').then(m => m.MotDePasse), title: 'Mon mot de passe' },
      { path: 'reponses', canActivate: [droit('REPONSES')], loadComponent: () => import('./pages/reponses/reponses').then(m => m.Reponses), title: 'Réponses des clients' }
    ]
  },
  { path: '**', redirectTo: '' }
];
