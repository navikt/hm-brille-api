package no.nav.hjelpemidler.brille.virksomhet

import kotliquery.Row
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.store.Store
import no.nav.hjelpemidler.brille.store.query
import no.nav.hjelpemidler.brille.store.queryList
import no.nav.hjelpemidler.brille.store.update
import org.intellij.lang.annotations.Language
import java.time.LocalDateTime
import javax.sql.DataSource

private val log = KotlinLogging.logger {}

interface VirksomhetStore : Store {
    fun hentVirksomhetForOrganisasjon(orgnr: String): Virksomhet?
    fun hentVirksomheterForInnsender(fnrInnsender: String): List<Virksomhet>
    fun lagreVirksomhet(virksomhet: Virksomhet): Virksomhet
    fun oppdaterKontonummerOgEpost(fnrOppdatertAv: String, orgnr: String, kontonr: String, epost: String)
}

data class Virksomhet(
    val orgnr: String,
    val kontonr: String,
    val epost: String? = null,
    val fnrInnsender: String,
    val fnrOppdatertAv: String? = null,
    val navnInnsender: String,
    val aktiv: Boolean,
    val avtaleversjon: String? = null,
    val opprettet: LocalDateTime = LocalDateTime.now(),
    val oppdatert: LocalDateTime = opprettet,
)

internal class VirksomhetStorePostgres(private val ds: DataSource) : VirksomhetStore {

    override fun hentVirksomhetForOrganisasjon(orgnr: String): Virksomhet? {
        @Language("PostgreSQL")
        val sql = """
            SELECT orgnr, kontonr, epost, fnr_innsender, fnr_oppdatert_av, navn_innsender, aktiv, avtaleversjon, opprettet, oppdatert
            FROM virksomhet_v1
            WHERE orgnr = :orgnr
        """.trimIndent()
        return ds.query(sql, mapOf("orgnr" to orgnr), ::mapper)
    }

    override fun hentVirksomheterForInnsender(fnrInnsender: String): List<Virksomhet> {
        @Language("PostgreSQL")
        val sql = """
            SELECT orgnr, kontonr, epost, fnr_innsender, fnr_oppdatert_av, navn_innsender, aktiv, avtaleversjon, opprettet, oppdatert
            FROM virksomhet_v1
            WHERE fnr_innsender = :fnrInnsender
        """.trimIndent()
        return ds.queryList(sql, mapOf("fnrInnsender" to fnrInnsender), ::mapper)
    }

    override fun lagreVirksomhet(virksomhet: Virksomhet): Virksomhet {
        @Language("PostgreSQL")
        val sql = """
            INSERT INTO virksomhet_v1 (orgnr,
                                       kontonr,
                                       epost,
                                       fnr_innsender,
                                       navn_innsender,
                                       aktiv,
                                       avtaleversjon,
                                       opprettet,
                                       oppdatert)
            VALUES (:orgnr, :kontonr, :epost, :fnr_innsender, :navn_innsender, :aktiv, :avtaleversjon, :opprettet, :oppdatert)
            ON CONFLICT DO NOTHING
        """.trimIndent()
        ds.update(
            sql,
            mapOf(
                "orgnr" to virksomhet.orgnr,
                "kontonr" to virksomhet.kontonr,
                "epost" to virksomhet.epost,
                "fnr_innsender" to virksomhet.fnrInnsender,
                "navn_innsender" to virksomhet.navnInnsender,
                "aktiv" to virksomhet.aktiv,
                "avtaleversjon" to virksomhet.avtaleversjon,
                "opprettet" to virksomhet.opprettet,
                "oppdatert" to virksomhet.oppdatert,
            )
        ).validate()
        return virksomhet
    }

    override fun oppdaterKontonummerOgEpost(fnrOppdatertAv: String, orgnr: String, kontonr: String, epost: String) {
        @Language("PostgreSQL")
        val sql = """
            UPDATE virksomhet_v1
            SET kontonr = :kontonr, epost = :epost, fnr_oppdatert_av = :fnr_oppdatert_av, oppdatert = :oppdatert
            WHERE orgnr = :orgnr
        """.trimIndent()
        ds.update(
            sql,
            mapOf(
                "kontonr" to kontonr,
                "epost" to epost,
                "fnr_oppdatert_av" to fnrOppdatertAv,
                "orgnr" to orgnr,
                "oppdatert" to LocalDateTime.now()
            )
        ).validate()
    }

    private fun mapper(row: Row): Virksomhet = Virksomhet(
        orgnr = row.string("orgnr"),
        kontonr = row.string("kontonr"),
        epost = row.stringOrNull("epost"),
        fnrInnsender = row.string("fnr_innsender"),
        fnrOppdatertAv = row.stringOrNull("fnr_oppdatert_av"),
        navnInnsender = row.string("navn_innsender"),
        aktiv = row.boolean("aktiv"),
        avtaleversjon = row.stringOrNull("avtaleversjon"),
        opprettet = row.localDateTime("opprettet"),
        oppdatert = row.localDateTime("oppdatert"),
    )
}
