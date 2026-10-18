# Interface UBIPHARM CRM (Angular)

Angular 21 + PrimeNG 21, d'apres la maquette validee (29 ecrans). Node 20.19+ ou 22.12+.

```
npm install          # legacy-peer-deps est regle dans .npmrc
npm start            # http://localhost:4200, /api relaye vers Spring Boot sur :8080 (proxy.conf.json)
npm test             # tests unitaires (Vitest)
npm run build        # dist/front/browser, embarque ensuite dans le jar par Maven
```

Organisation : `src/app/core` (acces API, session, gardes, formats), `src/app/shell` (menu par profil),
`src/app/pages/<ecran>`. La charte (couleurs, boutons, tableaux) est dans `src/styles.css`, partagee par tous
les ecrans ; PrimeNG ne sert qu'aux notifications et aux fenetres.
