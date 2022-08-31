package no.nav.hjelpemidler.brille

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import mu.KotlinLogging
import org.flywaydb.core.Flyway
import java.net.Socket
import java.time.LocalDateTime
import java.util.Properties
import javax.sql.DataSource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val log = KotlinLogging.logger { }

class DatabaseConfiguration(private val props: Configuration.DatabaseProperties) {
    fun dataSource(): DataSource {
        val dataSource = if (Configuration.cronjob) {
            // Set up URL parameters
            val jdbcURL = "jdbc:postgresql:///${props.databaseNavn}"
            val connProps = Properties().apply {
                setProperty("socketFactory", "com.google.cloud.sql.postgres.SocketFactory")
                setProperty("cloudSqlInstance", "${props.cronjobGcpProject}:${props.cronjobGcpRegion}:${props.cronjobGcpDbInstance}")
            }

            // Initialize connection pool
            val config = HikariConfig().apply {
                jdbcUrl = jdbcURL
                dataSourceProperties = connProps
                username = props.databaseUser
                password = props.databasePassword
                connectionTimeout = 10000 // 10s
                maximumPoolSize = 1
            }

            val dataSource = HikariDataSource(config)
            dataSource
        } else {
            if (!waitForDB(10.minutes)) {
                throw RuntimeException("Databasen ble ikke tilgjengelig innenfor tidsfristen")
            }

            val dataSource = HikariDataSource().apply {
                username = props.databaseUser
                password = props.databasePassword
                jdbcUrl =
                    "jdbc:postgresql://${props.databaseHost}:${props.databasePort}/${props.databaseNavn}"
                maximumPoolSize = 10
                minimumIdle = 1
                idleTimeout = 10001
                connectionTimeout = 1000
                maxLifetime = 30001
            }

            val flyway = Flyway.configure().dataSource(dataSource).load()
            flyway.migrate()

            dataSource
        }

        return dataSource
    }

    private fun waitForDB(timeout: Duration): Boolean {
        val deadline = LocalDateTime.now().plusSeconds(timeout.inWholeSeconds)
        while (true) {
            try {
                Socket(props.databaseHost, props.databasePort.toInt())
                return true
            } catch (e: Exception) {
                log.info("Databasen er ikke tilgjengelig ennå, venter...")
                Thread.sleep(2.seconds.inWholeMilliseconds)
            }
            if (LocalDateTime.now().isAfter(deadline)) break
        }
        return false
    }
}
