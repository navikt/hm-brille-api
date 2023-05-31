package no.nav.hjelpemidler.brille.sats

import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import no.nav.hjelpemidler.brille.test.TestRouting
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvFileSource
import java.time.LocalDate
import kotlin.test.assertEquals

internal class SatsApiTest {
    private val routing = TestRouting {
        satsApi()
    }

    @ParameterizedTest
    @CsvFileSource(resources = ["/SatsKalkulatorTest.csv"], useHeadersInDisplayName = true)
    fun `kalkulator skal utlede riktige satser`(
        høyreSfære: Double,
        høyreSylinder: Double,
        venstreSfære: Double,
        venstreSylinder: Double,
        sats: String,
    ) = routing.test {
        val response = client.post("/brillesedler") {
            setBody(
                Brilleseddel(
                    høyreSfære = høyreSfære,
                    høyreSylinder = høyreSylinder,
                    venstreSfære = venstreSfære,
                    venstreSylinder = venstreSylinder,
                )
            )
        }

        response.status shouldBe HttpStatusCode.OK
        response.body<BeregnetSatsDto>().sats shouldBe SatsType.valueOf(sats)
    }

    @ParameterizedTest
    @CsvFileSource(resources = ["/SatsBeløpTest.csv"], useHeadersInDisplayName = true)
    fun `kalkulator skal utlede riktige satser beløp basert på dato`(
        dato: LocalDate,
        nyeSatser: Boolean,
    ) = routing.test {
        if (!nyeSatser) {
            SatsType.SATS_1.beløp(dato) shouldBe 750
            SatsType.SATS_2.beløp(dato) shouldBe 1950
            SatsType.SATS_3.beløp(dato) shouldBe 2650
            SatsType.SATS_4.beløp(dato) shouldBe 3150
            SatsType.SATS_5.beløp(dato) shouldBe 4850
            SatsType.INGEN.beløp(dato) shouldBe 0
        } else {
            SatsType.SATS_1.beløp(dato) shouldBe 791
            SatsType.SATS_2.beløp(dato) shouldBe 2055
            SatsType.SATS_3.beløp(dato) shouldBe 2793
            SatsType.SATS_4.beløp(dato) shouldBe 3320
            SatsType.SATS_5.beløp(dato) shouldBe 5112
            SatsType.INGEN.beløp(dato) shouldBe 0
        }
    }
}
