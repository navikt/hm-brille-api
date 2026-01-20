package no.nav.hjelpemidler.brille.admin

import no.nav.hjelpemidler.brille.pdl.HentPersonExtensions.navn
import no.nav.hjelpemidler.brille.pdl.PersonCompat
import no.nav.hjelpemidler.brille.utbetaling.UtbetalingStatus
import no.nav.hjelpemidler.brille.vedtak.SlettetAvType
import no.nav.hjelpemidler.database.JdbcOperations
import no.nav.hjelpemidler.database.Store
import no.nav.hjelpemidler.database.pgJsonbOf
import org.intellij.lang.annotations.Language
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

interface AdminStore : Store {
    fun hentVedtakListe(fnr: String): List<VedtakListe>
    fun hentVedtak(vedtakId: Long): Vedtak?
    fun lagreAvvisning(
        fnrBarn: String,
        fnrInnsender: String,
        orgnr: String,
        butikkId: String?,
        årsaker: List<String>,
    )

    fun hentAvvisning(fnrBarn: String, etterVedtak: VedtakListe?): Avvisning?
    fun harAvvisningDeSiste7DageneFor(fnrBarn: String, orgnr: String): Boolean
    fun hentUtbetalinger(utbetalingsRef: String): List<Utbetaling>
}

class AdminStorePostgres(private val tx: JdbcOperations) : AdminStore {
    override fun hentVedtakListe(fnr: String): List<VedtakListe> {
        @Language("PostgreSQL")
        val sql = """
            SELECT
                COALESCE(v.id, vs.id) AS id,
                COALESCE(v.bestillingsdato, vs.bestillingsdato) AS bestillingsdato,
                COALESCE(v.opprettet, vs.opprettet) AS opprettet,
                COALESCE(v.vilkarsvurdering, vs.vilkarsvurdering) -> 'grunnlag' -> 'pdlOppslagBarn' ->> 'data' AS pdlOppslag,
                COALESCE(u1.utbetalingsdato, u2.utbetalingsdato) AS utbetalingsdato,
                COALESCE(u1.status, u2.status) AS utbetalingsstatus,
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

        return tx.list(
            sql,
            mapOf("fnr" to fnr),
        ) { row ->
            val person: PersonCompat = row.json("pdlOppslag")
            VedtakListe(
                vedtakId = row.long("id"),
                barnsNavn = person.asPerson().navn(),
                bestillingsdato = row.localDate("bestillingsdato"),
                opprettet = row.localDateTime("opprettet"),
                utbetalt = row.localDateTimeOrNull("utbetalingsdato"),
                utbetalingsstatus = row.stringOrNull("utbetalingsstatus")
                    ?.let { status -> UtbetalingStatus.valueOf(status) },
                slettet = row.localDateTimeOrNull("slettet"),
            )
        }
    }

    override fun hentVedtak(vedtakId: Long): Vedtak? {
        @Language("PostgreSQL")
        val sql = """
            SELECT
                COALESCE(v.id, vs.id) AS id,
                COALESCE(v.orgnr, vs.orgnr) AS orgnr,
                COALESCE(v.fnr_innsender, vs.fnr_innsender) AS fnr_innsender,
                COALESCE(v.navn_innsender, vs.navn_innsender) AS navn_innsender,
                COALESCE(v.bestillingsreferanse, vs.bestillingsreferanse) AS bestillingsreferanse,
                COALESCE(v.bestillingsdato, vs.bestillingsdato) AS bestillingsdato,
                COALESCE(v.belop, vs.belop) AS belop,
                COALESCE(v.opprettet, vs.opprettet) AS opprettet,
                COALESCE(v.vilkarsvurdering, vs.vilkarsvurdering) -> 'grunnlag' -> 'pdlOppslagBarn' ->> 'data' AS pdlOppslag,
                COALESCE(u1.utbetalingsdato, u2.utbetalingsdato) AS utbetalingsdato,
                COALESCE(u1.status, u2.status) AS utbetalingsstatus,
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

        return tx.singleOrNull(
            sql,
            mapOf("vedtakId" to vedtakId),
        ) { row ->
            val person: PersonCompat = row.json("pdlOppslag")
            Vedtak(
                vedtakId = row.long("id"),
                orgnr = row.string("orgnr"),
                innsenderFnr = row.string("fnr_innsender"),
                innsenderNavn = row.string("navn_innsender"),
                barnsNavn = person.asPerson().navn(),
                bestillingsreferanse = row.string("bestillingsreferanse"),
                bestillingsdato = row.localDate("bestillingsdato"),
                beløp = row.bigDecimal("belop"),
                opprettet = row.localDateTime("opprettet"),
                utbetalingsdato = row.localDateOrNull("utbetalingsdato"),
                utbetalingsstatus = row.stringOrNull("utbetalingsstatus")
                    ?.let { status -> UtbetalingStatus.valueOf(status) },
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

    override fun lagreAvvisning(
        fnrBarn: String,
        fnrInnsender: String,
        orgnr: String,
        butikkId: String?,
        årsaker: List<String>,
    ) {
        @Language("PostgreSQL")
        val sql = """
            INSERT INTO avviste_krav_v1 (fnrBarn, fnrInnsender, orgnr, butikkId, begrunnelser, opprettet) VALUES (:fnrBarn, :fnrInnsender, :orgnr, :butikkId, :begrunnelser, NOW())
        """.trimIndent()

        tx.update(
            sql,
            mapOf(
                "fnrBarn" to fnrBarn,
                "fnrInnsender" to fnrInnsender,
                "orgnr" to orgnr,
                "butikkId" to butikkId,
                "begrunnelser" to pgJsonbOf(årsaker),
            ),
        ).expect(1)
    }

    override fun hentAvvisning(fnrBarn: String, etterVedtak: VedtakListe?): Avvisning? {
        val AND_WHERE = if (etterVedtak != null) "AND opprettet > :vedtakOpprettet" else ""

        @Language("PostgreSQL")
        val sql = """
            SELECT orgnr, begrunnelser, opprettet FROM avviste_krav_v1 WHERE fnrBarn = :fnrBarn $AND_WHERE ORDER BY opprettet DESC LIMIT 1
        """.trimIndent()

        return tx.singleOrNull(
            sql,
            mapOf(
                "fnrBarn" to fnrBarn,
                "vedtakOpprettet" to etterVedtak?.opprettet,
            ),
        ) { row ->
            Avvisning(
                orgnr = row.string("orgnr"),
                orgNavn = "",
                årsaker = row.json("begrunnelser"),
                opprettet = row.localDateTime("opprettet"),
            )
        }
    }

    override fun harAvvisningDeSiste7DageneFor(fnrBarn: String, orgnr: String): Boolean {
        @Language("PostgreSQL")
        val sql = """
            SELECT 1 FROM avviste_krav_v1
            WHERE
                fnrBarn = :fnrBarn AND
                orgnr = :orgnr AND
                opprettet > :innslagspunkt
            LIMIT 1
        """.trimIndent()

        return tx.singleOrNull(
            sql,
            mapOf(
                "fnrBarn" to fnrBarn,
                "orgnr" to orgnr,
                "innslagspunkt" to LocalDateTime.now().minusDays(7),
            ),
        ) { true } ?: false
    }

    override fun hentUtbetalinger(utbetalingsRef: String): List<Utbetaling> {
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

        return tx.list(
            sql,
            mapOf("utbetalingsRef" to utbetalingsRef),
        ) { row ->
            val person: PersonCompat = row.json("pdlOppslag")
            Utbetaling(
                orgnr = row.string("orgnr"),
                vedtakId = row.long("id"),
                bestillingsreferanse = row.string("bestillingsreferanse"),
                barnsNavn = person.asPerson().navn(),
                beløp = row.bigDecimal("belop"),
                slettet = row.localDateTimeOrNull("slettet"),
            )
        }
    }
}

data class VedtakListe(
    val vedtakId: Long,
    val barnsNavn: String,
    val bestillingsdato: LocalDate,
    val opprettet: LocalDateTime,
    val utbetalt: LocalDateTime?,
    val utbetalingsstatus: UtbetalingStatus?,
    val slettet: LocalDateTime?,
)

data class Vedtak(
    val vedtakId: Long,
    val orgnr: String,
    val innsenderFnr: String,
    val innsenderNavn: String,
    val barnsNavn: String,
    val bestillingsreferanse: String,
    val bestillingsdato: LocalDate,
    val beløp: BigDecimal,
    val opprettet: LocalDateTime,
    val utbetalingsdato: LocalDate?,
    val utbetalingsstatus: UtbetalingStatus?,
    val batchId: String?,
    val slettet: LocalDateTime?,
    val slettetAv: String?,
    val slettetAvType: SlettetAvType?,
)

data class Avvisning(
    val orgnr: String,
    val orgNavn: String,
    val årsaker: List<String>,
    val opprettet: LocalDateTime,
)

data class Utbetaling(
    val orgnr: String,
    val vedtakId: Long,
    val bestillingsreferanse: String,
    val barnsNavn: String,
    val beløp: BigDecimal,
    val slettet: LocalDateTime?,
)
