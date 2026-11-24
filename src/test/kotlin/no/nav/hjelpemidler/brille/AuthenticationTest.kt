package no.nav.hjelpemidler.brille

import com.auth0.jwk.Jwk
import com.auth0.jwk.JwkProvider
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTAuthenticationProvider
import io.ktor.server.auth.jwt.JWTConfigureFunction
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.unmockkConstructor
import io.mockk.unmockkObject
import io.mockk.verify
import no.nav.hjelpemidler.brille.admin.AdminService
import no.nav.hjelpemidler.brille.admin.adminApi
import no.nav.hjelpemidler.brille.audit.AuditService
import no.nav.hjelpemidler.brille.integrasjon.integrasjonApi
import no.nav.hjelpemidler.brille.kafka.KafkaService
import no.nav.hjelpemidler.brille.redis.RedisClient
import no.nav.hjelpemidler.brille.test.createToken
import no.nav.hjelpemidler.brille.test.groups
import no.nav.hjelpemidler.brille.test.role
import no.nav.hjelpemidler.brille.test.testRSAKey
import no.nav.hjelpemidler.brille.tilgang.AzureAdGruppe
import no.nav.hjelpemidler.brille.tilgang.AzureAdRolle
import no.nav.hjelpemidler.brille.utbetaling.UtbetalingService
import no.nav.hjelpemidler.brille.vedtak.SlettVedtakService
import no.nav.hjelpemidler.brille.vedtak.VedtakService
import no.nav.hjelpemidler.brille.vedtak.kravApi
import no.nav.hjelpemidler.brille.vilkarsvurdering.vilkårHotsakApi
import no.nav.hjelpemidler.configuration.TokenXEnvironmentVariable
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import java.time.Instant
import java.util.Date
import java.util.UUID

class AuthenticationTest {
    private val vedtakService = mockk<VedtakService>()
    private val slettVedtakService = mockk<SlettVedtakService>()
    private val utbetalingService = mockk<UtbetalingService>()
    private val auditService = mockk<AuditService>()
    private val adminService = mockk<AdminService>()
    private val kafkaService = mockk<KafkaService>(relaxed = true)
    private val redisClient = mockk<RedisClient>()
    private val jwkProviders = mutableListOf<JwkProvider>()

    @BeforeEach
    fun setUp() {
        // Bytt bare nøkkeloppslaget, ikke produksjonens signatur- eller claim-kontroller.
        mockkConstructor(JWTAuthenticationProvider.Config::class)
        every {
            anyConstructed<JWTAuthenticationProvider.Config>().verifier(
                any<JwkProvider>(),
                any<String>(),
                any<JWTConfigureFunction>(),
            )
        } answers {
            val provider = firstArg<JwkProvider>()
            if (jwkProviders.none { it === provider }) {
                jwkProviders += provider
                mockkObject(provider)
                every { provider.get(any()) } returns Jwk.fromValues(testRSAKey.toPublicJWK().toJSONObject())
            }
            callOriginal()
        }

        every { redisClient.erOptiker(any()) } returns true
        coEvery { vedtakService.hentVedtak(any()) } returns null
        coEvery { adminService.hentVedtak(any()) } returns null
    }

    @AfterEach
    fun tearDown() {
        unmockkConstructor(JWTAuthenticationProvider.Config::class)
        jwkProviders.forEach { unmockkObject(it) }
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "/krav/42",
            "/admin/detaljer/42",
            "/ad/vilkarsspesifikasjon",
            "/integrasjon/kliniske-data-statistikk",
        ],
    )
    fun `beskyttede ruter avviser kall uten token`(path: String) = testRoutes {
        client.protectedRequest(path).status shouldBe HttpStatusCode.Unauthorized
        verifyNoServiceCalls()
    }

    @ParameterizedTest
    @ValueSource(strings = ["issuer", "audience", "expired", "role", "azp", "missing-azp"])
    fun `saksbehandlingsruten avviser ugyldige tokens`(invalidClaim: String) = testRoutes {
        val token = token {
            when (invalidClaim) {
                "issuer" -> issuer("https://invalid.example")
                "audience" -> audience("another-api")
                "expired" -> expirationTime(Date.from(Instant.now().minusSeconds(60)))
                "role" -> claim("roles", listOf("another-role"))
                "azp" -> claim("azp", AzureAdRolle.SYSTEMBRUKER_BRILLE_INTEGRASJON.clientId.toString())
                "missing-azp" -> claim("azp", null)
            }
        }

        client.protectedRequest("/ad/vilkarsspesifikasjon", token).status shouldBe HttpStatusCode.Unauthorized
        verifyNoServiceCalls()
    }

    @Test
    fun `saksbehandlingsruten avviser feil signatur`() = testRoutes {
        val original = SignedJWT.parse(token())
        val invalidToken = SignedJWT(original.header, original.jwtClaimsSet).apply {
            sign(RSASSASigner(RSAKeyGenerator(2048).generate()))
        }.serialize()

        client.protectedRequest("/ad/vilkarsspesifikasjon", invalidToken).status shouldBe HttpStatusCode.Unauthorized
        verifyNoServiceCalls()
    }

    @ParameterizedTest
    @EnumSource(
        value = AzureAdRolle::class,
        names = ["SYSTEMBRUKER_SAKSBEHANDLING", "SYSTEMBRUKER_AZURE_TOKEN_GENERATOR"],
    )
    fun `saksbehandlingsruten godtar autoriserte systembrukere`(rolle: AzureAdRolle) = testRoutes {
        client.protectedRequest("/ad/vilkarsspesifikasjon", token { role(rolle) }).status shouldBe HttpStatusCode.OK
    }

    @Test
    fun `integrasjonsruten godtar bare integrasjonens systembruker`() = testRoutes {
        val path = "/integrasjon/kliniske-data-statistikk"
        client.protectedRequest(path, token()).status shouldBe HttpStatusCode.Unauthorized
        verifyNoServiceCalls()

        client.protectedRequest(path, token { role(AzureAdRolle.SYSTEMBRUKER_BRILLE_INTEGRASJON) }).status shouldBe HttpStatusCode.OK
        verify(exactly = 1) { kafkaService.kliniskDataOpprettet("999999999") }
    }

    @ParameterizedTest
    @EnumSource(AzureAdGruppe::class)
    fun `adminruten godtar brukere i autorisert gruppe`(gruppe: AzureAdGruppe) = testRoutes {
        val token = token {
            claim("roles", emptyList<String>())
            groups(gruppe)
            claim("preferred_username", "test@example.invalid")
            claim("name", "Test Administrator")
            claim("NAVident", "X000000")
        }

        client.protectedRequest("/admin/detaljer/42", token).status shouldBe HttpStatusCode.NotFound
        coVerify(exactly = 1) { adminService.hentVedtak(42) }
    }

    @Test
    fun `adminruten avviser token uten autorisert gruppe`() = testRoutes {
        val token = token {
            claim("groups", listOf(UUID.randomUUID().toString()))
        }

        client.protectedRequest("/admin/detaljer/42", token).status shouldBe HttpStatusCode.Unauthorized
        verifyNoServiceCalls()
    }

    @Test
    fun `kravruten verifiserer TokenX og optikerstatus`() = testRoutes {
        val token = token {
            issuer(TokenXEnvironmentVariable.TOKEN_X_ISSUER)
            audience(TokenXEnvironmentVariable.TOKEN_X_CLIENT_ID)
            subject("00000000001")
            claim("pid", "00000000001")
            claim("acr", "Level4")
        }

        client.protectedRequest("/krav/42", token).status shouldBe HttpStatusCode.NotFound
        coVerify(exactly = 1) { vedtakService.hentVedtak(42) }
        verify(exactly = 1) { redisClient.erOptiker("00000000001") }
    }

    @Test
    fun `kravruten avviser TokenX med for lavt sikkerhetsnivå`() = testRoutes {
        val token = token {
            issuer(TokenXEnvironmentVariable.TOKEN_X_ISSUER)
            audience(TokenXEnvironmentVariable.TOKEN_X_CLIENT_ID)
            subject("00000000001")
            claim("pid", "00000000001")
            claim("acr", "Level3")
        }

        client.protectedRequest("/krav/42", token).status shouldBe HttpStatusCode.Unauthorized
        verifyNoServiceCalls()
    }

    @Test
    fun `kravruten avviser autentisert bruker som ikke er optiker`() = testRoutes {
        every { redisClient.erOptiker(any()) } returns false
        val token = token {
            issuer(TokenXEnvironmentVariable.TOKEN_X_ISSUER)
            audience(TokenXEnvironmentVariable.TOKEN_X_CLIENT_ID)
            subject("00000000001")
            claim("pid", "00000000001")
            claim("acr", "Level4")
        }

        client.protectedRequest("/krav/42", token).status shouldBe HttpStatusCode.Forbidden
        coVerify(exactly = 0) { vedtakService.hentVedtak(any()) }
        coVerify(exactly = 0) { slettVedtakService.slettVedtak(any(), any(), any()) }
    }

    private fun token(block: JWTClaimsSet.Builder.() -> Unit = {}): String = createToken {
        role(AzureAdRolle.SYSTEMBRUKER_SAKSBEHANDLING)
        expirationTime(Date.from(Instant.now().plusSeconds(300)))
        block()
    }.serialize()

    private suspend fun HttpClient.protectedRequest(path: String, token: String? = null) = request(path) {
        method = when {
            path.startsWith("/krav/") -> HttpMethod.Delete
            path.startsWith("/integrasjon/") -> HttpMethod.Post
            else -> HttpMethod.Get
        }
        if (token != null) bearerAuth(token)
        if (method == HttpMethod.Post) {
            contentType(ContentType.Application.Json)
            setBody("""{"organisasjonsnummer":"999999999"}""")
        }
    }

    private fun verifyNoServiceCalls() {
        coVerify {
            listOf(
                vedtakService,
                slettVedtakService,
                utbetalingService,
                auditService,
                adminService,
                kafkaService,
                redisClient,
            ) wasNot Called
        }
    }

    private fun testRoutes(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            configure()
            installAuthentication()
            routing {
                authenticate(AuthenticationProvider.TOKEN_X) {
                    authenticateOptiker(mockk(), redisClient) {
                        kravApi(vedtakService, auditService, slettVedtakService, utbetalingService, redisClient, mockk())
                    }
                }
                authenticate(AuthenticationProvider.AZURE_AD_BRILLEADMIN_BRUKERE) {
                    adminApi(adminService, slettVedtakService, mockk(), mockk())
                }
                authenticate(AuthenticationProvider.AZURE_AD_SYSTEMBRUKER_SAKSBEHANDLING) {
                    vilkårHotsakApi(mockk(), vedtakService, mockk())
                }
                authenticate(AuthenticationProvider.AZURE_AD_SYSTEMBRUKER_BRILLE_INTEGRASJON) {
                    integrasjonApi(
                        mockk(),
                        vedtakService,
                        auditService,
                        mockk(),
                        mockk(),
                        mockk(),
                        mockk(),
                        utbetalingService,
                        slettVedtakService,
                        mockk(),
                        adminService,
                        kafkaService,
                    )
                }
            }
        }
        block()
    }
}
