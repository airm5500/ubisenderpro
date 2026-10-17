package ci.ubipharm.crm.commun.rest.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ci.ubipharm.crm.commun.rest.service.ParametreService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ParametreServiceImpl implements ParametreService {

    private final EntityManager em;

    @Override
    @Transactional(readOnly = true)
    public String valeur(String cle, String parDefaut) {
        @SuppressWarnings("unchecked")
        List<String> valeurs = em.createNativeQuery("select valeur from crm_parametre where cle = :cle")
                .setParameter("cle", cle)
                .getResultList();
        return valeurs.isEmpty() || valeurs.getFirst() == null || valeurs.getFirst().isBlank() ? parDefaut : valeurs.getFirst().trim();
    }

    @Override
    public int entier(String cle, int parDefaut) {
        try {
            return Integer.parseInt(valeur(cle, String.valueOf(parDefaut)));
        } catch (NumberFormatException e) {
            return parDefaut;
        }
    }
}
