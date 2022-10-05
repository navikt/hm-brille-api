package no.nav.hjelpemidler.brille.aareg.model

data class ArbeidsforholdDto(
    val navArbeidsforholdId: Long? = null,
    val eksternArbeidsforholdId: String? = null,
    var type: String? = null,
    val sistBekreftet: String? = null,
    val arbeidsgiver: ArbeidsgiverDto? = null,
    val opplysningspliktigarbeidsgiver: ArbeidsgiverDto? = null,
    val ansettelsesperiode: AnsettelsesperiodeDto? = null,
    val utenlandsopphold: List<UtenlandsoppholdDto>? = null,
    val permisjonPermittering: List<PermisjonPermitteringDto>? = null,
    val arbeidsavtaler: List<ArbeidsavtaleDto>? = null,
    val ansettelsesform: String? = null,
    val antallTimerForTimelonnet: List<AntallTimerForTimeloennetDto>? = null,
    val antallTimerPrUke: Double? = null,
    var arbeidstidsordning: String? = null,
    val sisteStillingsendring: String? = null,
    val sisteLoennsendring: String? = null,
    val stillingsprosent: Double? = null,
    var yrke: String? = null,
    var fartsomraade: String? = null,
    var skipsregister: String? = null,
    var skipstype: String? = null
)
