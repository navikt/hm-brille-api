package no.nav.hjelpemidler.brille

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.path
import io.ktor.server.routing.IgnoreTrailingSlash
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.KafkaConfig
import no.nav.helse.rapids_rivers.KafkaRapid
import no.nav.hjelpemidler.brille.HttpClientConfig.httpClient
import no.nav.hjelpemidler.brille.altinn.AltinnClient
import no.nav.hjelpemidler.brille.altinn.AltinnService
import no.nav.hjelpemidler.brille.audit.AuditService
import no.nav.hjelpemidler.brille.audit.AuditStorePostgres
import no.nav.hjelpemidler.brille.avtale.AvtaleService
import no.nav.hjelpemidler.brille.avtale.avtaleApi
import no.nav.hjelpemidler.brille.enhetsregisteret.EnhetsregisteretClient
import no.nav.hjelpemidler.brille.enhetsregisteret.EnhetsregisteretService
import no.nav.hjelpemidler.brille.featuretoggle.FeatureToggleService
import no.nav.hjelpemidler.brille.featuretoggle.featureToggleApi
import no.nav.hjelpemidler.brille.innbygger.innbyggerApi
import no.nav.hjelpemidler.brille.innsender.InnsenderService
import no.nav.hjelpemidler.brille.innsender.InnsenderStorePostgres
import no.nav.hjelpemidler.brille.innsender.innsenderApi
import no.nav.hjelpemidler.brille.internal.internalRoutes
import no.nav.hjelpemidler.brille.internal.setupMetrics
import no.nav.hjelpemidler.brille.kafka.KafkaService
import no.nav.hjelpemidler.brille.medlemskap.MedlemskapBarn
import no.nav.hjelpemidler.brille.medlemskap.MedlemskapClient
import no.nav.hjelpemidler.brille.oversikt.oversiktApi
import no.nav.hjelpemidler.brille.pdl.PdlClient
import no.nav.hjelpemidler.brille.pdl.PdlService
import no.nav.hjelpemidler.brille.rapportering.RapportService
import no.nav.hjelpemidler.brille.rapportering.RapportStorePostgres
import no.nav.hjelpemidler.brille.rapportering.rapportApi
import no.nav.hjelpemidler.brille.redis.RedisClient
import no.nav.hjelpemidler.brille.sats.satsApi
import no.nav.hjelpemidler.brille.scheduler.LeaderElection
import no.nav.hjelpemidler.brille.syfohelsenettproxy.SyfohelsenettproxyClient
import no.nav.hjelpemidler.brille.syfohelsenettproxy.sjekkErOptikerMedHprnr
import no.nav.hjelpemidler.brille.utbetaling.SendTilUtbetalingScheduler
import no.nav.hjelpemidler.brille.utbetaling.UtbetalingService
import no.nav.hjelpemidler.brille.utbetaling.UtbetalingStorePostgres
import no.nav.hjelpemidler.brille.utbetaling.UtbetalingsKvitteringRiver
import no.nav.hjelpemidler.brille.vedtak.VedtakService
import no.nav.hjelpemidler.brille.vedtak.VedtakStorePostgres
import no.nav.hjelpemidler.brille.vedtak.VedtakTilUtbetalingScheduler
import no.nav.hjelpemidler.brille.vedtak.kravApi
import no.nav.hjelpemidler.brille.vilkarsvurdering.VilkårsvurderingService
import no.nav.hjelpemidler.brille.vilkarsvurdering.vilkårApi
import no.nav.hjelpemidler.brille.virksomhet.VirksomhetStorePostgres
import no.nav.hjelpemidler.brille.virksomhet.virksomhetApi
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

fun Application.module() {
    log.info("hm-brille-api starting up (git_sha=${Configuration.gitCommit})")
    configure()
    setupRoutes()
}

// Config stuff that we want to reuse in tests
fun Application.configure() {
    TimeZone.setDefault(TimeZone.getTimeZone("Europe/Oslo"))

    setupCallId()
    setupMetrics()
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
    val dataSource = DatabaseConfiguration(Configuration.dbProperties).dataSource()
    val auditStore = AuditStorePostgres(dataSource)
    val innsenderStore = InnsenderStorePostgres(dataSource)
    val rapportStore = RapportStorePostgres(dataSource)
    val vedtakStore = VedtakStorePostgres(dataSource)
    val virksomhetStore = VirksomhetStorePostgres(dataSource)
    val utbetalingStore = UtbetalingStorePostgres(dataSource)

    // Kafka
    val instanceId = InetAddress.getLocalHost().hostName
    val kafkaProps = Configuration.kafkaProperties
    val kafkaConfig = KafkaConfig(
        bootstrapServers = kafkaProps.bootstrapServers,
        consumerGroupId = kafkaProps.clientId,
        clientId = instanceId,
        truststore = kafkaProps.truststorePath,
        truststorePassword = kafkaProps.truststorePassword,
        keystoreLocation = kafkaProps.keystorePath,
        keystorePassword = kafkaProps.keystorePassword
    )

    val rapid = KafkaRapid.create(kafkaConfig, kafkaProps.topic, emptyList())
    val kafkaService = KafkaService(rapid)

    // Klienter
    val redisClient = RedisClient()
    val enhetsregisteretClient = EnhetsregisteretClient(Configuration.enhetsregisteretProperties)
    val syfohelsenettproxyClient = SyfohelsenettproxyClient(Configuration.syfohelsenettproxyProperties)
    val pdlClient = PdlClient(Configuration.pdlProperties)
    val medlemskapClient = MedlemskapClient(Configuration.medlemskapOppslagProperties)
    // Tjenester
    val medlemskapBarn = MedlemskapBarn(medlemskapClient, pdlClient, redisClient, kafkaService)
    val altinnService = AltinnService(AltinnClient(Configuration.altinnProperties))
    val pdlService = PdlService(pdlClient)
    val auditService = AuditService(auditStore)
    val innsenderService = InnsenderService(innsenderStore)
    val rapportService = RapportService(rapportStore)
    val enhetsregisteretService = EnhetsregisteretService(enhetsregisteretClient, redisClient)
    val vilkårsvurderingService = VilkårsvurderingService(vedtakStore, pdlClient, medlemskapBarn)
    val utbetalingService = UtbetalingService(utbetalingStore, Configuration.utbetalingProperties)
    val vedtakService = VedtakService(vedtakStore, vilkårsvurderingService, kafkaService, utbetalingService)
    val avtaleService = AvtaleService(virksomhetStore, altinnService, enhetsregisteretService, kafkaService)
    val featureToggleService = FeatureToggleService()
    val leaderElection = LeaderElection(Configuration.electorPath)
    val vedtakTilUtbetalingScheduler = VedtakTilUtbetalingScheduler(vedtakService, leaderElection)
    val sendTilUtbetalingScheduler = SendTilUtbetalingScheduler(utbetalingService, leaderElection)

    UtbetalingsKvitteringRiver(rapid)
    thread(isDaemon = false) {
        rapid.start()
    }

    installAuthentication(httpClient(engineFactory { StubEngine.tokenX() }))

    routing {
        internalRoutes(vedtakTilUtbetalingScheduler, sendTilUtbetalingScheduler, kafkaService)

        route("/api") {
            satsApi()
            featureToggleApi(featureToggleService)

            authenticate(if (Configuration.local) "local" else TOKEN_X_AUTH) {
                authenticateOptiker(syfohelsenettproxyClient, redisClient) {
                    innbyggerApi(pdlService, auditService)
                    virksomhetApi(vedtakStore, enhetsregisteretService, virksomhetStore)
                    if (Configuration.dev) oversiktApi(vedtakStore, enhetsregisteretService)
                    innsenderApi(innsenderService)
                    vilkårApi(vilkårsvurderingService, auditService, kafkaService)
                    kravApi(vedtakService, auditService)
                }
                avtaleApi(avtaleService)
                rapportApi(rapportService, altinnService)
            }

            // Admin apis
            // rapportApiAdmin(rapportService)
            sjekkErOptikerMedHprnr(syfohelsenettproxyClient)
        }
    }
}

fun cronjobSyncTss() {
    log.info("cronjob sync tss start")

    val dataSource = DatabaseConfiguration(Configuration.dbProperties).dataSource()
    val virksomhetStore = VirksomhetStorePostgres(dataSource)

    val kafkaProps = Configuration.kafkaProperties
    val kafkaConfig = KafkaConfig(
        bootstrapServers = kafkaProps.bootstrapServers,
        consumerGroupId = kafkaProps.clientId,
        clientId = kafkaProps.clientId,
        truststore = kafkaProps.truststorePath,
        truststorePassword = kafkaProps.truststorePassword,
        keystoreLocation = kafkaProps.keystorePath,
        keystorePassword = kafkaProps.keystorePassword
    )
    val rapid = KafkaRapid.create(kafkaConfig, kafkaProps.topic, emptyList())
    val kafkaService = KafkaService(rapid)

    val virksomheter = virksomhetStore.hentAlleVirksomheterMedKontonr().map {
        Pair(it.orgnr, it.kontonr)
    }

    virksomheter.forEach {
        kafkaService.oppdaterTSS(
            orgnr = it.first,
            kontonr = it.second,
        )
    }

    log.info("Virksomheter er oppdatert i TSS: $virksomheter")
}
