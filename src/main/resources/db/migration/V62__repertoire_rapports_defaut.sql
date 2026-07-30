-- =====================================================================
-- UbiSenderPro - V62 : repertoire des rapports par defaut = D:\REPORTS
--   Le parametre etait cree vide en V61 ; la valeur par defaut convenue
--   est D:\REPORTS (les modeles .jrxml embarques y sont deposes au
--   demarrage s'ils n'y figurent pas deja, pour etre personnalisables).
--   On ne touche pas a une valeur deja renseignee par l'exploitant.
-- =====================================================================

UPDATE usp_parametre
SET valeur = 'D:\\REPORTS',
    description = 'Repertoire serveur des modeles de rapport .jrxml (les modeles standard y sont deposes au demarrage)'
WHERE cle = 'rapports.repertoire'
  AND (valeur IS NULL OR valeur = '');
