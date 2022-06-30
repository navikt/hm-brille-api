package no.nav.hjelpemidler.brille.nare.evaluering

class Evalueringer {
    fun ja(begrunnelse: String) = Evaluering(Resultat.JA, begrunnelse)

    fun nei(begrunnelse: String) = Evaluering(Resultat.NEI, begrunnelse)

    fun kanskje(begrunnelse: String) = Evaluering(Resultat.KANSKJE, begrunnelse)

    fun evaluer(identifikator: String, beskrivelse: String, evaluering: Evaluering) =
        evaluering.copy(identifikator = identifikator, beskrivelse = beskrivelse)
}
