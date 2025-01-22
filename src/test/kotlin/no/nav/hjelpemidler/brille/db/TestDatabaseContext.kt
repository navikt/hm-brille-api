package no.nav.hjelpemidler.brille.db

import no.nav.hjelpemidler.database.JdbcOperations
import no.nav.hjelpemidler.database.Store
import no.nav.hjelpemidler.database.Testcontainers
import no.nav.hjelpemidler.database.createDataSource
import no.nav.hjelpemidler.database.createRole
import no.nav.hjelpemidler.database.migrate
import no.nav.hjelpemidler.database.transactionAsync

object TestDatabaseContext : DatabaseContext() {
    override val dataSource by lazy {
        createDataSource(Testcontainers) {
            tag = "13-alpine"
        }.also { dataSource ->
            dataSource.migrate {
                createRole("cloudsqliamuser")
                createRole("naisjob")
            }
        }
    }
}

suspend inline fun <S : Store, T> testTransaction(
    noinline factory: (JdbcOperations) -> S,
    noinline test: suspend S.() -> T,
): T = transactionAsync(TestDatabaseContext.dataSource) {
    factory(it).test()
}
