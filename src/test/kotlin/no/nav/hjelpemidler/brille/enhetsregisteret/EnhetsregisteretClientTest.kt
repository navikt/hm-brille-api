package no.nav.hjelpemidler.brille.enhetsregisteret

import io.kotest.matchers.shouldBe
import io.mockk.every
import kotlinx.coroutines.test.runTest
import no.nav.hjelpemidler.brille.db.MockDatabaseContext
import kotlin.test.Ignore
import kotlin.test.Test

@Ignore("Benyttes bare til manuell test av integrasjon mot enhetsregisteret")
class EnhetsregisteretClientTest {
    private val databaseContext = MockDatabaseContext()
    private val client = EnhetsregisteretClient(databaseContext)
    private val service = EnhetsregisteretService(client, databaseContext)

    @Test
    fun `henter organisasjonsenhet`() = runTest {
        val orgnr = "929464958"

        every {
            databaseContext.enhetsregisteretStore.hentEnhet(any())
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
