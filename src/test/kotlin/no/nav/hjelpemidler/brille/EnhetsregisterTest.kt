package no.nav.hjelpemidler.brille

import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer
import io.kotest.matchers.shouldBe
import io.mockk.every
import kotlinx.coroutines.runBlocking
import no.nav.hjelpemidler.brille.db.createDatabaseContext
import no.nav.hjelpemidler.brille.db.createDatabaseSessionContextWithMocks
import no.nav.hjelpemidler.brille.enhetsregisteret.EnhetsregisteretClient
import no.nav.hjelpemidler.brille.enhetsregisteret.EnhetsregisteretService
import no.nav.hjelpemidler.brille.enhetsregisteret.Organisasjonsenhet
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

internal class EnhetsregisterTest {
    @Test
    fun `Test mot prod enhetsregister`() {
        val props = Configuration.EnhetsregisteretProperties("https://data.brreg.no/enhetsregisteret/api")
        val sessionContext = createDatabaseSessionContextWithMocks()
        val databaseContext = createDatabaseContext(sessionContext)
        val enhetsregisteretClient = EnhetsregisteretClient(props, databaseContext)
        val enhetsregisteretService = EnhetsregisteretService(enhetsregisteretClient, databaseContext)

        val orgnr = "889234962"

        every {
            sessionContext.enhetsregisteretStore.hentEnhet(any())
        } answers {
            Organisasjonsenhet(
                orgnr = orgnr,
                navn = "Hello",
            )
        }

        val resultat = runBlocking { enhetsregisteretService.hentOrganisasjonsenhet(orgnr) }
        println(resultat)
    }

    @Test
    fun `Test tid siden sist oppdatert logikk` () {
        val sistOppdatert = LocalDateTime.now().minusHours(24)
        val timerSidenOppdatert = sistOppdatert.until(LocalDateTime.now(), ChronoUnit.HOURS)
        timerSidenOppdatert shouldBe 24
    }
}
