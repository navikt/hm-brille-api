package no.nav.hjelpemidler.brille.db

import io.mockk.every
import io.mockk.mockk
import no.nav.hjelpemidler.brille.audit.AuditStore
import no.nav.hjelpemidler.brille.innsender.InnsenderStore
import no.nav.hjelpemidler.brille.rapportering.RapportStore
import no.nav.hjelpemidler.brille.utbetaling.UtbetalingStore
import no.nav.hjelpemidler.brille.vedtak.VedtakStore
import no.nav.hjelpemidler.brille.virksomhet.VirksomhetStore
import javax.sql.DataSource

fun createDatabaseContext(sessionContext: DatabaseSessionContext = createDatabaseSessionContextWithMocks()): DatabaseContext {
    return object : DatabaseContext {
        override val dataSource: DataSource = mockk {
            every { connection } returns mockk {
                every { commit() } returns Unit
                every { close() } returns Unit
                every { rollback() } returns Unit
                every { autoCommit = any() } returns Unit
                every { isReadOnly = any() } returns Unit
            }
        }

        override fun createSessionContext(sessionFactory: SessionFactory): DatabaseSessionContext =
            sessionContext
    }
}

fun createDatabaseSessionContextWithMocks(): DatabaseSessionContext {
    return object : DatabaseSessionContext {
        override val vedtakStore: VedtakStore = mockk(relaxed = true)
        override val auditStore: AuditStore = mockk(relaxed = true)
        override val virksomhetStore: VirksomhetStore = mockk(relaxed = true)
        override val utbetalingStore: UtbetalingStore = mockk(relaxed = true)
        override val innsenderStore: InnsenderStore = mockk(relaxed = true)
        override val rapportStore: RapportStore = mockk(relaxed = true)
    }
}
