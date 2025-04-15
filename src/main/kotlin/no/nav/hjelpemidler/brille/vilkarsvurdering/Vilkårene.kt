package no.nav.hjelpemidler.brille.vilkarsvurdering

import no.nav.hjelpemidler.brille.medlemskap.MedlemskapResultatResultat
import no.nav.hjelpemidler.brille.sats.mangler
import no.nav.hjelpemidler.brille.tid.mangler
import no.nav.hjelpemidler.localization.LOCALE_NORWEGIAN_BOKMÅL
import no.nav.hjelpemidler.nare.regel.Lovreferanse
import no.nav.hjelpemidler.nare.regel.Regel
import no.nav.hjelpemidler.nare.regel.Regelevaluering.Companion.ja
import no.nav.hjelpemidler.nare.regel.Regelevaluering.Companion.nei
import no.nav.hjelpemidler.nare.regel.Årsak
import java.time.LocalDate
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

val DATO_ORDNINGEN_STARTET: LocalDate = LocalDate.of(2022, Month.AUGUST, 1)

object Vilkårene {
    val HarIkkeVedtakIKalenderåret = Regel<Vilkårsgrunnlag>(
        beskrivelse = "Barnet kan kun få tilskudd én gang i året",
        id = "HarIkkeVedtakIKalenderåret",
        lovreferanse = Lovreferanse(
            "§ 3",
            "https://lovdata.no/dokument/LTI/forskrift/2023-06-26-1129",
        ),
    ) { grunnlag ->
        val bestillingsdato = grunnlag.bestillingsdato
        val eksisterendeVedtakDato = grunnlag.vedtakBarn
            .map { it.bestillingsdato }
            .find { it.year == bestillingsdato.year }

        when {
            grunnlag.vedtakBarn.isEmpty() -> ja(
                "Barnet har ingen vedtak om brille fra tidligere",
            )

            bestillingsdato.mangler() -> nei(
                "Bestillingsdato mangler, kan ikke vurdere om barnet allerede har vedtak om brille i kalenderåret",
                årsak = Årsak.DOKUMENTASJON_MANGLER,
            )

            eksisterendeVedtakDato != null -> nei(
                "Barnet har allerede vedtak om brille i kalenderåret",
                mapOf(
                    "eksisterendeVedtakDato" to eksisterendeVedtakDato.formatert(),
                    "bestillingsdato" to bestillingsdato.formatert(),
                ),
            )

            else -> ja(
                "Barnet har ikke vedtak om brille i kalenderåret",
                mapOf("bestillingsdato" to bestillingsdato.formatert()),
            )
        }
    }

    val Under18ÅrPåBestillingsdato = Regel<Vilkårsgrunnlag>(
        beskrivelse = "Barnet må være under 18 år på bestillingsdato",
        id = "Under18ÅrPåBestillingsdato",
        lovreferanse = Lovreferanse(
            "§ 2",
            "https://lovdata.no/dokument/LTI/forskrift/2023-06-26-1129",
        ),
    ) { grunnlag ->
        val barnetsFødselsdato = grunnlag.barnetsFødselsdato
        val barnetsAlderPåBestillingsdato = grunnlag.barnetsAlderPåBestillingsdato
        val barnetsAlderPåMottaksdato = grunnlag.barnetsAlderPåMottaksdato
        val bestillingsdato = grunnlag.bestillingsdato
        val mottaksdato = grunnlag.mottaksdato

        when {
            barnetsFødselsdato == null -> nei(
                "Barnets fødselsdato er ukjent",
                mapOf(
                    "bestillingsdato" to bestillingsdato.formatert(),
                    "barnetsAlder" to "Ukjent",
                ),
            )

            barnetsAlderPåBestillingsdato.erUnder18() -> ja(
                "Barnet var under 18 år på bestillingsdato",
                mapOf(
                    "bestillingsdato" to bestillingsdato.formatert(),
                    "barnetsAlder" to "${barnetsFødselsdato.formatert()} ($barnetsAlderPåBestillingsdato år)",
                ),
            )

            barnetsAlderPåMottaksdato.erUnder18() -> ja(
                "Barnet var under 18 år på mottaksdato",
                mapOf(
                    "mottaksdato" to mottaksdato.formatert(),
                    "barnetsAlder" to "${barnetsFødselsdato.formatert()} ($barnetsAlderPåMottaksdato år)",
                ),
            )

            bestillingsdato.mangler() -> nei(
                "Bestillingsdato mangler, kan ikke vurdere om barnet var under 18 år på bestillingsdato",
                årsak = Årsak.DOKUMENTASJON_MANGLER,
            )

            else -> nei(
                "Barnet var 18 år eller eldre på bestillingsdato",
                mapOf(
                    "bestillingsdato" to bestillingsdato.formatert(),
                    "barnetsAlder" to "${barnetsFødselsdato.formatert()} ($barnetsAlderPåBestillingsdato år)",
                ),
            )
        }
    }

    val MedlemAvFolketrygden = Regel<Vilkårsgrunnlag>(
        beskrivelse = "Medlem av folketrygden",
        id = "MedlemAvFolketrygden",
        lovreferanse = Lovreferanse(
            "ftrl. § 10-7 a",
            "https://lovdata.no/dokument/NL/lov/1997-02-28-19/KAPITTEL_5-6#%C2%A710-7a",
        ),
    ) { grunnlag ->
        val bestillingsdato = grunnlag.bestillingsdato
        if (bestillingsdato.mangler()) {
            return@Regel nei(
                "Bestillingsdato mangler, kan ikke vurdere om barnet er medlem i folketrygden",
                årsak = Årsak.DOKUMENTASJON_MANGLER,
            )
        }

        val medlemskapResultat = grunnlag.medlemskapResultat
        when (medlemskapResultat.resultat) {
            MedlemskapResultatResultat.JA -> ja(
                "Barnet er medlem i folketrygden",
                mapOf(
                    "bestillingsdato" to bestillingsdato.formatert(),
                    "forenkletSjekkResultat" to "Oppfylt",
                ),
            )

            MedlemskapResultatResultat.UAVKLART -> ja(
                "Barnet er antatt medlem i folketrygden basert på folkeregistrert adresse i Norge",
                mapOf(
                    "bestillingsdato" to bestillingsdato.formatert(),
                    "forenkletSjekkResultat" to "Uavklart medlemskap - må utredes av saksbehandler",
                ),
            )

            else -> nei(
                "Barnet er antatt ikke medlem i folketrygden fordi vi ikke har klart å påvise folkeregistrert adresse i Norge",
                mapOf(
                    "bestillingsdato" to bestillingsdato.formatert(),
                    "forenkletSjekkResultat" to "Ikke oppfylt",
                ),
            )
        }
    }

    val Brillestyrke = Regel<Vilkårsgrunnlag>(
        beskrivelse = "Brillestyrken er innenfor fastsatte styrker",
        id = "Brillestyrke",
        lovreferanse = Lovreferanse(
            "§ 2",
            "https://lovdata.no/dokument/LTI/forskrift/2023-06-26-1129",
        ),
    ) { grunnlag ->
        val minsteSfære = grunnlag.minsteSfære
        val minsteSylinder = grunnlag.minsteSylinder
        val brilleseddel = grunnlag.brilleseddel
        val brillestyrkeGrunnlag = mapOf(
            "venstreSfære" to brilleseddel.venstreSfære.toString(),
            "venstreSylinder" to brilleseddel.venstreSylinder.toString(),
            "høyreSfære" to brilleseddel.høyreSfære.toString(),
            "høyreSylinder" to brilleseddel.høyreSylinder.toString(),
        )

        when {
            brilleseddel.mangler() -> nei(
                "Brilleseddel mangler, kan ikke vurdere om brillestyrken er innenfor fastsatte styrker",
                årsak = Årsak.DOKUMENTASJON_MANGLER,
            )

            brilleseddel.høyreSfære >= minsteSfære -> ja(
                "Høyre sfære oppfyller vilkår om brillestyrke ≥ $minsteSfære",
                brillestyrkeGrunnlag,
            )

            brilleseddel.høyreSylinder >= minsteSylinder -> ja(
                "Høyre sylinder oppfyller vilkår om sylinderstyrke ≥ $minsteSylinder",
                brillestyrkeGrunnlag,
            )

            brilleseddel.venstreSfære >= minsteSfære -> ja(
                "Venstre sfære oppfyller vilkår om brillestyrke ≥ $minsteSfære",
                brillestyrkeGrunnlag,
            )

            brilleseddel.venstreSylinder >= minsteSylinder -> ja(
                "Venstre sylinder oppfyller vilkår om sylinderstyrke ≥ $minsteSylinder",
                brillestyrkeGrunnlag,
            )

            else -> nei(
                "Vilkår om brillestyrke og/eller sylinderstyrke er ikke oppfylt",
                brillestyrkeGrunnlag,
            )
        }
    }

    val Bestillingsdato = Regel<Vilkårsgrunnlag>(
        beskrivelse = "Bestillingsdato må være innenfor gyldig periode",
        id = "Bestillingsdato",
        lovreferanse = Lovreferanse(
            "§ 6",
            "https://lovdata.no/dokument/LTI/forskrift/2023-06-26-1129",
        ),
    ) { grunnlag ->
        val bestillingsdato = grunnlag.bestillingsdato
        val dagensDato = grunnlag.dagensDato
        val seksMånederSiden = grunnlag.seksMånederSiden
        val tidligsteMuligeBestillingsdato = grunnlag.tidligsteMuligeBestillingsdato

        when {
            bestillingsdato.mangler() -> nei(
                "Bestillingsdato mangler, kan ikke vurdere om den er innenfor gyldig periode",
                årsak = Årsak.DOKUMENTASJON_MANGLER,
            )

            bestillingsdato.isAfter(dagensDato) -> nei(
                "Bestillingsdato kan ikke være i fremtiden (etter ${dagensDato.formatert()})",
                mapOf(
                    "bestillingsdato" to bestillingsdato.formatert(),
                    "dagensDato" to dagensDato.formatert(),
                ),
            )

            bestillingsdato.isBefore(tidligsteMuligeBestillingsdato) -> nei(
                "Bestillingsdato kan ikke være før ${tidligsteMuligeBestillingsdato.formatert()}",
                mapOf(
                    "bestillingsdato" to bestillingsdato.formatert(),
                    "seksMånederSiden" to seksMånederSiden.formatert(),
                    "tidligsteMuligeBestillingsdato" to tidligsteMuligeBestillingsdato.formatert(),
                ),
            )

            else -> ja(
                "Bestillingsdato er ${tidligsteMuligeBestillingsdato.formatert()} eller senere",
                mapOf(
                    "bestillingsdato" to bestillingsdato.formatert(),
                    "seksMånederSiden" to seksMånederSiden.formatert(),
                    "tidligsteMuligeBestillingsdato" to tidligsteMuligeBestillingsdato.formatert(),
                ),
            )
        }
    }

    val Brille: Regel<Vilkårsgrunnlag> =
        (HarIkkeVedtakIKalenderåret og Under18ÅrPåBestillingsdato og MedlemAvFolketrygden og Brillestyrke og Bestillingsdato).med(
            id = "Brille",
            beskrivelse = "Personen oppfyller vilkår for krav om barnebriller",
        )

    private fun LocalDate.formatert(): String =
        this.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(LOCALE_NORWEGIAN_BOKMÅL))
}
