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
      { path: 'marketing/campagnes', canActivate: [droit('CAMPAGNES_MARKETING')], loadComponent: () => import('./pages/campagnes-marketing/campagnes-marketing').then(m => m.CampagnesMarketing), title: 'Campagnes promotionnelles' },
      { path: 'marketing/campagnes/:id', canActivate: [droit('CAMPAGNES_MARKETING')], loadComponent: () => import('./pages/campagne-marketing/campagne-marketing').then(m => m.CampagneMarketingSuivi), title: 'Campagne promotionnelle' },
      { path: 'marketing/modeles', canActivate: [droit('MODELES_MARKETING')], data: { module: 'MARKETING' }, loadComponent: () => import('./pages/modeles/modeles').then(m => m.Modeles), title: 'Modèles promotionnels' },
      { path: 'marketing/informations', canActivate: [droit('INFORMATIONS')], loadComponent: () => import('./pages/informations/informations').then(m => m.Informations), title: 'Informations clients' },
      { path: 'marketing/evenements', canActivate: [droit('EVENEMENTS')], loadComponent: () => import('./pages/evenements/evenements').then(m => m.Evenements), title: 'Calendrier des événements' },
      { path: 'marketing/catalogue', canActivate: [droit('PRODUITS')], loadComponent: () => import('./pages/catalogue/catalogue').then(m => m.Catalogue), title: 'Catalogue des produits' },
      { path: 'marketing/catalogue/import', canActivate: [droit('PRODUITS_GERER')], data: { type: 'produits' }, loadComponent: () => import('./pages/assistant-import/assistant-import').then(m => m.AssistantImport), title: 'Importer des produits' },
      { path: 'marketing/promotions', canActivate: [droit('PROMOTIONS')], loadComponent: () => import('./pages/promotions/promotions').then(m => m.Promotions), title: 'Promotions' },
      { path: 'marketing/promotions/import', canActivate: [droit('PROMOTIONS_GERER')], data: { type: 'promotions' }, loadComponent: () => import('./pages/assistant-import/assistant-import').then(m => m.AssistantImport), title: 'Importer des produits en promotion' },
      { path: 'marketing/promotions/:id', canActivate: [droit('PROMOTIONS')], loadComponent: () => import('./pages/promotion/promotion').then(m => m.PromotionDetail), title: 'Promotion' },
      { path: 'marketing/numero', canActivate: [droit('NUMERO_MARKETING')], loadComponent: () => import('./pages/numeros/numeros').then(m => m.Numeros), title: 'Mon numéro WhatsApp' },
      { path: 'marketing/listes/:id', canActivate: [droit('LISTES')], loadComponent: () => import('./pages/liste-diffusion/liste-diffusion').then(m => m.ListeDiffusionDetail), title: 'Liste de diffusion' },
      { path: 'tableau-de-bord', canActivate: [droit('TABLEAU_BORD')], loadComponent: () => import('./pages/tableau-bord/tableau-bord').then(m => m.TableauBordPage), title: 'Tableau de bord' },
      { path: 'marketing/tableau-de-bord', canActivate: [droit('TABLEAU_BORD_MARKETING')], loadComponent: () => import('./pages/tableau-bord-marketing/tableau-bord-marketing').then(m => m.TableauBordMarketingPage), title: 'Tableau de bord Marketing' },
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
      { path: 'delais-relance', canActivate: [droit('DELAIS_RELANCE')], loadComponent: () => import('./pages/delais-relance/delais-relance').then(m => m.DelaisRelance), title: 'Délais de relance' },
      { path: 'modeles', canActivate: [droit('MODELES', 'MODELES_GERER')], loadComponent: () => import('./pages/modeles/modeles').then(m => m.Modeles), title: 'Modèles de messages' },
      { path: 'agences', canActivate: [droit('AGENCES')], loadComponent: () => import('./pages/agences/agences').then(m => m.Agences), title: 'Agences' },
      { path: 'numeros', canActivate: [droit('NUMEROS', 'NUMEROS_TOUS')], loadComponent: () => import('./pages/numeros/numeros').then(m => m.Numeros), title: 'Numéros WhatsApp' },
      { path: 'parametres', canActivate: [droit('PARAMETRES', 'REFERENTIELS', 'AGENCES', 'FOURNISSEURS_SMS')], loadComponent: () => import('./pages/parametres/parametres').then(m => m.Parametres), title: 'Paramètres' },
      { path: 'fournisseurs-sms', canActivate: [droit('FOURNISSEURS_SMS')], loadComponent: () => import('./pages/fournisseurs-sms/fournisseurs-sms').then(m => m.FournisseursSms), title: 'Fournisseurs SMS' },
      { path: 'utilisateurs', canActivate: [droit('UTILISATEURS')], loadComponent: () => import('./pages/utilisateurs/utilisateurs').then(m => m.Utilisateurs), title: 'Utilisateurs et habilitations' },
      { path: 'roles', canActivate: [droit('ROLES')], loadComponent: () => import('./pages/roles/roles').then(m => m.Roles), title: 'Rôles et droits' },
      { path: 'support', loadComponent: () => import('./pages/support/support').then(m => m.Support), title: 'Centre de support' },
      { path: 'mot-de-passe', loadComponent: () => import('./pages/mot-de-passe/mot-de-passe').then(m => m.MotDePasse), title: 'Mon mot de passe' },
      { path: 'discussions', canActivate: [droit('DISCUSSIONS')], loadComponent: () => import('./pages/discussions/discussions').then(m => m.Discussions), title: 'Discussions' },
      { path: 'historique-envois', canActivate: [droit('HISTORIQUE_ENVOIS')], loadComponent: () => import('./pages/historique-envois/historique-envois').then(m => m.HistoriqueEnvois), title: 'Historique des envois' },
      { path: 'envoi-masse', canActivate: [droit('ENVOI_MASSE')], loadComponent: () => import('./pages/envoi-masse/envoi-masse').then(m => m.EnvoiMassePage), title: 'Envoi WhatsApp en masse' },
      { path: 'reponses', canActivate: [droit('REPONSES')], loadComponent: () => import('./pages/reponses/reponses').then(m => m.Reponses), title: 'Réponses des clients' }
    ]
  },
  { path: '**', redirectTo: '' }
];
