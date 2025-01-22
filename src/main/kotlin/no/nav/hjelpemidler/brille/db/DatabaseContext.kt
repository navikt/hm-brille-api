package no.nav.hjelpemidler.brille.db

import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.brille.DatabaseConfiguration
import no.nav.hjelpemidler.brille.admin.AdminStore
import no.nav.hjelpemidler.brille.admin.AdminStorePostgres
import no.nav.hjelpemidler.brille.audit.AuditStore
import no.nav.hjelpemidler.brille.audit.AuditStorePostgres
import no.nav.hjelpemidler.brille.avtale.AvtaleStore
import no.nav.hjelpemidler.brille.avtale.AvtaleStorePostgres
import no.nav.hjelpemidler.brille.enhetsregisteret.EnhetsregisteretStore
import no.nav.hjelpemidler.brille.enhetsregisteret.EnhetsregisteretStorePostgres
import no.nav.hjelpemidler.brille.innsender.InnsenderStore
import no.nav.hjelpemidler.brille.innsender.InnsenderStorePostgres
import no.nav.hjelpemidler.brille.joarkref.JoarkrefStore
import no.nav.hjelpemidler.brille.joarkref.JoarkrefStorePostgres
import no.nav.hjelpemidler.brille.rapportering.RapportStore
import no.nav.hjelpemidler.brille.rapportering.RapportStorePostgres
import no.nav.hjelpemidler.brille.tss.TssIdentStore
import no.nav.hjelpemidler.brille.tss.TssIdentStorePostgres
import no.nav.hjelpemidler.brille.utbetaling.UtbetalingStore
import no.nav.hjelpemidler.brille.utbetaling.UtbetalingStorePostgres
import no.nav.hjelpemidler.brille.vedtak.SlettVedtakStore
import no.nav.hjelpemidler.brille.vedtak.SlettVedtakStorePostgres
import no.nav.hjelpemidler.brille.vedtak.VedtakStore
import no.nav.hjelpemidler.brille.vedtak.VedtakStorePostgres
import no.nav.hjelpemidler.brille.virksomhet.VirksomhetStore
import no.nav.hjelpemidler.brille.virksomhet.VirksomhetStorePostgres
import no.nav.hjelpemidler.database.JdbcOperations
import no.nav.hjelpemidler.database.transactionAsync
import javax.sql.DataSource

typealias Transaction = no.nav.hjelpemidler.database.Transaction<DatabaseTransactionContext>

abstract class DatabaseContext : Transaction {
    abstract val dataSource: DataSource

    open fun databaseTransactionContext(tx: JdbcOperations): DatabaseTransactionContext =
        DefaultDatabaseTransactionContext(tx)

    override suspend fun <T> invoke(block: suspend DatabaseTransactionContext.() -> T): T =
        transactionAsync(dataSource) {
            block(databaseTransactionContext(it))
        }
}

class DefaultDatabaseContext(override val dataSource: DataSource = DatabaseConfiguration(Configuration.dbProperties).dataSource()) :
    DatabaseContext()

interface DatabaseTransactionContext {
    val adminStore: AdminStore
    val auditStore: AuditStore
    val avtaleStore: AvtaleStore
    val enhetsregisteretStore: EnhetsregisteretStore
    val innsenderStore: InnsenderStore
    val joarkrefStore: JoarkrefStore
    val rapportStore: RapportStore
    val slettVedtakStore: SlettVedtakStore
    val tssIdentStore: TssIdentStore
    val utbetalingStore: UtbetalingStore
    val vedtakStore: VedtakStore
    val virksomhetStore: VirksomhetStore
}

class DefaultDatabaseTransactionContext(tx: JdbcOperations) : DatabaseTransactionContext {
    override val adminStore: AdminStore = AdminStorePostgres(tx)
    override val auditStore: AuditStore = AuditStorePostgres(tx)
    override val avtaleStore: AvtaleStore = AvtaleStorePostgres(tx)
    override val enhetsregisteretStore: EnhetsregisteretStore = EnhetsregisteretStorePostgres(tx)
    override val innsenderStore: InnsenderStore = InnsenderStorePostgres(tx)
    override val joarkrefStore: JoarkrefStore = JoarkrefStorePostgres(tx)
    override val rapportStore: RapportStore = RapportStorePostgres(tx)
    override val slettVedtakStore: SlettVedtakStore = SlettVedtakStorePostgres(tx)
    override val tssIdentStore: TssIdentStore = TssIdentStorePostgres(tx)
    override val utbetalingStore: UtbetalingStore = UtbetalingStorePostgres(tx)
    override val vedtakStore: VedtakStore = VedtakStorePostgres(tx)
    override val virksomhetStore: VirksomhetStore = VirksomhetStorePostgres(tx)
}
