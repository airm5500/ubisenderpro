package ci.ubipharm.crm.securite.rest.service.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RoleGestionDTO {

    private Long id;
    private String code;
    private String libelle;
    private String description;
    /** Toutes les agences ; sinon les agences rattachees au compte. */
    private boolean visionGlobale;
    private boolean actif;
    /** Role livre avec l'application. */
    private boolean systeme;
    /** Comptes actifs qui ont ce role. */
    private long nbUtilisateurs;
    private List<String> droits;
    private String modifiePar;
    private LocalDateTime modifieLe;
}
