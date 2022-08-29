package no.nav.hjelpemidler.brille.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotliquery.queryOf
import kotliquery.sessionOf
import org.flywaydb.core.Flyway
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import javax.sql.DataSource

internal object PostgresTestHelper {

    private val instance by lazy {
        PostgreSQLContainer("postgres:12").apply {
            waitingFor(Wait.forListeningPort())
            start()
        }
    }

    private val dataSource by lazy {
        when (System.getProperty("hikaricp.configurationFile")) {
            null, "" -> HikariConfig()
                .apply {
                    driverClassName = instance.driverClassName
                    jdbcUrl = instance.jdbcUrl
                    username = instance.username
                    password = instance.password
                    maximumPoolSize = 10
                    minimumIdle = 1
                    idleTimeout = 10001
                    connectionTimeout = 1000
                    maxLifetime = 30001
                }
                .let { HikariDataSource(it) }
                .also {
                    sessionOf(it).transaction { tx ->
                        tx.run(queryOf("DROP ROLE IF EXISTS cloudsqliamuser").asExecute)
                        tx.run(queryOf("CREATE ROLE cloudsqliamuser").asExecute)
                        tx.run(queryOf("DROP ROLE IF EXISTS naisjob").asExecute)
                        tx.run(queryOf("CREATE ROLE naisjob").asExecute)
                    }
                }
            else -> HikariDataSource(HikariConfig())
                .also {
                    sessionOf(it).transaction { tx ->
                        tx.run(queryOf("DROP SCHEMA IF EXISTS test CASCADE").asExecute)
                        tx.run(queryOf("CREATE SCHEMA IF NOT EXISTS test").asExecute)
                    }
                }
        }
    }

    val sessionFactory by lazy {
        DataSourceSessionFactory(dataSource)
    }

    fun withMigratedDb(block: () -> Unit) {
        runMigration(dataSource)
        block()
    }

    private fun clean(dataSource: DataSource) = Flyway.configure().dataSource(dataSource).load().clean()

    private fun runMigration(dataSource: DataSource, initSql: String? = null) =
        Flyway.configure()
            .dataSource(dataSource)
            .initSql(initSql)
            .load()
            .migrate()
}
