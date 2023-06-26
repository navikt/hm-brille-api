package no.nav.hjelpemidler.brille

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopPreparing
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.path
import io.ktor.server.routing.IgnoreTrailingSlash
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.logging.LogbackMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.KafkaConfig
import no.nav.helse.rapids_rivers.KafkaRapid
import no.nav.hjelpemidler.brille.admin.AdminService
import no.nav.hjelpemidler.brille.admin.adminApi
import no.nav.hjelpemidler.brille.altinn.AltinnClient
import no.nav.hjelpemidler.brille.altinn.AltinnService
import no.nav.hjelpemidler.brille.audit.AuditService
import no.nav.hjelpemidler.brille.avtale.AvtaleService
import no.nav.hjelpemidler.brille.avtale.avtaleApi
import no.nav.hjelpemidler.brille.db.DefaultDatabaseContext
import no.nav.hjelpemidler.brille.db.transaction
import no.nav.hjelpemidler.brille.enhetsregisteret.EnhetsregisteretClient
import no.nav.hjelpemidler.brille.enhetsregisteret.EnhetsregisteretService
import no.nav.hjelpemidler.brille.featuretoggle.FeatureToggleService
import no.nav.hjelpemidler.brille.featuretoggle.featureToggleApi
import no.nav.hjelpemidler.brille.hotsak.HotsakClient
import no.nav.hjelpemidler.brille.innbygger.innbyggerApi
import no.nav.hjelpemidler.brille.innsender.InnsenderService
import no.nav.hjelpemidler.brille.innsender.innsenderApi
import no.nav.hjelpemidler.brille.integrasjon.integrasjonApi
import no.nav.hjelpemidler.brille.internal.MetricsConfig
import no.nav.hjelpemidler.brille.internal.internalRoutes
import no.nav.hjelpemidler.brille.internal.setupMetrics
import no.nav.hjelpemidler.brille.joarkref.JoarkrefRiver
import no.nav.hjelpemidler.brille.joarkref.JoarkrefService
import no.nav.hjelpemidler.brille.kafka.KafkaService
import no.nav.hjelpemidler.brille.medlemskap.MedlemskapBarn
import no.nav.hjelpemidler.brille.medlemskap.MedlemskapClient
import no.nav.hjelpemidler.brille.oversikt.oversiktApi
import no.nav.hjelpemidler.brille.pdl.PdlClient
import no.nav.hjelpemidler.brille.pdl.PdlService
import no.nav.hjelpemidler.brille.rapportering.RapportService
import no.nav.hjelpemidler.brille.rapportering.rapportApi
import no.nav.hjelpemidler.brille.redis.RedisClient
import no.nav.hjelpemidler.brille.sats.satsApi
import no.nav.hjelpemidler.brille.scheduler.LeaderElection
import no.nav.hjelpemidler.brille.syfohelsenettproxy.SyfohelsenettproxyClient
import no.nav.hjelpemidler.brille.syfohelsenettproxy.sjekkErOptiker
import no.nav.hjelpemidler.brille.tss.RapporterManglendeTssIdentScheduler
import no.nav.hjelpemidler.brille.tss.TssIdentRiver
import no.nav.hjelpemidler.brille.tss.TssIdentService
import no.nav.hjelpemidler.brille.utbetaling.RekjorUtbetalingerScheduler
import no.nav.hjelpemidler.brille.utbetaling.SendTilUtbetalingScheduler
import no.nav.hjelpemidler.brille.utbetaling.UtbetalingService
import no.nav.hjelpemidler.brille.utbetaling.UtbetalingsKvitteringRiver
import no.nav.hjelpemidler.brille.vedtak.SlettVedtakService
import no.nav.hjelpemidler.brille.vedtak.VedtakService
import no.nav.hjelpemidler.brille.vedtak.VedtakTilUtbetalingScheduler
import no.nav.hjelpemidler.brille.vedtak.kravApi
import no.nav.hjelpemidler.brille.vilkarsvurdering.VilkårsvurderingService
import no.nav.hjelpemidler.brille.vilkarsvurdering.vilkårApi
import no.nav.hjelpemidler.brille.vilkarsvurdering.vilkårHotsakApi
import no.nav.hjelpemidler.brille.virksomhet.virksomhetApi
import no.nav.hjelpemidler.configuration.Environment
import no.nav.hjelpemidler.configuration.LocalEnvironment
import org.slf4j.event.Level
import java.net.InetAddress
import java.util.TimeZone
import kotlin.concurrent.thread

private val log = KotlinLogging.logger {}

fun main(args: Array<String>) {
    when (System.getenv("CRONJOB_TYPE")) {
        "SYNC_TSS" -> cronjobSyncTss()
        else -> io.ktor.server.cio.EngineMain.main(args)
    }
}

fun Application.applicationEvents(kafkaRapid: KafkaRapid) {
    fun onStopPreparing() {
        log.info("Application is shutting down, stopping rapid app aswell!")
        kafkaRapid.stop()
    }
    environment.monitor.subscribe(ApplicationStopPreparing) { onStopPreparing() }
}

fun Application.module() {
    log.info("hm-brille-api starting up (git_sha=${Configuration.gitCommit})")
    configure()
    setupRoutes()
}

// Config stuff that we want to reuse in tests
fun Application.configure() {
    TimeZone.setDefault(TimeZone.getTimeZone("Europe/Oslo"))

    setupCallId()
    configureStatusPages()

    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }
    install(CallLogging) {
        level = Level.TRACE
        filter { call ->
            !call.request.path().startsWith("/internal")
        }
        // Set correlation-id i logg-innslag. Også tilgjengelig direkte med: MDC.get(MDC_CORRELATION_ID)
        callIdMdc(MDC_CORRELATION_ID)
    }
    install(IgnoreTrailingSlash)
}

// Wire up services and routes
fun Application.setupRoutes() {
    // Database
    val databaseContext = DefaultDatabaseContext(DatabaseConfiguration(Configuration.dbProperties).dataSource())

    // Kafka
    val rapid = createKafkaRapid()
    val kafkaService = KafkaService(rapid)

    // Klienter
    val redisClient = RedisClient()
    val enhetsregisteretClient = EnhetsregisteretClient(Configuration.enhetsregisteretProperties)
    val syfohelsenettproxyClient = SyfohelsenettproxyClient(Configuration.syfohelsenettproxyProperties)
    val pdlClient = PdlClient(Configuration.pdlProperties)
    val medlemskapClient = MedlemskapClient(Configuration.medlemskapOppslagProperties)
    val hotsakClient = HotsakClient(Configuration.hotsakApiProperties)

    // Tjenester
    val medlemskapBarn = MedlemskapBarn(medlemskapClient, pdlClient, redisClient, kafkaService)
    val altinnService = AltinnService(AltinnClient(Configuration.altinnProperties))
    val pdlService = PdlService(pdlClient)
    val auditService = AuditService(databaseContext)
    val innsenderService = InnsenderService(databaseContext)
    val rapportService = RapportService(databaseContext)
    val enhetsregisteretService = EnhetsregisteretService(enhetsregisteretClient, redisClient)
    val vilkårsvurderingService = VilkårsvurderingService(databaseContext, pdlClient, hotsakClient, medlemskapBarn)
    val utbetalingService = UtbetalingService(databaseContext, kafkaService)
    val vedtakService = VedtakService(databaseContext, vilkårsvurderingService, kafkaService)
    val avtaleService = AvtaleService(databaseContext, altinnService, enhetsregisteretService, kafkaService)
    val joarkrefService = JoarkrefService(databaseContext)
    val slettVedtakService = SlettVedtakService(utbetalingService, joarkrefService, kafkaService, databaseContext)
    val tssIdentService = TssIdentService(databaseContext)
    val featureToggleService = FeatureToggleService()
    val adminService = AdminService(databaseContext)
    val leaderElection = LeaderElection(Configuration.electorPath)

    val metrics = MetricsConfig(
        meterbinders = listOf(
            JvmMemoryMetrics(),
            JvmGcMetrics(),
            ProcessorMetrics(),
            JvmThreadMetrics(),
            LogbackMetrics()
        ) + rapid.getMetrics()
    )

    setupMetrics(metrics)

    VedtakTilUtbetalingScheduler(vedtakService, leaderElection, utbetalingService, enhetsregisteretService, metrics)
    SendTilUtbetalingScheduler(utbetalingService, databaseContext, leaderElection, metrics)
    RekjorUtbetalingerScheduler(utbetalingService, databaseContext, leaderElection, metrics)
    if (Configuration.prod) RapporterManglendeTssIdentScheduler(
        tssIdentService,
        enhetsregisteretService,
        leaderElection,
        metrics
    )

    UtbetalingsKvitteringRiver(rapid, utbetalingService, metrics)
    TssIdentRiver(rapid, tssIdentService)
    JoarkrefRiver(rapid, joarkrefService)

    thread(isDaemon = false) {
        rapid.start()
    }

    installAuthentication()

    routing {
        internalRoutes(databaseContext, kafkaService, hotsakClient, pdlService, syfohelsenettproxyClient, enhetsregisteretService)

        route("/api") {
            satsApi()
            featureToggleApi(featureToggleService)

            authenticate(
                when (Environment.current) {
                    LocalEnvironment -> AuthenticationProvider.TOKEN_X_LOCAL
                    else -> AuthenticationProvider.TOKEN_X
                }
            ) {
                authenticateOptiker(syfohelsenettproxyClient, redisClient) {
                    innbyggerApi(pdlService, auditService)
                    virksomhetApi(databaseContext, enhetsregisteretService)
                    oversiktApi(databaseContext, enhetsregisteretService)
                    innsenderApi(innsenderService)
                    vilkårApi(vilkårsvurderingService, adminService, auditService, kafkaService)
                    kravApi(vedtakService, auditService, slettVedtakService, utbetalingService, redisClient)
                }
                avtaleApi(avtaleService)
                rapportApi(rapportService, altinnService)
            }

            authenticate(
                when (Environment.current) {
                    LocalEnvironment -> AuthenticationProvider.AZURE_AD_BRILLEADMIN_BRUKERE_LOCAL
                    else -> AuthenticationProvider.AZURE_AD_BRILLEADMIN_BRUKERE
                }
            ) {
                adminApi(adminService, slettVedtakService, enhetsregisteretService, rapportService)
            }

            authenticate(AuthenticationProvider.AZURE_AD_SYSTEMBRUKER) {
                vilkårHotsakApi(vilkårsvurderingService)

                // FIXME: Integrasjon api skal ikke ha kanBehandlePersonerMedAdressebeskyttelse(): Boolean = true, det får
                // den her pga. AuthenticationProvider.AZURE_AD_SYSTEMBRUKER
                if (!Configuration.prod) integrasjonApi(
                    vilkårsvurderingService,
                    vedtakService,
                    auditService,
                    enhetsregisteretService,
                    pdlService,
                    databaseContext,
                    syfohelsenettproxyClient,
                    utbetalingService,
                    slettVedtakService
                )
            }

            // Admin apis
            sjekkErOptiker(syfohelsenettproxyClient)
        }
    }
    applicationEvents(rapid)
}

private fun createKafkaRapid(): KafkaRapid {
    val instanceId = InetAddress.getLocalHost().hostName
    val kafkaProps = Configuration.kafkaProperties
    val kafkaConfig = kafkaConfig(kafkaProps, instanceId)
    return KafkaRapid.create(kafkaConfig, kafkaProps.topic, emptyList())
}

fun cronjobSyncTss() {
    log.info("cronjob sync-tss: start")

    val databaseContext = DefaultDatabaseContext(DatabaseConfiguration(Configuration.dbProperties).dataSource())

    val rapid = createKafkaRapid()
    val kafkaService = KafkaService(rapid)

    runBlocking {
        val virksomheter = transaction(databaseContext) { ctx ->
            ctx.virksomhetStore.hentAlleVirksomheterMedKontonr()
                .filter { it.aktiv } // Ignorer alle deaktiverte avtaler
                .map {
                    Pair(it.orgnr, it.kontonr)
                }
        }

        virksomheter.forEach {
            log.info("cronjob sync-tss: Oppdaterer tss med=$it")
            kafkaService.oppdaterTSS(
                orgnr = it.first,
                kontonr = it.second
            )
        }

        log.info("cronjob sync-tss: Virksomheter er oppdatert i TSS!")
    }
}

private fun kafkaConfig(
    kafkaProps: Configuration.KafkaProperties,
    instanceId: String?,
) = KafkaConfig(
    bootstrapServers = kafkaProps.bootstrapServers,
    consumerGroupId = kafkaProps.clientId,
    clientId = instanceId,
    truststore = kafkaProps.truststorePath,
    truststorePassword = kafkaProps.truststorePassword,
    keystoreLocation = kafkaProps.keystorePath,
    keystorePassword = kafkaProps.keystorePassword
)
