package ci.ubipharm.crm.rest.service.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Erreur inattendue dedoublonnee : une ligne par signature, avec son nombre de survenues. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SupportErreurDTO {

    private Long id;
    private String signature;
    /** SERVEUR ou NAVIGATEUR. */
    private String origine;
    private String type;
    private String message;
    private String detail;
    private String chemin;
    private String utilisateur;
    private long occurrences;
    private LocalDateTime premiereLe;
    private LocalDateTime derniereLe;
    private boolean traite;
    private String traitePar;
    private LocalDateTime traiteLe;
}
