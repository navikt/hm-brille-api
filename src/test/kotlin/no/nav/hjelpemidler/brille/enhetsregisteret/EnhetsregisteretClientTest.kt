package no.nav.hjelpemidler.brille.enhetsregisteret

import io.kotest.matchers.shouldBe
import io.mockk.every
import kotlinx.coroutines.runBlocking
import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.brille.db.createDatabaseContext
import no.nav.hjelpemidler.brille.db.createDatabaseSessionContextWithMocks
import no.nav.hjelpemidler.brille.utbetaling.Utbetaling
import org.junit.Ignore
import kotlin.test.Test

@Ignore("Benyttes bare til manuell test av integrasjon mot enhetsregisteret")
internal class EnhetsregisteretClientTest {
    private val sessionContext = createDatabaseSessionContextWithMocks()
    private val databaseContext = createDatabaseContext(sessionContext)
    private val client = EnhetsregisteretClient(Configuration.enhetsregisteretProperties, databaseContext)
    private val service = EnhetsregisteretService(client, databaseContext)

    @Test
    internal fun `henter organisasjonsenhet`() = runBlocking {
        val orgnr = "929464958"

        every {
            sessionContext.enhetsregisteretStore.hentEnhet(any())
        } answers {
            Organisasjonsenhet(
                orgnr = orgnr,
                navn = "Hello",
            )
        }

        val enhet = service.hentOrganisasjonsenhet(orgnr)

        enhet?.orgnr shouldBe orgnr
    }
}
