package no.nav.hjelpemidler.brille.enhetsregisteret

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import no.nav.hjelpemidler.brille.Configuration
import kotlin.test.Test
import kotlin.test.assertEquals

internal class EnhetsregisteretClientTest {
    private val client = EnhetsregisteretClient(Configuration.enhetsregisteretProperties.baseUrl)

    @Test
    internal fun `henter organisasjonsenhet`() = runBlocking(Dispatchers.IO) {
        val organisasjonsnummer = Organisasjonsnummer("936314783")
        val organisasjonsenhet = client.hentOrganisasjonsenhet(organisasjonsnummer)
        assertEquals(organisasjonsnummer.toString(), organisasjonsenhet.organisasjonsnummer)
    }
}
