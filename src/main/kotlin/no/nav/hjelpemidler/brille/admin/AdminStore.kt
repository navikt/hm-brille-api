package no.nav.hjelpemidler.brille.admin

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Session
import no.nav.hjelpemidler.brille.jsonMapper
import no.nav.hjelpemidler.brille.pdl.HentPersonExtensions.navn
import no.nav.hjelpemidler.brille.pdl.Person
import no.nav.hjelpemidler.brille.store.Store
import no.nav.hjelpemidler.brille.store.TransactionalStore
import no.nav.hjelpemidler.brille.store.query
import no.nav.hjelpemidler.brille.store.queryList
import no.nav.hjelpemidler.brille.vedtak.SlettetAvType
import org.intellij.lang.annotations.Language
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

interface AdminStore : Store {
    fun hentVedtakListe(fnr: String): List<VedtakListe>
    fun hentVedtak(vedtakId: Long): Vedtak?
    fun hentUtbetalinger(utbetalingsRef: String): List<Utbetaling>
}

class AdminStorePostgres(private val sessionFactory: () -> Session) : AdminStore,
    TransactionalStore(sessionFactory) {

    override fun hentVedtakListe(fnr: String): List<VedtakListe> = session {
        @Language("PostgreSQL")
        val sql = """
            SELECT
                COALESCE(v.id, vs.id) AS id,
                COALESCE(v.opprettet, vs.opprettet) AS opprettet,
                COALESCE(v.vilkarsvurdering, vs.vilkarsvurdering) -> 'grunnlag' -> 'pdlOppslagBarn' ->> 'data' AS pdlOppslag,
                COALESCE(u1.utbetalingsdato, u2.utbetalingsdato) AS utbetalingsdato,
                vs.slettet
            FROM vedtak_v1 v
            FULL OUTER JOIN vedtak_slettet_v1 vs ON v.id = vs.id
            LEFT JOIN utbetaling_v1 u1 ON v.id = u1.vedtak_id
            LEFT JOIN utbetaling_v1 u2 ON vs.id = u2.vedtak_id
            WHERE
                (v.fnr_barn = :fnr OR vs.fnr_barn = :fnr)
            ORDER BY opprettet DESC
            ;
        """.trimIndent()

        sessionFactory().queryList(
            sql,
            mapOf("fnr" to fnr)
        ) { row ->
            val person: Person = jsonMapper.readValue(row.string("pdlOppslag"))
            VedtakListe(
                vedtakId = row.long("id"),
                barnsNavn = person.navn(),
                opprettet = row.localDateTime("opprettet"),
                utbetalt = row.localDateTimeOrNull("utbetalingsdato"),
                slettet = row.localDateTimeOrNull("slettet"),
            )
        }
    }

    override fun hentVedtak(vedtakId: Long): Vedtak? = session {
        @Language("PostgreSQL")
        val sql = """
            SELECT
                COALESCE(v.id, vs.id) AS id,
                COALESCE(v.orgnr, vs.orgnr) AS orgnr,
                COALESCE(v.bestillingsreferanse, vs.bestillingsreferanse) AS bestillingsreferanse,
                COALESCE(v.bestillingsdato, vs.bestillingsdato) AS bestillingsdato,
                COALESCE(v.belop, vs.belop) AS belop,
                COALESCE(v.opprettet, vs.opprettet) AS opprettet,
                COALESCE(v.vilkarsvurdering, vs.vilkarsvurdering) -> 'grunnlag' -> 'pdlOppslagBarn' ->> 'data' AS pdlOppslag,
                COALESCE(u1.utbetalingsdato, u2.utbetalingsdato) AS utbetalingsdato,
                COALESCE(u1.batch_id, u2.batch_id) AS batch_id,
                vs.slettet,
                vs.slettet_av,
                vs.slettet_av_type
            FROM vedtak_v1 v
            FULL OUTER JOIN vedtak_slettet_v1 vs ON v.id = vs.id
            LEFT JOIN utbetaling_v1 u1 ON v.id = u1.vedtak_id
            LEFT JOIN utbetaling_v1 u2 ON vs.id = u2.vedtak_id
            WHERE
                (v.id = :vedtakId OR vs.id = :vedtakId)
            ;
        """.trimIndent()

        sessionFactory().query(
            sql,
            mapOf("vedtakId" to vedtakId)
        ) { row ->
            val person: Person = jsonMapper.readValue(row.string("pdlOppslag"))
            Vedtak(
                vedtakId = row.long("id"),
                orgnr = row.string("orgnr"),
                barnsNavn = person.navn(),
                bestillingsreferanse = row.string("bestillingsreferanse"),
                bestillingsdato = row.localDate("bestillingsdato"),
                beløp = row.bigDecimal("belop"),
                opprettet = row.localDateTime("opprettet"),
                utbetalingsdato = row.localDateTimeOrNull("utbetalingsdato"),
                batchId = row.stringOrNull("batch_id"),
                slettet = row.localDateTimeOrNull("slettet"),
                slettetAv = row.stringOrNull("slettet_av_type")?.let {
                    val slettetAvType = SlettetAvType.valueOf(it)
                    if (slettetAvType == SlettetAvType.INNSENDER) {
                        null
                    } else {
                        row.string("slettet_av")
                    }
                },
                slettetAvType = row.stringOrNull("slettet_av_type")?.let { SlettetAvType.valueOf(it) },
            )
        }
    }
    override fun hentUtbetalinger(utbetalingsRef: String): List<Utbetaling> = session {
        @Language("PostgreSQL")
        val sql = """
            SELECT
                COALESCE(v.orgnr, vs.orgnr) AS orgnr,
                COALESCE(v.id, vs.id) AS id,
                COALESCE(v.bestillingsreferanse, vs.bestillingsreferanse) AS bestillingsreferanse,
                COALESCE(v.vilkarsvurdering, vs.vilkarsvurdering) -> 'grunnlag' -> 'pdlOppslagBarn' ->> 'data' AS pdlOppslag,
                COALESCE(v.belop, vs.belop) AS belop,
                vs.slettet
            FROM vedtak_v1 v
            FULL OUTER JOIN vedtak_slettet_v1 vs ON v.id = vs.id
            LEFT JOIN utbetaling_v1 u1 ON v.id = u1.vedtak_id
            LEFT JOIN utbetaling_v1 u2 ON vs.id = u2.vedtak_id
            WHERE u1.batch_id = :utbetalingsRef OR u2.batch_id = :utbetalingsRef
            ORDER BY id
            ;
        """.trimIndent()

        sessionFactory().queryList(
            sql,
            mapOf("utbetalingsRef" to utbetalingsRef)
        ) { row ->
            val person: Person = jsonMapper.readValue(row.string("pdlOppslag"))
            Utbetaling(
                orgnr = row.string("orgnr"),
                vedtakId = row.long("id"),
                bestillingsreferanse = row.string("bestillingsreferanse"),
                barnsNavn = person.navn(),
                beløp = row.bigDecimal("belop"),
                slettet = row.localDateTimeOrNull("slettet"),
            )
        }
    }
}

data class VedtakListe(
    val vedtakId: Long,
    val barnsNavn: String,
    val opprettet: LocalDateTime,
    val utbetalt: LocalDateTime?,
    val slettet: LocalDateTime?,
)

data class Vedtak(
    val vedtakId: Long,
    val orgnr: String,
    val barnsNavn: String,
    val bestillingsreferanse: String,
    val bestillingsdato: LocalDate,
    val beløp: BigDecimal,
    val opprettet: LocalDateTime,
    val utbetalingsdato: LocalDateTime?,
    val batchId: String?,
    val slettet: LocalDateTime?,
    val slettetAv: String?,
    val slettetAvType: SlettetAvType?,
)

data class Utbetaling(
    val orgnr: String,
    val vedtakId: Long,
    val bestillingsreferanse: String,
    val barnsNavn: String,
    val beløp: BigDecimal,
    val slettet: LocalDateTime?,
)
