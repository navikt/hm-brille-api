package no.nav.hjelpemidler.brille.vilkarsvurdering

import no.nav.hjelpemidler.brille.nare.spesifikasjon.Spesifikasjon
import no.nav.hjelpemidler.brille.pdl.PersonDetaljerDto
import no.nav.hjelpemidler.brille.sats.Diopter
import no.nav.hjelpemidler.brille.sats.SatsGrunnlag
import no.nav.hjelpemidler.brille.vedtak.Vedtak_v2
import java.time.LocalDate
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

object Vilkår_v1 {
    data class Grunnlag_v1(
        val vedtakIBestillingsdatoAr: Vedtak_v2?,
        val personInformasjon: PersonDetaljerDto,
        val satsGrunnlag: SatsGrunnlag,
        val bestillingsdato: LocalDate,
        val dagsDato: LocalDate = LocalDate.now(),
        val datoOrdningenStarter: LocalDate = LocalDate.of(2022, Month.AUGUST, 1),
        val tidligsteMuligeBestillingsdato: LocalDate = LocalDate.now().minusMonths(6),
    ) : VilkårsvurderingGrunnlag

    // ingen andre vedtak med samme bestillingsdato-år lik denne
    val FåttBrilleIKalenderåret_v1 = Spesifikasjon<Grunnlag_v1>(
        beskrivelse = "Har barnet allerede fått brille i kalenderåret?",
        identifikator = "FåttBrilleIKalenderåret_v1"
    ) { grunnlag ->
        when (grunnlag.vedtakIBestillingsdatoAr) {
            null -> nei("Barnet har ikke fått brille i kalenderåret")
            else -> ja("Barnet har allerede fått brille i kalenderåret")
        }
    }

    val IkkeFåttBrilleIKalenderåret_v1 = FåttBrilleIKalenderåret_v1.ikke()

    val Under18ÅrPåBestillingsdato_v1 = Spesifikasjon<Grunnlag_v1>(
        beskrivelse = "Var barnet under 18 år på bestillingsdatoen?",
        identifikator = "Under18ÅrPåBestillingsdato_v1"
    ) { grunnlag ->
        val fodselsdato = grunnlag.personInformasjon.fodselsdato
        when {
            fodselsdato == null -> kanskje("Barnets fødselsdato er ukjent")
            fodselsdato.until(grunnlag.bestillingsdato).years < 18 -> ja("Barnet var under 18 år på bestillingsdatoen")
            else -> nei("Barnet var 18 år eller eldre på bestillingsdatoen")
        }
    }

    val MedlemAvFolketrygden_v1 = Spesifikasjon<Grunnlag_v1>(
        beskrivelse = "Er barnet medlem av folketrygden?",
        identifikator = "MedlemAvFolketrygden_v1"
    ) { grunnlag ->
        kanskje("Barnets medlemskap i folketrygden er ukjent")
    }

    val Brillestyrke_v1 = Spesifikasjon<Grunnlag_v1>(
        beskrivelse = "Er brillestyrken innenfor de fastsatte rammene?",
        identifikator = "Brillestyrke_v1"
    ) { grunnlag ->
        val satsGrunnlag = grunnlag.satsGrunnlag
        val minsteSfære = Diopter.ONE
        val minsteSylinder = Diopter.ONE
        when {
            satsGrunnlag.høyreSfære >= minsteSfære -> ja("Høyre sfære oppfyller vilkår om brillestyrke ≥ $minsteSfære")
            satsGrunnlag.høyreSylinder >= minsteSylinder -> ja("Høyre sylinder oppfyller vilkår om sylinderstyrke ≥ $minsteSylinder")
            satsGrunnlag.venstreSfære >= minsteSfære -> ja("Venstre sfære oppfyller vilkår om brillestyrke ≥ $minsteSfære")
            satsGrunnlag.venstreSylinder >= minsteSylinder -> ja("Venstre sylinder oppfyller vilkår om sylinderstyrke ≥ $minsteSylinder")
            else -> nei("Vilkår om brillestyrke og/eller sylinderstyrke er ikke oppfylt")
        }
    }

    val Bestillingsdato_v1 = Spesifikasjon<Grunnlag_v1>(
        beskrivelse = "Er bestillingsdato 01.08.2022 eller senere?",
        identifikator = "Bestillingsdato_v1"
    ) { grunnlag ->
        val datoOrdningenStarterFormatert =
            grunnlag.datoOrdningenStarter.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT))
        when {
            grunnlag.bestillingsdato.isBefore(grunnlag.datoOrdningenStarter) -> nei("Bestillingsdato kan ikke være før $datoOrdningenStarterFormatert")
            else -> ja("Bestillingsdato er $datoOrdningenStarterFormatert eller senere")
        }
    }

    val BestillingsdatoTilbakeITid_v1 = Spesifikasjon<Grunnlag_v1>(
        beskrivelse = "Er bestillingsdato innenfor siste 6 måneder fra dags dato?",
        identifikator = "BestillingsdatoTilbakeITid_v1"
    ) { grunnlag ->
        val tidligsteMuligeBestillingsdatoFormatert =
            grunnlag.tidligsteMuligeBestillingsdato.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT))
        when {
            grunnlag.bestillingsdato.isBefore(grunnlag.tidligsteMuligeBestillingsdato) -> nei("Bestillingsdato kan ikke være før $tidligsteMuligeBestillingsdatoFormatert")
            else -> ja("Bestillingsdato er $tidligsteMuligeBestillingsdatoFormatert eller senere")
        }
    }

    val Brille_v1 = (
        IkkeFåttBrilleIKalenderåret_v1 og
            Under18ÅrPåBestillingsdato_v1 og
            MedlemAvFolketrygden_v1 og
            Brillestyrke_v1 og
            Bestillingsdato_v1 og
            BestillingsdatoTilbakeITid_v1
        ).med("Brille_v1", "Personen oppfyller vilkår for søknad om barnebriller")
}
