package no.nav.hjelpemidler.brille.db

import io.mockk.every
import io.mockk.mockk
import no.nav.hjelpemidler.brille.admin.AdminStore
import no.nav.hjelpemidler.brille.audit.AuditStore
import no.nav.hjelpemidler.brille.avtale.AvtaleStore
import no.nav.hjelpemidler.brille.enhetsregisteret.EnhetsregisteretStore
import no.nav.hjelpemidler.brille.innsender.InnsenderStore
import no.nav.hjelpemidler.brille.joarkref.JoarkrefStore
import no.nav.hjelpemidler.brille.rapportering.RapportStore
import no.nav.hjelpemidler.brille.tss.TssIdentStore
import no.nav.hjelpemidler.brille.utbetaling.UtbetalingStore
import no.nav.hjelpemidler.brille.vedtak.SlettVedtakStore
import no.nav.hjelpemidler.brille.vedtak.VedtakStore
import no.nav.hjelpemidler.brille.virksomhet.VirksomhetStore
import no.nav.hjelpemidler.database.DatabaseVendor
import no.nav.hjelpemidler.database.JdbcOperations
import javax.sql.DataSource

class MockDatabaseContext : DatabaseContext(), DatabaseTransactionContext {
    override val dataSource: DataSource = mockk {
        every { connection } returns mockk {
            every { commit() } returns Unit
            every { rollback() } returns Unit
            every { close() } returns Unit
            every { metaData } returns mockk {
                every { databaseProductName } returns DatabaseVendor.POSTGRESQL.databaseProductName
            }
            every { autoCommit = any() } returns Unit
            every { isReadOnly = any() } returns Unit
        }
    }

    override fun databaseTransactionContext(tx: JdbcOperations): DatabaseTransactionContext = this

    override fun close() {
    }

    override val adminStore: AdminStore = mockk(relaxed = true)
    override val auditStore: AuditStore = mockk(relaxed = true)
    override val avtaleStore: AvtaleStore = mockk(relaxed = true)
    override val enhetsregisteretStore: EnhetsregisteretStore = mockk(relaxed = true)
    override val innsenderStore: InnsenderStore = mockk(relaxed = true)
    override val joarkrefStore: JoarkrefStore = mockk(relaxed = true)
    override val rapportStore: RapportStore = mockk(relaxed = true)
    override val slettVedtakStore: SlettVedtakStore = mockk(relaxed = true)
    override val tssIdentStore: TssIdentStore = mockk(relaxed = true)
    override val utbetalingStore: UtbetalingStore = mockk(relaxed = true)
    override val vedtakStore: VedtakStore = mockk(relaxed = true)
    override val virksomhetStore: VirksomhetStore = mockk(relaxed = true)
}
