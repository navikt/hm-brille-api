package no.nav.hjelpemidler.brille.vedtak

import com.fasterxml.jackson.module.kotlin.readValue
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
    fun <T> hentVedtakIkkeRegistrertForUtbetaling(
        opprettet: LocalDateTime,
        behandlingsresultat: Behandlingsresultat = Behandlingsresultat.INNVILGET
    ): List<Vedtak<T>>
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
                v.id,
                v.orgnr,
                v.bestillingsdato,
                v.brillepris,
                v.bestillingsreferanse,
                v.sats,
                v.sats_belop,
                v.sats_beskrivelse,
                v.belop,
                v.behandlingsresultat,
                u.utbetalingsdato,
                v.opprettet,
                v.fnr_barn,
                v.vilkarsvurdering -> 'grunnlag' -> 'brilleseddel' ->> 'høyreSfære' AS høyreSfære,
                v.vilkarsvurdering -> 'grunnlag' -> 'brilleseddel' ->> 'høyreSylinder' AS høyreSylinder,
                v.vilkarsvurdering -> 'grunnlag' -> 'brilleseddel' ->> 'venstreSfære' AS venstreSfære,
                v.vilkarsvurdering -> 'grunnlag' -> 'brilleseddel' ->> 'venstreSylinder' AS venstreSylinder,
                v.vilkarsvurdering -> 'grunnlag' -> 'pdlOppslagBarn' ->> 'data' AS pdlOppslag
            FROM vedtak_v1 v
            LEFT JOIN utbetaling_v1 u ON v.id = u.vedtak_id
            WHERE
                v.fnr_innsender = :fnr_innsender AND
                v.id = :vedtak_id
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
            )
        }
    }

    // TODO: Trim ned datamodell når design er landet for liste-viewet
    override fun hentAlleVedtakForOptiker(fnrInnsender: String, page: Int, itemsPerPage: Int): OversiktVedtakPaged =
        session {
            val offset = (page - 1) * itemsPerPage

            @Language("PostgreSQL")
            val sqlTotal = """
            SELECT COUNT(id) AS antall
            FROM vedtak_v1
            WHERE fnr_innsender = :fnr_innsender
            """.trimIndent()

            val totaltAntall = sessionFactory().query(sqlTotal, mapOf("fnr_innsender" to fnrInnsender)) { row ->
                row.int("antall")
            } ?: 0

            @Language("PostgreSQL")
            val sql = """
            SELECT
                v.id,
                v.orgnr,
                v.bestillingsdato,
                v.brillepris,
                v.bestillingsreferanse,
                v.sats,
                v.sats_belop,
                v.sats_beskrivelse,
                v.belop,
                v.behandlingsresultat,
                u.utbetalingsdato,
                v.opprettet,
                v.fnr_barn,
                v.vilkarsvurdering -> 'grunnlag' -> 'brilleseddel' ->> 'høyreSfære' AS høyreSfære,
                v.vilkarsvurdering -> 'grunnlag' -> 'brilleseddel' ->> 'høyreSylinder' AS høyreSylinder,
                v.vilkarsvurdering -> 'grunnlag' -> 'brilleseddel' ->> 'venstreSfære' AS venstreSfære,
                v.vilkarsvurdering -> 'grunnlag' -> 'brilleseddel' ->> 'venstreSylinder' AS venstreSylinder,
                v.vilkarsvurdering -> 'grunnlag' -> 'pdlOppslagBarn' ->> 'data' AS pdlOppslag
            FROM vedtak_v1 v
            LEFT JOIN utbetaling_v1 u ON v.id = u.vedtak_id
            WHERE
                v.fnr_innsender = :fnr_innsender
            ORDER BY v.opprettet DESC
            LIMIT :limit OFFSET :offset
            """.trimIndent()

            val items = it.queryList(
                sql,
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

    override fun <T> hentVedtakIkkeRegistrertForUtbetaling(
        opprettet: LocalDateTime,
        behandlingsresultat: Behandlingsresultat
    ): List<Vedtak<T>> = session {
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
                opprettet
            FROM vedtak_v1
            WHERE opprettet >= :opprettet AND behandlingsresultat = :behandlingsresultat 
            AND NOT EXISTS(SELECT FROM utbetaling_v1 WHERE id = utbetaling_v1.vedtak_id)
            ORDER by opprettet LIMIT 1000
        """.trimIndent()
        sessionFactory().queryList<Vedtak<T>>(
            sql,
            mapOf(
                "opprettet" to opprettet,
                "behandlingsresultat" to behandlingsresultat.toString()
            )
        ) { row ->
            Vedtak(
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
        }.toList()
    }
}
