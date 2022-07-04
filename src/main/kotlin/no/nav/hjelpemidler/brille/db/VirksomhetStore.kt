package no.nav.hjelpemidler.brille.db

import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import mu.KotlinLogging
import org.intellij.lang.annotations.Language
import javax.sql.DataSource

private val log = KotlinLogging.logger {}

interface VirksomhetStore {
    fun hentVirksomhet(orgnr: String): VirksomhetModell?
    fun lagreVirksomhet(virksomhetModell: VirksomhetModell)
}

data class VirksomhetModell(
    val orgnr: String,
    val kontonr: String,
    val fnrInnsender: String,
    val navnInnsender: String,
    val harNavAvtale: Boolean,
    val avtaleVersjon: String? = null
)

internal class VirksomhetStorePostgres(private val ds: DataSource) : VirksomhetStore {

    override fun hentVirksomhet(orgnr: String): VirksomhetModell? = using(sessionOf(ds)) { session ->
        @Language("PostgreSQL")
        val sql = """
            SELECT *
            FROM virksomhet
            WHERE orgnr = ?
        """.trimIndent()
        session.run(
            queryOf(
                sql,
                orgnr,
            ).map {
                VirksomhetModell(
                    orgnr = it.string("orgnr"),
                    kontonr = it.string("kontonr"),
                    fnrInnsender = it.string("fnr_innsender"),
                    navnInnsender = it.string("navn_innsender"),
                    harNavAvtale = it.boolean("har_nav_avtale"),
                    avtaleVersjon = it.stringOrNull("avtale_versjon")
                )
            }.asSingle
        )
    }

    override fun lagreVirksomhet(virksomhetModell: VirksomhetModell) {
        val result = using(sessionOf(ds)) { session ->
            @Language("PostgreSQL")
            val sql = """
                INSERT INTO virksomhet (orgnr,
                                    kontonr,
                                    fnr_innsender,
                                    navn_innsender,
                                    har_nav_avtale,
                                    avtale_versjon)
                VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent()
            session.run(
                queryOf(
                    sql,
                    virksomhetModell.orgnr,
                    virksomhetModell.kontonr,
                    virksomhetModell.fnrInnsender,
                    virksomhetModell.navnInnsender,
                    virksomhetModell.harNavAvtale,
                    virksomhetModell.avtaleVersjon,
                ).asUpdate
            )
        }
        if (result == 0) {
            throw RuntimeException("VirksomhetStore.lagreVirksomhet: feilet i å opprette virksomhet (result==0)")
        }
    }
}
