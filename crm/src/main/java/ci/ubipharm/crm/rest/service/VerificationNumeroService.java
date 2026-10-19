package ci.ubipharm.crm.rest.service;

import ci.ubipharm.crm.rest.service.dto.VerificationNumerosDTO;
import ci.ubipharm.crm.rest.service.dto.VerificationNumerosDemandeDTO;

/** Verifie le format des numeros puis, par une session WhatsApp Web connectee, qu'ils ont un compte WhatsApp. */
public interface VerificationNumeroService {

    VerificationNumerosDTO verifier(VerificationNumerosDemandeDTO demande);
}
