package no.nav.hjelpemidler.brille.vedtak

import kotliquery.Row
import no.nav.hjelpemidler.brille.sats.SatsType
import no.nav.hjelpemidler.brille.vilkarsvurdering.Vilkårsvurdering
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class Vedtak<T>(
    val id: Long = -1,
    val fnrBarn: String,
    val fnrInnsender: String,
    val orgnr: String,
    val bestillingsdato: LocalDate,
    val brillepris: BigDecimal,
    val bestillingsreferanse: String,
    val vilkårsvurdering: Vilkårsvurdering<T>,
    val behandlingsresultat: Behandlingsresultat,
    val sats: SatsType,
    val satsBeløp: Int,
    val satsBeskrivelse: String,
    val beløp: BigDecimal,
    val opprettet: LocalDateTime = LocalDateTime.now(),
)

data class EksisterendeVedtak(
    val id: Long,
    val fnrBarn: String,
    val bestillingsdato: LocalDate,
    val behandlingsresultat: String,
    val opprettet: LocalDateTime,
)

data class OversiktVedtakPaged(
    val numberOfPages: Int,
    val itemsPerPage: Int,
    val totalItems: Int,
    var items: List<OversiktVedtak>,
)

data class OversiktVedtak(
    val id: Long,
    var orgnavn: String,
    val orgnr: String,
    val barnsNavn: String,
    val barnsFnr: String,
    val barnsAlder: Int,
    val høyreSfære: Double,
    val høyreSylinder: Double,
    val venstreSfære: Double,
    val venstreSylinder: Double,
    val bestillingsdato: LocalDate,
    val brillepris: BigDecimal,
    val beløp: BigDecimal,
    val bestillingsreferanse: String,
    val satsNr: Int,
    val satsBeløp: Int,
    val satsBeskrivelse: String,
    val behandlingsresultat: String,
    val utbetalingsdato: LocalDate?,
    val opprettet: LocalDateTime,
    val slettet: LocalDateTime?,
)

data class Kravlinje(
    val id: Long,
    val bestillingsdato: LocalDate,
    val behandlingsresultat: String,
    val opprettet: LocalDateTime,
    val beløp: BigDecimal,
    val bestillingsreferanse: String,
    val utbetalingsdato: LocalDate?,
    val batchId: String?
) {

    companion object {
        fun fromRow(row: Row) = Kravlinje(
            id = row.long("id"),
            bestillingsdato = row.localDate("bestillingsdato"),
            behandlingsresultat = row.string("behandlingsresultat"),
            opprettet = row.localDateTime("opprettet"),
            beløp = row.bigDecimal("belop"),
            bestillingsreferanse = row.string("bestillingsreferanse"),
            utbetalingsdato = row.localDateOrNull("utbetalingsdato"),
            batchId = row.stringOrNull("batch_id")
        )
    }
}

enum class Behandlingsresultat {
    INNVILGET
}
