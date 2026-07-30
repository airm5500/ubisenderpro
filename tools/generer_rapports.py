#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Génère les modèles JasperReports (.jrxml) des listes de l'application dans
src/main/resources/reports/, tous sur le même gabarit visuel que clients.jrxml
(en-tête société + logo, bandeau de colonnes bleu, lignes alternées, pied de
page « Page X / Y », total).

Usage :  python3 tools/generer_rapports.py

Les fichiers générés sont du JRXML ordinaire : ils restent modifiables à la
main ou dans Jaspersoft Studio (notamment les exemplaires déposés dans le
répertoire des rapports, D:\\REPORTS par défaut). Relancer ce script écrase
UNIQUEMENT les modèles listés ici, dans src/main/resources/reports/ — jamais
ceux du répertoire externe.

clients.jrxml (écrit à la main, colonne E-mail plus large, etc.) n'est PAS
régénéré par ce script.
"""

import io
import os

# (champ, libellé, largeur, alignement 'Left'/'Right')
# La somme des largeurs doit valoir 555 (A4 portrait) ou 802 (A4 paysage).
RAPPORTS = {
    'campagnes': {
        'titre': 'Campagnes', 'paysage': True,
        'colonnes': [
            ('nom', 'Nom', 267, 'Left'),
            ('canal', 'Canal', 55, 'Left'),
            ('statut', 'Statut', 90, 'Left'),
            ('nbDestinataires', 'Cibles', 60, 'Right'),
            ('nbEnvoyes', 'Envoyés', 60, 'Right'),
            ('nbDistribues', 'Distribués', 65, 'Right'),
            ('nbLus', 'Lus', 55, 'Right'),
            ('nbEchoues', 'Échoués', 60, 'Right'),
            ('createurNom', 'Par', 90, 'Left'),
        ]},
    'catalogue_articles': {
        'titre': 'Catalogue articles', 'paysage': True,
        'colonnes': [
            ('pscode', 'PS Code', 70, 'Left'),
            ('designation', 'Désignation', 190, 'Left'),
            ('cip', 'CIP', 60, 'Left'),
            ('prixVente', 'Prix', 65, 'Right'),
            ('prixVentePublic', 'Prix public', 70, 'Right'),
            ('prixPromotionnel', 'Promo', 65, 'Right'),
            ('quantiteCommandee', 'Qté cmd', 55, 'Right'),
            ('quantiteUg', 'UG', 40, 'Right'),
            ('stockDisponible', 'Stock', 50, 'Right'),
            ('actif', 'Actif', 40, 'Left'),
            ('promotions', 'Promotions', 97, 'Left'),
        ]},
    'promotions_catalogue': {
        'titre': 'Promotions (catalogue)', 'paysage': False,
        'colonnes': [
            ('code', 'Code', 90, 'Left'),
            ('nom', 'Nom', 250, 'Left'),
            ('dateDebut', 'Début', 80, 'Left'),
            ('dateFin', 'Fin', 80, 'Left'),
            ('actif', 'Active', 55, 'Left'),
        ]},
    'promotions': {
        'titre': 'Promotions', 'paysage': False,
        'colonnes': [
            ('code', 'Code', 70, 'Left'),
            ('nom', 'Nom', 140, 'Left'),
            ('dateDebut', 'Début', 65, 'Left'),
            ('dateFin', 'Fin', 65, 'Left'),
            ('statut', 'Statut', 75, 'Left'),
            ('responsable', 'Responsable', 75, 'Left'),
            ('creePar', 'Par', 65, 'Left'),
        ]},
    'disponibilites': {
        'titre': 'Disponibilités & ruptures', 'paysage': True,
        'colonnes': [
            ('code', 'Code', 80, 'Left'),
            ('type', 'Type', 100, 'Left'),
            ('titre', 'Titre', 230, 'Left'),
            ('dateDebut', 'Début', 70, 'Left'),
            ('dateFin', 'Fin', 70, 'Left'),
            ('agence', 'Agence', 90, 'Left'),
            ('statut', 'Statut', 90, 'Left'),
            ('creePar', 'Par', 72, 'Left'),
        ]},
    'historique_envois': {
        'titre': 'Historique des envois', 'paysage': True,
        'colonnes': [
            ('date', 'Date', 90, 'Left'),
            ('canal', 'Canal', 45, 'Left'),
            ('type', 'Type', 80, 'Left'),
            ('libelle', 'Regroupement', 110, 'Left'),
            ('numero', 'Numéro', 90, 'Left'),
            ('nom', 'Nom', 100, 'Left'),
            ('utilisateur', 'Utilisateur', 80, 'Left'),
            ('apercu', 'Aperçu / erreur', 147, 'Left'),
            ('statut', 'Statut', 60, 'Left'),
        ]},
    'informations': {
        'titre': 'Informations clients', 'paysage': True,
        'colonnes': [
            ('code', 'Code', 80, 'Left'),
            ('type', 'Type', 120, 'Left'),
            ('titre', 'Titre', 240, 'Left'),
            ('priorite', 'Priorité', 70, 'Left'),
            ('agence', 'Agence', 90, 'Left'),
            ('dateEnvoi', 'Envoi', 70, 'Left'),
            ('statut', 'Statut', 80, 'Left'),
            ('creePar', 'Par', 52, 'Left'),
        ]},
    'rec_agences': {
        'titre': 'Recouvrement — point par agence', 'paysage': False,
        'colonnes': [
            ('agence', 'Agence', 135, 'Left'),
            ('encours', 'Encours', 70, 'Right'),
            ('recouvre', 'Encaissé', 70, 'Right'),
            ('solde', 'Solde', 70, 'Right'),
            ('tauxRecouvrement', 'Taux %', 50, 'Right'),
            ('clients', 'Clients', 50, 'Right'),
            ('facturesEchues', 'Fact. échues', 60, 'Right'),
            ('promesses', 'Promesses', 50, 'Right'),
        ]},
    'rec_encours': {
        'titre': 'Recouvrement — clients & encours', 'paysage': True,
        'colonnes': [
            ('numeroClient', 'N° client', 70, 'Left'),
            ('nomCompte', 'Client', 150, 'Left'),
            ('agence', 'Agence', 80, 'Left'),
            ('segmentCommercial', 'Segment', 75, 'Left'),
            ('profilPaiement', 'Profil paiement', 80, 'Left'),
            ('responsable', 'Responsable', 80, 'Left'),
            ('statut', 'Statut', 72, 'Left'),
            ('encoursInitial', 'Encours init.', 65, 'Right'),
            ('totalPaiements', 'Réglé', 65, 'Right'),
            ('solde', 'Solde', 65, 'Right'),
        ]},
    'rec_historique': {
        'titre': 'Recouvrement — historique des relances', 'paysage': True,
        'colonnes': [
            ('createdAt', 'Date', 90, 'Left'),
            ('canal', 'Canal', 60, 'Left'),
            ('destinataire', 'Destinataire', 100, 'Left'),
            ('message', 'Message', 262, 'Left'),
            ('pieceJointe', 'Pièce jointe', 90, 'Left'),
            ('statut', 'Statut', 60, 'Left'),
            ('erreur', 'Erreur', 90, 'Left'),
            ('creePar', 'Par', 50, 'Left'),
        ]},
    'utilisateurs': {
        'titre': 'Utilisateurs', 'paysage': False,
        'colonnes': [
            ('login', 'Login', 80, 'Left'),
            ('nomComplet', 'Nom complet', 130, 'Left'),
            ('email', 'E-mail', 130, 'Left'),
            ('roles', 'Rôles', 100, 'Left'),
            ('derniereConnexion', 'Dern. connexion', 75, 'Left'),
            ('actif', 'Actif', 40, 'Left'),
        ]},
    'connexions': {
        'titre': 'Historique des connexions', 'paysage': False,
        'colonnes': [
            ('login', 'Utilisateur', 85, 'Left'),
            ('connexionAt', 'Connexion', 80, 'Left'),
            ('deconnexionAt', 'Déconnexion', 80, 'Left'),
            ('dureeSecondes', 'Durée (s)', 70, 'Right'),
            ('ip', 'Adresse IP', 75, 'Left'),
            ('poste', 'Poste', 80, 'Left'),
            ('lieu', 'Lieu', 85, 'Left'),
        ]},
    'journal_actions': {
        'titre': "Journal d'actions", 'paysage': True,
        'colonnes': [
            ('createdAt', 'Date', 85, 'Left'),
            ('login', 'Utilisateur', 85, 'Left'),
            ('action', 'Action', 100, 'Left'),
            ('entite', 'Entité', 85, 'Left'),
            ('entiteId', 'Réf.', 40, 'Right'),
            ('details', 'Détails', 247, 'Left'),
            ('adresseIp', 'Adresse IP', 80, 'Left'),
            ('poste', 'Poste', 80, 'Left'),
        ]},
    'evolution': {
        'titre': 'Évolution des envois (30 jours)', 'paysage': False,
        'colonnes': [
            ('date', 'Jour', 111, 'Left'),
            ('campagnes', 'Campagnes', 111, 'Right'),
            ('waweb', 'WhatsApp Web (masse)', 111, 'Right'),
            ('api', 'Messages API', 111, 'Right'),
            ('discussions', 'Discussions (Web)', 111, 'Right'),
        ]},
}

ENTETE_XML = u'''<?xml version="1.0" encoding="UTF-8"?>
<!--
  UbiSenderPro — {titre} (impression PDF).

  GÉNÉRÉ par tools/generer_rapports.py : pour changer les colonnes du modèle
  standard, modifier le script puis le relancer. Pour une personnalisation
  locale SANS redéploiement, copier ce fichier dans le répertoire des rapports
  (paramètre « rapports.repertoire », D:\\REPORTS par défaut) et l'éditer :
  le fichier externe remplace celui-ci.

  Paramètres fournis par l'application : SOCIETE_NOM / SOCIETE_ADRESSE /
  SOCIETE_TEL / SOCIETE_SITE, LOGO (java.io.InputStream), TITRE, SOUS_TITRE.
-->
<jasperReport xmlns="http://jasperreports.sourceforge.net/jasperreports"
              xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
              xsi:schemaLocation="http://jasperreports.sourceforge.net/jasperreports http://jasperreports.sourceforge.net/xsd/jasperreport.xsd"
              name="{nom}" language="java"
              pageWidth="{pw}" pageHeight="{ph}" orientation="{orient}"
              columnWidth="{cw}" leftMargin="20" rightMargin="20" topMargin="16" bottomMargin="16"
              whenNoDataType="AllSectionsNoDetail" isSummaryNewPage="false">

    <style name="Titre" fontName="SansSerif" fontSize="15" isBold="true" forecolor="#1A3E63"/>
    <style name="Societe" fontName="SansSerif" fontSize="13" isBold="true" forecolor="#1976D2"/>
    <style name="Discret" fontName="SansSerif" fontSize="7" forecolor="#666666"/>
    <style name="Entete" fontName="SansSerif" fontSize="7" isBold="true" forecolor="#FFFFFF"/>
    <style name="Cellule" fontName="SansSerif" fontSize="7" forecolor="#222222"/>
    <style name="Zebra" mode="Transparent">
        <conditionalStyle>
            <conditionExpression><![CDATA[$V{{REPORT_COUNT}} % 2 == 0]]></conditionExpression>
            <style mode="Opaque" backcolor="#EFF4F9"/>
        </conditionalStyle>
    </style>

    <parameter name="SOCIETE_NOM" class="java.lang.String"><defaultValueExpression><![CDATA[""]]></defaultValueExpression></parameter>
    <parameter name="SOCIETE_ADRESSE" class="java.lang.String"><defaultValueExpression><![CDATA[""]]></defaultValueExpression></parameter>
    <parameter name="SOCIETE_TEL" class="java.lang.String"><defaultValueExpression><![CDATA[""]]></defaultValueExpression></parameter>
    <parameter name="SOCIETE_SITE" class="java.lang.String"><defaultValueExpression><![CDATA[""]]></defaultValueExpression></parameter>
    <parameter name="LOGO" class="java.io.InputStream" isForPrompting="false"/>
    <parameter name="TITRE" class="java.lang.String"><defaultValueExpression><![CDATA["{titre_java}"]]></defaultValueExpression></parameter>
    <parameter name="SOUS_TITRE" class="java.lang.String"><defaultValueExpression><![CDATA[""]]></defaultValueExpression></parameter>

{champs}
    <title>
        <band height="66" splitType="Stretch">
            <image scaleImage="RetainShape" hAlign="Left" vAlign="Middle" onErrorType="Blank">
                <reportElement x="0" y="2" width="100" height="42">
                    <printWhenExpression><![CDATA[$P{{LOGO}} != null]]></printWhenExpression>
                </reportElement>
                <imageExpression><![CDATA[$P{{LOGO}}]]></imageExpression>
            </image>
            <textField isBlankWhenNull="true">
                <reportElement style="Societe" x="110" y="4" width="{w_soc}" height="18"/>
                <textElement verticalAlignment="Middle"/>
                <textFieldExpression><![CDATA[$P{{SOCIETE_NOM}}]]></textFieldExpression>
            </textField>
            <textField isBlankWhenNull="true">
                <reportElement style="Discret" x="110" y="22" width="{w_soc}" height="11"/>
                <textFieldExpression><![CDATA[$P{{SOCIETE_ADRESSE}}]]></textFieldExpression>
            </textField>
            <textField isBlankWhenNull="true">
                <reportElement style="Discret" x="110" y="33" width="{w_soc}" height="11"/>
                <textFieldExpression><![CDATA[($P{{SOCIETE_TEL}}.isEmpty() ? "" : "Tél. " + $P{{SOCIETE_TEL}})
                    + (($P{{SOCIETE_TEL}}.isEmpty() || $P{{SOCIETE_SITE}}.isEmpty()) ? "" : "   ·   ")
                    + $P{{SOCIETE_SITE}}]]></textFieldExpression>
            </textField>
            <textField>
                <reportElement style="Titre" x="{x_titre}" y="4" width="{w_titre}" height="20"/>
                <textElement textAlignment="Right"/>
                <textFieldExpression><![CDATA[$P{{TITRE}}]]></textFieldExpression>
            </textField>
            <textField isBlankWhenNull="true">
                <reportElement style="Discret" x="{x_titre}" y="24" width="{w_titre}" height="11"/>
                <textElement textAlignment="Right"/>
                <textFieldExpression><![CDATA[$P{{SOUS_TITRE}}]]></textFieldExpression>
            </textField>
            <textField>
                <reportElement style="Discret" x="{x_titre}" y="35" width="{w_titre}" height="11"/>
                <textElement textAlignment="Right"/>
                <textFieldExpression><![CDATA["Édité le " + new java.text.SimpleDateFormat("dd/MM/yyyy 'à' HH:mm").format(new java.util.Date())]]></textFieldExpression>
            </textField>
            <line>
                <reportElement x="0" y="52" width="{cw}" height="1" forecolor="#1976D2"/>
                <graphicElement><pen lineWidth="1.6"/></graphicElement>
            </line>
        </band>
    </title>

    <columnHeader>
        <band height="18">
            <frame>
                <reportElement mode="Opaque" x="0" y="0" width="{cw}" height="18" backcolor="#1F4E79"/>
{entetes}            </frame>
        </band>
    </columnHeader>

    <detail>
        <band height="14" splitType="Prevent">
            <frame>
                <reportElement style="Zebra" stretchType="RelativeToBandHeight" x="0" y="0" width="{cw}" height="14"/>
                <box><bottomPen lineWidth="0.4" lineColor="#D5DEE8"/></box>
{cellules}            </frame>
        </band>
    </detail>

    <pageFooter>
        <band height="16">
            <line>
                <reportElement x="0" y="2" width="{cw}" height="1" forecolor="#C9D4E0"/>
                <graphicElement><pen lineWidth="0.5"/></graphicElement>
            </line>
            <textField>
                <reportElement style="Discret" x="0" y="4" width="300" height="11"/>
                <textFieldExpression><![CDATA["UbiSmartCRM Pro" + ($P{{SOCIETE_NOM}}.isEmpty() ? "" : " — " + $P{{SOCIETE_NOM}})]]></textFieldExpression>
            </textField>
            <textField>
                <reportElement style="Discret" x="{x_page}" y="4" width="100" height="11"/>
                <textElement textAlignment="Right"/>
                <textFieldExpression><![CDATA["Page " + $V{{PAGE_NUMBER}} + " /"]]></textFieldExpression>
            </textField>
            <textField evaluationTime="Report">
                <reportElement style="Discret" x="{x_total}" y="4" width="38" height="11"/>
                <textElement textAlignment="Left"/>
                <textFieldExpression><![CDATA[" " + $V{{PAGE_NUMBER}}]]></textFieldExpression>
            </textField>
        </band>
    </pageFooter>

    <summary>
        <band height="18">
            <textField>
                <reportElement style="Cellule" x="0" y="4" width="400" height="12" forecolor="#1A3E63">
                    <printWhenExpression><![CDATA[$V{{REPORT_COUNT}} > 0]]></printWhenExpression>
                </reportElement>
                <textElement><font isBold="true"/></textElement>
                <textFieldExpression><![CDATA["Total : " + $V{{REPORT_COUNT}} + " ligne(s)"]]></textFieldExpression>
            </textField>
            <staticText>
                <reportElement style="Cellule" x="0" y="4" width="400" height="12">
                    <printWhenExpression><![CDATA[$V{{REPORT_COUNT}} == 0]]></printWhenExpression>
                </reportElement>
                <text><![CDATA[Aucune ligne à imprimer.]]></text>
            </staticText>
        </band>
    </summary>
</jasperReport>
'''


def xml_escape(s):
    return s.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')


def generer(nom, spec):
    cols = spec['colonnes']
    paysage = spec['paysage']
    cw = 802 if paysage else 555
    somme = sum(c[2] for c in cols)
    assert somme == cw, '%s : somme des largeurs %d != %d' % (nom, somme, cw)

    champs = ''.join('    <field name="%s" class="java.lang.String"/>\n' % c[0] for c in cols)

    entetes, cellules = [], []
    x = 0
    for (champ, libelle, largeur, align) in cols:
        # 3 px de marge interne à gauche, 4 px repris à droite.
        entetes.append(
            '                <staticText>\n'
            '                    <reportElement style="Entete" x="%d" y="0" width="%d" height="18"/>\n'
            '                    <textElement verticalAlignment="Middle" textAlignment="%s"/>'
            '<text><![CDATA[%s]]></text>\n'
            '                </staticText>\n' % (x + 3, largeur - 7, align, xml_escape(libelle)))
        cellules.append(
            '                <textField textAdjust="CutText" isBlankWhenNull="true">\n'
            '                    <reportElement style="Cellule" x="%d" y="1" width="%d" height="12"/>\n'
            '                    <textElement textAlignment="%s"/>\n'
            '                    <textFieldExpression><![CDATA[$F{%s}]]></textFieldExpression>\n'
            '                </textField>\n' % (x + 3, largeur - 7, align, champ))
        x += largeur

    titre_java = spec['titre'].replace('"', '\\"')
    contenu = ENTETE_XML.format(
        nom=nom, titre=spec['titre'], titre_java=titre_java,
        pw=842 if paysage else 595, ph=595 if paysage else 842,
        orient='Landscape' if paysage else 'Portrait', cw=cw,
        w_soc=min(380, cw - 320), x_titre=cw - 312, w_titre=312,
        x_page=cw - 140, x_total=cw - 38,
        champs=champs, entetes=''.join(entetes), cellules=''.join(cellules))
    return contenu


def main():
    base = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'reports')
    for nom, spec in sorted(RAPPORTS.items()):
        chemin = os.path.join(base, nom + '.jrxml')
        with io.open(chemin, 'w', encoding='utf-8') as f:
            f.write(generer(nom, spec))
        print('OK  %s.jrxml (%d colonnes, %s)' % (nom, len(spec['colonnes']),
              'paysage' if spec['paysage'] else 'portrait'))


if __name__ == '__main__':
    main()
