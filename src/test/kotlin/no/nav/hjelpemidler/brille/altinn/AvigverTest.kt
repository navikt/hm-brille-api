package no.nav.hjelpemidler.brille.altinn

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.test.assertFailsWith

internal class AvigverTest {

    @Test
    internal fun `Altinn roller kan ikke være fraværende`() {
        assertFailsWith<java.lang.IllegalArgumentException> {
            buildRolleQuery(AltinnRoller())
        }
    }

    @Test
    internal fun `Rolle query builder fungerer med en rolle`() {
        val query = buildRolleQuery(AltinnRoller(AltinnRolle.HOVEDADMINISTRATOR))
        query shouldBe " eq HADM"
    }

    @Test
    internal fun `Rolle query builder fungerer med flere roller`() {
        val query = buildRolleQuery(AltinnRoller(AltinnRolle.HOVEDADMINISTRATOR, AltinnRolle.REGNSKAPSMEDARBEIDER))
        query shouldBe " eq HADM or RoleDefinitionCode eq REGNA"

        val query2 = buildRolleQuery(
            AltinnRoller(
                AltinnRolle.REGNSKAPSMEDARBEIDER,
                AltinnRolle.HOVEDADMINISTRATOR,
                AltinnRolle.REGNSKAPSMEDARBEIDER
            )
        )
        query2 shouldBe " eq REGNA or RoleDefinitionCode eq HADM or RoleDefinitionCode eq REGNA"
    }
}
