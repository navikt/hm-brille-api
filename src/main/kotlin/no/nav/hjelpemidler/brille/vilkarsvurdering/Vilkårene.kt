package no.nav.hjelpemidler.brille.vilkarsvurdering

import no.nav.hjelpemidler.brille.medlemskap.MedlemskapResultatResultat
import no.nav.hjelpemidler.brille.sats.mangler
import no.nav.hjelpemidler.brille.tid.mangler
import no.nav.hjelpemidler.nare.evaluering.Årsak
import no.nav.hjelpemidler.nare.spesifikasjon.Spesifikasjon
import java.time.LocalDate
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

val DATO_ORDNINGEN_STARTET: LocalDate = LocalDate.of(2022, Month.AUGUST, 1)

object Vilkårene {
    val HarIkkeVedtakIKalenderåret = Spesifikasjon<Vilkårsgrunnlag>(
        beskrivelse = "Ikke fått støtte til barnebriller tidligere i bestillingsåret",
        identifikator = "HarIkkeVedtakIKalenderåret",
        lovreferanse = "§ 3",
        lovdataUrl = "https://lovdata.no/dokument/LTI/forskrift/2023-06-26-1129",
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
                Årsak.DOKUMENTASJON_MANGLER,
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

    val Under18ÅrPåBestillingsdato = Spesifikasjon<Vilkårsgrunnlag>(
        beskrivelse = "Barnet må være under 18 år på bestillingsdato",
        identifikator = "Under18ÅrPåBestillingsdato",
        lovreferanse = "§ 2",
        lovdataUrl = "https://lovdata.no/dokument/LTI/forskrift/2023-06-26-1129",
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
                Årsak.DOKUMENTASJON_MANGLER,
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

    val MedlemAvFolketrygden = Spesifikasjon<Vilkårsgrunnlag>(
        beskrivelse = "Medlem av folketrygden",
        identifikator = "MedlemAvFolketrygden",
        lovreferanse = "ftrl. § 10-7 a",
        lovdataUrl = "https://lovdata.no/dokument/NL/lov/1997-02-28-19/KAPITTEL_5-6#%C2%A710-7a",
    ) { grunnlag ->
        val bestillingsdato = grunnlag.bestillingsdato
        if (bestillingsdato.mangler()) {
            return@Spesifikasjon nei(
                "Bestillingsdato mangler, kan ikke vurdere om barnet er medlem i folketrygden",
                Årsak.DOKUMENTASJON_MANGLER,
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

    val Brillestyrke = Spesifikasjon<Vilkårsgrunnlag>(
        beskrivelse = "Brillestyrken er innenfor fastsatte styrker",
        identifikator = "Brillestyrke",
        lovreferanse = "§ 2",
        lovdataUrl = "https://lovdata.no/dokument/LTI/forskrift/2023-06-26-1129",
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
                Årsak.DOKUMENTASJON_MANGLER,
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

            else -> nei("Vilkår om brillestyrke og/eller sylinderstyrke er ikke oppfylt", brillestyrkeGrunnlag)
        }
    }

    val Bestillingsdato = Spesifikasjon<Vilkårsgrunnlag>(
        beskrivelse = "Bestillingsdato innenfor gyldig periode",
        identifikator = "Bestillingsdato",
        lovreferanse = "§ 6",
        lovdataUrl = "https://lovdata.no/dokument/LTI/forskrift/2023-06-26-1129",
    ) { grunnlag ->
        val bestillingsdato = grunnlag.bestillingsdato
        val dagensDato = grunnlag.dagensDato
        val seksMånederSiden = grunnlag.seksMånederSiden
        val tidligsteMuligeBestillingsdato = grunnlag.tidligsteMuligeBestillingsdato

        when {
            bestillingsdato.mangler() -> nei(
                "Bestillingsdato mangler, kan ikke vurdere om den er innenfor gyldig periode",
                Årsak.DOKUMENTASJON_MANGLER,
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

    val Brille: Spesifikasjon<Vilkårsgrunnlag> =
        (HarIkkeVedtakIKalenderåret og Under18ÅrPåBestillingsdato og MedlemAvFolketrygden og Brillestyrke og Bestillingsdato).med(
            identifikator = "Brille",
            beskrivelse = "Personen oppfyller vilkår for krav om barnebriller",
        )

    private fun LocalDate.formatert(): String =
        this.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(Locale("nb")))
}
