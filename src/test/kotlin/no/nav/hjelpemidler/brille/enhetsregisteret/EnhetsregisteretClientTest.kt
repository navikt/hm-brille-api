package no.nav.hjelpemidler.brille.enhetsregisteret

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import no.nav.hjelpemidler.brille.Configuration
import kotlin.test.Test

internal class EnhetsregisteretClientTest {
    private val client = EnhetsregisteretClient(Configuration.enhetsregisteretProperties.baseUrl)

    @Test
    internal fun `henter organisasjonsenhet`() = runBlocking(Dispatchers.IO) {
        val orgnr = "936314783"
        val enhet = client.hentOrganisasjonsenhet(orgnr)

        enhet?.orgnr shouldBe orgnr
    }
}
