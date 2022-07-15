package no.nav.hjelpemidler.brille

import no.nav.hjelpemidler.brille.test.withMigratedDB
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

internal class DatabaseTest {
    @Test
    fun `migrering skjer uten feil`() {
        assertDoesNotThrow {
            withMigratedDB { }
        }
    }
}
