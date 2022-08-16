package no.nav.hjelpemidler.brille.vedtak

import no.nav.hjelpemidler.brille.json
import no.nav.hjelpemidler.brille.pgObjectOf
import no.nav.hjelpemidler.brille.sats.SatsType
import no.nav.hjelpemidler.brille.store.Store
import no.nav.hjelpemidler.brille.store.query
import no.nav.hjelpemidler.brille.store.queryList
import no.nav.hjelpemidler.brille.vilkarsvurdering.Vilkårsvurdering
import org.intellij.lang.annotations.Language
import java.time.LocalDateTime
import javax.sql.DataSource

interface VedtakStore : Store {
    fun hentTidligereBrukteOrgnrForInnsender(fnrInnsender: String): List<String>
    fun hentVedtakForBarn(fnrBarn: String): List<EksisterendeVedtak>
    fun <T> lagreVedtak(vedtak: Vedtak<T>): Vedtak<T>
    fun <T> hentVedtakIkkeRegistrertForUtbetaling(opprettet: LocalDateTime, behandlingsresultat: Behandlingsresultat = Behandlingsresultat.INNVILGET): List<Vedtak<T>>
}

internal class VedtakStorePostgres(private val ds: DataSource) : VedtakStore {
    override fun hentVedtakForBarn(fnrBarn: String): List<EksisterendeVedtak> {
        @Language("PostgreSQL")
        val sql = """
            SELECT id, fnr_barn, bestillingsdato, behandlingsresultat, opprettet
            FROM vedtak_v1
            WHERE fnr_barn = :fnr_barn 
        """.trimIndent()
        return ds.queryList(sql, mapOf("fnr_barn" to fnrBarn)) { row ->
            EksisterendeVedtak(
                id = row.long("id"),
                fnrBarn = row.string("fnr_barn"),
                bestillingsdato = row.localDate("bestillingsdato"),
                behandlingsresultat = row.string("behandlingsresultat"),
                opprettet = row.localDateTime("opprettet"),
            )
        }
    }

    override fun hentTidligereBrukteOrgnrForInnsender(fnrInnsender: String): List<String> {
        @Language("PostgreSQL")
        val sql = """
            SELECT orgnr
            FROM vedtak_v1
            WHERE fnr_innsender = :fnr_innsender
            ORDER BY opprettet DESC
        """.trimIndent()
        return ds.queryList(sql, mapOf("fnr_innsender" to fnrInnsender)) { row ->
            row.string("orgnr")
        }.toSet().toList()
    }

    override fun <T> lagreVedtak(vedtak: Vedtak<T>): Vedtak<T> {
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
        val id = ds.query(
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
        return vedtak.copy(id = id)
    }

    override fun <T> hentVedtakIkkeRegistrertForUtbetaling(opprettet: LocalDateTime, behandlingsresultat: Behandlingsresultat): List<Vedtak<T>> {
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
        return ds.queryList<Vedtak<T>>(
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
