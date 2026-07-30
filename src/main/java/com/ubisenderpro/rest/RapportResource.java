package com.ubisenderpro.rest;

import com.ubisenderpro.entity.Client;
import com.ubisenderpro.entity.SegmentationClient;
import com.ubisenderpro.security.Secured;
import com.ubisenderpro.service.ClientService;
import com.ubisenderpro.service.RapportService;
import com.ubisenderpro.service.SegmentationService;

import javax.ejb.EJB;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.ubisenderpro.service.RapportService.texte;

/**
 * Éditions PDF générées côté serveur sur modèles JasperReports (.jrxml).
 * Chaque édition reçoit les mêmes filtres que l'écran qui la demande : le
 * document imprime exactement ce que la grille affiche.
 */
@Path("/rapports")
@Secured
@Produces("application/pdf")
public class RapportResource {

    @EJB
    private RapportService rapportService;
    @EJB
    private com.ubisenderpro.service.RapportExcelService rapportExcelService;
    @EJB
    private ClientService clientService;
    @EJB
    private SegmentationService segmentationService;

    /** Liste des comptes clients (actifs ou désactivés), filtres de l'écran compris. */
    @GET
    @Path("/clients")
    @Secured(menu = "clients")
    public Response clients(@QueryParam("q") String q,
                            @QueryParam("agence") String agence,
                            @QueryParam("region") String region,
                            @QueryParam("commune") String commune,
                            @QueryParam("tournee") String tournee,
                            @QueryParam("segmentationId") Long segmentationId,
                            @QueryParam("actif") Boolean actif) {
        boolean actifs = actif == null || actif;
        List<Client> clients = clientService.rechercher(q, agence, region, commune, tournee,
                segmentationId, actifs, 0, 100000).getData();

        Map<Long, String> segs = new HashMap<>();
        for (SegmentationClient s : segmentationService.lister()) {
            segs.put(s.getId(), s.getLibelle());
        }

        List<Map<String, ?>> lignes = new ArrayList<>();
        for (Client c : clients) {
            Map<String, Object> l = RapportService.ligne();
            l.put("code", texte(c.getNumeroClient()));
            l.put("nom", texte(c.getNomCompte()));
            l.put("entreprise", texte(c.getEntreprise()));
            l.put("telephone", texte(c.getTelephonePrincipal()));
            l.put("email", texte(c.getEmailPrincipal()));
            l.put("segmentation", texte(segs.get(c.getSegmentationId())));
            l.put("agence", texte(c.getAgence()));
            l.put("region", texte(c.getRegion()));
            l.put("tournee", texte(c.getTournee()));
            lignes.add(l);
        }

        Map<String, Object> params = new HashMap<>();
        params.put("TITRE", actifs ? "Comptes clients" : "Clients désactivés");
        params.put("SOUS_TITRE", sousTitre(q, agence, region, tournee,
                segmentationId == null ? null : segs.get(segmentationId)));

        byte[] pdf = rapportService.pdf("clients", params, lignes);
        return Response.ok(pdf)
                .header("Content-Disposition", "inline; filename=\"comptes_clients.pdf\"")
                .build();
    }

    /**
     * Édition générique d'une liste : l'écran envoie le titre et les lignes
     * telles qu'affichées (mêmes valeurs que l'export CSV), le serveur les
     * coule dans le modèle .jrxml du même nom. Chaque écran garde ainsi son
     * fichier de mise en page dédié, personnalisable dans le répertoire des
     * rapports, sans qu'il faille un endpoint par écran.
     */
    @javax.ws.rs.POST
    @Path("/liste/{nom}")
    @javax.ws.rs.Consumes(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    public Response liste(@javax.ws.rs.PathParam("nom") String nom, Map<String, Object> body) {
        Map<String, Object> params = new HashMap<>();
        Object titre = body == null ? null : body.get("titre");
        Object sousTitre = body == null ? null : body.get("sousTitre");
        if (titre != null && !String.valueOf(titre).isEmpty()) { params.put("TITRE", String.valueOf(titre)); }
        params.put("SOUS_TITRE", sousTitre == null ? "" : String.valueOf(sousTitre));

        List<Map<String, ?>> lignes = new ArrayList<>();
        Object brut = body == null ? null : body.get("lignes");
        if (brut instanceof List) {
            for (Object o : (List<?>) brut) {
                if (!(o instanceof Map)) { continue; }
                Map<String, Object> l = RapportService.ligne();
                for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet()) {
                    l.put(String.valueOf(e.getKey()), texte(e.getValue()));
                }
                lignes.add(l);
            }
        }
        byte[] pdf = rapportService.pdf(nom, params, lignes);
        return Response.ok(pdf)
                .header("Content-Disposition", "inline; filename=\"" + nom + ".pdf\"")
                .build();
    }

    /**
     * Export Excel (.xlsx) générique : colonnes + lignes telles qu'affichées.
     * Le classeur est archivé côté serveur (sous-dossier excel) puis renvoyé.
     */
    @javax.ws.rs.POST
    @Path("/excel")
    @javax.ws.rs.Consumes(javax.ws.rs.core.MediaType.APPLICATION_JSON)
    @Produces("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public Response excel(Map<String, Object> body) {
        String titre = body == null || body.get("titre") == null ? "" : String.valueOf(body.get("titre"));
        List<Map<String, String>> colonnes = new ArrayList<>();
        Object bc = body == null ? null : body.get("colonnes");
        if (bc instanceof List) {
            for (Object o : (List<?>) bc) {
                if (!(o instanceof Map)) { continue; }
                Map<String, String> c = new HashMap<>();
                for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet()) {
                    c.put(String.valueOf(e.getKey()), texte(e.getValue()));
                }
                colonnes.add(c);
            }
        }
        List<Map<String, ?>> lignes = new ArrayList<>();
        Object bl = body == null ? null : body.get("lignes");
        if (bl instanceof List) {
            for (Object o : (List<?>) bl) {
                if (!(o instanceof Map)) { continue; }
                Map<String, Object> l = RapportService.ligne();
                for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet()) {
                    l.put(String.valueOf(e.getKey()), texte(e.getValue()));
                }
                lignes.add(l);
            }
        }
        byte[] xlsx = rapportExcelService.xlsx(titre, colonnes, lignes);
        return Response.ok(xlsx)
                .header("Content-Disposition", "attachment; filename=\""
                        + RapportService.nomArchive(titre, "xlsx") + "\"")
                .build();
    }

    /** Rappel des filtres appliqués, imprimé sous le titre (vide si aucun). */
    private String sousTitre(String q, String agence, String region, String tournee, String segmentation) {
        List<String> parts = new ArrayList<>();
        if (q != null && !q.trim().isEmpty()) { parts.add("Recherche « " + q.trim() + " »"); }
        if (segmentation != null && !segmentation.isEmpty()) { parts.add("Segmentation " + segmentation); }
        if (agence != null && !agence.isEmpty()) { parts.add("Agence " + agence); }
        if (region != null && !region.isEmpty()) { parts.add("Région " + region); }
        if (tournee != null && !tournee.isEmpty()) { parts.add("Tournée " + tournee); }
        return parts.isEmpty() ? "" : "Filtres : " + String.join(" · ", parts);
    }
}
