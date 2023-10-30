package no.nav.hjelpemidler.brille.vedtak

import no.nav.hjelpemidler.brille.test.Builder
import java.time.LocalDate
import java.time.LocalDateTime

class EksisterendeVedtakBuilder : Builder<EksisterendeVedtak> {
    var id: Long = -1
    var fnrBarn: String = "12345678910"
    var fnrInnsender: String = "01987654321"
    var bestillingsdato: LocalDate = LocalDate.now().minusWeeks(3)
    var behandlingsresultat: String = "INNVILGET"
    var bestillingsreferanse: String = ""
    var opprettet: LocalDateTime = bestillingsdato.atTime(14, 0)

    override fun build(): EksisterendeVedtak = EksisterendeVedtak(
        id = id,
        fnrBarn = fnrBarn,
        fnrInnsender = fnrInnsender,
        bestillingsdato = bestillingsdato,
        behandlingsresultat = behandlingsresultat,
        bestillingsreferanse = bestillingsreferanse,
        opprettet = opprettet,
    )
}

fun lagEksisterendeVedtak(block: EksisterendeVedtakBuilder.() -> Unit = {}): EksisterendeVedtak =
    EksisterendeVedtakBuilder().also(block).build()

fun EksisterendeVedtak.toList(): List<EksisterendeVedtak> = listOf(this)
