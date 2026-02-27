package no.nav.hjelpemidler.brille.db

import no.nav.hjelpemidler.database.JdbcOperations
import no.nav.hjelpemidler.database.Store
import no.nav.hjelpemidler.database.Testcontainers
import no.nav.hjelpemidler.database.createDataSource
import no.nav.hjelpemidler.database.createRole
import no.nav.hjelpemidler.database.migrate
import no.nav.hjelpemidler.database.transaction
import java.io.Closeable

object TestDatabaseContext : DatabaseContext() {
    override val dataSource by lazy {
        createDataSource(Testcontainers) {
            tag = "17-alpine"
        }.also { dataSource ->
            dataSource.migrate {
                createRole("cloudsqliamuser")
                createRole("naisjob")
            }
        }
    }

    override fun close() {
        (dataSource as? Closeable)?.close()
    }
}

suspend inline fun <S : Store, T> testTransaction(
    noinline factory: (JdbcOperations) -> S,
    noinline test: suspend S.() -> T,
): T = transaction(TestDatabaseContext.dataSource) {
    factory(it).test()
}
