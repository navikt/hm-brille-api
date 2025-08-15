package no.nav.hjelpemidler.brille.vedtak

import no.nav.hjelpemidler.brille.sats.SatsType
import no.nav.hjelpemidler.brille.vilkarsvurdering.Vilkårsvurdering
import no.nav.hjelpemidler.database.JdbcOperations
import no.nav.hjelpemidler.database.Row
import no.nav.hjelpemidler.database.Store
import org.intellij.lang.annotations.Language
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

interface SlettVedtakStore : Store {
    fun hentVedtakSlettet(vedtakId: Long): VedtakSlettet?
    fun slettVedtak(vedtakId: Long, slettetAv: String, slettetAvType: SlettetAvType = SlettetAvType.INNSENDER): Int?
}

class SlettVedtakStorePostgres(private val tx: JdbcOperations) : SlettVedtakStore {
    override fun hentVedtakSlettet(vedtakId: Long): VedtakSlettet? {
        @Language("PostgreSQL")
        val sql = """
            SELECT id,
                   fnr_barn,
                   fnr_innsender,
                   orgnr,
                   bestillingsdato,
                   brillepris,
                   bestillingsreferanse,
                   vilkarsvurdering,
                   behandlingsresultat,
                   sats,
                   sats_belop,
                   sats_beskrivelse,
                   belop,
                   opprettet,
                   slettet
            FROM vedtak_slettet_v1
            WHERE id = :id
        """.trimIndent()
        return tx.singleOrNull(sql, mapOf("id" to vedtakId)) { row ->
            mapVedtakSlettet(row)
        }
    }

    override fun slettVedtak(vedtakId: Long, slettetAv: String, slettetAvType: SlettetAvType): Int? {
        @Language("PostgreSQL")
        val sql = """
            INSERT INTO vedtak_slettet_v1 (id,
                                           fnr_barn,
                                           fnr_innsender,
                                           navn_innsender,
                                           orgnr,
                                           bestillingsdato,
                                           brillepris,
                                           bestillingsreferanse,
                                           vilkarsvurdering,
                                           behandlingsresultat,
                                           sats,
                                           sats_belop,
                                           sats_beskrivelse,
                                           belop,
                                           opprettet,
                                           kilde)
            SELECT id,
                   fnr_barn,
                   fnr_innsender,
                   navn_innsender,
                   orgnr,
                   bestillingsdato,
                   brillepris,
                   bestillingsreferanse,
                   vilkarsvurdering,
                   behandlingsresultat,
                   sats,
                   sats_belop,
                   sats_beskrivelse,
                   belop,
                   opprettet,
                   kilde
            FROM vedtak_v1
            WHERE id = :id;
            DELETE
            FROM vedtak_v1
            WHERE id = :id
        """.trimIndent()

        tx.update(sql, mapOf("id" to vedtakId)).actualRowCount.takeIf { it > 0 } ?: return null

        val sql2 = """
            UPDATE vedtak_slettet_v1
            SET slettet_av = :slettetAv, slettet_av_type = :slettetAvType
            WHERE id = :id
        """.trimIndent()
        return tx.update(
            sql2,
            mapOf(
                "id" to vedtakId,
                "slettetAv" to slettetAv,
                "slettetAvType" to slettetAvType.toString(),
            ),
        ).actualRowCount
    }

    private fun mapVedtakSlettet(row: Row) = VedtakSlettet(
        id = row.long("id"),
        fnrBarn = row.string("fnr_barn"),
        fnrInnsender = row.string("fnr_innsender"),
        orgnr = row.string("orgnr"),
        bestillingsdato = row.localDate("bestillingsdato"),
        brillepris = row.bigDecimal("brillepris"),
        bestillingsreferanse = row.string("bestillingsreferanse"),
        vilkårsvurdering = row.json("vilkarsvurdering"),
        behandlingsresultat = Behandlingsresultat.valueOf(row.string("behandlingsresultat")),
        sats = SatsType.valueOf(row.string("sats")),
        satsBeløp = row.int("sats_belop"),
        satsBeskrivelse = row.string("sats_beskrivelse"),
        beløp = row.bigDecimal("belop"),
        opprettet = row.localDateTime("opprettet"),
        slettet = row.localDateTime("slettet"),
    )
}

enum class SlettetAvType {
    INNSENDER,
    NAV_ADMIN,
}

data class VedtakSlettet(
    val id: Long = -1,
    val fnrBarn: String,
    val fnrInnsender: String,
    val orgnr: String,
    val bestillingsdato: LocalDate,
    val brillepris: BigDecimal,
    val bestillingsreferanse: String,
    val vilkårsvurdering: Vilkårsvurdering<*>,
    val behandlingsresultat: Behandlingsresultat,
    val sats: SatsType,
    val satsBeløp: Int,
    val satsBeskrivelse: String,
    val beløp: BigDecimal,
    val opprettet: LocalDateTime = LocalDateTime.now(),
    val slettet: LocalDateTime = LocalDateTime.now(),
)
