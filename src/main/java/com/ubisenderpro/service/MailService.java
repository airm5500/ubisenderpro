package com.ubisenderpro.service;

import javax.ejb.Asynchronous;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.activation.DataHandler;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Envoi d'e-mails via SMTP (paramètres mail.smtp.* configurables dans l'UI).
 * Best-effort : si le SMTP n'est pas configuré, l'envoi est ignoré sans erreur.
 */
@Stateless
public class MailService {

    private static final Logger LOG = Logger.getLogger(MailService.class.getName());

    @EJB
    private ParametreService parametreService;

    /** Vrai si un serveur SMTP est configuré. */
    public boolean estConfigure() {
        String host = parametreService.valeur("mail.smtp.host", "");
        return host != null && !host.trim().isEmpty();
    }

    /** Envoi asynchrone (ne bloque pas le traitement du message entrant). */
    @Asynchronous
    public void envoyer(List<String> destinataires, String sujet, String corps) {
        envoyerInterne(destinataires, sujet, corps, null);
    }

    /**
     * Envoi asynchrone avec pièces jointes (relance e-mail du module Recouvrement).
     * Best-effort comme {@link #envoyer} : aucune exception remontée à l'appelant.
     */
    @Asynchronous
    public void envoyerAvecPieces(List<String> destinataires, String sujet, String corps,
                                  List<PieceJointe> pieces) {
        envoyerInterne(destinataires, sujet, corps, pieces);
    }

    private void envoyerInterne(List<String> destinataires, String sujet, String corps,
                                List<PieceJointe> pieces) {
        if (destinataires == null || destinataires.isEmpty()) { return; }
        String host = parametreService.valeur("mail.smtp.host", "");
        if (host == null || host.trim().isEmpty()) { return; }
        try {
            final String user = parametreService.valeur("mail.smtp.user", "");
            final String pwd = parametreService.valeur("mail.smtp.password", "");
            String port = parametreService.valeur("mail.smtp.port", "587");
            String from = parametreService.valeur("mail.smtp.from", "");
            boolean tls = "true".equalsIgnoreCase(parametreService.valeur("mail.smtp.tls", "true"));
            if (from == null || from.isEmpty()) { from = user; }

            Properties props = new Properties();
            props.put("mail.smtp.host", host);
            props.put("mail.smtp.port", port);
            props.put("mail.smtp.auth", String.valueOf(user != null && !user.isEmpty()));
            props.put("mail.smtp.starttls.enable", String.valueOf(tls));

            Session session = (user != null && !user.isEmpty())
                    ? Session.getInstance(props, new Authenticator() {
                        protected PasswordAuthentication getPasswordAuthentication() {
                            return new PasswordAuthentication(user, pwd);
                        }
                    })
                    : Session.getInstance(props);

            MimeMessage msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(from));
            for (String d : destinataires) {
                if (d != null && !d.trim().isEmpty()) {
                    msg.addRecipient(Message.RecipientType.TO, new InternetAddress(d.trim()));
                }
            }
            msg.setSubject(sujet, "UTF-8");

            boolean avecPieces = pieces != null && !pieces.isEmpty();
            if (!avecPieces) {
                msg.setText(corps == null ? "" : corps, "UTF-8");
            } else {
                MimeMultipart multipart = new MimeMultipart();
                MimeBodyPart texte = new MimeBodyPart();
                texte.setText(corps == null ? "" : corps, "UTF-8");
                multipart.addBodyPart(texte);
                for (PieceJointe p : pieces) {
                    if (p == null || p.contenu == null || p.contenu.length == 0) { continue; }
                    MimeBodyPart piece = new MimeBodyPart();
                    String mime = (p.mimeType == null || p.mimeType.isEmpty())
                            ? "application/octet-stream" : p.mimeType;
                    piece.setDataHandler(new DataHandler(new ByteArrayDataSource(p.contenu, mime)));
                    piece.setFileName(p.nomFichier == null || p.nomFichier.isEmpty()
                            ? "piece-jointe" : p.nomFichier);
                    multipart.addBodyPart(piece);
                }
                msg.setContent(multipart);
            }
            Transport.send(msg);
            LOG.info("E-mail envoyé à " + destinataires.size() + " destinataire(s) : " + sujet
                    + (avecPieces ? " (" + pieces.size() + " pièce(s) jointe(s))" : ""));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Échec d'envoi d'e-mail (" + sujet + ")", e);
        }
    }

    /** Pièce jointe d'un e-mail (contenu binaire + métadonnées). */
    public static final class PieceJointe {
        public final byte[] contenu;
        public final String nomFichier;
        public final String mimeType;

        public PieceJointe(byte[] contenu, String nomFichier, String mimeType) {
            this.contenu = contenu;
            this.nomFichier = nomFichier;
            this.mimeType = mimeType;
        }
    }
}
