package no.nav.hjelpemidler.brille.avtale

import kotliquery.Session
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.store.Store
import no.nav.hjelpemidler.brille.store.TransactionalStore
import no.nav.hjelpemidler.brille.store.update
import org.intellij.lang.annotations.Language
import java.time.LocalDateTime

const val OPPGJORSAVTALE = 1
const val UTVIDET_AVTALE = 2

private val log = KotlinLogging.logger {}

interface AvtaleStore : Store {
    fun lagreAvtale(avtale: Avtale): Avtale
}

data class Avtale(
    val id: Int? = null,
    val orgnr: String,
    val fnrInnsender: String,
    val aktiv: Boolean,
    val avtaleId: Int,
    val opprettet: LocalDateTime = LocalDateTime.now(),
    val oppdatert: LocalDateTime = opprettet,
)

class AvtaleStorePostgres(private val sessionFactory: () -> Session) : AvtaleStore,
    TransactionalStore(sessionFactory) {

    override fun lagreAvtale(avtale: Avtale): Avtale = session {
        @Language("PostgreSQL")
        val sql = """
            INSERT INTO avtale_v1 (orgnr,
                                       fnr_innsender,
                                       aktiv,
                                       avtale_id,
                                       opprettet,
                                       oppdatert)
            VALUES (:orgnr, :fnr_innsender, :aktiv, :avtale_id, :opprettet, :oppdatert)
        """.trimIndent()
        it.update(
            sql,
            mapOf(
                "orgnr" to avtale.orgnr,
                "fnr_innsender" to avtale.fnrInnsender,
                "aktiv" to avtale.aktiv,
                "avtale_id" to avtale.avtaleId,
                "opprettet" to avtale.opprettet,
                "oppdatert" to avtale.oppdatert,
            )
        ).validate()
        avtale
    }
}
