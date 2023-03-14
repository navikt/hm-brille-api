package no.nav.hjelpemidler.brille.enhetsregisteret

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import no.nav.hjelpemidler.brille.Configuration
import kotlin.test.Ignore
import kotlin.test.Test

@Ignore("Benyttes bare til manuell test av integrasjon mot enhetsregisteret")
internal class EnhetsregisteretClientTest {
    private val client = EnhetsregisteretClient(Configuration.enhetsregisteretProperties)

    @Test
    internal fun `henter organisasjonsenhet`() = runBlocking {
        val orgnr = "929464958"
        val enhet = client.hentOrganisasjonsenhet(orgnr)

        enhet?.orgnr shouldBe orgnr
    }
}
