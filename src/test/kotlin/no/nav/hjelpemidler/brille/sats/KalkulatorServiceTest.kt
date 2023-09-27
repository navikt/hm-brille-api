package no.nav.hjelpemidler.brille.sats

import no.nav.hjelpemidler.brille.sats.kalkulator.Beregningsgrunnlag
import no.nav.hjelpemidler.brille.sats.kalkulator.KalkulatorService
import org.junit.jupiter.api.Test
import java.time.LocalDate

class KalkulatorServiceTest {

    @Test
    fun `Ingen støtte`() {
        val beregningsgrunnlag = Beregningsgrunnlag(
            alder = true,
            strabisme = false,
            brilleseddel = Brilleseddel(
                høyreSylinder = 1.25,
                venstreSylinder = 1.25,
                høyreSfære = 1.0,
                venstreSfære = 1.0,
            ),
            bestillingsdato = LocalDate.now(),
        )

        val kalkulatorResultat = KalkulatorService().kalkuler(beregningsgrunnlag)

        assert(kalkulatorResultat.amblyopistøtte.sats == AmblyopiSatsType.INGEN)
    }

    @Test
    fun `Sats 1 pga sylinder`() {
        val beregningsgrunnlag = Beregningsgrunnlag(
            alder = true,
            strabisme = false,
            brilleseddel = Brilleseddel(
                høyreSylinder = 1.25,
                venstreSylinder = -2.0,
                høyreSfære = 1.0,
                venstreSfære = 1.0,
            ),
            bestillingsdato = LocalDate.now(),
        )

        val kalkulatorResultat = KalkulatorService().kalkuler(beregningsgrunnlag)

        assert(kalkulatorResultat.amblyopistøtte.sats == AmblyopiSatsType.SATS_1)
    }

    @Test
    fun `Sats 2 pga sylinder`() {
        val beregningsgrunnlag = Beregningsgrunnlag(
            alder = true,
            strabisme = false,
            brilleseddel = Brilleseddel(
                høyreSylinder = 1.25,
                venstreSylinder = -2.0,
                høyreSfære = 4.0,
                venstreSfære = 1.0,
            ),
            bestillingsdato = LocalDate.now(),
        )

        val kalkulatorResultat = KalkulatorService().kalkuler(beregningsgrunnlag)

        assert(kalkulatorResultat.amblyopistøtte.sats == AmblyopiSatsType.SATS_2)
    }

    @Test
    fun `Sats 1 pga strabisme`() {
        val beregningsgrunnlag = Beregningsgrunnlag(
            alder = true,
            strabisme = true,
            brilleseddel = Brilleseddel(
                høyreSylinder = 1.25,
                venstreSylinder = 1.25,
                høyreSfære = 3.75,
                venstreSfære = 1.0,
            ),
            bestillingsdato = LocalDate.now(),
        )

        val kalkulatorResultat = KalkulatorService().kalkuler(beregningsgrunnlag)

        assert(kalkulatorResultat.amblyopistøtte.sats == AmblyopiSatsType.SATS_1)
    }

    @Test
    fun `Sats 2 pga strabisme`() {
        val beregningsgrunnlag = Beregningsgrunnlag(
            alder = true,
            strabisme = true,
            brilleseddel = Brilleseddel(
                høyreSylinder = 1.25,
                venstreSylinder = 1.25,
                høyreSfære = 3.75,
                venstreSfære = 6.0,
            ),
            bestillingsdato = LocalDate.now(),
        )

        val kalkulatorResultat = KalkulatorService().kalkuler(beregningsgrunnlag)

        assert(kalkulatorResultat.amblyopistøtte.sats == AmblyopiSatsType.SATS_2)
    }

    @Test
    fun `Sats 1 pga anisometropi`() {
        val beregningsgrunnlag = Beregningsgrunnlag(
            alder = true,
            strabisme = false,
            brilleseddel = Brilleseddel(
                høyreSylinder = 1.25,
                venstreSylinder = 1.25,
                høyreSfære = 0.5,
                venstreSfære = -0.5,
            ),
            bestillingsdato = LocalDate.now(),
        )

        val kalkulatorResultat = KalkulatorService().kalkuler(beregningsgrunnlag)

        assert(kalkulatorResultat.amblyopistøtte.sats == AmblyopiSatsType.SATS_1)
    }

    @Test
    fun `Individuelt beløp pga ADD`() {
        val beregningsgrunnlag = Beregningsgrunnlag(
            alder = true,
            strabisme = false,
            brilleseddel = Brilleseddel(
                høyreSylinder = 2.0,
                venstreSylinder = 2.0,
                høyreSfære = 0.0,
                venstreSfære = 0.0,
                venstreAdd = 1.0,
            ),
            bestillingsdato = LocalDate.now(),
        )

        val kalkulatorResultat = KalkulatorService().kalkuler(beregningsgrunnlag)

        assert(kalkulatorResultat.amblyopistøtte.sats == AmblyopiSatsType.INDIVIDUELT)
    }

    @Test
    fun `Individuelt beløp pga sfære`() {
        val beregningsgrunnlag = Beregningsgrunnlag(
            alder = true,
            strabisme = false,
            brilleseddel = Brilleseddel(
                høyreSylinder = 1.25,
                venstreSylinder = 1.25,
                høyreSfære = 8.0,
                venstreSfære = -0.5,
            ),
            bestillingsdato = LocalDate.now(),
        )

        val kalkulatorResultat = KalkulatorService().kalkuler(beregningsgrunnlag)

        assert(kalkulatorResultat.amblyopistøtte.sats == AmblyopiSatsType.INDIVIDUELT)
    }
}
