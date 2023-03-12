rootProject.name = "hm-brille-api"

sourceControl {
    gitRepository(uri("https://github.com/navikt/hm-database.git")) {
        producesModule("no.nav.hjelpemidler.database:hm-database")
    }
    gitRepository(uri("https://github.com/navikt/hm-http.git")) {
        producesModule("no.nav.hjelpemidler.http:hm-http")
    }
}
