package ci.ubipharm.crm.commun.rest.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class ValeursImportTest {

    @Test
    void lesEntetesSontNormalisees() {
        assertThat(ValeursImport.normaliserEntete("Date d'échéance")).isEqualTo("date_d_echeance");
        assertThat(ValeursImport.normaliserEntete("  N° Facture ")).isEqualTo("n_facture");
        assertThat(ValeursImport.normaliserEntete("Reste à payer (FCFA)")).isEqualTo("reste_a_payer_fcfa");
    }

    @Test
    void lesDatesCourantesSontLues() {
        LocalDate attendu = LocalDate.of(2026, 9, 30);
        assertThat(ValeursImport.date("30/09/2026")).contains(attendu);
        assertThat(ValeursImport.date("2026-09-30")).contains(attendu);
        assertThat(ValeursImport.date("30-09-2026")).contains(attendu);
        assertThat(ValeursImport.date("30/09/26")).contains(attendu);
        assertThat(ValeursImport.date("46295")).contains(attendu);
        assertThat(ValeursImport.date("31/02/2026")).isEmpty();
        assertThat(ValeursImport.date("demain")).isEmpty();
    }

    @Test
    void lesMontantsCourantsSontLus() {
        assertThat(ValeursImport.montant("150 000")).contains(new BigDecimal("150000"));
        assertThat(ValeursImport.montant("150 000 FCFA")).contains(new BigDecimal("150000"));
        assertThat(ValeursImport.montant("1 234 567,50")).contains(new BigDecimal("1234567.50"));
        assertThat(ValeursImport.montant("1.234.567,50")).contains(new BigDecimal("1234567.50"));
        assertThat(ValeursImport.montant("1,234,567.50")).contains(new BigDecimal("1234567.50"));
        assertThat(ValeursImport.montant("150,000")).contains(new BigDecimal("150000"));
        assertThat(ValeursImport.montant("12,5")).contains(new BigDecimal("12.5"));
        assertThat(ValeursImport.montant("-20 000")).contains(new BigDecimal("-20000"));
        assertThat(ValeursImport.montant("abc")).isEmpty();
    }

    @Test
    void lesNumerosSontMisAuFormatInternational() {
        assertThat(ValeursImport.numeroWhatsapp("07 00 00 00 01", "225")).contains("2250700000001");
        assertThat(ValeursImport.numeroWhatsapp("+225 07-00-00-00-01", "225")).contains("2250700000001");
        assertThat(ValeursImport.numeroWhatsapp("002250700000001", "225")).contains("2250700000001");
        assertThat(ValeursImport.numeroWhatsapp("07000001", "225")).isEmpty();
        assertThat(ValeursImport.numeroWhatsapp("33612345678", "225")).isEmpty();
    }
}
