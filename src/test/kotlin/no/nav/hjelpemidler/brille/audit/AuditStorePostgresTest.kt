package no.nav.hjelpemidler.brille.audit

import kotlinx.coroutines.runBlocking
import no.nav.hjelpemidler.brille.db.PostgresTestHelper
import no.nav.hjelpemidler.brille.db.PostgresTestHelper.withMigratedDb
import kotlin.test.Test

internal class AuditStorePostgresTest {
    @Test
    internal fun `lagrer oppslag`() =
        runBlocking {
            withMigratedDb {
                with(AuditStorePostgres(PostgresTestHelper.sessionFactory)) {
                    lagreOppslag("20053115633", "13017621305", "test")
                }
            }
        }
}
