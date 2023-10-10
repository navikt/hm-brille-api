package no.nav.hjelpemidler.brille.sats.kalkulator

import no.nav.hjelpemidler.brille.kafka.KafkaService
import no.nav.hjelpemidler.brille.sats.AmblyopiSatsType
import no.nav.hjelpemidler.brille.sats.SatsBeregning
import no.nav.hjelpemidler.brille.sats.SatsBeregningAmblyopi
import no.nav.hjelpemidler.brille.sats.SatsGrunnlag
import no.nav.hjelpemidler.brille.sats.SatsType
import no.nav.hjelpemidler.brille.sats.beregnAmblyopiSats
import no.nav.hjelpemidler.brille.sats.beregnSats
import kotlin.math.abs

class KalkulatorService(
    private val kafkaService: KafkaService,
) {

    fun kalkuler(beregningsgrunnlag: Beregningsgrunnlag): KalkulatorResultat {
        val kalkulatorResultat = KalkulatorResultat(
            brillestøtte = kalkulerBrillestøtte(beregningsgrunnlag),
            amblyopistøtte = kalkulerAmblyopistøtte(beregningsgrunnlag),
        )
        kafkaService.kalkulertBrillestøtte(kalkulatorResultat)
        return kalkulatorResultat
    }

    private fun kalkulerBrillestøtte(beregningsgrunnlag: Beregningsgrunnlag): SatsBeregning {
        if (!beregningsgrunnlag.alder) {
            return SatsBeregning.ingen()
        }

        return beregnSats(
            SatsGrunnlag(
                brilleseddel = beregningsgrunnlag.brilleseddel,
                bestillingsdato = beregningsgrunnlag.bestillingsdato,
            ),
        )
    }

    private fun kalkulerAmblyopistøtte(beregningsgrunnlag: Beregningsgrunnlag): SatsBeregningAmblyopi {
        if (!beregningsgrunnlag.alder) {
            return SatsBeregningAmblyopi.ingen()
        }
        if (!beregningsgrunnlag.strabisme &&
            (!(abs(beregningsgrunnlag.brilleseddel.høyreSylinder) >= 1.5) && !(abs(beregningsgrunnlag.brilleseddel.venstreSylinder) >= 1.5)) &&
            !(abs(beregningsgrunnlag.brilleseddel.høyreSfære - beregningsgrunnlag.brilleseddel.venstreSfære) >= 1) &&
            !((beregningsgrunnlag.brilleseddel.høyreSfære >= 4) && (beregningsgrunnlag.brilleseddel.venstreSfære >= 4))

        ) {
            return SatsBeregningAmblyopi.ingen()
        }

        return beregnAmblyopiSats(
            SatsGrunnlag(
                brilleseddel = beregningsgrunnlag.brilleseddel,
                bestillingsdato = beregningsgrunnlag.bestillingsdato,
            ),
        )
    }
}

data class KalkulatorResultat(
    val brillestøtte: SatsBeregning,
    val amblyopistøtte: SatsBeregningAmblyopi,
) {
    companion object {
        fun ingen() = KalkulatorResultat(
            brillestøtte = SatsBeregning(SatsType.INGEN, SatsType.INGEN.beskrivelse, 0),
            amblyopistøtte = SatsBeregningAmblyopi(AmblyopiSatsType.INGEN, AmblyopiSatsType.INGEN.beskrivelse, 0),
        )
    }
}
