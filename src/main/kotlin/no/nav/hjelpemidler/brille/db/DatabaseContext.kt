package no.nav.hjelpemidler.brille.db

import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.brille.DatabaseConfiguration
import no.nav.hjelpemidler.brille.admin.AdminStore
import no.nav.hjelpemidler.brille.admin.AdminStorePostgres
import no.nav.hjelpemidler.brille.audit.AuditStore
import no.nav.hjelpemidler.brille.audit.AuditStorePostgres
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
import no.nav.hjelpemidler.brille.vedtak.VedtakSlettetStore
import no.nav.hjelpemidler.brille.vedtak.VedtakSlettetStorePostgres
import no.nav.hjelpemidler.brille.vedtak.VedtakStore
import no.nav.hjelpemidler.brille.vedtak.VedtakStorePostgres
import no.nav.hjelpemidler.brille.virksomhet.VirksomhetStore
import no.nav.hjelpemidler.brille.virksomhet.VirksomhetStorePostgres
import javax.sql.DataSource

interface DatabaseContext {
    val dataSource: DataSource

    fun createSessionContext(sessionFactory: SessionFactory): DatabaseSessionContext
}

class DefaultDatabaseContext(override val dataSource: DataSource = DatabaseConfiguration(Configuration.dbProperties).dataSource()) :
    DatabaseContext {
    override fun createSessionContext(sessionFactory: SessionFactory): DatabaseSessionContext =
        DefaultDatabaseSessionContext(sessionFactory)
}

interface DatabaseSessionContext {
    val vedtakStore: VedtakStore
    val virksomhetStore: VirksomhetStore
    val auditStore: AuditStore
    val innsenderStore: InnsenderStore
    val rapportStore: RapportStore
    val utbetalingStore: UtbetalingStore
    val tssIdentStore: TssIdentStore
    val joarkrefStore: JoarkrefStore
    val vedtakSlettetStore: VedtakSlettetStore
    val adminStore: AdminStore
}

class DefaultDatabaseSessionContext(sessionFactory: SessionFactory) : DatabaseSessionContext {
    override val vedtakStore = VedtakStorePostgres(sessionFactory)
    override val virksomhetStore = VirksomhetStorePostgres(sessionFactory)
    override val auditStore: AuditStore = AuditStorePostgres(sessionFactory)
    override val innsenderStore: InnsenderStore = InnsenderStorePostgres(sessionFactory)
    override val rapportStore: RapportStore = RapportStorePostgres(sessionFactory)
    override val utbetalingStore: UtbetalingStore = UtbetalingStorePostgres(sessionFactory)
    override val tssIdentStore: TssIdentStore = TssIdentStorePostgres(sessionFactory)
    override val joarkrefStore: JoarkrefStore = JoarkrefStorePostgres(sessionFactory)
    override val vedtakSlettetStore: VedtakSlettetStore = VedtakSlettetStorePostgres(sessionFactory)
    override val adminStore: AdminStore = AdminStorePostgres(sessionFactory)
}
