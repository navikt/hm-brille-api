package no.nav.hjelpemidler.brille.virksomhet

import io.github.oshai.kotlinlogging.KotlinLogging
import kotliquery.Row
import no.nav.hjelpemidler.database.JdbcOperations
import no.nav.hjelpemidler.database.Store
import org.intellij.lang.annotations.Language
import java.time.LocalDateTime

private val log = KotlinLogging.logger {}

interface VirksomhetStore : Store {
    fun hentVirksomhetForOrganisasjon(orgnr: String): Virksomhet?
    fun hentVirksomheterForOrganisasjoner(orgnr: List<String>): List<Virksomhet>
    fun lagreVirksomhet(virksomhet: Virksomhet): Virksomhet
    fun oppdaterVirksomhet(virksomhet: Virksomhet): Virksomhet
    fun opprettEndringsloggInnslag(orgnr: String, fnrOppdatertAv: String, kontonr: String)
    fun hentAlleVirksomheterMedKontonr(): List<Virksomhet>
}

data class Virksomhet(
    val orgnr: String,
    val kontonr: String,
    val epost: String? = null,
    val fnrInnsender: String,
    val fnrOppdatertAv: String? = null,
    val navnInnsender: String, // todo -> slett
    val aktiv: Boolean,
    val bruksvilkår: Boolean = false,
    val bruksvilkårGodtattDato: LocalDateTime? = null,
    val avtaleversjon: String? = null,
    val opprettet: LocalDateTime = LocalDateTime.now(),
    val oppdatert: LocalDateTime = opprettet,
)

class VirksomhetStorePostgres(private val tx: JdbcOperations) : VirksomhetStore {
    override fun hentVirksomhetForOrganisasjon(orgnr: String): Virksomhet? {
        @Language("PostgreSQL")
        val sql = """
            SELECT v.orgnr, v.kontonr, v.epost, v.fnr_innsender, v.fnr_oppdatert_av, v.navn_innsender, v.aktiv AS hovedavtale_aktiv, a.aktiv AS utvidet_aktiv, a.opprettet AS utvidet_opprettet, v.avtaleversjon, v.opprettet, v.oppdatert
            FROM virksomhet_v1 v
            LEFT JOIN bruksvilkar_v1 a ON a.orgnr = v.orgnr AND a.bruksvilkardefinisjon_id = 1
            WHERE v.orgnr = :orgnr
        """.trimIndent()
        return tx.singleOrNull(sql, mapOf("orgnr" to orgnr), ::mapper)
    }

    override fun hentVirksomheterForOrganisasjoner(orgnr: List<String>): List<Virksomhet> {
        return if (orgnr.isEmpty()) {
            emptyList()
        } else {
            @Language("PostgreSQL")
            val sql = """
                SELECT v.orgnr, v.kontonr, v.epost, v.fnr_innsender, v.fnr_oppdatert_av, v.navn_innsender, v.aktiv AS hovedavtale_aktiv, a.aktiv AS utvidet_aktiv, a.opprettet AS utvidet_opprettet, v.avtaleversjon, v.opprettet, v.oppdatert
                FROM virksomhet_v1 v
                LEFT JOIN bruksvilkar_v1 a ON a.orgnr = v.orgnr AND a.bruksvilkardefinisjon_id = 1
                WHERE v.orgnr = ANY (:orgnr)
            """.trimIndent()
            tx.list(sql, mapOf("orgnr" to orgnr.toTypedArray()), ::mapper)
        }
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
        tx.update(
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
            ),
        ).expect(1)
        return virksomhet
    }

    override fun oppdaterVirksomhet(virksomhet: Virksomhet): Virksomhet {
        @Language("PostgreSQL")
        val sql = """
            UPDATE virksomhet_v1
            SET kontonr = :kontonr, epost = :epost, fnr_oppdatert_av = :fnr_oppdatert_av, oppdatert = :oppdatert
            WHERE orgnr = :orgnr
        """.trimIndent()
        tx.update(
            sql,
            mapOf(
                "kontonr" to virksomhet.kontonr,
                "epost" to virksomhet.epost,
                "fnr_oppdatert_av" to virksomhet.fnrOppdatertAv,
                "orgnr" to virksomhet.orgnr,
                "oppdatert" to virksomhet.oppdatert,
            ),
        ).expect(1)
        return virksomhet
    }

    override fun opprettEndringsloggInnslag(orgnr: String, fnrOppdatertAv: String, kontonr: String) {
        @Language("PostgreSQL")
        val sql = """
            INSERT INTO kontonr_endringslogg_v1 (orgnr, fnr_oppdatert_av, kontonr)
            VALUES (:orgnr, :fnr_oppdatert_av, :kontonr);
        """.trimIndent()
        tx.update(
            sql,
            mapOf(
                "orgnr" to orgnr,
                "fnr_oppdatert_av" to fnrOppdatertAv,
                "kontonr" to kontonr,
            ),
        ).expect(1)
    }

    override fun hentAlleVirksomheterMedKontonr(): List<Virksomhet> {
        @Language("PostgreSQL")
        val sql = """
            SELECT v.orgnr, v.kontonr, v.epost, v.fnr_innsender, v.fnr_oppdatert_av, v.navn_innsender, v.aktiv AS hovedavtale_aktiv, a.aktiv AS utvidet_aktiv, a.opprettet AS utvidet_opprettet, v.avtaleversjon, v.opprettet, v.oppdatert
            FROM virksomhet_v1 v
            LEFT JOIN bruksvilkar_v1 a ON a.orgnr = v.orgnr AND a.bruksvilkardefinisjon_id = 1
            WHERE LENGTH(v.kontonr) > 1
        """.trimIndent()
        return tx.list(sql, mapOf(), ::mapper)
    }

    private fun mapper(row: Row): Virksomhet = Virksomhet(
        orgnr = row.string("orgnr"),
        kontonr = row.string("kontonr"),
        epost = row.stringOrNull("epost"),
        fnrInnsender = row.string("fnr_innsender"),
        fnrOppdatertAv = row.stringOrNull("fnr_oppdatert_av"),
        navnInnsender = row.string("navn_innsender"),
        aktiv = row.boolean("hovedavtale_aktiv"),
        bruksvilkår = row.boolean("utvidet_aktiv"),
        bruksvilkårGodtattDato = row.localDateTimeOrNull("utvidet_opprettet"),
        avtaleversjon = row.stringOrNull("avtaleversjon"),
        opprettet = row.localDateTime("opprettet"),
        oppdatert = row.localDateTime("oppdatert"),
    )
}
