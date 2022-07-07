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
import no.nav.hjelpemidler.brille.HttpClientConfig.httpClient
import no.nav.hjelpemidler.brille.azuread.AzureAdClient
import no.nav.hjelpemidler.brille.enhetsregisteret.EnhetsregisteretClient
import no.nav.hjelpemidler.brille.enhetsregisteret.EnhetsregisteretService
import no.nav.hjelpemidler.brille.exceptions.configureStatusPages
import no.nav.hjelpemidler.brille.internal.selfTestRoutes
import no.nav.hjelpemidler.brille.internal.setupMetrics
import no.nav.hjelpemidler.brille.kafka.AivenKafkaConfiguration
import no.nav.hjelpemidler.brille.kafka.KafkaService
import no.nav.hjelpemidler.brille.medlemskap.MedlemskapBarn
import no.nav.hjelpemidler.brille.medlemskap.MedlemskapClient
import no.nav.hjelpemidler.brille.pdl.PdlClient
import no.nav.hjelpemidler.brille.pdl.PdlService
import no.nav.hjelpemidler.brille.pdl.pdlApi
import no.nav.hjelpemidler.brille.redis.RedisClient
import no.nav.hjelpemidler.brille.sats.satsApi
import no.nav.hjelpemidler.brille.syfohelsenettproxy.SyfohelsenettproxyClient
import no.nav.hjelpemidler.brille.vedtak.VedtakService
import no.nav.hjelpemidler.brille.vedtak.VedtakStorePostgres
import no.nav.hjelpemidler.brille.vedtak.søknadApi
import no.nav.hjelpemidler.brille.vilkarsvurdering.VilkårsvurderingService
import no.nav.hjelpemidler.brille.vilkarsvurdering.vilkårApi
import no.nav.hjelpemidler.brille.virksomhet.VirksomhetStorePostgres
import no.nav.hjelpemidler.brille.virksomhet.virksomhetApi
import org.apache.kafka.clients.producer.MockProducer
import org.slf4j.event.Level
import java.util.TimeZone

private val log = KotlinLogging.logger {}

fun main(args: Array<String>): Unit = io.ktor.server.cio.EngineMain.main(args)

fun Application.module() {
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
            !call.request.path().startsWith("/hm/internal")
        }
        // Set correlation-id i logginnslag. Også tilgjengelig direkte med: MDC.get(MDC_CORRELATION_ID)
        callIdMdc(MDC_CORRELATION_ID)
    }
    install(IgnoreTrailingSlash)
}

// Wire up services and routes
fun Application.setupRoutes() {
    val azureAdClient = AzureAdClient()
    val pdlClient = PdlClient(
        Configuration.pdlProperties.graphqlUri,
        Configuration.pdlProperties.apiScope,
        azureAdClient,
    )
    val pdlService = PdlService(pdlClient)

    val redisClient = RedisClient()
    val dataSource = DatabaseConfiguration(Configuration.dbProperties).dataSource()
    val vedtakStore = VedtakStorePostgres(dataSource)
    val virksomhetStore = VirksomhetStorePostgres(dataSource)
    val enhetsregisteretClient = EnhetsregisteretClient(Configuration.enhetsregisteretProperties.baseUrl)
    val enhetsregisteretService = EnhetsregisteretService(enhetsregisteretClient, redisClient)
    val syfohelsenettproxyClient = SyfohelsenettproxyClient(
        Configuration.syfohelsenettproxyProperties.baseUrl,
        Configuration.syfohelsenettproxyProperties.scope,
        azureAdClient
    )
    val kafkaService = KafkaService {
        when (Configuration.profile) {
            Configuration.Profile.LOCAL -> MockProducer()
            else -> AivenKafkaConfiguration().aivenKafkaProducer()
        }
    }

    val medlemskapClient = MedlemskapClient(Configuration.medlemskapOppslagProperties, azureAdClient)
    val medlemskapBarn = MedlemskapBarn(medlemskapClient, pdlClient, redisClient)

    val vilkårsvurderingService = VilkårsvurderingService(vedtakStore, pdlClient, medlemskapBarn)
    val vedtakService = VedtakService(vedtakStore, vilkårsvurderingService, kafkaService)

    installAuthentication(httpClient(engineFactory { StubEngine.tokenX() }))

    routing {
        selfTestRoutes()

        if (!Configuration.prod) {
            testApi(medlemskapClient, medlemskapBarn, virksomhetStore, enhetsregisteretService)
        }

        route("/api") {
            satsApi()

            authenticate(if (Configuration.local) "local" else TOKEN_X_AUTH) {
                authenticateOptiker(syfohelsenettproxyClient, redisClient) {
                    pdlApi(pdlService)
                    vilkårApi(vilkårsvurderingService)
                    søknadApi(vedtakService)
                    virksomhetApi(vedtakStore, enhetsregisteretService, virksomhetStore)
                }
            }
        }
    }
}
