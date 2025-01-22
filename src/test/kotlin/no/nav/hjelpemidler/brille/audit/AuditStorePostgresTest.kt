package no.nav.hjelpemidler.brille.audit

import io.kotest.assertions.throwables.shouldNotThrowAny
import kotlinx.coroutines.test.runTest
import no.nav.hjelpemidler.brille.test.AbstractStoreTest
import kotlin.test.Test

class AuditStorePostgresTest : AbstractStoreTest() {
    @Test
    fun `lagrer oppslag`() = runTest {
        shouldNotThrowAny {
            transaction {
                auditStore.lagreOppslag("20053115633", "13017621305", "test")
            }
        }
    }
}
