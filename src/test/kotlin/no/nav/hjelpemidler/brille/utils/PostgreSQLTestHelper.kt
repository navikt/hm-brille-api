package no.nav.hjelpemidler.brille.utils

import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.output.MigrateResult
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import javax.sql.DataSource

internal val postgresContainer: PostgreSQLContainer<Nothing> by lazy {
    PostgreSQLContainer<Nothing>("postgres:13.1").apply {
        waitingFor(Wait.forListeningPort())
        start()
    }
}

private val dataSource: DataSource by lazy {
    HikariDataSource()
        .apply {
            username = postgresContainer.username
            password = postgresContainer.password
            jdbcUrl = postgresContainer.jdbcUrl
            connectionTimeout = 1000L
        }
        .also {
            it.connection.prepareStatement("DROP ROLE IF EXISTS cloudsqliamuser").execute()
            it.connection.prepareStatement("CREATE ROLE cloudsqliamuser").execute()
        }
}

internal fun migrate(dataSource: DataSource, initSql: String = ""): MigrateResult =
    Flyway.configure().dataSource(dataSource).initSql(initSql).load().migrate()

internal fun clean(dataSource: DataSource) = Flyway.configure().dataSource(dataSource).load().clean()

internal fun withCleanDB(test: () -> Unit) = dataSource.also { clean(it) }
    .run { test() }

internal fun migratedDB() = dataSource.also {
    clean(it)
    migrate(it)
}

internal fun withMigratedDB(test: () -> Unit) = migratedDB().run { test() }
