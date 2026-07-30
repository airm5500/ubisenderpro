-- =====================================================================
-- UbiSenderPro - V63 : repertoire d'archivage des documents generes
--   Chaque document produit (PDF JasperReports, classeur Excel) est aussi
--   enregistre sur le serveur pour consultation ulterieure sans
--   reimpression : {repertoire}\pdf pour les PDF, {repertoire}\excel pour
--   les classeurs. Nom : <menu>_<ddMMyyyy>_<HHmmss??>.pdf|.xlsx
-- =====================================================================

INSERT INTO usp_parametre (cle, valeur, description, categorie, created_at)
SELECT 'archivage.repertoire', 'D:\\ARCHIVAGES',
       'Repertoire serveur d''archivage des documents generes (sous-dossiers pdf et excel)',
       'GENERAL', NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM usp_parametre WHERE cle = 'archivage.repertoire');
