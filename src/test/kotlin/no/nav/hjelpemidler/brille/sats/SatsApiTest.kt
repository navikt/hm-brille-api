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

    @Test
    fun `kalkulator skal utlede riktige satser beløp basert på dato`() {
        // Gamle satser
        assertEquals(750, SatsType.SATS_1.beløp(LocalDate.parse("2023-06-30")))
        assertEquals(1950, SatsType.SATS_2.beløp(LocalDate.parse("2023-06-30")))
        assertEquals(2650, SatsType.SATS_3.beløp(LocalDate.parse("2023-06-30")))
        assertEquals(3150, SatsType.SATS_4.beløp(LocalDate.parse("2023-06-30")))
        assertEquals(4850, SatsType.SATS_5.beløp(LocalDate.parse("2023-06-30")))
        assertEquals(0, SatsType.INGEN.beløp(LocalDate.parse("2023-06-30")))

        // Nye satser 1 juli 2023
        assertEquals(791, SatsType.SATS_1.beløp(LocalDate.parse("2023-07-01")))
        assertEquals(2055, SatsType.SATS_2.beløp(LocalDate.parse("2023-07-01")))
        assertEquals(2793, SatsType.SATS_3.beløp(LocalDate.parse("2023-07-01")))
        assertEquals(3320, SatsType.SATS_4.beløp(LocalDate.parse("2023-07-01")))
        assertEquals(5112, SatsType.SATS_5.beløp(LocalDate.parse("2023-07-01")))
        assertEquals(0, SatsType.INGEN.beløp(LocalDate.parse("2023-07-01")))
    }
}
