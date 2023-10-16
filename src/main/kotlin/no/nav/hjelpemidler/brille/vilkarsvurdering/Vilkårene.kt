package no.nav.hjelpemidler.brille.vilkarsvurdering

import no.nav.hjelpemidler.brille.medlemskap.MedlemskapResultatResultat
import no.nav.hjelpemidler.brille.nare.spesifikasjon.Spesifikasjon
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
        lovReferanse = "§ 3",
        lovdataLenke = "https://lovdata.no/dokument/LTI/forskrift/2023-06-26-1129",
    ) { grunnlag ->
        val bestillingsdato = grunnlag.bestillingsdato
        val eksisterendeVedtakDato = grunnlag.vedtakBarn
            .map { it.bestillingsdato }
            .find { it.year == bestillingsdato.year }
        val eksisterendeVedtakDatoHotsak = grunnlag.eksisterendeVedtakDatoHotsak

        when {
            eksisterendeVedtakDato != null -> nei(
                "Barnet har allerede vedtak om brille i kalenderåret",
                mapOf(
                    "eksisterendeVedtakDato" to eksisterendeVedtakDato.formatert(),
                    "bestillingsdato" to bestillingsdato.formatert(),
                ),
            )

            eksisterendeVedtakDatoHotsak != null -> nei(
                "Barnet har allerede vedtak om brille i kalenderåret",
                mapOf(
                    "eksisterendeVedtakDato" to eksisterendeVedtakDatoHotsak.formatert(),
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
        lovReferanse = "§ 2",
        lovdataLenke = "https://lovdata.no/dokument/LTI/forskrift/2023-06-26-1129",
    ) { grunnlag ->
        val barnetsAlder = grunnlag.barnetsAlderPåBestillingsdato
        val barnetsFødselsdato = grunnlag.barnetsFødselsdato
        val bestillingsdato = grunnlag.bestillingsdato

        when {
            barnetsAlder == null -> nei(
                "Barnets fødselsdato er ukjent",
                mapOf(
                    "bestillingsdato" to bestillingsdato.formatert(),
                    "barnetsAlder" to "ukjent",
                ),
            )

            barnetsAlder < 18 -> ja(
                "Barnet var under 18 år på bestillingsdato",
                mapOf(
                    "bestillingsdato" to bestillingsdato.formatert(),
                    "barnetsAlder" to "${barnetsFødselsdato?.formatert()} ($barnetsAlder år)",
                ),
            )

            else -> nei(
                "Barnet var 18 år eller eldre på bestillingsdato",
                mapOf(
                    "bestillingsdato" to bestillingsdato.formatert(),
                    "barnetsAlder" to "${barnetsFødselsdato?.formatert()} ($barnetsAlder år)",
                ),
            )
        }
    }

    val MedlemAvFolketrygden = Spesifikasjon<Vilkårsgrunnlag>(
        beskrivelse = "Medlem av folketrygden",
        identifikator = "MedlemAvFolketrygden",
        lovReferanse = "ftrl. § 10-7 a",
        lovdataLenke = "https://lovdata.no/dokument/NL/lov/1997-02-28-19/KAPITTEL_5-6#%C2%A710-7a",
    ) { grunnlag ->
        val medlemskapResultat = grunnlag.medlemskapResultat
        val bestillingsdato = grunnlag.bestillingsdato

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
        lovReferanse = "§ 2",
        lovdataLenke = "https://lovdata.no/dokument/LTI/forskrift/2023-06-26-1129",
    ) { grunnlag ->
        val brillestyrkeGrunnlag = mapOf<String, String>(
            "venstreSfære" to grunnlag.brilleseddel.venstreSfære.toString(),
            "venstreSylinder" to grunnlag.brilleseddel.venstreSylinder.toString(),
            "høyreSfære" to grunnlag.brilleseddel.høyreSfære.toString(),
            "høyreSylinder" to grunnlag.brilleseddel.høyreSylinder.toString(),
        )
        val brilleseddel = grunnlag.brilleseddel
        val minsteSfære = grunnlag.minsteSfære
        val minsteSylinder = grunnlag.minsteSylinder

        when {
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
        lovReferanse = "§ 6",
        lovdataLenke = "https://lovdata.no/dokument/LTI/forskrift/2023-06-26-1129",
    ) { grunnlag ->
        val bestillingsdato = grunnlag.bestillingsdato
        val seksMånederSiden = grunnlag.seksMånederSiden
        val dagensDato = grunnlag.dagensDato

        when {
            bestillingsdato.isAfter(dagensDato) -> nei(
                "Bestillingsdato kan ikke være i fremtiden (etter ${dagensDato.formatert()})",
                mapOf(
                    "bestillingsdato" to bestillingsdato.formatert(),
                    "dagensDato" to dagensDato.formatert(),
                ),
            )

            bestillingsdato.isBefore(seksMånederSiden) -> nei(
                "Bestillingsdato kan ikke være før ${seksMånederSiden.formatert()}",
                mapOf(
                    "bestillingsdato" to bestillingsdato.formatert(),
                    "seksMånederSiden" to seksMånederSiden.formatert(),
                ),
            )

            else -> ja(
                "Bestillingsdato er ${seksMånederSiden.formatert()} eller senere",
                mapOf(
                    "bestillingsdato" to bestillingsdato.formatert(),
                    "seksMånederSiden" to seksMånederSiden.formatert(),
                ),
            )
        }
    }

    val Brille = (
            HarIkkeVedtakIKalenderåret og
                    Under18ÅrPåBestillingsdato og
                    MedlemAvFolketrygden og
                    Brillestyrke og
                    Bestillingsdato
            ).med("Brille", "Personen oppfyller vilkår for krav om barnebriller")

    private fun LocalDate.formatert(): String =
        this.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(Locale("nb")))
}
