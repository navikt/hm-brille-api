package no.nav.hjelpemidler.brille.vedtak

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import kotliquery.Session
import no.nav.hjelpemidler.brille.json
import no.nav.hjelpemidler.brille.jsonMapper
import no.nav.hjelpemidler.brille.pdl.HentPersonExtensions.alder
import no.nav.hjelpemidler.brille.pdl.HentPersonExtensions.navn
import no.nav.hjelpemidler.brille.pdl.Person
import no.nav.hjelpemidler.brille.pgObjectOf
import no.nav.hjelpemidler.brille.sats.SatsType
import no.nav.hjelpemidler.brille.store.Store
import no.nav.hjelpemidler.brille.store.TransactionalStore
import no.nav.hjelpemidler.brille.store.query
import no.nav.hjelpemidler.brille.store.queryList
import no.nav.hjelpemidler.brille.store.update
import no.nav.hjelpemidler.brille.vilkarsvurdering.Vilkårsvurdering
import org.intellij.lang.annotations.Language
import java.time.LocalDateTime
import kotlin.math.ceil

interface VedtakStore : Store {
    fun hentTidligereBrukteOrgnrForInnsender(fnrInnsender: String): List<String>
    fun hentVedtakForBarn(fnrBarn: String): List<EksisterendeVedtak>
    fun hentVedtakForOptiker(fnrInnsender: String, vedtakId: Long): OversiktVedtak?
    fun hentAlleVedtakForOptiker(fnrInnsender: String, page: Int, itemsPerPage: Int = 10): OversiktVedtakPaged
    fun <T> lagreVedtak(vedtak: Vedtak<T>): Vedtak<T>
    fun lagreVedtakIKø(vedtakId: Long, opprettet: LocalDateTime): Long
    fun <T> hentVedtakForUtbetaling(
        opprettet: LocalDateTime,
        behandlingsresultat: Behandlingsresultat = Behandlingsresultat.INNVILGET
    ): List<Vedtak<T>>
    fun fjernFraVedTakKø(vedtakId: Long): Int?
    fun <T> hentVedtak(vedtakId: Long): Vedtak<T>?
    fun hentAntallVedtakIKø(): Int
}

class VedtakStorePostgres(private val sessionFactory: () -> Session) : VedtakStore,
    TransactionalStore(sessionFactory) {

    override fun lagreVedtakIKø(vedtakId: Long, opprettet: LocalDateTime) = transaction {
        @Language("PostgreSQL")
        val sql = """
            INSERT INTO vedtak_ko_v1 (
                id,
                opprettet
            )
            VALUES (
                :vedtakId,
                :opprettet
            )
            RETURNING id
        """.trimIndent()
        val id = it.query(
            sql,
            mapOf(
                "vedtakId" to vedtakId,
                "opprettet" to opprettet,
            )
        ) { row ->
            row.long("id")
        }
        requireNotNull(id) { "Lagring av vedtak feilet, id var null" }
        id
    }

    override fun hentVedtakForBarn(fnrBarn: String): List<EksisterendeVedtak> = session {
        @Language("PostgreSQL")
        val sql = """
            SELECT id, fnr_barn, bestillingsdato, behandlingsresultat, opprettet
            FROM vedtak_v1
            WHERE fnr_barn = :fnr_barn 
        """.trimIndent()
        it.queryList(sql, mapOf("fnr_barn" to fnrBarn)) { row ->
            EksisterendeVedtak(
                id = row.long("id"),
                fnrBarn = row.string("fnr_barn"),
                bestillingsdato = row.localDate("bestillingsdato"),
                behandlingsresultat = row.string("behandlingsresultat"),
                opprettet = row.localDateTime("opprettet"),
            )
        }
    }

    override fun hentVedtakForOptiker(fnrInnsender: String, vedtakId: Long): OversiktVedtak? = session {
        @Language("PostgreSQL")
        val sql = """
            SELECT
                COALESCE(v.id, vs.id) AS id,
                COALESCE(v.orgnr, vs.orgnr) AS orgnr,
                COALESCE(v.bestillingsdato, vs.bestillingsdato) AS bestillingsdato,
                COALESCE(v.brillepris, vs.brillepris) AS brillepris,
                COALESCE(v.bestillingsreferanse, vs.bestillingsreferanse) AS bestillingsreferanse,
                COALESCE(v.sats, vs.sats) AS sats,
                COALESCE(v.sats_belop, vs.sats_belop) AS sats_belop,
                COALESCE(v.sats_beskrivelse, vs.sats_beskrivelse) AS sats_beskrivelse,
                COALESCE(v.belop, vs.belop) AS belop,
                COALESCE(v.behandlingsresultat, vs.behandlingsresultat) AS behandlingsresultat,
                COALESCE(v.opprettet, vs.opprettet) AS opprettet,
                COALESCE(v.fnr_barn, vs.fnr_barn) AS fnr_barn,
                COALESCE(v.vilkarsvurdering, vs.vilkarsvurdering) -> 'grunnlag' -> 'brilleseddel' ->> 'høyreSfære' AS høyreSfære,
                COALESCE(v.vilkarsvurdering, vs.vilkarsvurdering) -> 'grunnlag' -> 'brilleseddel' ->> 'høyreSfære' AS høyreSylinder,
                COALESCE(v.vilkarsvurdering, vs.vilkarsvurdering) -> 'grunnlag' -> 'brilleseddel' ->> 'høyreSylinder' AS venstreSfære,
                COALESCE(v.vilkarsvurdering, vs.vilkarsvurdering) -> 'grunnlag' -> 'brilleseddel' ->> 'høyreSylinder' AS venstreSylinder,
                COALESCE(v.vilkarsvurdering, vs.vilkarsvurdering) -> 'grunnlag' -> 'pdlOppslagBarn' ->> 'data' AS pdlOppslag,
                u.utbetalingsdato,
                vs.slettet
            FROM vedtak_v1 v
            FULL OUTER JOIN vedtak_slettet_v1 vs ON v.id = vs.id
            LEFT JOIN utbetaling_v1 u ON v.id = u.vedtak_id
            WHERE
                (v.id = :vedtak_id OR vs.id = :vedtak_id) AND
                (v.fnr_innsender = :fnr_innsender OR vs.fnr_innsender = :fnr_innsender) AND
                (u.utbetalingsdato IS NULL OR (u.utbetalingsdato > NOW() - '28 days'::interval)) AND
                (vs.slettet IS NULL OR (vs.slettet > NOW() - '28 days'::interval))
        """.trimIndent()
        it.query(
            sql,
            mapOf(
                "fnr_innsender" to fnrInnsender,
                "vedtak_id" to vedtakId,
            )
        ) { row ->
            val person: Person = jsonMapper.readValue(row.string("pdlOppslag"))

            OversiktVedtak(
                id = row.long("id"),
                orgnavn = "",
                orgnr = row.string("orgnr"),
                barnsNavn = person.navn(),
                barnsFnr = row.string("fnr_barn"),
                barnsAlder = person.alder() ?: -1,
                høyreSfære = row.double("høyreSfære"),
                høyreSylinder = row.double("høyreSylinder"),
                venstreSfære = row.double("venstreSfære"),
                venstreSylinder = row.double("venstreSylinder"),
                bestillingsdato = row.localDate("bestillingsdato"),
                brillepris = row.bigDecimal("brillepris"),
                beløp = row.bigDecimal("belop"),
                bestillingsreferanse = row.string("bestillingsreferanse"),
                satsNr = SatsType.valueOf(row.string("sats")).sats,
                satsBeløp = row.int("sats_belop"),
                satsBeskrivelse = row.string("sats_beskrivelse"),
                behandlingsresultat = row.string("behandlingsresultat"),
                utbetalingsdato = row.localDateOrNull("utbetalingsdato"),
                opprettet = row.localDateTime("opprettet"),
                slettet = row.localDateTimeOrNull("slettet"),
            )
        }
    }

    // TODO: Trim ned datamodell når design er landet for liste-viewet
    override fun hentAlleVedtakForOptiker(fnrInnsender: String, page: Int, itemsPerPage: Int): OversiktVedtakPaged =
        session {
            val offset = (page - 1) * itemsPerPage

            @Language("PostgreSQL")
            val sql = """
                SELECT
                    COALESCE(v.id, vs.id) AS id,
                    COALESCE(v.orgnr, vs.orgnr) AS orgnr,
                    COALESCE(v.bestillingsdato, vs.bestillingsdato) AS bestillingsdato,
                    COALESCE(v.brillepris, vs.brillepris) AS brillepris,
                    COALESCE(v.bestillingsreferanse, vs.bestillingsreferanse) AS bestillingsreferanse,
                    COALESCE(v.sats, vs.sats) AS sats,
                    COALESCE(v.sats_belop, vs.sats_belop) AS sats_belop,
                    COALESCE(v.sats_beskrivelse, vs.sats_beskrivelse) AS sats_beskrivelse,
                    COALESCE(v.belop, vs.belop) AS belop,
                    COALESCE(v.behandlingsresultat, vs.behandlingsresultat) AS behandlingsresultat,
                    COALESCE(v.opprettet, vs.opprettet) AS opprettet,
                    COALESCE(v.fnr_barn, vs.fnr_barn) AS fnr_barn,
                    COALESCE(v.vilkarsvurdering, vs.vilkarsvurdering) -> 'grunnlag' -> 'brilleseddel' ->> 'høyreSfære' AS høyreSfære,
                    COALESCE(v.vilkarsvurdering, vs.vilkarsvurdering) -> 'grunnlag' -> 'brilleseddel' ->> 'høyreSfære' AS høyreSylinder,
                    COALESCE(v.vilkarsvurdering, vs.vilkarsvurdering) -> 'grunnlag' -> 'brilleseddel' ->> 'høyreSylinder' AS venstreSfære,
                    COALESCE(v.vilkarsvurdering, vs.vilkarsvurdering) -> 'grunnlag' -> 'brilleseddel' ->> 'høyreSylinder' AS venstreSylinder,
                    COALESCE(v.vilkarsvurdering, vs.vilkarsvurdering) -> 'grunnlag' -> 'pdlOppslagBarn' ->> 'data' AS pdlOppslag,
                    u.utbetalingsdato,
                    vs.slettet
                FROM vedtak_v1 v
                FULL OUTER JOIN vedtak_slettet_v1 vs ON v.id = vs.id
                LEFT JOIN utbetaling_v1 u ON v.id = u.vedtak_id
                WHERE
                    (v.fnr_innsender = :fnr_innsender OR vs.fnr_innsender = :fnr_innsender) AND
                    (u.utbetalingsdato IS NULL OR (u.utbetalingsdato > NOW() - '28 days'::interval)) AND
                    (vs.slettet IS NULL OR (vs.slettet > NOW() - '28 days'::interval))
                ORDER BY opprettet DESC
            """.trimIndent()

            @Language("PostgreSQL")
            val sqlTotal = """
                SELECT COUNT(subq.id) AS antall FROM (${sql}) AS subq
            """.trimIndent()

            val totaltAntall = sessionFactory().query(sqlTotal, mapOf("fnr_innsender" to fnrInnsender)) { row ->
                row.int("antall")
            } ?: 0

            val items = it.queryList(
                sql.plus(" LIMIT :limit OFFSET :offset"),
                mapOf(
                    "fnr_innsender" to fnrInnsender,
                    "limit" to itemsPerPage,
                    "offset" to offset,
                )
            ) { row ->
                val person: Person = jsonMapper.readValue(row.string("pdlOppslag"))

                OversiktVedtak(
                    id = row.long("id"),
                    orgnavn = "",
                    orgnr = row.string("orgnr"),
                    barnsNavn = person.navn(),
                    barnsFnr = row.string("fnr_barn"),
                    barnsAlder = person.alder() ?: -1,
                    høyreSfære = row.double("høyreSfære"),
                    høyreSylinder = row.double("høyreSylinder"),
                    venstreSfære = row.double("venstreSfære"),
                    venstreSylinder = row.double("venstreSylinder"),
                    bestillingsdato = row.localDate("bestillingsdato"),
                    brillepris = row.bigDecimal("brillepris"),
                    beløp = row.bigDecimal("belop"),
                    bestillingsreferanse = row.string("bestillingsreferanse"),
                    satsNr = SatsType.valueOf(row.string("sats")).sats,
                    satsBeløp = row.int("sats_belop"),
                    satsBeskrivelse = row.string("sats_beskrivelse"),
                    behandlingsresultat = row.string("behandlingsresultat"),
                    utbetalingsdato = row.localDateOrNull("utbetalingsdato"),
                    opprettet = row.localDateTime("opprettet"),
                    slettet = row.localDateTimeOrNull("slettet"),
                )
            }

            OversiktVedtakPaged(
                numberOfPages = ceil(totaltAntall.toDouble() / itemsPerPage.toDouble()).toInt(),
                itemsPerPage = itemsPerPage,
                totalItems = totaltAntall,
                items = items,
            )
        }

    override fun hentTidligereBrukteOrgnrForInnsender(fnrInnsender: String): List<String> = session {
        @Language("PostgreSQL")
        val sql = """
            SELECT orgnr
            FROM vedtak_v1
            WHERE fnr_innsender = :fnr_innsender
            ORDER BY opprettet DESC
        """.trimIndent()
        it.queryList(sql, mapOf("fnr_innsender" to fnrInnsender)) { row ->
            row.string("orgnr")
        }.toSet().toList()
    }

    override fun <T> lagreVedtak(vedtak: Vedtak<T>): Vedtak<T> = transaction {
        @Language("PostgreSQL")
        val sql = """
            INSERT INTO vedtak_v1 (
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
                opprettet
            )
            VALUES (
                :fnr_barn,
                :fnr_innsender,
                :orgnr,
                :bestillingsdato,
                :brillepris,
                :bestillingsreferanse,
                :vilkarsvurdering,
                :behandlingsresultat,
                :sats,
                :sats_belop,
                :sats_beskrivelse,
                :belop,
                :opprettet
            )
            RETURNING id
        """.trimIndent()
        val id = it.query(
            sql,
            mapOf(
                "fnr_barn" to vedtak.fnrBarn,
                "fnr_innsender" to vedtak.fnrInnsender,
                "orgnr" to vedtak.orgnr,
                "bestillingsdato" to vedtak.bestillingsdato,
                "brillepris" to vedtak.brillepris,
                "bestillingsreferanse" to vedtak.bestillingsreferanse,
                "vilkarsvurdering" to pgObjectOf(vedtak.vilkårsvurdering),
                "behandlingsresultat" to vedtak.behandlingsresultat.toString(),
                "sats" to vedtak.sats.toString(),
                "sats_belop" to vedtak.satsBeløp,
                "sats_beskrivelse" to vedtak.satsBeskrivelse,
                "belop" to vedtak.beløp,
                "opprettet" to vedtak.opprettet,
            )
        ) { row ->
            row.long("id")
        }
        requireNotNull(id) { "Lagring av vedtak feilet, id var null" }
        vedtak.copy(id = id)
    }

    override fun <T> hentVedtakForUtbetaling(
        opprettet: LocalDateTime,
        behandlingsresultat: Behandlingsresultat
    ): List<Vedtak<T>> = session {
        @Language("PostgreSQL")
        val sql = """
            SELECT 
                v.id,
                v.fnr_barn,
                v.fnr_innsender,
                v.orgnr,
                v.bestillingsdato,
                v.brillepris,
                v.bestillingsreferanse,
                v.vilkarsvurdering,
                v.behandlingsresultat,
                v.sats,
                v.sats_belop,
                v.sats_beskrivelse,
                v.belop,
                v.opprettet
            FROM
                vedtak_v1 v,
                vedtak_ko_v1 k,
                tssident_v1 t
            WHERE
                k.opprettet <= :opprettet
                AND k.id = v.id 
                AND v.orgnr = t.orgnr
                AND v.behandlingsresultat = :behandlingsresultat
        """.trimIndent()
        sessionFactory().queryList<Vedtak<T>>(
            sql,
            mapOf(
                "opprettet" to opprettet,
                "behandlingsresultat" to behandlingsresultat.toString()
            )
        ) { row -> mapVedtak(row) }.toList()
    }

    private fun <T> mapVedtak(row: Row) = Vedtak(
        id = row.long("id"),
        fnrBarn = row.string("fnr_barn"),
        fnrInnsender = row.string("fnr_innsender"),
        orgnr = row.string("orgnr"),
        bestillingsdato = row.localDate("bestillingsdato"),
        brillepris = row.bigDecimal("brillepris"),
        bestillingsreferanse = row.string("bestillingsreferanse"),
        vilkårsvurdering = row.json<Vilkårsvurdering<T>>("vilkarsvurdering"),
        behandlingsresultat = Behandlingsresultat.valueOf(row.string("behandlingsresultat")),
        sats = SatsType.valueOf(row.string("sats")),
        satsBeløp = row.int("sats_belop"),
        satsBeskrivelse = row.string("sats_beskrivelse"),
        beløp = row.bigDecimal("belop"),
        opprettet = row.localDateTime("opprettet")
    )

    override fun fjernFraVedTakKø(vedtakId: Long) = session {
        @Language("PostgreSQL")
        val sql = """
                DELETE from vedtak_ko_v1 where id = :vedtakId
        """.trimIndent()
        sessionFactory().update(sql, mapOf("vedtakId" to vedtakId)).rowCount
    }

    override fun <T> hentVedtak(vedtakId: Long): Vedtak<T>? = session {
        @Language("PostgreSQL")
        val sql = """
            SELECT 
                id,
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
                opprettet FROM vedtak_v1 WHERE id =:id
        """.trimIndent()
        sessionFactory().query(sql, mapOf("id" to vedtakId)) {
                row ->
            mapVedtak(row)
        }
    }

    override fun hentAntallVedtakIKø(): Int = session {
        @Language("PostgreSQL")
        val sql = """
            SELECT COUNT(*) as total FROM vedtak_ko_v1
        """.trimIndent()
        sessionFactory().query(sql) {
                row ->
            row.int("total")
        }!!
    }
}
