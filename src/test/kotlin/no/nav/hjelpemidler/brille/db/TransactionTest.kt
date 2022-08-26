package no.nav.hjelpemidler.brille.db

import io.mockk.every
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class TransactionTest {

    @Test
    internal fun `exception thrown in transaction block causes rollback`() {
        val sessionContext = createDatabaseSessionContextWithMocks()
        val databaseContext = createDatabaseContext(sessionContext)
        every {
            sessionContext.vedtakStore.hentVedtakForBarn(any())
        } throws DatabaseException()
        assertThrows<DatabaseException> {
            runBlocking {
                transaction(databaseContext) { ctx ->
                    ctx.vedtakStore.hentVedtakForBarn("123")
                }
            }
        }
        val connection = databaseContext.dataSource.connection
        verify(exactly = 1) {
            connection.rollback()
        }
    }

    @Test
    internal fun `exception thrown in nested transaction block causes rollback to both transactions`() {
        val sessionContext = createDatabaseSessionContextWithMocks()
        val databaseContext1 = createDatabaseContext(sessionContext)
        val databaseContext2 = createDatabaseContext(sessionContext)
        every {
            sessionContext.vedtakStore.hentVedtakForBarn(any())
        } throws DatabaseException()
        assertThrows<DatabaseException> {
            runBlocking {
                transaction(databaseContext1) {
                    runBlocking {
                        transaction(databaseContext2) { ctx ->
                            ctx.vedtakStore.hentVedtakForBarn("123")
                        }
                    }
                }
            }
        }
        val connection1 = databaseContext1.dataSource.connection
        verify(exactly = 1) {
            connection1.rollback()
        }
        val connection2 = databaseContext2.dataSource.connection
        verify(exactly = 1) {
            connection2.rollback()
        }
    }

    private class DatabaseException : RuntimeException("DatabaseException")
}
