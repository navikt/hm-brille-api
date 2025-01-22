package no.nav.hjelpemidler.brille.test

import no.nav.hjelpemidler.brille.db.DatabaseTransactionContext
import no.nav.hjelpemidler.brille.db.TestDatabaseContext
import no.nav.hjelpemidler.database.Transaction

abstract class AbstractStoreTest {
    protected val transaction: Transaction<DatabaseTransactionContext> = TestDatabaseContext
}
