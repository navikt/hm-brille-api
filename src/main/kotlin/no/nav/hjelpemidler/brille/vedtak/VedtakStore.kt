package no.nav.hjelpemidler.brille.vedtak

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import no.nav.hjelpemidler.brille.jsonMapper
import no.nav.hjelpemidler.brille.pdl.HentPersonExtensions.alderPåDato
import no.nav.hjelpemidler.brille.pdl.HentPersonExtensions.navn
import no.nav.hjelpemidler.brille.pdl.PersonCompat
import no.nav.hjelpemidler.brille.sats.SatsType
import no.nav.hjelpemidler.brille.utbetaling.UtbetalingStatus
import no.nav.hjelpemidler.brille.vilkarsvurdering.Vilkårsvurdering
import no.nav.hjelpemidler.database.JdbcOperations
import no.nav.hjelpemidler.database.Store
import no.nav.hjelpemidler.database.json
import no.nav.hjelpemidler.database.pgJsonbOf
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
        behandlingsresultat: Behandlingsresultat = Behandlingsresultat.INNVILGET,
    ): List<Vedtak<T>>

    fun fjernFraVedTakKø(vedtakId: Long): Int?
    fun <T> hentVedtak(vedtakId: Long): Vedtak<T>?
    fun hentAntallVedtakIKø(): Int
}

class VedtakStorePostgres(private val tx: JdbcOperations) : VedtakStore {
    override fun lagreVedtakIKø(vedtakId: Long, opprettet: LocalDateTime): Long {
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
        val id = tx.single(
            sql,
            mapOf(
                "vedtakId" to vedtakId,
                "opprettet" to opprettet,
            ),
        ) { row ->
            row.long("id")
        }
        return id
    }

    override fun hentVedtakForBarn(fnrBarn: String): List<EksisterendeVedtak> {
        @Language("PostgreSQL")
        val sql = """
            SELECT id, fnr_barn, fnr_innsender, bestillingsdato, bestillingsreferanse, behandlingsresultat, opprettet
            FROM vedtak_v1
            WHERE fnr_barn = :fnr_barn 
        """.trimIndent()
        return tx.list(sql, mapOf("fnr_barn" to fnrBarn)) { row ->
            EksisterendeVedtak(
                id = row.long("id"),
                fnrBarn = row.string("fnr_barn"),
                fnrInnsender = row.string("fnr_innsender"),
                bestillingsdato = row.localDate("bestillingsdato"),
                behandlingsresultat = row.string("behandlingsresultat"),
                bestillingsreferanse = row.string("bestillingsreferanse"),
                opprettet = row.localDateTime("opprettet"),
            )
        }
    }

    override fun hentVedtakForOptiker(fnrInnsender: String, vedtakId: Long): OversiktVedtak? {
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
                COALESCE(u1.utbetalingsdato, u2.utbetalingsdato) AS utbetalingsdato,
                COALESCE(u1.status, u2.status) AS utbetalingsstatus,
                vs.slettet,
                vs.slettet_av_type
            FROM vedtak_v1 v
            FULL OUTER JOIN vedtak_slettet_v1 vs ON v.id = vs.id
            LEFT JOIN utbetaling_v1 u1 ON v.id = u1.vedtak_id
            LEFT JOIN utbetaling_v1 u2 ON vs.id = u2.vedtak_id
            WHERE
                (v.id = :vedtak_id OR vs.id = :vedtak_id) AND
                (v.fnr_innsender = :fnr_innsender OR vs.fnr_innsender = :fnr_innsender) AND
                (u1.utbetalingsdato IS NULL OR (u1.utbetalingsdato > NOW() - '28 days'::INTERVAL)) AND
                (u2.utbetalingsdato IS NULL OR (u2.utbetalingsdato > NOW() - '28 days'::INTERVAL)) AND
                (vs.slettet IS NULL OR (vs.slettet > NOW() - '28 days'::INTERVAL))
        """.trimIndent()
        return tx.singleOrNull(
            sql,
            mapOf(
                "fnr_innsender" to fnrInnsender,
                "vedtak_id" to vedtakId,
            ),
        ) { row ->
            val person: PersonCompat = jsonMapper.readValue(row.string("pdlOppslag"))

            OversiktVedtak(
                id = row.long("id"),
                orgnavn = "",
                orgnr = row.string("orgnr"),
                barnsNavn = person.asPerson().navn(),
                barnsFnr = row.string("fnr_barn"),
                barnsAlder = person.asPerson().alderPåDato(row.localDate("bestillingsdato")) ?: -1,
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
                utbetalingsstatus = row.stringOrNull("utbetalingsstatus")
                    ?.let { status -> UtbetalingStatus.valueOf(status) },
                opprettet = row.localDateTime("opprettet"),
                slettet = row.localDateTimeOrNull("slettet"),
                slettetAvType = row.stringOrNull("slettet_av_type")?.let { SlettetAvType.valueOf(it) },
            )
        }
    }

    override fun hentAlleVedtakForOptiker(fnrInnsender: String, page: Int, itemsPerPage: Int): OversiktVedtakPaged {
        val offset = (page - 1) * itemsPerPage

        @Language("PostgreSQL")
        val sql = """
            SELECT
                COALESCE(v.id, vs.id) AS id,
                COALESCE(v.orgnr, vs.orgnr) AS orgnr,
                COALESCE(v.bestillingsreferanse, vs.bestillingsreferanse) AS bestillingsreferanse,
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
                (v.fnr_innsender = :fnr_innsender OR vs.fnr_innsender = :fnr_innsender) AND
                (u1.utbetalingsdato IS NULL OR (u1.utbetalingsdato > NOW() - '28 days'::INTERVAL)) AND
                (u2.utbetalingsdato IS NULL OR (u2.utbetalingsdato > NOW() - '28 days'::INTERVAL)) AND
                (vs.slettet IS NULL OR (vs.slettet > NOW() - '28 days'::INTERVAL))
            ORDER BY opprettet DESC
        """.trimIndent()

        @Language("PostgreSQL")
        val sqlTotal = """
                SELECT COUNT(subq.id) AS antall FROM ($sql) AS subq
        """.trimIndent()

        val totaltAntall = tx.singleOrNull(sqlTotal, mapOf("fnr_innsender" to fnrInnsender)) { row ->
            row.int("antall")
        } ?: 0

        val items = tx.list(
            sql.plus(" LIMIT :limit OFFSET :offset"),
            mapOf(
                "fnr_innsender" to fnrInnsender,
                "limit" to itemsPerPage,
                "offset" to offset,
            ),
        ) { row ->
            val person: PersonCompat = jsonMapper.readValue(row.string("pdlOppslag"))

            OversiktVedtakListItem(
                id = row.long("id"),
                orgnavn = "",
                orgnr = row.string("orgnr"),
                barnsNavn = person.asPerson().navn(),
                bestillingsreferanse = row.string("bestillingsreferanse"),
                utbetalingsdato = row.localDateOrNull("utbetalingsdato"),
                utbetalingsstatus = row.stringOrNull("utbetalingsstatus")
                    ?.let { status -> UtbetalingStatus.valueOf(status) },
                opprettet = row.localDateTime("opprettet"),
                slettet = row.localDateTimeOrNull("slettet"),
            )
        }

        return OversiktVedtakPaged(
            numberOfPages = ceil(totaltAntall.toDouble() / itemsPerPage.toDouble()).toInt(),
            itemsPerPage = itemsPerPage,
            totalItems = totaltAntall,
            items = items,
        )
    }

    override fun hentTidligereBrukteOrgnrForInnsender(fnrInnsender: String): List<String> {
        @Language("PostgreSQL")
        val sql = """
            SELECT orgnr
            FROM vedtak_v1
            WHERE
                fnr_innsender = :fnr_innsender
                AND orgnr IN (SELECT orgnr FROM virksomhet_v1 WHERE aktiv)
            ORDER BY opprettet DESC
        """.trimIndent()
        return tx.list(sql, mapOf("fnr_innsender" to fnrInnsender)) { row ->
            row.string("orgnr")
        }.toSet().toList()
    }

    override fun <T> lagreVedtak(vedtak: Vedtak<T>): Vedtak<T> {
        @Language("PostgreSQL")
        val sql = """
            INSERT INTO vedtak_v1 (
                fnr_barn,
                fnr_innsender,
                navn_innsender,
                orgnr,
                butikk_id,
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
                kilde,
                avsendersystem_org_nr
            )
            VALUES (
                :fnr_barn,
                :fnr_innsender,
                :navn_innsender,
                :orgnr,
                :butikk_id,
                :bestillingsdato,
                :brillepris,
                :bestillingsreferanse,
                :vilkarsvurdering,
                :behandlingsresultat,
                :sats,
                :sats_belop,
                :sats_beskrivelse,
                :belop,
                :opprettet,
                :kilde,
                :avsendersystem_org_nr
            )
            RETURNING id
        """.trimIndent()
        val id = tx.single(
            sql,
            mapOf(
                "fnr_barn" to vedtak.fnrBarn,
                "fnr_innsender" to vedtak.fnrInnsender,
                "navn_innsender" to vedtak.navnInnsender,
                "orgnr" to vedtak.orgnr,
                "butikk_id" to vedtak.butikkId,
                "bestillingsdato" to vedtak.bestillingsdato,
                "brillepris" to vedtak.brillepris,
                "bestillingsreferanse" to vedtak.bestillingsreferanse,
                "vilkarsvurdering" to pgJsonbOf(vedtak.vilkårsvurdering),
                "behandlingsresultat" to vedtak.behandlingsresultat.toString(),
                "sats" to vedtak.sats.toString(),
                "sats_belop" to vedtak.satsBeløp,
                "sats_beskrivelse" to vedtak.satsBeskrivelse,
                "belop" to vedtak.beløp,
                "opprettet" to vedtak.opprettet,
                "kilde" to vedtak.kilde.toString(),
                "avsendersystem_org_nr" to vedtak.avsendersystemOrgNr,
            ),
        ) { row ->
            row.long("id")
        }
        return vedtak.copy(id = id)
    }

    override fun <T> hentVedtakForUtbetaling(
        opprettet: LocalDateTime,
        behandlingsresultat: Behandlingsresultat,
    ): List<Vedtak<T>> {
        @Language("PostgreSQL")
        val sql = """
            SELECT 
                v.id,
                v.fnr_barn,
                v.fnr_innsender,
                v.navn_innsender,
                v.orgnr,
                v.butikk_id,
                v.bestillingsdato,
                v.brillepris,
                v.bestillingsreferanse,
                v.vilkarsvurdering,
                v.behandlingsresultat,
                v.sats,
                v.sats_belop,
                v.sats_beskrivelse,
                v.belop,
                v.opprettet,
                v.kilde,
                v.avsendersystem_org_nr
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
        return tx.list<Vedtak<T>>(
            sql,
            mapOf(
                "opprettet" to opprettet,
                "behandlingsresultat" to behandlingsresultat.toString(),
            ),
        ) { row -> mapVedtak(row) }.toList()
    }

    private fun <T> mapVedtak(row: Row) = Vedtak(
        id = row.long("id"),
        fnrBarn = row.string("fnr_barn"),
        fnrInnsender = row.string("fnr_innsender"),
        navnInnsender = row.string("navn_innsender"),
        orgnr = row.string("orgnr"),
        butikkId = row.stringOrNull("butikk_id"),
        bestillingsdato = row.localDate("bestillingsdato"),
        brillepris = row.bigDecimal("brillepris"),
        bestillingsreferanse = row.string("bestillingsreferanse"),
        vilkårsvurdering = row.json<Vilkårsvurdering<T>>("vilkarsvurdering"),
        behandlingsresultat = Behandlingsresultat.valueOf(row.string("behandlingsresultat")),
        sats = SatsType.valueOf(row.string("sats")),
        satsBeløp = row.int("sats_belop"),
        satsBeskrivelse = row.string("sats_beskrivelse"),
        beløp = row.bigDecimal("belop"),
        opprettet = row.localDateTime("opprettet"),
        kilde = KravKilde.valueOf(row.string("kilde")),
        avsendersystemOrgNr = row.stringOrNull("avsendersystem_org_nr"),
    )

    override fun fjernFraVedTakKø(vedtakId: Long): Int {
        @Language("PostgreSQL")
        val sql = """
                DELETE FROM vedtak_ko_v1 WHERE id = :vedtakId
        """.trimIndent()
        return tx.update(sql, mapOf("vedtakId" to vedtakId)).actualRowCount
    }

    override fun <T> hentVedtak(vedtakId: Long): Vedtak<T>? {
        @Language("PostgreSQL")
        val sql = """
            SELECT 
                id,
                fnr_barn,
                fnr_innsender,
                navn_innsender,
                orgnr,
                butikk_id,
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
                kilde,
                avsendersystem_org_nr
            FROM vedtak_v1
            WHERE id = :id
        """.trimIndent()
        return tx.singleOrNull(sql, mapOf("id" to vedtakId)) { row ->
            mapVedtak(row)
        }
    }

    override fun hentAntallVedtakIKø(): Int {
        @Language("PostgreSQL")
        val sql = """
            SELECT COUNT(*) AS total FROM vedtak_ko_v1
        """.trimIndent()
        return tx.single(sql) { row ->
            row.int("total")
        }
    }
}
