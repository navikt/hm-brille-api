package no.nav.hjelpemidler.brille.enhetsregisteret

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.brille.test.shouldBe
import kotlin.test.Test

internal class EnhetsregisteretClientTest {
    private val client = EnhetsregisteretClient(Configuration.enhetsregisteretProperties.baseUrl)

    @Test
    internal fun `henter organisasjonsenhet`() = runBlocking(Dispatchers.IO) {
        val organisasjonsnummer = Organisasjonsnummer("936314783")
        val organisasjonsenhet = client.hentOrganisasjonsenhet(organisasjonsnummer)

        organisasjonsenhet.organisasjonsnummer shouldBe organisasjonsnummer.toString()
    }
}
