package no.nav.hjelpemidler.brille.sats

import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import no.nav.hjelpemidler.brille.test.TestRouting
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvFileSource

internal class SatsApiTest {
    private val routing = TestRouting {
        satsApi()
    }

    @ParameterizedTest
    @CsvFileSource(resources = ["/SatsKalkulatorTest.csv"], useHeadersInDisplayName = true)
    fun `kalkulator skal utlede riktige satser`(
        høyreSfære: String,
        høyreSylinder: String,
        venstreSfære: String,
        venstreSylinder: String,
        sats: String,
    ) = routing.test {
        val response = client.post("/beregn-sats") {
            setBody(
                Brilleseddel(
                    høyreSfære = høyreSfære.tilDiopter(),
                    høyreSylinder = høyreSylinder.tilDiopter(),
                    venstreSfære = venstreSfære.tilDiopter(),
                    venstreSylinder = venstreSylinder.tilDiopter(),
                )
            )
        }

        response.status shouldBe HttpStatusCode.OK
        response.body<BeregnetSatsDto>().sats shouldBe SatsType.valueOf(sats)
    }
}
