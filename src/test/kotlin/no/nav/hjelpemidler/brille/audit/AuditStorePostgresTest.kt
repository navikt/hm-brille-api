package no.nav.hjelpemidler.brille.audit

import io.kotest.matchers.shouldBe
import no.nav.hjelpemidler.brille.store.query
import no.nav.hjelpemidler.brille.test.withMigratedDB
import kotlin.test.Test

internal class AuditStorePostgresTest {
    @Test
    internal fun `lagrer oppslag`() = withMigratedDB {
        val store = AuditStorePostgres(it)
        store.lagreOppslag("20053115633", "13017621305", "test")
        val beskrivelse = it.query("SELECT oppslag_beskrivelse FROM audit_v1") { row ->
            row.string("oppslag_beskrivelse")
        }
        beskrivelse shouldBe "test"
    }
}
