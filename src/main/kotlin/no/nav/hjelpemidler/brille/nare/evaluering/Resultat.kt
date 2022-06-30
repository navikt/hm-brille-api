package no.nav.hjelpemidler.brille.nare.evaluering

enum class Resultat {
    JA {
        override infix fun og(annen: Resultat): Resultat = annen
        override infix fun eller(annen: Resultat): Resultat = JA
        override fun ikke(): Resultat = NEI
    },

    NEI {
        override infix fun og(annen: Resultat): Resultat = NEI
        override infix fun eller(annen: Resultat): Resultat = annen
        override fun ikke(): Resultat = JA
    },

    KANSKJE {
        override infix fun og(annen: Resultat): Resultat = when (annen) {
            JA -> KANSKJE
            else -> annen
        }

        override infix fun eller(annen: Resultat): Resultat = when (annen) {
            NEI -> KANSKJE
            else -> annen
        }

        override fun ikke(): Resultat = KANSKJE
    };

    abstract infix fun og(annen: Resultat): Resultat
    abstract infix fun eller(annen: Resultat): Resultat
    abstract fun ikke(): Resultat
}
