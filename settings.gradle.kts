rootProject.name = "hm-brille-api"

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/navikt/*")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
        maven {
            url = uri("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
        }
    }
    versionCatalogs {
        create("libs") {
            from("no.nav.hjelpemidler:katalog:25.106.114735")
            library("hmRapidsAndRiversV2Core", "com.github.navikt:hm-rapids-and-rivers-v2-core:202410290928")
            version("spotless", "6.25.0")
        }
    }
}
