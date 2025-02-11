package no.nav.hjelpemidler.brille.sats

import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.mockk.every
import io.mockk.mockk
import no.nav.hjelpemidler.brille.kafka.KafkaService
import no.nav.hjelpemidler.brille.sats.kalkulator.Beregningsgrunnlag
import no.nav.hjelpemidler.brille.sats.kalkulator.KalkulatorResultat
import no.nav.hjelpemidler.brille.sats.kalkulator.KalkulatorService
import no.nav.hjelpemidler.brille.test.TestRouting
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvFileSource
import java.time.LocalDate
import java.time.Month
import kotlin.math.abs
import kotlin.test.BeforeTest

class SatsApiTest {
    private val kafkaService = mockk<KafkaService>()
    private val kalkulatorService = KalkulatorService(kafkaService)
    private val routing = TestRouting {
        satsApi(kalkulatorService)
    }

    @BeforeTest
    fun setup() {
        every { kafkaService.kalkulertBrillestøtte(any()) } returns Unit
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
        val response = client.post("/satsgrunnlag") {
            setBody(
                SatsGrunnlag(
                    høyreSfære = høyreSfære,
                    høyreSylinder = høyreSylinder,
                    venstreSfære = venstreSfære,
                    venstreSylinder = venstreSylinder,
                    bestillingsdato = LocalDate.of(2023, Month.JULY, 1),
                ),
            )
        }

        response.status shouldBe HttpStatusCode.OK
        response.body<SatsBeregning>().sats shouldBe SatsType.valueOf(sats)
    }

    @ParameterizedTest
    @CsvFileSource(resources = ["/SatsBeløpTest.csv"], useHeadersInDisplayName = true)
    fun `kalkulator skal utlede riktige satser beløp basert på dato`(
        dato: LocalDate,
        nyeSatser: Boolean,
    ) = routing.test {
        if (!nyeSatser) {
            SatsType.SATS_1.beløp(dato) shouldBe 770
            SatsType.SATS_2.beløp(dato) shouldBe 2000
            SatsType.SATS_3.beløp(dato) shouldBe 2715
            SatsType.SATS_4.beløp(dato) shouldBe 3230
            SatsType.SATS_5.beløp(dato) shouldBe 4975
            SatsType.INGEN.beløp(dato) shouldBe 0
        } else {
            SatsType.SATS_1.beløp(dato) shouldBe 800
            SatsType.SATS_2.beløp(dato) shouldBe 2075
            SatsType.SATS_3.beløp(dato) shouldBe 2820
            SatsType.SATS_4.beløp(dato) shouldBe 3355
            SatsType.SATS_5.beløp(dato) shouldBe 5165
            SatsType.INGEN.beløp(dato) shouldBe 0
        }
    }

    @Test
    fun `differanse mellom to tall`() {
        val tall1 = 5
        val tall2 = -5
        val differanse = abs(tall2 - tall1)
        differanse shouldBe 10

        val tall3 = 5
        val tall4 = 10
        val differanse2 = abs(tall4 - tall3)
        differanse2 shouldBe 5
    }

    @Test
    fun `Individuell sats grunnet ADD`() = routing.test {
        val response = client.post("/kalkulator/beregningsgrunnlag") {
            setBody(
                Beregningsgrunnlag(
                    Brilleseddel(
                        høyreSfære = 0.0,
                        høyreSylinder = 0.0,
                        venstreSfære = 2.0,
                        venstreSylinder = 2.0,
                        venstreAdd = 1.25,
                    ),
                    alder = true,
                    strabisme = true,
                    bestillingsdato = LocalDate.of(2023, Month.JULY, 1),
                ),
            )
        }

        response.status shouldBe HttpStatusCode.OK

        val satsBeregningAmblyopi = response.body<KalkulatorResultat>().amblyopistøtte
        satsBeregningAmblyopi.sats shouldBe AmblyopiSatsType.INDIVIDUELT
    }
}
