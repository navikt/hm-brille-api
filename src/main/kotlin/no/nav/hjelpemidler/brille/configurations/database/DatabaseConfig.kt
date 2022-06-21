package no.nav.hjelpemidler.brille.configurations.database

import com.zaxxer.hikari.HikariDataSource
import no.nav.hjelpemidler.brille.Configuration
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.output.MigrateResult
import javax.sql.DataSource

class DatabaseConfig(
    private val dbProperties: Configuration.DatabaseProperties = Configuration.dbProperties,
) {

    fun dataSource(): DataSource {
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

    private fun migrate(dataSource: HikariDataSource, initSql: String = ""): MigrateResult =
        Flyway.configure().dataSource(dataSource).initSql(initSql).load().migrate()
}
