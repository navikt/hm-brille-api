package no.nav.hjelpemidler.brille.vedtak

import com.fasterxml.jackson.databind.JsonNode
import kotliquery.Row
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.helse.rapids_rivers.asOptionalLocalDate
import no.nav.helse.rapids_rivers.asOptionalLocalDateTime
import no.nav.hjelpemidler.brille.jsonOrNull
import no.nav.hjelpemidler.brille.sats.SatsType
import no.nav.hjelpemidler.brille.tid.toInstant
import no.nav.hjelpemidler.brille.utbetaling.UtbetalingStatus
import no.nav.hjelpemidler.brille.vilkarsvurdering.Vilkårsgrunnlag
import no.nav.hjelpemidler.brille.vilkarsvurdering.Vilkårsvurdering
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
) {

    companion object {
        fun fromRow(row: Row): Kravlinje {
            val ekstraKravArray: Array<JsonNode>? = row.jsonOrNull("potensielt_bortfiltrerte_krav")
            val ekstraKrav: List<Kravlinje>? = (ekstraKravArray?.toList())?.map { node ->
                Kravlinje(
                    id = node.get("id").asLong(),
                    bestillingsdato = node.get("bestillingsdato").asLocalDate(),
                    behandlingsresultat = node.get("behandlingsresultat").textValue(),
                    opprettet = node.get("opprettet").asLocalDateTime(),
                    beløp = node.get("belop").decimalValue(),
                    bestillingsreferanse = node.get("bestillingsreferanse").textValue(),
                    utbetalingsdato = node.get("utbetalingsdato").asOptionalLocalDate(),
                    batchId = node.get("batch_id").textValue(),
                    batchTotalBeløp = node.get("batch_totalbelop").decimalValue(),
                    slettet = node.get("slettet").asOptionalLocalDateTime(),
                    potensieltBortfiltrerteKrav = null,
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
            )
        }
    }
}

enum class Behandlingsresultat {
    INNVILGET,
}
