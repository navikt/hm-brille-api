package no.nav.hjelpemidler.brille.virksomhet

import mu.KotlinLogging
import no.nav.hjelpemidler.brille.query
import no.nav.hjelpemidler.brille.queryList
import no.nav.hjelpemidler.brille.update
import org.intellij.lang.annotations.Language
import javax.sql.DataSource

private val log = KotlinLogging.logger {}

interface VirksomhetStore {
    fun hentVirksomhetForOrganisasjon(orgnr: String): Virksomhet?
    fun hentVirksomheterForInnsender(fnrInnsender: String): List<Virksomhet>
    fun lagreVirksomhet(virksomhetModell: Virksomhet)
}

data class Virksomhet(
    val orgnr: String,
    val kontonr: String,
    val fnrInnsender: String,
    val navnInnsender: String,
    val harNavAvtale: Boolean,
    val avtaleVersjon: String? = null,
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
                avtaleVersjon = row.stringOrNull("avtale_versjon")
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
                avtaleVersjon = row.stringOrNull("avtale_versjon")
            )
        }
    }

    override fun lagreVirksomhet(virksomhetModell: Virksomhet) {
        @Language("PostgreSQL")
        val sql = """
            INSERT INTO virksomhet (orgnr,
                                    kontonr,
                                    fnr_innsender,
                                    navn_innsender,
                                    har_nav_avtale,
                                    avtale_versjon)
            VALUES (:orgnr, :kontonr, :fnr_innsender, :navn_innsender, :har_nav_avtale, :avtale_versjon)
        """.trimIndent()
        val result = ds.update(
            sql,
            mapOf(
                "orgnr" to virksomhetModell.orgnr,
                "kontonr" to virksomhetModell.kontonr,
                "fnr_innsender" to virksomhetModell.fnrInnsender,
                "navn_innsender" to virksomhetModell.navnInnsender,
                "har_nav_avtale" to virksomhetModell.harNavAvtale,
                "avtale_versjon" to virksomhetModell.avtaleVersjon,
            )
        )
        if (result == 0) {
            throw RuntimeException("VirksomhetStore.lagreVirksomhet: feilet i å opprette virksomhet (result==0)")
        }
    }
}
