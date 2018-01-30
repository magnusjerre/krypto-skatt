import org.junit.Assert
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

class CryptoTest {

    @Test
    fun testInit() {
        val service = CryptoService()
        val deposit = DepositFiat(
                Amount(BigDecimal("1000.00"), Currency.EUR),
                LocalDateTime.of(2018, 1, 1, 12, 0, 0))
        service.addTransaction(deposit)

        Assert.assertEquals(1, service.events.size)
        testEquals(BigDecimal("1000.00"), service.crypto.getAmount(Currency.EUR)?.amount)
    }

    @Test
    fun testDepositMultipleTimes() {
        val service = CryptoService()
        val deposit = DepositFiat(
                Amount(BigDecimal("1000.00"), Currency.EUR),
                LocalDateTime.of(2018, 1, 1, 12, 0, 0))
        val deposit2 = DepositFiat(
                Amount(BigDecimal("200.00"), Currency.EUR),
                LocalDateTime.of(2018, 1, 1, 14, 0, 0))
        service.addTransaction(deposit)
        service.addTransaction(deposit2)

        Assert.assertEquals(2, service.events.size)
        testEquals(BigDecimal("1200.00"), service.crypto.getAmount(Currency.EUR).amount)
    }

    @Test(expected = RuntimeException::class)
    fun testBuyCryptoWithoutFiat() {
        val service = CryptoService()
        val trade = TradeEvent(
                Trade(
                        Amount(BigDecimal("100.00"), Currency.EUR),
                        Amount(BigDecimal("66.56"), Currency.RIPPLE),
                        BigDecimal("0.16")
                ),
                LocalDateTime.now()
        )
        service.addTransaction(trade)
    }

    @Test
    fun testBuyCryptoWithEnoughFiat() {
        val service = CryptoService()
        val deposit = DepositFiat(
                Amount(BigDecimal("1000.00"), Currency.EUR),
                LocalDateTime.of(2018, 1, 1, 12, 0, 0))
        val trade = TradeEvent(
                Trade(
                        Amount(BigDecimal("100.00"), Currency.EUR),
                        Amount(BigDecimal("66.56"), Currency.RIPPLE),
                        BigDecimal("0.16")
                ),
                LocalDateTime.now()
        )
        service.addTransaction(deposit)
        service.addTransaction(trade)

        Assert.assertEquals(2, service.events.size)
        testEquals(BigDecimal("900.00"), service.crypto.getAmount(Currency.EUR).amount)
        testEquals(BigDecimal("66.56"), service.crypto.getAmount(Currency.RIPPLE).amount)

        val trade2 = TradeEvent(
                Trade(
                        Amount(BigDecimal("400.00"), Currency.EUR),
                        Amount(BigDecimal("285.25"), Currency.RIPPLE),
                        BigDecimal("0.64")
                ),
                LocalDateTime.now()
        )
        service.addTransaction(trade2)
        Assert.assertEquals(3, service.events.size)
        testEquals(BigDecimal("500.00"), service.crypto.getAmount(Currency.EUR).amount)
        testEquals(BigDecimal("351.81"), service.crypto.getAmount(Currency.RIPPLE).amount)

        val trade3 = TradeEvent(
                Trade(
                        Amount(BigDecimal("200.00"), Currency.RIPPLE),
                        Amount(BigDecimal("319.488"), Currency.EUR),
                        BigDecimal("0.512")
                ),
                LocalDateTime.now()
        )
        service.addTransaction(trade3)
        Assert.assertEquals(4, service.events.size)
        testEquals(BigDecimal("819.488"), service.crypto.getAmount(Currency.EUR).amount)
        testEquals(BigDecimal("151.81"), service.crypto.getAmount(Currency.RIPPLE).amount)
    }

    @Test
    fun testTradeFromFiatToCrypto() {
        val trade = TradeEvent(
                Trade(
                        Amount(BigDecimal("100.00"), Currency.EUR),
                        Amount(BigDecimal("66.56"), Currency.RIPPLE),
                        BigDecimal("0.16")
                ),
                LocalDateTime.now()
        )
        testEquals(number("100.00") / number("66.56"), trade.trade.getCryptoPrice())
    }

    @Test
    fun testTradeFromCryptoToFiat() {
        val trade = TradeEvent(
                Trade(
                        Amount(BigDecimal("66.56"), Currency.RIPPLE),
                        Amount(BigDecimal("100.00"), Currency.EUR),
                        BigDecimal("0.16")
                ),
                LocalDateTime.now()
        )
        testEquals(number("100.00") / number("66.56"), trade.trade.getCryptoPrice())
    }

    @Test
    fun testTaxesForNoSells() {
        val cryptoservice = CryptoService()
        testEquals(BigDecimal.ZERO, cryptoservice.taxes.getTotalTaxes())
        cryptoservice.addTransaction(DepositFiat(
                Amount(BigDecimal("100.00"), Currency.EUR),
                LocalDateTime.now()
        ))
        testEquals(BigDecimal.ZERO, cryptoservice.taxes.getTotalTaxes())
        cryptoservice.addTransaction(TradeEvent(
                Trade(
                        Amount(BigDecimal("50.00"), Currency.EUR),
                        Amount(BigDecimal("40.00"), Currency.RIPPLE),
                        BigDecimal.ZERO
                ),
                LocalDateTime.now()
        ))
        testEquals(BigDecimal.ZERO, cryptoservice.taxes.getTotalTaxes())
    }

    @Test
    fun testTaxesForOneSale() {
        val cryptoservice = CryptoService()
        testEquals(BigDecimal.ZERO, cryptoservice.taxes.getTotalTaxes())
        cryptoservice.addTransaction(DepositFiat(
                Amount(BigDecimal("100.00"), Currency.EUR),
                LocalDateTime.now()
        ))
        testEquals(BigDecimal.ZERO, cryptoservice.taxes.getTotalTaxes())
        cryptoservice.addTransaction(TradeEvent(
                Trade(
                        Amount(BigDecimal("50.00"), Currency.EUR),
                        Amount(BigDecimal("40.00"), Currency.RIPPLE),
                        BigDecimal.ZERO
                ),
                LocalDateTime.now()
        ))
        testEquals(BigDecimal.ZERO, cryptoservice.taxes.getTotalTaxes())
        cryptoservice.addTransaction(TradeEvent(
                Trade(
                        Amount(BigDecimal("30.00"), Currency.RIPPLE),
                        Amount(BigDecimal("45.00"), Currency.EUR),
                        BigDecimal.ZERO
                ),
                LocalDateTime.now()
        ))
        testEquals(number("30.00") * number("0.25"), cryptoservice.taxes.getTotalTaxes())
    }

    @Test
    fun testTaxesForTwoSales() {
        val cryptoservice = CryptoService()
        testEquals(BigDecimal.ZERO, cryptoservice.taxes.getTotalTaxes())
        cryptoservice.addTransaction(DepositFiat(
                Amount(BigDecimal("100.00"), Currency.EUR),
                LocalDateTime.now()
        ))
        testEquals(BigDecimal.ZERO, cryptoservice.taxes.getTotalTaxes())
        cryptoservice.addTransaction(TradeEvent(
                Trade(
                        Amount(BigDecimal("50.00"), Currency.EUR),
                        Amount(BigDecimal("40.00"), Currency.RIPPLE),
                        BigDecimal.ZERO
                ),
                LocalDateTime.now()
        ))
        testEquals(BigDecimal.ZERO, cryptoservice.taxes.getTotalTaxes())
        cryptoservice.addTransaction(TradeEvent(
                Trade(
                        Amount(BigDecimal("30.00"), Currency.RIPPLE),
                        Amount(BigDecimal("45.00"), Currency.EUR),
                        BigDecimal.ZERO
                ),
                LocalDateTime.now()
        ))
        testEquals(number("30.00") * number("0.25"), cryptoservice.taxes.getTotalTaxes())
        cryptoservice.addTransaction(TradeEvent(
                Trade(
                        Amount(BigDecimal("10.00"), Currency.RIPPLE),
                        Amount(BigDecimal("12.00"), Currency.EUR),
                        BigDecimal.ZERO
                ),
                LocalDateTime.now()
        ))
        testEquals(number("10.00") * number("-0.05") + number("30.00") * number("0.25"), cryptoservice.taxes.getTotalTaxes())
    }

    @Test
    fun testTaxesForMulitpleSales() {
        val cryptoservice = CryptoService()
        cryptoservice.addTransaction(DepositFiat(Amount("1000.00", Currency.EUR)))

        cryptoservice.addTransaction(TradeEvent(Trade(
                        Amount("120", Currency.EUR),
                        Amount("100", Currency.RIPPLE),
                        BigDecimal.ZERO
                )
        ))
        cryptoservice.addTransaction(TradeEvent(Trade(
                Amount("65", Currency.EUR),
                Amount("50", Currency.RIPPLE),
                BigDecimal.ZERO
        )))
        cryptoservice.addTransaction(TradeEvent(Trade(
                Amount("50", Currency.RIPPLE),
                Amount("70", Currency.EUR),
                BigDecimal.ZERO
        )))
        cryptoservice.addTransaction(TradeEvent(Trade(
                Amount("75", Currency.RIPPLE),
                Amount("112.5", Currency.EUR),
                BigDecimal.ZERO
        )))
        cryptoservice.addTransaction(TradeEvent(Trade(
                Amount("25", Currency.RIPPLE),
                Amount("35", Currency.EUR),
                BigDecimal.ZERO
        )))

        val totalTaxes = cryptoservice.taxes.getTotalTaxes()
        testEquals(BigDecimal("32.5").setScale(totalTaxes.scale()), totalTaxes)
    }

    fun testEquals(expected: BigDecimal, actual: BigDecimal) {
        Assert.assertEquals(expected.setScale(actual.scale()), actual)
    }

    @Test(expected = NullPointerException::class)
    fun testFindRatesNonExisting() {
        val rates : HashMap<CurrencyPair, ArrayList<RateExchange>> = HashMap()
        findCorrectRate(CurrencyPair.EUR_TO_NOK, LocalDate.now(), rates)
    }

    @Test(expected = NullPointerException::class)
    fun testFindRatesNonExisting2() {
        val rates : HashMap<CurrencyPair, ArrayList<RateExchange>> = HashMap()
        rates.put(CurrencyPair.EUR_TO_NOK, ArrayList())
        findCorrectRate(CurrencyPair.EUR_TO_NOK, LocalDate.now(), rates)
    }

    @Test
    fun testFindRatesBefore() {
        val rates : HashMap<CurrencyPair, ArrayList<RateExchange>> = HashMap()
        rates.put(CurrencyPair.EUR_TO_NOK, ArrayList())
        rates[CurrencyPair.EUR_TO_NOK]?.add(RateExchange(
            CurrencyPair.EUR_TO_NOK,
                number("9.85"),
                LocalDate.of(2018, 2, 3)
        ))

        val date = LocalDate.of(2018, 1, 1)
        val rate = findCorrectRate(CurrencyPair.EUR_TO_NOK, date, rates)
        Assert.assertEquals(number("9.85"), rate.rate)
        Assert.assertEquals(date, rate.date)
        Assert.assertEquals(CurrencyPair.EUR_TO_NOK, rate.pair)
    }

    @Test
    fun testFindRatesAfter() {
        val rates : HashMap<CurrencyPair, ArrayList<RateExchange>> = HashMap()
        rates.put(CurrencyPair.EUR_TO_NOK, ArrayList())
        rates[CurrencyPair.EUR_TO_NOK]?.add(RateExchange(
                CurrencyPair.EUR_TO_NOK,
                number("9.85"),
                LocalDate.of(2018, 2, 3)
        ))

        val date = LocalDate.of(2018, 3, 1)
        val rate = findCorrectRate(CurrencyPair.EUR_TO_NOK, date, rates)
        Assert.assertEquals(number("9.85"), rate.rate)
        Assert.assertEquals(date, rate.date)
        Assert.assertEquals(CurrencyPair.EUR_TO_NOK, rate.pair)
    }

    @Test
    fun testFindRatesAfterWhenMoreThanOne() {
        val rates : HashMap<CurrencyPair, ArrayList<RateExchange>> = HashMap()
        rates.put(CurrencyPair.EUR_TO_NOK, ArrayList())
        rates[CurrencyPair.EUR_TO_NOK]?.add(RateExchange(
                CurrencyPair.EUR_TO_NOK,
                number("9.85"),
                LocalDate.of(2018, 2, 3)
        ))
        rates[CurrencyPair.EUR_TO_NOK]?.add(RateExchange(
                CurrencyPair.EUR_TO_NOK,
                number("9.9"),
                LocalDate.of(2018, 2, 5)
        ))

        val date = LocalDate.of(2018, 3, 1)
        val rate = findCorrectRate(CurrencyPair.EUR_TO_NOK, date, rates)
        Assert.assertEquals(number("9.9"), rate.rate)
        Assert.assertEquals(date, rate.date)
        Assert.assertEquals(CurrencyPair.EUR_TO_NOK, rate.pair)
    }

    @Test
    fun testFindRatesBeforeWhenMoreThanOne() {
        val rates : HashMap<CurrencyPair, ArrayList<RateExchange>> = HashMap()
        rates.put(CurrencyPair.EUR_TO_NOK, ArrayList())
        rates[CurrencyPair.EUR_TO_NOK]?.add(RateExchange(
                CurrencyPair.EUR_TO_NOK,
                number("9.85"),
                LocalDate.of(2018, 2, 3)
        ))
        rates[CurrencyPair.EUR_TO_NOK]?.add(RateExchange(
                CurrencyPair.EUR_TO_NOK,
                number("9.9"),
                LocalDate.of(2018, 2, 5)
        ))

        val date = LocalDate.of(2018, 1, 1)
        val rate = findCorrectRate(CurrencyPair.EUR_TO_NOK, date, rates)
        Assert.assertEquals(number("9.85"), rate.rate)
        Assert.assertEquals(date, rate.date)
        Assert.assertEquals(CurrencyPair.EUR_TO_NOK, rate.pair)
    }

    @Test
    fun testFindRatesBetwenWhenMoreThanOne() {
        val rates : HashMap<CurrencyPair, ArrayList<RateExchange>> = HashMap()
        rates.put(CurrencyPair.EUR_TO_NOK, ArrayList())
        rates[CurrencyPair.EUR_TO_NOK]?.add(RateExchange(
                CurrencyPair.EUR_TO_NOK,
                number("9.85"),
                LocalDate.of(2018, 2, 3)
        ))
        rates[CurrencyPair.EUR_TO_NOK]?.add(RateExchange(
                CurrencyPair.EUR_TO_NOK,
                number("9.9"),
                LocalDate.of(2018, 2, 5)
        ))

        val date = LocalDate.of(2018, 2, 4)
        val rate = findCorrectRate(CurrencyPair.EUR_TO_NOK, date, rates)
        Assert.assertEquals(number("9.875"), rate.rate)
        Assert.assertEquals(date, rate.date)
        Assert.assertEquals(CurrencyPair.EUR_TO_NOK, rate.pair)
    }

    @Test
    fun testFindRatesStartWhenMoreThanOne() {
        val rates : HashMap<CurrencyPair, ArrayList<RateExchange>> = HashMap()
        rates.put(CurrencyPair.EUR_TO_NOK, ArrayList())
        rates[CurrencyPair.EUR_TO_NOK]?.add(RateExchange(
                CurrencyPair.EUR_TO_NOK,
                number("9.85"),
                LocalDate.of(2018, 2, 3)
        ))
        rates[CurrencyPair.EUR_TO_NOK]?.add(RateExchange(
                CurrencyPair.EUR_TO_NOK,
                number("9.9"),
                LocalDate.of(2018, 2, 5)
        ))

        val date = LocalDate.of(2018, 2, 3)
        val rate = findCorrectRate(CurrencyPair.EUR_TO_NOK, date, rates)
        Assert.assertEquals(number("9.85"), rate.rate)
        Assert.assertEquals(date, rate.date)
        Assert.assertEquals(CurrencyPair.EUR_TO_NOK, rate.pair)
    }

    @Test
    fun testFindRatesEndWhenMoreThanOne() {
        val rates : HashMap<CurrencyPair, ArrayList<RateExchange>> = HashMap()
        rates.put(CurrencyPair.EUR_TO_NOK, ArrayList())
        rates[CurrencyPair.EUR_TO_NOK]?.add(RateExchange(
                CurrencyPair.EUR_TO_NOK,
                number("9.85"),
                LocalDate.of(2018, 2, 3)
        ))
        rates[CurrencyPair.EUR_TO_NOK]?.add(RateExchange(
                CurrencyPair.EUR_TO_NOK,
                number("9.9"),
                LocalDate.of(2018, 2, 5)
        ))

        val date = LocalDate.of(2018, 2, 5)
        val rate = findCorrectRate(CurrencyPair.EUR_TO_NOK, date, rates)
        Assert.assertEquals(number("9.9"), rate.rate)
        Assert.assertEquals(date, rate.date)
        Assert.assertEquals(CurrencyPair.EUR_TO_NOK, rate.pair)
    }

    @Test
    fun test() {
//        println(topTax(650_000))
        println(topTaxImproved(1_000_000))
    }
}