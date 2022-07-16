package no.nav.hjelpemidler.brille.vilkarsvurdering

import no.nav.hjelpemidler.brille.nare.spesifikasjon.Spesifikasjon
import java.time.LocalDate
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

val DATO_ORDNINGEN_STARTET: LocalDate = LocalDate.of(2022, Month.AUGUST, 1)

object Vilkårene {
    val HarIkkeVedtakIKalenderåret = Spesifikasjon<Vilkårsgrunnlag>(
        beskrivelse = "Har barnet allerede vedtak om brille i kalenderåret?",
        identifikator = "HarIkkeVedtakIKalenderåret v1"
    ) { grunnlag ->
        val harIkkeVedtakIKalenderåret = grunnlag.vedtakForBarn.none { vedtak ->
            vedtak.bestillingsdato.year == grunnlag.bestillingsdato.year
        }
        when (harIkkeVedtakIKalenderåret) {
            true -> ja("Barnet har ikke vedtak om brille i kalenderåret")
            false -> nei("Barnet har allerede vedtak om brille i kalenderåret")
        }
    }

    val Under18ÅrPåBestillingsdato = Spesifikasjon<Vilkårsgrunnlag>(
        beskrivelse = "Var barnet under 18 år på bestillingsdato?",
        identifikator = "Under18ÅrPåBestillingsdato v1"
    ) { grunnlag ->
        val fodselsdato = grunnlag.fodselsdatoBruker
        when {
            fodselsdato == null -> nei("Barnets fødselsdato er ukjent")
            fodselsdato.until(grunnlag.bestillingsdato).years < 18 -> ja("Barnet var under 18 år på bestillingsdato")
            else -> nei("Barnet var 18 år eller eldre på bestillingsdato")
        }
    }

    val MedlemAvFolketrygden = Spesifikasjon<Vilkårsgrunnlag>(
        beskrivelse = "Er barnet medlem av folketrygden?",
        identifikator = "MedlemAvFolketrygden v1"
    ) { grunnlag ->
        val medlemskapResultat = grunnlag.medlemskapResultat
        when {
            medlemskapResultat.medlemskapBevist -> ja("Barnet er medlem i folketrygden")
            medlemskapResultat.uavklartMedlemskap -> ja("Barnet er antatt medlem i folketrygden basert på folkeregistrert adresse i Norge")
            else -> nei("Barnet er antatt ikke medlem i folketrygden fordi vi ikke har klart å påvise folkeregistrert adresse i Norge")
        }
    }

    val Brillestyrke = Spesifikasjon<Vilkårsgrunnlag>(
        beskrivelse = "Er brillestyrken innenfor de fastsatte rammene?",
        identifikator = "Brillestyrke v1"
    ) { grunnlag ->
        val brilleseddel = grunnlag.brilleseddel
        val minsteSfære = grunnlag.minsteSfære
        val minsteSylinder = grunnlag.minsteSylinder
        when {
            brilleseddel.høyreSfære >= minsteSfære -> ja("Høyre sfære oppfyller vilkår om brillestyrke ≥ $minsteSfære")
            brilleseddel.høyreSylinder >= minsteSylinder -> ja("Høyre sylinder oppfyller vilkår om sylinderstyrke ≥ $minsteSylinder")
            brilleseddel.venstreSfære >= minsteSfære -> ja("Venstre sfære oppfyller vilkår om brillestyrke ≥ $minsteSfære")
            brilleseddel.venstreSylinder >= minsteSylinder -> ja("Venstre sylinder oppfyller vilkår om sylinderstyrke ≥ $minsteSylinder")
            else -> nei("Vilkår om brillestyrke og/eller sylinderstyrke er ikke oppfylt")
        }
    }

    val Bestillingsdato = Spesifikasjon<Vilkårsgrunnlag>(
        beskrivelse = "Er bestillingsdato ${DATO_ORDNINGEN_STARTET.formatert()} eller senere?",
        identifikator = "Bestillingsdato v1"
    ) { grunnlag ->
        val datoOrdningenStartet = grunnlag.datoOrdningenStartet
        when {
            grunnlag.bestillingsdato.isAfter(grunnlag.dagensDato) -> nei("Bestillingsdato kan ikke være i fremtiden (etter ${grunnlag.dagensDato.formatert()})")
            grunnlag.bestillingsdato.isBefore(datoOrdningenStartet) -> nei("Bestillingsdato kan ikke være før ${datoOrdningenStartet.formatert()}")
            else -> ja("Bestillingsdato er ${datoOrdningenStartet.formatert()} eller senere")
        }
    }

    val BestillingsdatoTilbakeITid = Spesifikasjon<Vilkårsgrunnlag>(
        beskrivelse = "Er bestillingsdato innenfor siste 6 måneder fra dagens dato?",
        identifikator = "BestillingsdatoTilbakeITid v1"
    ) { grunnlag ->
        val seksMånederSiden = grunnlag.seksMånederSiden
        when {
            grunnlag.bestillingsdato.isBefore(seksMånederSiden) -> nei("Bestillingsdato kan ikke være før ${seksMånederSiden.formatert()}")
            else -> ja("Bestillingsdato er ${seksMånederSiden.formatert()} eller senere")
        }
    }

    val Brille = (
        HarIkkeVedtakIKalenderåret og
            Under18ÅrPåBestillingsdato og
            MedlemAvFolketrygden og
            Brillestyrke og
            Bestillingsdato og
            BestillingsdatoTilbakeITid
        ).med("Brille_v1", "Personen oppfyller vilkår for søknad om barnebriller")

    private fun LocalDate.formatert(): String =
        this.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(Locale("nb")))
}
