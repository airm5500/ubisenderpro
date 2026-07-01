package com.ubisenderpro.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.ubisenderpro.entity.Client;
import com.ubisenderpro.entity.RecCreance;
import com.ubisenderpro.entity.RecPaiement;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Génération d'un relevé de compte PDF pour un client : en-tête société, situation
 * (encours, factures, avoirs, règlements, solde), détail des créances et paiements.
 * Sert de pièce jointe optionnelle aux relances (e-mail / WhatsApp).
 */
@Stateless
public class RecRelevePdfService {

    @PersistenceContext(unitName = "ubisenderproPU")
    private EntityManager em;

    @EJB
    private RecFicheService ficheService;
    @EJB
    private ParametreService parametreService;

    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Nom de fichier suggéré pour le relevé d'un client. */
    public String nomFichier(Long clientId) {
        Client c = em.find(Client.class, clientId);
        String ref = c == null ? String.valueOf(clientId)
                : (c.getNumeroClient() != null && !c.getNumeroClient().isEmpty()
                    ? c.getNumeroClient() : String.valueOf(clientId));
        return "releve-compte-" + ref.replaceAll("[^A-Za-z0-9_-]", "_") + ".pdf";
    }

    /** Construit le relevé de compte PDF du client et renvoie le binaire. */
    public byte[] genererReleve(Long clientId) {
        Client client = em.find(Client.class, clientId);
        java.util.Map<String, Object> s = ficheService.situation(clientId);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 40, 40, 48, 40);
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();

            Font titre = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, new Color(0x1f, 0x2d, 0x3d));
            Font sousTitre = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, new Color(0x1f, 0x2d, 0x3d));
            Font normal = FontFactory.getFont(FontFactory.HELVETICA, 10);
            Font petit = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);

            String societe = parametreService.valeur("app.societe", "");
            if (societe != null && !societe.trim().isEmpty()) {
                Paragraph entete = new Paragraph(societe.trim(), titre);
                doc.add(entete);
            }
            Paragraph t = new Paragraph("Relevé de compte", titre);
            t.setSpacingBefore(4);
            doc.add(t);
            Paragraph dateEdition = new Paragraph("Édité le " + LocalDate.now().format(DF), petit);
            dateEdition.setSpacingAfter(12);
            doc.add(dateEdition);

            // Bloc client.
            doc.add(new Paragraph("Client", sousTitre));
            String nom = client == null ? "" : nz(client.getNomCompte());
            String num = client == null ? "" : nz(client.getNumeroClient());
            String ag = client == null ? "" : nz(client.getAgence());
            Paragraph infosClient = new Paragraph();
            infosClient.setFont(normal);
            infosClient.add(nom + (num.isEmpty() ? "" : "  (N° " + num + ")") + "\n");
            if (!ag.isEmpty()) { infosClient.add("Agence : " + ag + "\n"); }
            infosClient.setSpacingAfter(12);
            doc.add(infosClient);

            // Synthèse de la situation.
            doc.add(new Paragraph("Situation", sousTitre));
            PdfPTable synth = new PdfPTable(2);
            synth.setWidthPercentage(60);
            synth.setHorizontalAlignment(Element.ALIGN_LEFT);
            synth.setSpacingBefore(4);
            synth.setSpacingAfter(14);
            ligneSynthese(synth, "Encours initial", (BigDecimal) s.get("encoursInitial"), normal, false);
            ligneSynthese(synth, "Total factures", (BigDecimal) s.get("totalFactures"), normal, false);
            ligneSynthese(synth, "Total avoirs", (BigDecimal) s.get("totalAvoirs"), normal, false);
            ligneSynthese(synth, "Total règlements", (BigDecimal) s.get("totalPaiements"), normal, false);
            ligneSynthese(synth, "Solde dû", (BigDecimal) s.get("solde"), sousTitre, true);
            doc.add(synth);

            // Détail des créances.
            List<RecCreance> creances = em.createQuery(
                    "SELECT c FROM RecCreance c WHERE c.clientId = :id ORDER BY c.dateEcheance ASC, c.id ASC",
                    RecCreance.class).setParameter("id", clientId).getResultList();
            doc.add(new Paragraph("Factures et avoirs", sousTitre));
            if (creances.isEmpty()) {
                Paragraph aucun = new Paragraph("Aucune créance enregistrée.", petit);
                aucun.setSpacingAfter(12);
                doc.add(aucun);
            } else {
                PdfPTable tbl = new PdfPTable(new float[]{2.2f, 1.4f, 1.6f, 1.6f, 1.6f});
                tbl.setWidthPercentage(100);
                tbl.setSpacingBefore(4);
                tbl.setSpacingAfter(14);
                entete(tbl, normal, "Numéro", "Type", "Émission", "Échéance", "Montant");
                for (RecCreance c : creances) {
                    cellule(tbl, normal, nz(c.getNumero()), Element.ALIGN_LEFT);
                    cellule(tbl, normal, "AVOIR".equalsIgnoreCase(c.getType()) ? "Avoir" : "Facture", Element.ALIGN_LEFT);
                    cellule(tbl, normal, c.getDateEmission() == null ? "" : c.getDateEmission().format(DF), Element.ALIGN_CENTER);
                    cellule(tbl, normal, c.getDateEcheance() == null ? "" : c.getDateEcheance().format(DF), Element.ALIGN_CENTER);
                    cellule(tbl, normal, montant(c.getMontant()), Element.ALIGN_RIGHT);
                }
                doc.add(tbl);
            }

            // Détail des règlements.
            List<RecPaiement> paiements = em.createQuery(
                    "SELECT p FROM RecPaiement p WHERE p.clientId = :id ORDER BY p.datePaiement ASC, p.id ASC",
                    RecPaiement.class).setParameter("id", clientId).getResultList();
            doc.add(new Paragraph("Règlements", sousTitre));
            if (paiements.isEmpty()) {
                Paragraph aucun = new Paragraph("Aucun règlement enregistré.", petit);
                aucun.setSpacingAfter(12);
                doc.add(aucun);
            } else {
                PdfPTable tbl = new PdfPTable(new float[]{1.6f, 1.8f, 2.2f, 1.6f});
                tbl.setWidthPercentage(100);
                tbl.setSpacingBefore(4);
                tbl.setSpacingAfter(14);
                entete(tbl, normal, "Date", "Mode", "Référence", "Montant");
                for (RecPaiement p : paiements) {
                    cellule(tbl, normal, p.getDatePaiement() == null ? "" : p.getDatePaiement().format(DF), Element.ALIGN_CENTER);
                    cellule(tbl, normal, nz(p.getMode()), Element.ALIGN_LEFT);
                    cellule(tbl, normal, nz(p.getReference()), Element.ALIGN_LEFT);
                    cellule(tbl, normal, montant(p.getMontant()), Element.ALIGN_RIGHT);
                }
                doc.add(tbl);
            }

            Paragraph pied = new Paragraph(
                    "Ce relevé est édité automatiquement. Pour toute question, contactez votre service recouvrement.", petit);
            pied.setSpacingBefore(10);
            doc.add(pied);

            doc.close();
        } catch (Exception e) {
            if (doc.isOpen()) { doc.close(); }
            throw new IllegalStateException("Échec de génération du relevé PDF : " + e.getMessage(), e);
        }
        return out.toByteArray();
    }

    private void ligneSynthese(PdfPTable tbl, String libelle, BigDecimal valeur, Font font, boolean fond) {
        PdfPCell c1 = new PdfPCell(new Phrase(libelle, font));
        PdfPCell c2 = new PdfPCell(new Phrase(montant(valeur), font));
        c2.setHorizontalAlignment(Element.ALIGN_RIGHT);
        if (fond) {
            Color f = new Color(0xee, 0xf3, 0xfb);
            c1.setBackgroundColor(f);
            c2.setBackgroundColor(f);
        }
        c1.setPadding(5);
        c2.setPadding(5);
        tbl.addCell(c1);
        tbl.addCell(c2);
    }

    private void entete(PdfPTable tbl, Font base, String... titres) {
        Font f = FontFactory.getFont(FontFactory.HELVETICA_BOLD, base.getSize(), Color.WHITE);
        Color fond = new Color(0x2d, 0x6c, 0xdf);
        for (String titre : titres) {
            PdfPCell c = new PdfPCell(new Phrase(titre, f));
            c.setBackgroundColor(fond);
            c.setPadding(5);
            tbl.addCell(c);
        }
    }

    private void cellule(PdfPTable tbl, Font font, String texte, int alignement) {
        PdfPCell c = new PdfPCell(new Phrase(texte == null ? "" : texte, font));
        c.setHorizontalAlignment(alignement);
        c.setPadding(4);
        tbl.addCell(c);
    }

    private String montant(BigDecimal b) {
        if (b == null) { return "0"; }
        return String.format("%,.2f", b).replace(',', ' ').replace('.', ',');
    }

    private String nz(String s) { return s == null ? "" : s; }
}
