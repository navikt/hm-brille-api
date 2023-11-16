package no.nav.hjelpemidler.brille.vilkarsvurdering

import no.nav.hjelpemidler.brille.sats.Brilleseddel
import no.nav.hjelpemidler.brille.sats.BrilleseddelBuilder
import no.nav.hjelpemidler.brille.sats.lagBrilleseddel
import no.nav.hjelpemidler.brille.test.Builder
import java.math.BigDecimal
import java.time.LocalDate

class VilkårsgrunnlagDtoBuilder : Builder<VilkårsgrunnlagDto> {
    var orgnr: String = "123456789"
    var fnrBarn: String = "12345678910"
    var brilleseddel: Brilleseddel = lagBrilleseddel()
    var bestillingsdato: LocalDate = LocalDate.now().minusWeeks(3)
    var brillepris: BigDecimal = 1500.toBigDecimal()
    var extras: VilkårsgrunnlagExtrasDto = VilkårsgrunnlagExtrasDto("test", "test")

    fun brilleseddel(block: BrilleseddelBuilder.() -> Unit) {
        brilleseddel = lagBrilleseddel(block)
    }

    override fun build(): VilkårsgrunnlagDto = VilkårsgrunnlagDto(
        orgnr = orgnr,
        butikkId = null,
        fnrBarn = fnrBarn,
        brilleseddel = brilleseddel,
        bestillingsdato = bestillingsdato,
        brillepris = brillepris,
        extras = extras,
    )
}

fun lagVilkårsgrunnlagDto(block: VilkårsgrunnlagDtoBuilder.() -> Unit = {}): VilkårsgrunnlagDto =
    VilkårsgrunnlagDtoBuilder().apply(block).build()
