package no.nav.hjelpemidler.brille.kafka

// internal class KafkaServiceTest {
//    private val service = KafkaService("test") {
//        MockProducer(true, StringSerializer(), StringSerializer())
//    }
//
//    @Test
//    internal fun `vedtak fattet`() {
//        val sats = SatsType.SATS_1
//        val krav = KravDto(
//            VilkårsgrunnlagDto(
//                orgnr = "067234162",
//                fnrBarn = "07083440346",
//                brilleseddel = Brilleseddel(
//                    høyreSfære = 1.0,
//                    høyreSylinder = 1.0,
//                    venstreSfære = 1.0,
//                    venstreSylinder = 1.0
//                ),
//                bestillingsdato = LocalDate.now(),
//                brillepris = BigDecimal.ZERO,
//                extras = VilkårsgrunnlagExtrasDto("", "")
//            ),
//            "test",
//            "test",
//            "test",
//            "test"
//        )
//        val vedtak = Vedtak<Vilkårsgrunnlag>(
//            fnrBarn = krav.vilkårsgrunnlag.fnrBarn,
//            fnrInnsender = "28102525703",
//            orgnr = krav.vilkårsgrunnlag.orgnr,
//            bestillingsdato = krav.vilkårsgrunnlag.bestillingsdato,
//            brillepris = krav.vilkårsgrunnlag.brillepris,
//            bestillingsreferanse = krav.bestillingsreferanse,
//            vilkårsvurdering = Vilkårsvurdering(mockk(relaxed = true), Evalueringer().ja("test")),
//            behandlingsresultat = Behandlingsresultat.INNVILGET,
//            sats = sats,
//            satsBeløp = sats.beløp,
//            satsBeskrivelse = sats.beskrivelse,
//            beløp = sats.beløp.toBigDecimal()
//        )
//
//        assertDoesNotThrow {
//            service.vedtakFattet(
//                krav = krav,
//                vedtak = vedtak
//            )
//        }
//    }
// }
