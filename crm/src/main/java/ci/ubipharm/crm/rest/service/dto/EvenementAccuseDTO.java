package ci.ubipharm.crm.rest.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Callback /ack : message distribue ou lu. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EvenementAccuseDTO {

    private String sessionId;
    private String id;
    private String status;
}
