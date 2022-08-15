package no.nav.hjelpemidler.brille.test

import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
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
            it.connection.prepareStatement("DROP ROLE IF EXISTS naisjob").execute()
            it.connection.prepareStatement("CREATE ROLE naisjob").execute()
        }
}

private val flyway: Flyway by lazy { Flyway.configure().dataSource(dataSource).load() }

internal fun migrate(): DataSource = dataSource.also {
    flyway.clean()
    flyway.migrate()
}

internal fun withMigratedDB(test: (ds: DataSource) -> Unit) = migrate().run { test(this) }
