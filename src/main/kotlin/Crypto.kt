import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

data class Crypto(var currencies: ArrayList<Amount> = ArrayList()) {
    fun getAmount(type: Currency): Amount {
        var curr = currencies.find { c -> c.type == type }
        if (curr == null) {
            curr = Amount(BigDecimal.ZERO, type)
            currencies.add(curr)
        }
        return curr
    }
}

class CryptoService {
    val events: ArrayList<TransactionEvent> = ArrayList()
    val crypto: Crypto = Crypto()
    val taxes: Taxes = Taxes()

    fun addTransaction(transaction: TransactionEvent) {
        events.add(transaction)
        transaction.process(crypto)
        if (transaction is TradeEvent && transaction.isBuyCryptoEvent()) {
            taxes.unspentBuyEvents.add(UnfinishedBuyTrade(transaction.trade.to.amount, transaction))
        }
        taxes.calculateSellTaxes(transaction)
    }
}

fun number(amount: BigDecimal) = amount.setScale(5, BigDecimal.ROUND_HALF_UP)
fun number(amount: String) = BigDecimal(amount).setScale(5, BigDecimal.ROUND_HALF_UP)

class Amount(amount: BigDecimal, val type: Currency) {
    var amount: BigDecimal = number(amount)

    constructor(amount: String, type: Currency) : this(BigDecimal(amount), type)

    override fun toString(): String {
        return "Amount(amount=${amount}, type=${type})"
    }
}

enum class Currency(val fullName: String, val isCrypto: Boolean) {
    EUR("Euro", false),
    NOK("Norske kroner", false),
    RIPPLE("Ripple", true)
}

data class Trade(val from: Amount, val to: Amount, val fee: BigDecimal) {
    fun getCryptoPrice() = if (to.type.isCrypto)
        from.amount / to.amount
    else
        to.amount / from.amount
}

data class TaxTradeSingle(
        val sellAmount: BigDecimal,
        val sellPrice: Amount,
        val leftOfOriginalTrade: BigDecimal,
        val originalBuyTrade: TradeEvent,
        val gain: BigDecimal)

data class TaxTradeComplete(val totalAmount: BigDecimal, val sellPrice: BigDecimal, val subtrades: ArrayList<TaxTradeSingle> = ArrayList())

fun min(a: BigDecimal, b: BigDecimal): BigDecimal {
    return if (a < b) a else b
}

data class UnfinishedBuyTrade(var amountLeft: BigDecimal, val tradeEvent: TradeEvent)

class Taxes {
    val unspentBuyEvents: LinkedList<UnfinishedBuyTrade> = LinkedList()
    val trades: ArrayList<TaxTradeComplete> = ArrayList()
    val tradeRates: HashMap<CurrencyPair, ArrayList<RateExchange>> = HashMap()

    fun addRateExchange(rateExchange: RateExchange) {
        var exisiting = tradeRates[rateExchange.pair]
        if (exisiting == null) {
            exisiting = ArrayList()
            tradeRates.put(rateExchange.pair, exisiting)
        }
        exisiting.add(rateExchange)
    }
    fun calculateSellTaxes(event: TransactionEvent) {
        if (event is TradeEvent && event.isSellCryptoEvent()) {
            var amountLeft = event.trade.from.amount
            val price = event.trade.getCryptoPrice()
            val taxableTrade = TaxTradeComplete(amountLeft, price)
            trades.add(taxableTrade)
            var buyTrade = unspentBuyEvents.peek()
            while (amountLeft > number(BigDecimal.ZERO)) {
                val partialAmount = min(amountLeft, buyTrade.amountLeft)
                buyTrade.amountLeft -= partialAmount
                amountLeft -= partialAmount
                taxableTrade.subtrades.add(TaxTradeSingle(
                        partialAmount,
                        Amount(price, Currency.EUR),
                        buyTrade.amountLeft,
                        buyTrade.tradeEvent,
                        partialAmount * (price - buyTrade.tradeEvent.trade.getCryptoPrice())
                ))

                if (buyTrade.amountLeft == number(BigDecimal.ZERO)) {
                    unspentBuyEvents.pop()
                    buyTrade = unspentBuyEvents.peek()
                }
            }
        }
    }

    fun getTotalTaxes(): BigDecimal {
        var output = BigDecimal.ZERO
        for (i in 0 until trades.size) {
            for (j in 0 until trades[i].subtrades.size) {
                output += trades[i].subtrades[j].gain
            }
        }
        return output
    }

    fun getTotalTaxes(currency: Currency) : Amount {
        var bigDecimaloutput = number(BigDecimal.ZERO)
        for ( i in 0 until trades.size ) {
            for ( j in 0 until trades[i].subtrades.size ) {

            }
        }



        return Amount("0", currency)
    }
}

fun findCorrectRate(currencyPair: CurrencyPair, date: LocalDate, tradeRates: HashMap<CurrencyPair, ArrayList<RateExchange>> = HashMap()) : RateExchange {
    val rates = tradeRates[currencyPair]
    if (rates == null || rates.isEmpty())
        throw NullPointerException("Couldn't find rates for currencypair: ${currencyPair}")
    val rateForDay = rates.find { r -> r.date == date }
    if (rateForDay != null) {
        return rateForDay
    }

    if (rates.size == 1) {
        return RateExchange(currencyPair, number(rates[0].rate), date)
    }
    if (date <= rates.first().date)
        return RateExchange(currencyPair, rates.first().rate, date)
    if (rates.last().date <= date)
        return RateExchange(currencyPair, rates.last().rate, date)


    for (i in 0 until rates.size - 1) {
        val a = rates[i]
        val b = rates[i + 1]
        if (a.date <= date && date <= b.date) {
            if (a.date == date) {
                return RateExchange(currencyPair, number(a.rate), date)
            } else if (b.date == date) {
                return RateExchange(currencyPair, number(b.rate), date)
            } else {
                return RateExchange(
                        currencyPair,
                        number((a.rate + b.rate) / number("2")),
                        date
                )
            }
        }
    }
    throw NullPointerException("Found nothing to match")
}


interface TransactionEvent {
    fun process(c: Crypto)
}

class DepositFiat(val fiat: Amount, val date: LocalDateTime) : TransactionEvent {
    constructor(fiat: Amount) : this(fiat, LocalDateTime.now())
    override fun process(c: Crypto) {
        c.getAmount(fiat.type).amount += fiat.amount
    }
}

class TradeEvent(val trade: Trade, val date: LocalDateTime) : TransactionEvent {
    constructor(trade: Trade) : this(trade, LocalDateTime.now())
    override fun process(c: Crypto) {
        val exFrom = c.getAmount(trade.from.type)
        if (exFrom.amount < trade.from.amount) {
            throw RuntimeException("Can't perform a trade larger than the original from-amount")
        }
        c.getAmount(trade.from.type).amount -= trade.from.amount
        c.getAmount(trade.to.type).amount += trade.to.amount
    }
    fun isBuyCryptoEvent() = trade.to.type.isCrypto
    fun isSellCryptoEvent() = !trade.to.type.isCrypto
}
enum class CurrencyPair(val from: Currency, val to: Currency) {
    EUR_TO_NOK(Currency.EUR, Currency.NOK)
}
data class RateExchange(val pair: CurrencyPair, val rate: BigDecimal, val date: LocalDate)

class IncomeTax (val incomeLowerLimit: Int, val incomeUpperLimit: Int, val tax: Float)

fun topTaxImproved(income: Int) : Float {
    val incomeTaxes = listOf(
            IncomeTax(169_000, 237_900, 1.4f / 100f),
            IncomeTax(237_900, 598_050, 3.3f / 100f),
            IncomeTax(598_050, 962_050, 12.4f / 100f),
            IncomeTax(962_050, Int.MAX_VALUE, 15.4f / 100f)
    )

    var sum = 0f
    for (i in 0 until incomeTaxes.size) {
        val it = incomeTaxes[i]
        if (income < it.incomeLowerLimit)
            break
        if (income in it.incomeLowerLimit..it.incomeUpperLimit)
            sum += (income - it.incomeLowerLimit) * it.tax
        else
            sum += (it.incomeUpperLimit - it.incomeLowerLimit) * it.tax
    }

    return sum
}

fun topTax(income: Int) : Float {
    val incomeLimit1 = 169_000
    val incomeLimit2 = 237_900
    val incomeLimit3 = 598_050
    val incomeLimit4 = 962_050

    val standardTaxAmount12 = (incomeLimit2 - incomeLimit1) * 1.4 / 100f
    val standardTaxAmount23 = (incomeLimit3 - incomeLimit2) * 3.3 / 100f
    val standardTaxAmount34 = (incomeLimit4 - incomeLimit3) * 12.4 / 100f

    val taxAmount1 = (income - incomeLimit1) * 1.4f / 100f
    val taxAmount2 = (income - incomeLimit2) * 3.3f / 100f
    val taxAmount3 = (income - incomeLimit3) * 12.4f / 100f
    val taxAmount4 = (income - incomeLimit4) * 15.4f / 100f


    return taxAmount1 + taxAmount2 + taxAmount3 + taxAmount4

}