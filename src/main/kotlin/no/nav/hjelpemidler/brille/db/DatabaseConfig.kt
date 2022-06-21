package no.nav.hjelpemidler.brille.db

import com.zaxxer.hikari.HikariDataSource
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.Configuration
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.output.MigrateResult
import java.net.Socket
import java.time.LocalDateTime
import javax.sql.DataSource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val LOG = KotlinLogging.logger {}

class DatabaseConfig(
    private val dbProperties: Configuration.DatabaseProperties = Configuration.dbProperties,
) {
    fun dataSource(): DataSource {
        if (!waitForDB(10.minutes, Configuration)) {
            throw Exception("database never became available within the deadline")
        }

        val ds = HikariDataSource().apply {
            username = dbProperties.databaseUser
            password = dbProperties.databasePassword
            jdbcUrl =
                "jdbc:postgresql://${dbProperties.databaseHost}:${dbProperties.databasePort}/${dbProperties.databaseNavn}"
            maximumPoolSize = 10
            minimumIdle = 1
            idleTimeout = 10001
            connectionTimeout = 1000
            maxLifetime = 30001
        }

        migrate(ds)

        return ds
    }

    private fun waitForDB(timeout: Duration, config: Configuration): Boolean {
        val deadline = LocalDateTime.now().plusSeconds(timeout.inWholeSeconds)
        while (true) {
            try {
                Socket(dbProperties.databaseHost, dbProperties.databasePort.toInt())
                return true
            } catch (e: Exception) {
                LOG.info("Database not available yet, waiting...")
                Thread.sleep(2.seconds.inWholeMilliseconds)
            }
            if (LocalDateTime.now().isAfter(deadline)) break
        }
        return false
    }

    private fun migrate(dataSource: HikariDataSource, initSql: String = ""): MigrateResult =
        Flyway.configure().dataSource(dataSource).initSql(initSql).load().migrate()
}
