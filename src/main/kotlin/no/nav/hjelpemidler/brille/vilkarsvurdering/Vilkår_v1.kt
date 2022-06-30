package no.nav.hjelpemidler.brille.vilkarsvurdering

import no.nav.hjelpemidler.brille.nare.spesifikasjon.Spesifikasjon
import no.nav.hjelpemidler.brille.pdl.PersonDetaljerDto
import no.nav.hjelpemidler.brille.sats.Diopter
import no.nav.hjelpemidler.brille.sats.SatsGrunnlag

object Vilkår_v1 {
    data class Grunnlag_v1(
        val harFåttBrilleDetteKalenderåret: Boolean,
        val personInformasjon: PersonDetaljerDto,
        val satsGrunnlag: SatsGrunnlag,
    ) : VilkårsvurderingGrunnlag

    val FåttBrilleIKalenderåret_v1 = Spesifikasjon<Grunnlag_v1>(
        beskrivelse = "Har personen allerede fått brille i kalenderåret?",
        identifikator = "FåttBrilleIKalenderåret_v1"
    ) { grunnlag ->
        when {
            grunnlag.harFåttBrilleDetteKalenderåret -> ja("Barnet har allerede fått brille i kalenderåret")
            else -> nei("Barnet har ikke fått brille i kalenderåret")
        }
    }

    val IkkeFåttBrilleIKalenderåret_v1 = FåttBrilleIKalenderåret_v1.ikke()

    val Under18År_v1 = Spesifikasjon<Grunnlag_v1>(
        beskrivelse = "Er personen under 18 år?",
        identifikator = "Under18År_v1"
    ) { grunnlag ->
        when {
            grunnlag.personInformasjon.alder == null -> kanskje("Personens alder er ukjent")
            grunnlag.personInformasjon.alder < 18 -> ja("Personen er under 18 år")
            else -> nei("Personen er 18 år eller eldre")
        }
    }

    val MedlemAvFolketrygden_v1 = Spesifikasjon<Grunnlag_v1>(
        beskrivelse = "Er personen medlem av folketrygden?",
        identifikator = "MedlemAvFolketrygden_v1"
    ) { grunnlag ->
        kanskje("Personens medlemskap i folketrygden er ukjent")
    }

    val Brillestyrke_v1 = Spesifikasjon<Grunnlag_v1>(
        beskrivelse = "",
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

    val Brille_V1 = (IkkeFåttBrilleIKalenderåret_v1 og Under18År_v1 og MedlemAvFolketrygden_v1 og Brillestyrke_v1)
        .med("Brille_v1", "Personen oppfyller vilkår for søknad om barnebriller")
}
