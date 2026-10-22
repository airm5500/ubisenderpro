package ci.ubipharm.crm.rest.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Callback /status du service wa-web. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EvenementStatutDTO {

    private String sessionId;
    private String status;
    private String health;
    private String reason;
}
