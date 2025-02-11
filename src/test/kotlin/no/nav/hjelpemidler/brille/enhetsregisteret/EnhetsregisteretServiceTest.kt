package no.nav.hjelpemidler.brille.enhetsregisteret

import io.kotest.matchers.maps.shouldHaveSize
import io.ktor.client.engine.HttpClientEngine
import io.ktor.http.HttpHeaders
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import kotlinx.coroutines.test.runTest
import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.brille.db.TestDatabaseContext
import no.nav.hjelpemidler.brille.test.AbstractStoreTest
import no.nav.hjelpemidler.brille.test.baseUrl
import no.nav.hjelpemidler.brille.test.externalServices
import kotlin.test.Test

class EnhetsregisteretServiceTest : AbstractStoreTest() {
    private val enheter = (100000000..100000009)
    private val underenheter = (200000000..200000009)

    private val databaseContext = TestDatabaseContext
    private val enhetsregisteretClient = EnhetsregisteretClient(
        databaseContext,
        enhetsregisteretClientStub(enheter, underenheter),
    )
    private val service = EnhetsregisteretService(databaseContext, enhetsregisteretClient)

    @Test
    fun `Skal oppdatere mirror`() = runTest {
        service.oppdaterMirrorHvisUtdatert(true)

        transaction {
            val lagredeEnheter = enhetsregisteretStore.hentEnheter(enheter.map(Int::toString).toSet())
            lagredeEnheter shouldHaveSize enheter.count()

            val lagredeUnderenheter = enhetsregisteretStore.hentEnheter(underenheter.map(Int::toString).toSet())
            lagredeUnderenheter shouldHaveSize underenheter.count()
        }
    }
}

private fun enhetsregisteretClientStub(enheter: Iterable<Int>, underenheter: Iterable<Int>): HttpClientEngine =
    externalServices {
        baseUrl(Configuration.ENHETSREGISTERET_API_URL) {
            get("$it/enhetsregisteret/api/enheter/lastned") {
                call.response.header(
                    HttpHeaders.ContentType,
                    EnhetsregisteretClient.CONTENT_TYPE_ENHET_GZIP.toString(),
                )
                call.response.header(HttpHeaders.ContentEncoding, "gzip")
                call.respond(enheter.map(::lagOrganisasjonsenhet))
            }
            get("$it/enhetsregisteret/api/underenheter/lastned") {
                call.response.header(
                    HttpHeaders.ContentType,
                    EnhetsregisteretClient.CONTENT_TYPE_UNDERENHET_GZIP.toString(),
                )
                call.response.header(HttpHeaders.ContentEncoding, "gzip")
                call.respond(underenheter.map(::lagOrganisasjonsenhet))
            }
        }
    }

private fun lagOrganisasjonsenhet(orgnr: Int): Organisasjonsenhet = Organisasjonsenhet(
    orgnr = orgnr.toString(),
    overordnetEnhet = null,
    navn = "TEST",
    forretningsadresse = null,
    beliggenhetsadresse = null,
    naeringskode1 = null,
    naeringskode2 = null,
    naeringskode3 = null,
    konkursdato = null,
    slettedato = null,
)
