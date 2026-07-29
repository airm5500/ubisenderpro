-- =====================================================================
-- UbiSenderPro - V61 : repertoire des modeles de rapport (.jrxml)
--   Les impressions PDF sont generees par JasperReports a partir de
--   modeles .jrxml. Les modeles standard sont embarques dans le livrable ;
--   ce parametre designe un repertoire du serveur ou deposer des modeles
--   personnalises : un fichier du meme nom y remplace le modele embarque,
--   sans redeploiement.
-- =====================================================================

INSERT INTO usp_parametre (cle, valeur, description, categorie, created_at)
SELECT 'rapports.repertoire', '',
       'Repertoire serveur des modeles de rapport .jrxml (vide = modeles embarques)',
       'GENERAL', NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM usp_parametre WHERE cle = 'rapports.repertoire');
