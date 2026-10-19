package ci.ubipharm.crm.rest.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Erreur JavaScript remontee par le navigateur ; les champs trop longs sont tronques a l'enregistrement. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SupportErreurNavigateurDTO {

    private String type;
    private String message;
    private String pile;
    private String url;
}
