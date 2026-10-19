import { ApplicationConfig, ErrorHandler, LOCALE_ID, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withInterceptors, withXsrfConfiguration } from '@angular/common/http';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { providePrimeNG } from 'primeng/config';
import { definePreset } from '@primeuix/themes';
import Aura from '@primeuix/themes/aura';
import { ConfirmationService, MessageService } from 'primeng/api';

import { routes } from './app.routes';
import { intercepteurErreurs } from './core/intercepteur';
import { ErreursNavigateur } from './core/erreurs-navigateur';

registerLocaleData(localeFr);

/** Theme PrimeNG aligne sur la charte : primaire bleu nuit, police Manrope. */
const Charte = definePreset(Aura, {
  semantic: {
    primary: {
      50: '#eef3f7', 100: '#d5e1ea', 200: '#abc3d5', 300: '#7fa3bd', 400: '#4f7c9c', 500: '#05253D',
      600: '#052137', 700: '#041c2f', 800: '#031726', 900: '#02111d', 950: '#010a12'
    }
  }
});

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // Erreurs JavaScript remontees au diagnostic du centre de support
    { provide: ErrorHandler, useClass: ErreursNavigateur },
    provideRouter(routes, withComponentInputBinding()),
    // Spring Security (csrf.spa) : cookie XSRF-TOKEN, en-tete X-XSRF-TOKEN
    provideHttpClient(withXsrfConfiguration({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' }), withInterceptors([intercepteurErreurs])),
    providePrimeNG({
      theme: { preset: Charte, options: { darkModeSelector: false } },
      translation: {
        accept: 'Confirmer', reject: 'Annuler', dayNames: ['dimanche', 'lundi', 'mardi', 'mercredi', 'jeudi', 'vendredi', 'samedi'],
        monthNames: ['janvier', 'février', 'mars', 'avril', 'mai', 'juin', 'juillet', 'août', 'septembre', 'octobre', 'novembre', 'décembre']
      }
    }),
    MessageService,
    ConfirmationService,
    { provide: LOCALE_ID, useValue: 'fr' }
  ]
};
