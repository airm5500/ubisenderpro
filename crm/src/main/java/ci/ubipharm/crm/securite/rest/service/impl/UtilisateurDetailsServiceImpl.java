package ci.ubipharm.crm.securite.rest.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ci.ubipharm.crm.securite.rest.service.dto.UtilisateurPrincipal;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UtilisateurDetailsServiceImpl implements UserDetailsService {

    static final String PREFIXE_ROLE = "ROLE_";
    static final String PREFIXE_MODULE = "MODULE_";
    static final String PREFIXE_DROIT = "DROIT_";

    private final EntityManager em;

    @Override
    @Transactional(readOnly = true)
    public UtilisateurPrincipal loadUserByUsername(String login) {
        List<Tuple> lignes = em.createNativeQuery("""
                select u.id as id, u.login as login, u.nom_complet as nomComplet,
                       u.mot_de_passe_hash as motDePasseHash, u.statut as statut
                from crm_utilisateur u
                where u.login = :login and u.statut <> 'ARCHIVE'
                """, Tuple.class)
                .setParameter("login", login)
                .getResultList();
        if (lignes.isEmpty()) {
            throw new UsernameNotFoundException("Utilisateur inconnu");
        }
        Tuple utilisateur = lignes.getFirst();
        return mapTupleToPrincipal(utilisateur, droits(utilisateur.get("id", Long.class)));
    }

    private List<SimpleGrantedAuthority> droits(Long utilisateurId) {
        List<Tuple> habilitations = em.createNativeQuery("""
                select r.code as role, m.code as module
                from crm_habilitation h
                join crm_role r on r.id = h.role_id and r.actif = true
                left join crm_module m on m.id = h.module_id
                where h.utilisateur_id = :id and h.actif = true
                """, Tuple.class)
                .setParameter("id", utilisateurId)
                .getResultList();
        Set<String> droits = new LinkedHashSet<>();
        for (Tuple h : habilitations) {
            droits.add(PREFIXE_ROLE + h.get("role", String.class));
            String module = h.get("module", String.class);
            if (module != null) {
                droits.add(PREFIXE_MODULE + module);
            } else {
                tousLesModules().forEach(m -> droits.add(PREFIXE_MODULE + m));
            }
        }
        // Un droit d'un module ne vient que d'une habilitation sur ce module (ou sur tous les modules)
        @SuppressWarnings("unchecked")
        List<String> codesDroits = em.createNativeQuery("""
                select distinct d.code
                from crm_habilitation h
                join crm_role r on r.id = h.role_id and r.actif = true
                join crm_role_droit rd on rd.role_id = r.id and rd.actif = true
                join crm_droit d on d.id = rd.droit_id
                where h.utilisateur_id = :id and h.actif = true
                  and (d.module_id is null or h.module_id is null or d.module_id = h.module_id)
                order by d.code
                """).setParameter("id", utilisateurId).getResultList();
        codesDroits.forEach(d -> droits.add(PREFIXE_DROIT + d));
        List<SimpleGrantedAuthority> autorites = new ArrayList<>();
        droits.forEach(d -> autorites.add(new SimpleGrantedAuthority(d)));
        return autorites;
    }

    @SuppressWarnings("unchecked")
    private List<String> tousLesModules() {
        return em.createNativeQuery("select code from crm_module where actif = true order by id").getResultList();
    }

    private UtilisateurPrincipal mapTupleToPrincipal(Tuple tuple, List<SimpleGrantedAuthority> droits) {
        return UtilisateurPrincipal.builder()
                .id(tuple.get("id", Long.class))
                .login(tuple.get("login", String.class))
                .nomComplet(tuple.get("nomComplet", String.class))
                .motDePasseHash(tuple.get("motDePasseHash", String.class))
                .actif("ACTIF".equals(tuple.get("statut", String.class)))
                .authorities(droits)
                .build();
    }
}
