package no.nav.hjelpemidler.brille.vilkarsvurdering

import no.nav.hjelpemidler.brille.hotsak.HotsakVedtak
import no.nav.hjelpemidler.brille.hotsak.HotsakVedtakBuilder
import no.nav.hjelpemidler.brille.hotsak.lagHotsakVedtak
import no.nav.hjelpemidler.brille.sats.Brilleseddel
import no.nav.hjelpemidler.brille.sats.BrilleseddelBuilder
import no.nav.hjelpemidler.brille.sats.lagBrilleseddel
import no.nav.hjelpemidler.brille.test.Builder
import no.nav.hjelpemidler.brille.tid.toInstant
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class VilkårsgrunnlagHotsakDtoBuilder : Builder<VilkårsgrunnlagHotsakDto> {
    var fnrBarn: String = "12345678910"
    var brilleseddel: Brilleseddel? = lagBrilleseddel()
    var bestillingsdato: LocalDate? = LocalDate.now().minusWeeks(3)
    var brillepris: BigDecimal? = 1500.toBigDecimal()
    var mottaksdato: Instant = LocalDate.now().minusWeeks(2).atTime(14, 0).toInstant()
    var vedtak: List<HotsakVedtak> = mutableListOf()

    fun brilleseddel(block: BrilleseddelBuilder.() -> Unit) {
        brilleseddel = lagBrilleseddel(block)
    }

    fun vedtak(block: HotsakVedtakBuilder.() -> Unit) {
        vedtak += lagHotsakVedtak(block)
    }

    override fun build(): VilkårsgrunnlagHotsakDto = VilkårsgrunnlagHotsakDto(
        fnrBarn = fnrBarn,
        brilleseddel = brilleseddel,
        bestillingsdato = bestillingsdato,
        brillepris = brillepris,
        mottaksdato = mottaksdato,
        vedtak = vedtak,
    )
}

fun lagVilkårsgrunnlagHotsakDto(block: VilkårsgrunnlagHotsakDtoBuilder.() -> Unit = {}): VilkårsgrunnlagHotsakDto =
    VilkårsgrunnlagHotsakDtoBuilder().apply(block).build()
