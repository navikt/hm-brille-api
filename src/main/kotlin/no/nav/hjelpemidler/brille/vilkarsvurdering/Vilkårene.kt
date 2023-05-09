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
        identifikator = "HarIkkeVedtakIKalenderåret v1",
        lovReferanse = "§ 3",
        lovdataLenke = "https://lovdata.no/LTI/forskrift/2022-07-19-1364/§3",
    ) { grunnlag ->
        val harIkkeVedtakIKalenderåret = grunnlag.vedtakBarn.none { vedtak ->
            vedtak.bestillingsdato.year == grunnlag.bestillingsdato.year
        }

        val eksisterendeVedtakDato = grunnlag.vedtakBarn.find { vedtak ->
            vedtak.bestillingsdato.year == grunnlag.bestillingsdato.year
        }?.bestillingsdato.toString()

        when (harIkkeVedtakIKalenderåret) {
            true -> {
                ja(
                    "Barnet har ikke vedtak om brille i kalenderåret",
                    mapOf("bestillingsdato" to grunnlag.bestillingsdato.formatert())
                )
            }

            false -> nei(
                "Barnet har allerede vedtak om brille i kalenderåret",
                mapOf(
                    "eksisterendeVedtakDato" to eksisterendeVedtakDato,
                    "bestillingsdato" to grunnlag.bestillingsdato.formatert()
                ),
            )
        }
    }

    val HarIkkeHotsakVedtakIKalenderåret = Spesifikasjon<Vilkårsgrunnlag>(
        beskrivelse = "Ikke fått støtte til barnebriller gjennom manuell søknad tidligere i bestillingsåret",
        identifikator = "HarIkkeManueltVedtakIKalenderåret v1",
        lovReferanse = "§ 3",
        lovdataLenke = "https://lovdata.no/LTI/forskrift/2022-07-19-1364/§3",
    ) { grunnlag ->

        val harIkkeVedtakIKalenderåret = when (grunnlag.eksisterendeVedtakDatoHotsak) {
            null -> true
            else -> false
        }

        when (harIkkeVedtakIKalenderåret) {
            true -> {
                ja(
                    "Barnet har ikke vedtak om brille i kalenderåret",
                    mapOf("bestillingsdato" to grunnlag.bestillingsdato.toString())
                )
            }

            false -> nei(
                "Barnet har allerede vedtak om brille i kalenderåret",
                mapOf(
                    "eksisterendeVedtakDato" to grunnlag.eksisterendeVedtakDatoHotsak.toString(),
                    "bestillingsdato" to grunnlag.bestillingsdato.toString()
                ),
            )
        }
    }

    val Under18ÅrPåBestillingsdato = Spesifikasjon<Vilkårsgrunnlag>(
        beskrivelse = "Barnet må være under 18 år på bestillingsdato",
        identifikator = "Under18ÅrPåBestillingsdato v1",
        lovReferanse = "§ 2, 4.ledd",
        lovdataLenke = "https://lovdata.no/LTI/forskrift/2022-07-19-1364/§2"
    ) { grunnlag ->
        val barnetsAlder = grunnlag.barnetsAlderPåBestillingsdato
        when {
            barnetsAlder == null -> nei(
                "Barnets fødselsdato er ukjent",
                mapOf(
                    "bestillingsdato" to grunnlag.bestillingsdato.formatert(),
                    "barnetsAlder" to "ukjent")
            )

            barnetsAlder < 18 -> ja(
                "Barnet var under 18 år på bestillingsdato",
                mapOf(
                    "bestillingsdato" to grunnlag.bestillingsdato.formatert(),
                    "barnetsAlder" to "${grunnlag.barnetsFødselsdato?.formatert()} (${barnetsAlder} år)",
                )
            )

            else -> nei(
                "Barnet var 18 år eller eldre på bestillingsdato",
                mapOf(
                    "bestillingsdato" to grunnlag.bestillingsdato.formatert(),
                    "barnetsAlder" to "${grunnlag.barnetsFødselsdato?.formatert()} (${barnetsAlder} år)",
                )
            )
        }
    }

    val MedlemAvFolketrygden = Spesifikasjon<Vilkårsgrunnlag>(
        beskrivelse = "Medlem av folketrygden",
        identifikator = "MedlemAvFolketrygden v1",
        lovReferanse = "§ 2, 1. ledd",
        lovdataLenke = "https://lovdata.no/LTI/forskrift/2022-07-19-1364/§2"
    ) { grunnlag ->
        val medlemskapResultat = grunnlag.medlemskapResultat
        when {
            medlemskapResultat.resultat == MedlemskapResultatResultat.JA -> ja(
                "Barnet er medlem i folketrygden",
                mapOf("bestillingsdato" to grunnlag.bestillingsdato.formatert())
            )

            medlemskapResultat.resultat == MedlemskapResultatResultat.UAVKLART -> ja(
                "Barnet er antatt medlem i folketrygden basert på folkeregistrert adresse i Norge",
                mapOf("bestillingsdato" to grunnlag.bestillingsdato.formatert())
            )

            else -> nei(
                "Barnet er antatt ikke medlem i folketrygden fordi vi ikke har klart å påvise folkeregistrert adresse i Norge",
                mapOf("bestillingsdato" to grunnlag.bestillingsdato.formatert())
            )
        }
    }

    val Brillestyrke = Spesifikasjon<Vilkårsgrunnlag>(
        beskrivelse = "Brillestyrken er innenfor fastsatte styrker",
        identifikator = "Brillestyrke v1",
        lovReferanse = "§ 2, 2. ledd",
        lovdataLenke = "https://lovdata.no/LTI/forskrift/2022-07-19-1364/§4"
    ) { grunnlag ->
        val brillestyrkeGrunnlag = mapOf<String, String>(
            "venstreSfære" to grunnlag.brilleseddel.venstreSfære.toString(),
            "venstreSylinder" to grunnlag.brilleseddel.venstreSylinder.toString(),
            "høyreSfære" to grunnlag.brilleseddel.høyreSfære.toString(),
            "høyreSylinder" to grunnlag.brilleseddel.høyreSylinder.toString()
        )
        val brilleseddel = grunnlag.brilleseddel
        val minsteSfære = grunnlag.minsteSfære
        val minsteSylinder = grunnlag.minsteSylinder
        when {
            brilleseddel.høyreSfære >= minsteSfære -> ja(
                "Høyre sfære oppfyller vilkår om brillestyrke ≥ $minsteSfære",
                brillestyrkeGrunnlag
            )

            brilleseddel.høyreSylinder >= minsteSylinder -> ja(
                "Høyre sylinder oppfyller vilkår om sylinderstyrke ≥ $minsteSylinder",
                brillestyrkeGrunnlag
            )

            brilleseddel.venstreSfære >= minsteSfære -> ja(
                "Venstre sfære oppfyller vilkår om brillestyrke ≥ $minsteSfære",
                brillestyrkeGrunnlag
            )

            brilleseddel.venstreSylinder >= minsteSylinder -> ja(
                "Venstre sylinder oppfyller vilkår om sylinderstyrke ≥ $minsteSylinder",
                brillestyrkeGrunnlag
            )

            else -> nei("Vilkår om brillestyrke og/eller sylinderstyrke er ikke oppfylt", brillestyrkeGrunnlag)
        }
    }

    val Bestillingsdato = Spesifikasjon<Vilkårsgrunnlag>(
        beskrivelse = "Bestillingen er gjort etter at loven trådte i kraft",
        identifikator = "Bestillingsdato v1",
        lovReferanse = "§ 13",
        lovdataLenke = "https://lovdata.no/LTI/forskrift/2022-07-19-1364/§13"
    ) { grunnlag ->
        val datoOrdningenStartet = grunnlag.datoOrdningenStartet
        when {
            grunnlag.bestillingsdato.isAfter(grunnlag.dagensDato) -> nei(
                "Bestillingsdato kan ikke være i fremtiden (etter ${grunnlag.dagensDato.formatert()})",
                mapOf(
                    "bestillingsdato" to grunnlag.bestillingsdato.formatert(),
                    "datoOrdningenStartet" to datoOrdningenStartet.formatert()
                )
            )

            grunnlag.bestillingsdato.isBefore(datoOrdningenStartet) -> nei(
                "Bestillingsdato kan ikke være før ${datoOrdningenStartet.formatert()}",
                mapOf(
                    "bestillingsdato" to grunnlag.bestillingsdato.formatert(),
                    "datoOrdningenStartet" to datoOrdningenStartet.formatert()
                )
            )

            else -> ja(
                "Bestillingsdato er ${datoOrdningenStartet.formatert()} eller senere",
                mapOf(
                    "bestillingsdato" to grunnlag.bestillingsdato.formatert(),
                    "datoOrdningenStartet" to datoOrdningenStartet.formatert()
                )
            )
        }
    }

    val BestillingsdatoTilbakeITid = Spesifikasjon<Vilkårsgrunnlag>(
        beskrivelse = "Bestillingsdato innenfor gyldig periode",
        identifikator = "BestillingsdatoTilbakeITid v1",
        lovReferanse = "§ 6, 2. ledd",
        lovdataLenke = "https://lovdata.no/LTI/forskrift/2022-07-19-1364/§6"
    ) { grunnlag ->
        val seksMånederSiden = grunnlag.seksMånederSiden
        when {
            grunnlag.bestillingsdato.isBefore(seksMånederSiden) -> nei(
                "Bestillingsdato kan ikke være før ${seksMånederSiden.formatert()}",
                mapOf(
                    "bestillingsdato" to grunnlag.bestillingsdato.formatert(),
                    "seksMånederSiden" to grunnlag.seksMånederSiden.formatert()
                )
            )

            else -> ja(
                "Bestillingsdato er ${seksMånederSiden.formatert()} eller senere",
                mapOf(
                    "bestillingsdato" to grunnlag.bestillingsdato.formatert(),
                    "seksMånederSiden" to grunnlag.seksMånederSiden.formatert()
                )
            )
        }
    }

    val Brille = (
            HarIkkeVedtakIKalenderåret og
                    Under18ÅrPåBestillingsdato og
                    MedlemAvFolketrygden og
                    Brillestyrke og
                    Bestillingsdato og
                    BestillingsdatoTilbakeITid
            ).med("Brille_v1", "Personen oppfyller vilkår for krav om barnebriller")

    val BrilleV2 = (
            HarIkkeVedtakIKalenderåret og
                    Under18ÅrPåBestillingsdato og
                    MedlemAvFolketrygden og
                    Brillestyrke og
                    Bestillingsdato og
                    BestillingsdatoTilbakeITid og
                    HarIkkeHotsakVedtakIKalenderåret
            ).med("Brille_v1", "Personen oppfyller vilkår for krav om barnebriller")

    private fun LocalDate.formatert(): String =
        this.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(Locale("nb")))
}
