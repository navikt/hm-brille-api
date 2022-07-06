package no.nav.hjelpemidler.brille.vilkarsvurdering

import no.nav.hjelpemidler.brille.medlemskap.MedlemskapResultat
import no.nav.hjelpemidler.brille.nare.spesifikasjon.Spesifikasjon
import no.nav.hjelpemidler.brille.pdl.PdlOppslag
import no.nav.hjelpemidler.brille.pdl.fodselsdato
import no.nav.hjelpemidler.brille.sats.BeregnSats
import no.nav.hjelpemidler.brille.sats.Diopter
import no.nav.hjelpemidler.brille.vedtak.EksisterendeVedtak
import java.time.LocalDate
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val DATO_ORDNINGEN_STARTET: LocalDate = LocalDate.of(2022, Month.AUGUST, 1)

object Vilkår_v1 {

    data class Grunnlag_v1(
        val vedtakForBruker: List<EksisterendeVedtak>,
        val pdlOppslagBruker: PdlOppslag,
        val beregnSats: BeregnSats,
        val bestillingsdato: LocalDate,
        val dagensDato: LocalDate = LocalDate.now(),
        val datoOrdningenStartet: LocalDate = DATO_ORDNINGEN_STARTET,
        val seksMånederSiden: LocalDate = LocalDate.now().minusMonths(6),
        val medlemskapResultat: MedlemskapResultat,
    )

    val HarIkkeVedtakIKalenderåret_v1 = Spesifikasjon<Grunnlag_v1>(
        beskrivelse = "Har barnet allerede vedtak om brille i kalenderåret?",
        identifikator = "HarIkkeVedtakIKalenderåret_v1"
    ) { grunnlag ->
        val harIkkeVedtakIKalenderåret = grunnlag.vedtakForBruker.none { vedtak ->
            vedtak.bestillingsdato.year == grunnlag.bestillingsdato.year
        }
        when (harIkkeVedtakIKalenderåret) {
            true -> ja("Barnet har ikke vedtak om brille i kalenderåret")
            false -> nei("Barnet har allerede vedtak om brille i kalenderåret")
        }
    }

    val Under18ÅrPåBestillingsdato_v1 = Spesifikasjon<Grunnlag_v1>(
        beskrivelse = "Var barnet under 18 år på bestillingsdato?",
        identifikator = "Under18ÅrPåBestillingsdato_v1"
    ) { grunnlag ->
        val fodselsdato = grunnlag.pdlOppslagBruker.pdlPersonResponse.data?.fodselsdato()
        when {
            fodselsdato == null -> kanskje("Barnets fødselsdato er ukjent")
            fodselsdato.until(grunnlag.bestillingsdato).years < 18 -> ja("Barnet var under 18 år på bestillingsdato")
            else -> nei("Barnet var 18 år eller eldre på bestillingsdato")
        }
    }

    val MedlemAvFolketrygden_v1 = Spesifikasjon<Grunnlag_v1>(
        beskrivelse = "Er barnet medlem av folketrygden?",
        identifikator = "MedlemAvFolketrygden_v1"
    ) { grunnlag ->
        when {
            grunnlag.medlemskapResultat.medlemskapBevist -> ja("Barnet er medlem i folketrygden")
            grunnlag.medlemskapResultat.uavklartMedlemskap -> ja("Barnet er antatt medlem i folketrygden basert på folkeregistrert adresse i Norge")
            else -> nei("Barnet er antatt ikke medlem i folketrygden fordi vi ikke har klart å påvise folkeregistrert adresse i Norge")
        }
    }

    val Brillestyrke_v1 = Spesifikasjon<Grunnlag_v1>(
        beskrivelse = "Er brillestyrken innenfor de fastsatte rammene?",
        identifikator = "Brillestyrke_v1"
    ) { grunnlag ->
        val satsGrunnlag = grunnlag.beregnSats
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
        beskrivelse = "Er bestillingsdato ${DATO_ORDNINGEN_STARTET.formatert()} eller senere?",
        identifikator = "Bestillingsdato_v1"
    ) { grunnlag ->
        val datoOrdningenStartet = grunnlag.datoOrdningenStartet
        when {
            grunnlag.bestillingsdato.isBefore(datoOrdningenStartet) -> nei("Bestillingsdato kan ikke være før ${datoOrdningenStartet.formatert()}")
            else -> ja("Bestillingsdato er ${datoOrdningenStartet.formatert()} eller senere")
        }
    }

    val BestillingsdatoTilbakeITid_v1 = Spesifikasjon<Grunnlag_v1>(
        beskrivelse = "Er bestillingsdato innenfor siste 6 måneder fra dagens dato?",
        identifikator = "BestillingsdatoTilbakeITid_v1"
    ) { grunnlag ->
        val seksMånederSiden = grunnlag.seksMånederSiden
        when {
            grunnlag.bestillingsdato.isBefore(seksMånederSiden) -> nei("Bestillingsdato kan ikke være før ${seksMånederSiden.formatert()}")
            else -> ja("Bestillingsdato er ${seksMånederSiden.formatert()} eller senere")
        }
    }

    val Brille_v1 = (
            HarIkkeVedtakIKalenderåret_v1 og
                    Under18ÅrPåBestillingsdato_v1 og
            MedlemAvFolketrygden_v1 og
            Brillestyrke_v1 og
            Bestillingsdato_v1 og
            BestillingsdatoTilbakeITid_v1
        ).med("Brille_v1", "Personen oppfyller vilkår for søknad om barnebriller")

    private fun LocalDate.formatert(): String =
        this.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(Locale("nb")))
}
