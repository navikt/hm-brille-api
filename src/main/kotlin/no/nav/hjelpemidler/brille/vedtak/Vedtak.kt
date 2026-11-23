package no.nav.hjelpemidler.brille.vedtak

import no.nav.hjelpemidler.brille.sats.SatsType
import no.nav.hjelpemidler.brille.utbetaling.UtbetalingStatus
import no.nav.hjelpemidler.brille.vilkarsvurdering.Vilkårsgrunnlag
import no.nav.hjelpemidler.brille.vilkarsvurdering.Vilkårsvurdering
import no.nav.hjelpemidler.database.Row
import no.nav.hjelpemidler.time.toInstant
import tools.jackson.databind.JsonNode
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

data class Vedtak<T>(
    val id: Long = -1,
    val fnrBarn: String,
    val fnrInnsender: String,
    val navnInnsender: String,
    val orgnr: String,
    val butikkId: String?,
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
    val kilde: KravKilde,
    val avsendersystemOrgNr: String? = null,
)

data class EksisterendeVedtak(
    val id: Long,
    val fnrBarn: String,
    val fnrInnsender: String,
    override val bestillingsdato: LocalDate,
    val behandlingsresultat: String,
    val bestillingsreferanse: String,
    val opprettet: LocalDateTime,
) : Vilkårsgrunnlag.Vedtak {
    override val vedtaksdato: Instant = opprettet.toInstant()
}

data class OversiktVedtakPaged(
    val numberOfPages: Int,
    val itemsPerPage: Int,
    val totalItems: Int,
    var items: List<OversiktVedtakListItem>,
)

data class OversiktVedtakListItem(
    val id: Long,
    var orgnavn: String,
    val orgnr: String,
    val barnsNavn: String,
    val bestillingsreferanse: String,
    val utbetalingsdato: LocalDate?,
    val utbetalingsstatus: UtbetalingStatus?,
    val opprettet: LocalDateTime,
    val slettet: LocalDateTime?,
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
    val utbetalingsstatus: UtbetalingStatus?,
    val opprettet: LocalDateTime,
    val slettet: LocalDateTime?,
    val slettetAvType: SlettetAvType?,
)

data class Kravlinje(
    val id: Long,
    val bestillingsdato: LocalDate,
    val behandlingsresultat: String,
    val opprettet: LocalDateTime,
    val beløp: BigDecimal,
    val bestillingsreferanse: String,
    val utbetalingsdato: LocalDate?,
    val batchId: String?,
    val batchTotalBeløp: BigDecimal?,
    val slettet: LocalDateTime?,
    val potensieltBortfiltrerteKrav: List<Kravlinje>?,
    val optiker: String?,
) {

    companion object {
        fun fromRow(row: Row): Kravlinje {
            val ekstraKravArray: Array<JsonNode>? = row.jsonOrNull("potensielt_bortfiltrerte_krav")
            val ekstraKrav: List<Kravlinje>? = (ekstraKravArray?.toList())?.map { node ->
                Kravlinje(
                    id = node.get("id").asLong(),
                    bestillingsdato = LocalDate.parse(node.get("bestillingsdato").asString()),
                    behandlingsresultat = node.get("behandlingsresultat").stringValue(),
                    opprettet = LocalDateTime.parse(node.get("opprettet").asString()),
                    beløp = node.get("belop").decimalValue(),
                    bestillingsreferanse = node.get("bestillingsreferanse").stringValue(),
                    utbetalingsdato = node.get("utbetalingsdato").stringValue(null)?.takeIf(String::isNotEmpty)?.let(LocalDate::parse),
                    batchId = node.get("batch_id").stringValue(null),
                    batchTotalBeløp = node.get("batch_totalbelop").decimalValue(BigDecimal.ZERO),
                    slettet = node.get("slettet").stringValue(null)?.takeIf(String::isNotEmpty)?.let(LocalDateTime::parse),
                    potensieltBortfiltrerteKrav = null,
                    optiker = node.get("navn_innsender").stringValue(null),
                )
            }

            return Kravlinje(
                id = row.long("id"),
                bestillingsdato = row.localDate("bestillingsdato"),
                behandlingsresultat = row.string("behandlingsresultat"),
                opprettet = row.localDateTime("opprettet"),
                beløp = row.bigDecimal("belop"),
                bestillingsreferanse = row.string("bestillingsreferanse"),
                utbetalingsdato = row.localDateOrNull("utbetalingsdato"),
                batchId = row.stringOrNull("batch_id"),
                batchTotalBeløp = row.bigDecimalOrNull("batch_totalbelop"),
                slettet = row.localDateTimeOrNull("slettet"),
                potensieltBortfiltrerteKrav = ekstraKrav,
                optiker = row.stringOrNull("navn_innsender"),
            )
        }
    }
}

enum class Behandlingsresultat {
    INNVILGET,
}
