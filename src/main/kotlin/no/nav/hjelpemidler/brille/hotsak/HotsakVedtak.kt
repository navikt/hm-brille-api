package no.nav.hjelpemidler.brille.hotsak

import no.nav.hjelpemidler.brille.vilkarsvurdering.Vilkårsgrunnlag
import java.time.Instant
import java.time.LocalDate

/**
 * Vedtak om barnebriller som barnet har fra tidligere.
 */
data class HotsakVedtak(
    val sakId: String,
    val vedtakId: String,
    val vedtaksstatus: String,
    override val vedtaksdato: Instant,
    override val bestillingsdato: LocalDate,
) : Vilkårsgrunnlag.Vedtak {
    init {
        check(vedtaksstatus == "INNVILGET") {
            "Kun vedtak med vedtaksstatus 'INNVILGET' skal vurderes!"
        }
    }
}
