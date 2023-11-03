package no.nav.hjelpemidler.brille.hotsak

import no.nav.hjelpemidler.brille.test.Builder
import no.nav.hjelpemidler.brille.tid.toInstant
import java.time.Instant
import java.time.LocalDate

class HotsakVedtakBuilder : Builder<HotsakVedtak> {
    var sakId: String = "-1"
    var vedtakId: String = "-1"
    var vedtaksdato: Instant = LocalDate.now().minusWeeks(2).atTime(14, 0).toInstant()
    var bestillingsdato: LocalDate = LocalDate.now().minusWeeks(3)

    override fun build(): HotsakVedtak = HotsakVedtak(
        sakId = sakId,
        vedtakId = vedtakId,
        vedtaksstatus = "INNVILGET",
        vedtaksdato = vedtaksdato,
        bestillingsdato = bestillingsdato,
    )
}

fun lagHotsakVedtak(block: HotsakVedtakBuilder.() -> Unit = {}): HotsakVedtak =
    HotsakVedtakBuilder().apply(block).build()

fun HotsakVedtak.toList(): List<HotsakVedtak> = listOf(this)
