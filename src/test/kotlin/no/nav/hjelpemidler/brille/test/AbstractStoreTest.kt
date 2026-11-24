package no.nav.hjelpemidler.brille.test

import no.nav.hjelpemidler.brille.db.TestDatabaseContext
import no.nav.hjelpemidler.brille.db.Transaction

abstract class AbstractStoreTest {
    protected val transaction: Transaction = TestDatabaseContext
}
