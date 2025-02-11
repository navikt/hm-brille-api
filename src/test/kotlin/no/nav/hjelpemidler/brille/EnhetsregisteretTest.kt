package no.nav.hjelpemidler.brille

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.shouldBe
import io.mockk.every
import kotlinx.coroutines.test.runTest
import no.nav.hjelpemidler.brille.db.MockDatabaseContext
import no.nav.hjelpemidler.brille.enhetsregisteret.EnhetsregisteretClient
import no.nav.hjelpemidler.brille.enhetsregisteret.EnhetsregisteretService
import no.nav.hjelpemidler.brille.enhetsregisteret.Organisasjonsenhet
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class EnhetsregisteretTest {
    @Test
    fun `Test mot enhetsregisteret i prod`() = runTest {
        val databaseContext = MockDatabaseContext()
        val enhetsregisteretClient = EnhetsregisteretClient(databaseContext)
        val enhetsregisteretService = EnhetsregisteretService(databaseContext, enhetsregisteretClient)

        val orgnr = "889234962"

        every {
            databaseContext.enhetsregisteretStore.hentEnhet(any())
        } answers {
            Organisasjonsenhet(
                orgnr = orgnr,
                navn = "Hello",
            )
        }

        shouldNotThrowAny {
            enhetsregisteretService.hentOrganisasjonsenhet(orgnr)
        }
    }

    @Test
    fun `Test tid siden sist oppdatert logikk`() {
        val sistOppdatert = LocalDateTime.now().minusHours(24)
        val timerSidenOppdatert = sistOppdatert.until(LocalDateTime.now(), ChronoUnit.HOURS)
        timerSidenOppdatert shouldBe 24
    }

    /*
    @Test
    fun `se kravquery`() {
        val result = kravlinjeQuery(KravFilter.EGENDEFINERT, LocalDate.now(), "some", true)
        println(result)
    }
     */
}
