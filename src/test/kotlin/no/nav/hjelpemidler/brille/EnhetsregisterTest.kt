package no.nav.hjelpemidler.brille

import kotlinx.coroutines.runBlocking
import no.nav.hjelpemidler.brille.enhetsregisteret.EnhetsregisteretClient
import org.junit.jupiter.api.Test

internal class EnhetsregisterTest {
    @Test
    fun `Test mod prod enhetsregister`() {
        val props = Configuration.EnhetsregisteretProperties("https://data.brreg.no/enhetsregisteret/api")
        val enhetsregisteretClient = EnhetsregisteretClient(props)
        val resultat = runBlocking { enhetsregisteretClient.hentOrganisasjonsenhet("889234962") }
        println(resultat)
    }
}
