package ci.ubipharm.crm.rest.service.impl;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import ci.ubipharm.crm.rest.service.CompteWhatsappService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompteWhatsappServiceImpl implements CompteWhatsappService {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public void retenir(String expediteur, String numero, String compteWhatsapp) {
        String ecrit = numero == null ? "" : numero.replaceAll("\\D", "");
        String compte = compteWhatsapp == null ? "" : compteWhatsapp.replaceAll("\\D", "");
        if (ecrit.isEmpty() || compte.isEmpty() || ecrit.equals(compte)) {
            return;
        }
        try {
            jdbc.update("""
                    insert into crm_compte_whatsapp (compte_whatsapp, numero, numero_expediteur, created_at)
                    values (:compte, :numero, :expediteur, utc_timestamp())
                    on duplicate key update numero = :numero, numero_expediteur = :expediteur, updated_at = utc_timestamp()
                    """, new MapSqlParameterSource().addValue("compte", compte).addValue("numero", ecrit)
                    .addValue("expediteur", expediteur));
        } catch (RuntimeException e) {
            // L'envoi a reussi : ne pas le faire passer pour un echec si la correspondance ne peut etre retenue
            log.warn("Compte WhatsApp {} du numero {} non retenu : {}", compte, ecrit, e.getMessage());
        }
    }

    @Override
    public Optional<String> numeroDuCompte(String compteWhatsapp) {
        List<String> numeros = jdbc.queryForList("select numero from crm_compte_whatsapp where compte_whatsapp = :compte",
                Map.of("compte", compteWhatsapp == null ? "" : compteWhatsapp), String.class);
        return numeros.stream().findFirst();
    }
}
