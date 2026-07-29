package com.ubisenderpro.config;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Traduction des erreurs de contrainte SQL en messages utilisateur.
 *
 * <p>Un message technique brut (« Data too long for column 'description' »)
 * n'aide pas un utilisateur non informaticien : le champ concerne doit etre
 * nomme explicitement.</p>
 */
class MessagesSqlTest {

    private String traduire(String sql, String menu) throws Exception {
        Method m = AppExceptionMapper.class.getDeclaredMethod("traduireSql", String.class, String.class);
        m.setAccessible(true);
        return (String) m.invoke(new AppExceptionMapper(), sql, menu);
    }

    @Test
    void valeurTropLongueNommeLeChamp() throws Exception {
        String msg = traduire("Data truncation: Data too long for column 'description' at row 1", "Marketing");
        assertTrue(msg.contains("Description"), "Le champ fautif doit etre nomme : " + msg);
        assertTrue(msg.contains("Marketing"), "Le menu doit etre rappele : " + msg);
        assertFalse(msg.contains("Data too long"), "Aucun jargon technique : " + msg);
    }

    @Test
    void champObligatoireNommeLeChamp() throws Exception {
        String msg = traduire("Column 'nom_compte' cannot be null", "Comptes clients");
        assertTrue(msg.contains("Nom du compte"), msg);
        assertTrue(msg.contains("obligatoire"), msg);
    }

    @Test
    void doublonEtCleEtrangereRestentComprehensibles() throws Exception {
        String doublon = traduire("Duplicate entry 'C001' for key 'numero_client'", "Comptes clients");
        assertTrue(doublon.toLowerCase().contains("existe"), doublon);

        String fk = traduire("Cannot delete or update a parent row: a foreign key constraint fails",
                "Catalogue");
        assertTrue(fk.toLowerCase().contains("impossible"), fk);
    }

    @Test
    void colonneInconnueResteLisible() throws Exception {
        // Colonne sans libelle dedie : les underscores deviennent des espaces.
        String msg = traduire("Data too long for column 'agence_cible' at row 1", "Campagnes");
        assertTrue(msg.contains("agence cible"), msg);
    }
}
