package ci.ubipharm.crm.rest.service.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Compte de l'API WhatsApp Cloud de Meta ; le jeton et le secret ne se relisent jamais. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CompteMetaDTO {

    private boolean jetonRenseigne;
    private boolean secretRenseigne;
    /** A recopier dans la configuration du webhook chez Meta. */
    private String jetonVerification;
    /** Adresse du webhook, si l'adresse publique du CRM est renseignee. */
    private String urlWebhook;
    private String versionApi;
    private String modifiePar;
    private LocalDateTime modifieLe;
}
