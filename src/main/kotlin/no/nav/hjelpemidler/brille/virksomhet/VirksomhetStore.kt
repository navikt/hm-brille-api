package no.nav.hjelpemidler.brille.virksomhet

import mu.KotlinLogging
import no.nav.hjelpemidler.brille.query
import no.nav.hjelpemidler.brille.queryList
import no.nav.hjelpemidler.brille.update
import org.intellij.lang.annotations.Language
import java.time.LocalDateTime
import javax.sql.DataSource

private val log = KotlinLogging.logger {}

interface VirksomhetStore {
    fun hentVirksomhetForOrganisasjon(orgnr: String): Virksomhet?
    fun hentVirksomheterForInnsender(fnrInnsender: String): List<Virksomhet>
    fun lagreVirksomhet(virksomhet: Virksomhet)
}

data class Virksomhet(
    val orgnr: String,
    val kontonr: String,
    val fnrInnsender: String,
    val navnInnsender: String,
    val harNavAvtale: Boolean,
    val avtaleVersjon: String? = null,
    val opprettet: LocalDateTime = LocalDateTime.now(),
)

internal class VirksomhetStorePostgres(private val ds: DataSource) : VirksomhetStore {

    override fun hentVirksomhetForOrganisasjon(orgnr: String): Virksomhet? {
        @Language("PostgreSQL")
        val sql = """
            SELECT *
            FROM virksomhet
            WHERE orgnr = :orgnr
        """.trimIndent()
        return ds.query(sql, mapOf("orgnr" to orgnr)) { row ->
            Virksomhet(
                orgnr = row.string("orgnr"),
                kontonr = row.string("kontonr"),
                fnrInnsender = row.string("fnr_innsender"),
                navnInnsender = row.string("navn_innsender"),
                harNavAvtale = row.boolean("har_nav_avtale"),
                avtaleVersjon = row.stringOrNull("avtale_versjon"),
                opprettet = row.localDateTime("opprettet")
            )
        }
    }

    override fun hentVirksomheterForInnsender(fnrInnsender: String): List<Virksomhet> {
        @Language("PostgreSQL")
        val sql = """
            SELECT *
            FROM virksomhet
            WHERE fnr_innsender = :fnrInnsender
        """.trimIndent()
        return ds.queryList(sql, mapOf("fnrInnsender" to fnrInnsender)) { row ->
            Virksomhet(
                orgnr = row.string("orgnr"),
                kontonr = row.string("kontonr"),
                fnrInnsender = row.string("fnr_innsender"),
                navnInnsender = row.string("navn_innsender"),
                harNavAvtale = row.boolean("har_nav_avtale"),
                avtaleVersjon = row.stringOrNull("avtale_versjon"),
                opprettet = row.localDateTime("opprettet"),
            )
        }
    }

    override fun lagreVirksomhet(virksomhet: Virksomhet) {
        @Language("PostgreSQL")
        val sql = """
            INSERT INTO virksomhet (orgnr,
                                    kontonr,
                                    fnr_innsender,
                                    navn_innsender,
                                    har_nav_avtale,
                                    avtale_versjon,
                                    opprettet)
            VALUES (:orgnr, :kontonr, :fnr_innsender, :navn_innsender, :har_nav_avtale, :avtale_versjon, :opprettet)
            ON CONFLICT DO NOTHING
        """.trimIndent()
        val result = ds.update(
            sql,
            mapOf(
                "orgnr" to virksomhet.orgnr,
                "kontonr" to virksomhet.kontonr,
                "fnr_innsender" to virksomhet.fnrInnsender,
                "navn_innsender" to virksomhet.navnInnsender,
                "har_nav_avtale" to virksomhet.harNavAvtale,
                "avtale_versjon" to virksomhet.avtaleVersjon,
                "opprettet" to virksomhet.opprettet
            )
        )
        if (result == 0) {
            throw RuntimeException("VirksomhetStore.lagreVirksomhet: feilet i å opprette virksomhet (result==0)")
        }
    }
}
