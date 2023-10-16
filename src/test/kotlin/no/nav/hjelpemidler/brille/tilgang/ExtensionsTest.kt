package no.nav.hjelpemidler.brille.tilgang

import com.auth0.jwt.exceptions.IncorrectClaimException
import com.auth0.jwt.exceptions.MissingClaimException
import com.auth0.jwt.interfaces.Verification
import com.nimbusds.jwt.JWT
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.hjelpemidler.brille.test.createToken
import no.nav.hjelpemidler.brille.test.groups
import no.nav.hjelpemidler.brille.test.role
import no.nav.hjelpemidler.brille.test.verifyToken
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test

class ExtensionsTest {
    private val tokenSaksbehandling = createToken {
        role(AzureAdRolle.SYSTEMBRUKER_SAKSBEHANDLING)
    }
    private val tokenAzureTokenGenerator = createToken {
        role(AzureAdRolle.SYSTEMBRUKER_AZURE_TOKEN_GENERATOR)
    }
    private val tokenBrilleIntegrasjon = createToken {
        role(AzureAdRolle.SYSTEMBRUKER_BRILLE_INTEGRASJON)
    }
    private val tokenBrilleAdmin = createToken {
        groups(AzureAdGruppe.BRILLEADMIN_BRUKERE)
    }
    private val tokenTeamDigihot = createToken {
        groups(AzureAdGruppe.TEAMDIGIHOT)
    }

    private val verificationSaksbehandling: Verification.() -> Unit = {
        withAnyRoleAndClientIdClaim(
            AzureAdRolle.SYSTEMBRUKER_SAKSBEHANDLING,
            AzureAdRolle.SYSTEMBRUKER_AZURE_TOKEN_GENERATOR,
        )
    }
    private val verificationBrilleAdmin: Verification.() -> Unit = {
        withAnyGroupClaim(
            AzureAdGruppe.TEAMDIGIHOT,
            AzureAdGruppe.BRILLEADMIN_BRUKERE,
        )
    }
    private val verificationBrilleIntegrasjon: Verification.() -> Unit = {
        withRoleAndClientIdClaim(AzureAdRolle.SYSTEMBRUKER_BRILLE_INTEGRASJON)
    }

    @BeforeTest
    fun setUp() {
        tokenSaksbehandling inneholderKunRolle AzureAdRolle.SYSTEMBRUKER_SAKSBEHANDLING
        tokenAzureTokenGenerator inneholderKunRolle AzureAdRolle.SYSTEMBRUKER_AZURE_TOKEN_GENERATOR
        tokenBrilleIntegrasjon inneholderKunRolle AzureAdRolle.SYSTEMBRUKER_BRILLE_INTEGRASJON
        tokenBrilleAdmin inneholderKunGruppe AzureAdGruppe.BRILLEADMIN_BRUKERE
        tokenTeamDigihot inneholderKunGruppe AzureAdGruppe.TEAMDIGIHOT
    }

    @Test
    fun `Skal validere at token inneholder verdier for forventet rolle`() {
        shouldNotThrowAny {
            verifyToken(tokenSaksbehandling, verificationSaksbehandling)
        }
        shouldNotThrowAny {
            verifyToken(tokenAzureTokenGenerator, verificationSaksbehandling)
        }
        shouldNotThrowAny {
            verifyToken(tokenBrilleIntegrasjon, verificationBrilleIntegrasjon)
        }
    }

    @Test
    fun `Skal validere at token ikke inneholder verdier for forventet rolle`() {
        shouldThrow<IncorrectClaimException> {
            verifyToken(tokenSaksbehandling, verificationBrilleIntegrasjon)
        }
        shouldThrow<IncorrectClaimException> {
            verifyToken(tokenAzureTokenGenerator, verificationBrilleIntegrasjon)
        }
        shouldThrow<IncorrectClaimException> {
            verifyToken(tokenBrilleIntegrasjon, verificationSaksbehandling)
        }
        shouldThrow<MissingClaimException> {
            verifyToken(createToken(), verificationSaksbehandling)
        }
    }

    @Test
    fun `Skal validere at token inneholder verdier for forventet gruppe`() {
        shouldNotThrowAny {
            verifyToken(tokenBrilleAdmin, verificationBrilleAdmin)
        }
        shouldNotThrowAny {
            verifyToken(tokenTeamDigihot, verificationBrilleAdmin)
        }
    }

    @Test
    fun `Skal validere at token ikke inneholder verdier for forventet gruppe`() {
        shouldThrow<IncorrectClaimException> {
            verifyToken(createToken { claim("groups", listOf(UUID.randomUUID())) }, verificationBrilleAdmin)
        }
        shouldThrow<MissingClaimException> {
            verifyToken(createToken(), verificationBrilleAdmin)
        }
    }

    private infix fun JWT.inneholderKunRolle(rolle: AzureAdRolle) =
        jwtClaimsSet.getClaim("roles") shouldBe setOf(rolle.role)

    private infix fun JWT.inneholderKunGruppe(gruppe: AzureAdGruppe) =
        jwtClaimsSet.getClaim("groups") shouldBe setOf(gruppe.objectId)
}
