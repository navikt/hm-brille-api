package no.nav.hjelpemidler.brille.utils

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

internal class PostgreSQLTest {
    @Test
    fun `migrering skjer uten feil`() {
        assertDoesNotThrow {
            withCleanDB {
                withMigratedDB { }
            }
        }
    }
}
