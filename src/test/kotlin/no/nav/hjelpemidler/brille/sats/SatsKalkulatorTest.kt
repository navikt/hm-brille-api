package no.nav.hjelpemidler.brille.sats

import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvFileSource

internal class SatsKalkulatorTest {
    @ParameterizedTest
    @CsvFileSource(resources = ["/SatsKalkulatorTest.csv"], useHeadersInDisplayName = true)
    fun `kalkulator skal utlede riktige satser`(
        høyreSfære: String,
        høyreSylinder: String,
        venstreSfære: String,
        venstreSylinder: String,
        sats: String,
    ) {
        val kalkulator = SatsKalkulator(
            SatsGrunnlag(
                høyreSfære = høyreSfære.tilDiopter(),
                høyreSylinder = høyreSylinder.tilDiopter(),
                venstreSfære = venstreSfære.tilDiopter(),
                venstreSylinder = venstreSylinder.tilDiopter(),
            )
        )
        kalkulator.kalkuler() shouldBe SatsType.valueOf(sats)
    }
}
