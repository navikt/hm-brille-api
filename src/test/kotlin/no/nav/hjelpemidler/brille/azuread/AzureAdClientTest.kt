package no.nav.hjelpemidler.brille.azuread

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import no.nav.hjelpemidler.brille.Configuration.AzureAdProperties
import org.junit.jupiter.api.Assertions.assertThrows
import kotlin.test.Test

internal class AzureAdClientTest {
    private val client = AzureAdClient(
        AzureAdProperties(
            openidConfigTokenEndpoint = "http://localhost:1234/default/token",
            tenantId = "default",
            clientId = "default",
            clientSecret = ""
        )
    )

    @Test
    internal fun `kaster feil`() {
        val throwable = assertThrows(Exception::class.java) {
            runBlocking(Dispatchers.IO) {
                client.getToken("default")
            }
        }
        println(throwable.message)
        println(throwable.cause?.message)
    }
}
