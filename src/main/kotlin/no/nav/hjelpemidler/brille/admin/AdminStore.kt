package no.nav.hjelpemidler.brille.admin

import kotliquery.Session
import no.nav.hjelpemidler.brille.store.Store
import no.nav.hjelpemidler.brille.store.TransactionalStore
import no.nav.hjelpemidler.brille.store.query
import no.nav.hjelpemidler.brille.store.queryList
import org.intellij.lang.annotations.Language
import java.time.LocalDateTime

interface AdminStore : Store {
    fun hentVedtakListe(fnr: String): List<VedtakListe>
    fun hentVedtak(vedtakId: Long): Vedtak?
}

class AdminStorePostgres(private val sessionFactory: () -> Session) : AdminStore,
    TransactionalStore(sessionFactory) {

    override fun hentVedtakListe(fnr: String): List<VedtakListe> = session {
        @Language("PostgreSQL")
        val sql = """
            SELECT
                COALESCE(v.id, vs.id) AS id,
                u.utbetalingsdato,
                vs.slettet
            FROM vedtak_v1 v
            FULL OUTER JOIN vedtak_slettet_v1 vs ON v.id = vs.id
            LEFT JOIN utbetaling_v1 u ON v.id = u.vedtak_id
            WHERE
                (v.fnr_barn = :fnr OR vs.fnr_barn = :fnr)
            ;
        """.trimIndent()

        sessionFactory().queryList(
            sql,
            mapOf("fnr" to fnr)
        ) { row ->
            VedtakListe(
                sakId = row.long("id"),
                utbetalingsdato = row.localDateTimeOrNull("utbetalingsdato"),
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
                COALESCE(v.opprettet, vs.opprettet) AS opprettet,
                u.utbetalingsdato,
                vs.slettet,
                vs.slettet_av_type
            FROM vedtak_v1 v
            FULL OUTER JOIN vedtak_slettet_v1 vs ON v.id = vs.id
            LEFT JOIN utbetaling_v1 u ON v.id = u.vedtak_id
            WHERE
                (v.id = :vedtakId OR vs.id = :vedtakId)
            ;
        """.trimIndent()

        sessionFactory().query(
            sql,
            mapOf("vedtakId" to vedtakId)
        ) { row ->
            Vedtak(
                sakId = row.long("id"),
                orgnr = row.string("orgnr"),
                opprettet = row.localDateTime("opprettet"),
                bestillingsreferanse = row.string("bestillingsreferanse"),
                utbetalingsdato = row.localDateTimeOrNull("utbetalingsdato"),
                slettet = row.localDateTimeOrNull("slettet"),
                slettetAvType = row.stringOrNull("slettet_av_type"),
            )
        }
    }
}

data class VedtakListe(
    val sakId: Long,
    val utbetalingsdato: LocalDateTime?,
    val slettet: LocalDateTime?,
)

data class Vedtak(
    val sakId: Long,
    val orgnr: String,
    val bestillingsreferanse: String,
    val opprettet: LocalDateTime,
    val utbetalingsdato: LocalDateTime?,
    val slettet: LocalDateTime?,
    val slettetAvType: String?,
)
