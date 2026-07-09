package com.cashguard.app.notification

import com.cashguard.app.data.TxType
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Parses raw bank / telco notification text (SMS alerts surfaced through the
 * notification listener, or push notifications from banking apps) into
 * structured transaction data.
 *
 * Supports every licensed commercial bank, licensed specialised bank and
 * foreign bank operating in Sri Lanka. Message wording differs per bank, e.g.:
 *
 *   BOC:      "Withdrawal Rs 500.00 From A/C No XXXXXXXXXX856.
 *              Balance available Rs 83750.00 - Thank you for banking with BOC"
 *   ComBank:  "Purchase at KEELLS SUPER for LKR 2,500.00 on 09/07/26 from card
 *              ending #4321"
 *   Sampath:  "Your A/C **1234 is debited with LKR 5,000.00 on 09-Jul-26.
 *              Avl Bal: LKR 20,000.00"
 *   Peoples:  "Your Account XXXX1234 has been Credited with Rs. 25,000.00.
 *              Available Balance Rs. 50,250.00"
 *   HNB:      "HNB: LKR 2,000.00 debited from A/C ***1234 on 09/07/26.
 *              Bal: LKR 15,000.00"
 *
 * The parser therefore keys on generic transaction keywords and a family of
 * balance phrases rather than one bank's exact template.
 */
object BankMessageParser {

    data class ParsedTransaction(
        val amount: Double,
        val type: TxType,
        /** null when the alert doesn't state a running balance (common for card purchase alerts) */
        val balanceAfter: Double?,
        val merchant: String,
        val source: String
    )

    enum class BankCategory(val label: String) {
        COMMERCIAL("Licensed Commercial Banks"),
        SPECIALISED("Licensed Specialised Banks"),
        FOREIGN("Foreign Banks"),
        TELCO("Mobile / Telco")
    }

    data class Bank(
        val name: String,
        val category: BankCategory,
        /** lowercase fragments matched against the SMS sender id / notification title */
        val senderTokens: List<String>,
        /** lowercase fragments matched against the message body */
        val bodyTokens: List<String>
    )

    /**
     * Order matters: more specific names must appear before shorter ones that
     * could swallow them (e.g. "Bank of China" before Bank of Ceylon's "BOC",
     * "Indian Overseas Bank" before "Indian Bank").
     */
    val SUPPORTED_BANKS: List<Bank> = listOf(
        // ---- Foreign banks with names that collide with local ones ----
        Bank("Bank of China", BankCategory.FOREIGN,
            listOf("bank of china", "bankofchina"), listOf("bank of china")),
        Bank("Indian Overseas Bank", BankCategory.FOREIGN,
            listOf("indian overseas", "indianoverseas", "iob"), listOf("indian overseas")),

        // ---- Licensed Commercial Banks ----
        Bank("BOC", BankCategory.COMMERCIAL,
            listOf("boc", "bank of ceylon", "bankofceylon"),
            listOf("bank of ceylon", "banking with boc", "boc")),
        Bank("People's Bank", BankCategory.COMMERCIAL,
            listOf("peoples bank", "people's bank", "peoplesbank", "pplsbank"),
            listOf("people's bank", "peoples bank")),
        Bank("Commercial Bank", BankCategory.COMMERCIAL,
            listOf("combank", "commercial bank"), listOf("combank", "commercial bank")),
        Bank("Sampath Bank", BankCategory.COMMERCIAL,
            listOf("sampath"), listOf("sampath")),
        Bank("HNB", BankCategory.COMMERCIAL,
            listOf("hnb", "hatton national"), listOf("hnb", "hatton national")),
        Bank("Seylan Bank", BankCategory.COMMERCIAL,
            listOf("seylan"), listOf("seylan")),
        Bank("DFCC Bank", BankCategory.COMMERCIAL,
            listOf("dfcc"), listOf("dfcc")),
        Bank("Nations Trust Bank", BankCategory.COMMERCIAL,
            listOf("nations trust", "nationstrust", "ntb"), listOf("nations trust", "ntb")),
        Bank("NDB", BankCategory.COMMERCIAL,
            listOf("ndb", "national development bank"), listOf("ndb bank", "ndb")),
        Bank("Pan Asia Bank", BankCategory.COMMERCIAL,
            listOf("pan asia", "panasia", "pabc"), listOf("pan asia", "pabc")),
        Bank("Union Bank", BankCategory.COMMERCIAL,
            listOf("union bank", "unionbank", "ubc"), listOf("union bank")),
        Bank("Cargills Bank", BankCategory.COMMERCIAL,
            listOf("cargills"), listOf("cargills bank")),
        Bank("Amana Bank", BankCategory.COMMERCIAL,
            listOf("amana"), listOf("amana bank", "amana")),

        // ---- Licensed Specialised Banks ----
        Bank("NSB", BankCategory.SPECIALISED,
            listOf("nsb", "national savings"), listOf("national savings", "nsb")),
        Bank("RDB", BankCategory.SPECIALISED,
            listOf("rdb", "regional development"), listOf("regional development bank", "rdb")),
        Bank("SDB Bank", BankCategory.SPECIALISED,
            listOf("sdb", "sanasa"), listOf("sdb bank", "sanasa")),
        Bank("SMIB", BankCategory.SPECIALISED,
            listOf("smib", "state mortgage"), listOf("state mortgage", "smib")),
        Bank("HDFC Bank", BankCategory.SPECIALISED,
            listOf("hdfc"), listOf("hdfc")),
        Bank("Sri Lanka Savings Bank", BankCategory.SPECIALISED,
            listOf("sri lanka savings", "slsb"), listOf("sri lanka savings")),

        // ---- Foreign Banks ----
        Bank("HSBC", BankCategory.FOREIGN, listOf("hsbc"), listOf("hsbc")),
        Bank("Standard Chartered", BankCategory.FOREIGN,
            listOf("standard chartered", "stanchart", "sc bank"),
            listOf("standard chartered", "stanchart")),
        Bank("Citibank", BankCategory.FOREIGN, listOf("citibank", "citi"), listOf("citibank")),
        Bank("Deutsche Bank", BankCategory.FOREIGN, listOf("deutsche"), listOf("deutsche")),
        Bank("State Bank of India", BankCategory.FOREIGN,
            listOf("state bank of india", "sbi"), listOf("state bank of india")),
        Bank("Indian Bank", BankCategory.FOREIGN,
            listOf("indian bank", "indianbank"), listOf("indian bank")),
        Bank("Habib Bank", BankCategory.FOREIGN, listOf("habib"), listOf("habib bank")),
        Bank("MCB Bank", BankCategory.FOREIGN, listOf("mcb"), listOf("mcb bank", "mcb")),
        Bank("Public Bank", BankCategory.FOREIGN,
            listOf("public bank", "publicbank"), listOf("public bank")),

        // ---- Telco wallets ----
        Bank("Hutch/SLT", BankCategory.TELCO,
            listOf("hutch", "sltmobitel", "slt", "mobitel"), listOf("sltmobitel", "hutch"))
    )

    /**
     * An amount preceded OR followed by a currency token:
     *   "Rs 500.00", "Rs. 1,250", "LKR 2,500.00", "2,500.00 LKR"
     */
    private val AMOUNT_PATTERN = Pattern.compile(
        "(?:(?:LKR|SLR|Rs\\.?)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?))" +
            "|(?:([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s*(?:LKR|SLR))",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * The many ways banks phrase the running balance:
     *   "Balance available Rs 28,849.03"   (BOC)
     *   "Available Balance Rs. 50,250.00"  (People's, NSB)
     *   "Avl Bal: LKR 20,000.00"           (Sampath, ComBank)
     *   "Bal: LKR 15,000.00"               (HNB)
     *   "Current/Remaining/New/Total/A/C/Account balance is Rs ..."
     */
    private val BALANCE_PATTERN = Pattern.compile(
        "(?:avl|avail(?:able)?|current|remaining|new|total|a/?c(?:count)?)?\\.?\\s*" +
            "\\bbal(?:ance)?\\s*(?:available)?\\s*(?:is|was|of)?\\s*[:=\\-]?\\s*" +
            "(?:LKR|SLR|Rs\\.?)?\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)",
        Pattern.CASE_INSENSITIVE
    )

    private val CREDIT_KEYWORDS = listOf(
        "credited", "credit received", "deposit", "deposited", "received",
        "remittance", "salary", "refund", "reversal", "transfer from",
        "transferred from", "credit"
    )
    private val DEBIT_KEYWORDS = listOf(
        "debited", "withdrawal", "withdrawn", "wdl", "purchase", "spent",
        "payment", "paid", "pos txn", "pos", "atm", "transfer to",
        "transferred to", "sent to", "bill pay", "debit"
    )

    /** Messages that contain money figures but are not transactions. */
    private val IGNORE_MARKERS = listOf(
        "otp", "one time password", "one-time password", "verification code",
        "do not share", "never share", "promo code"
    )

    /**
     * @param packageName the source app package that posted the notification
     * @param title notification title — for SMS this is usually the sender id
     *              (e.g. "COMBANK", "BOC", "Sampath")
     * @param text notification body text
     */
    fun parse(packageName: String, title: String?, text: String?): ParsedTransaction? {
        if (text.isNullOrBlank()) return null
        val body = text.trim()
        val bodyLower = body.lowercase()
        val titleLower = title?.lowercase()?.trim().orEmpty()

        if (IGNORE_MARKERS.any { bodyLower.contains(it) }) return null

        val bank = SUPPORTED_BANKS.firstOrNull { b -> b.senderTokens.any { titleLower.contains(it) } }
            ?: SUPPORTED_BANKS.firstOrNull { b -> b.bodyTokens.any { bodyLower.contains(it) } }

        // Only process messages that actually look like a bank transaction alert
        val creditIdx = earliestKeyword(bodyLower, CREDIT_KEYWORDS)
        val debitIdx = earliestKeyword(bodyLower, DEBIT_KEYWORDS)
        if (creditIdx == null && debitIdx == null) return null

        // Locate the balance first so its figure isn't mistaken for the amount
        val balanceMatcher = BALANCE_PATTERN.matcher(body)
        var balance: Double? = null
        var balanceStart = -1
        var balanceEnd = -1
        if (balanceMatcher.find()) {
            balance = balanceMatcher.group(1)?.parseAmount()
            balanceStart = balanceMatcher.start()
            balanceEnd = balanceMatcher.end()
        }

        // The transaction amount is the first currency figure outside the balance phrase
        var txAmount: Double? = null
        val amountMatcher = AMOUNT_PATTERN.matcher(body)
        while (amountMatcher.find()) {
            val insideBalance = amountMatcher.start() < balanceEnd && amountMatcher.end() > balanceStart
            if (!insideBalance) {
                txAmount = amountMatcher.group(1)?.parseAmount()
                    ?: amountMatcher.group(2)?.parseAmount()
                if (txAmount != null) break
            }
        }
        val amount = txAmount ?: balance ?: return null

        val type = when {
            debitIdx == null -> TxType.CREDIT
            creditIdx == null -> TxType.DEBIT
            // Both wordings present (e.g. "debited from your A/C ... credited to
            // beneficiary") — the phrase that appears first describes YOUR account.
            creditIdx < debitIdx -> TxType.CREDIT
            else -> TxType.DEBIT
        }

        val source = bank?.name ?: title?.takeIf { it.isNotBlank() } ?: "Bank"
        val merchant = extractMerchant(body, type) ?: source

        return ParsedTransaction(
            amount = amount,
            type = type,
            balanceAfter = balance,
            merchant = merchant,
            source = source
        )
    }

    private fun String.parseAmount(): Double? = replace(",", "").toDoubleOrNull()

    /**
     * Position of the first whole-word occurrence of any keyword, so that
     * e.g. "pos" doesn't fire inside "deposit" or "debit" inside "debited"
     * (which has its own entry).
     */
    private fun earliestKeyword(bodyLower: String, keywords: List<String>): Int? =
        keywords.mapNotNull { kw ->
            Regex("\\b${Regex.escape(kw)}\\b").find(bodyLower)?.range?.first
        }.minOrNull()

    /** Words that end a merchant/beneficiary fragment. */
    private const val MERCHANT_TERMINATORS =
        "(?=\\s+(?:for|on|at|from|using|via|ref|txn|dated)\\b|[.,;\\n]|\\s+(?:LKR|SLR|Rs\\.?)\\s*[0-9]|$)"

    private fun extractMerchant(body: String, type: TxType): String? {
        // "Purchase at KEELLS SUPER for LKR ..." / "POS txn at ODEL on ..."
        // "Loc: KEELLS SUPER" used by some card alerts
        val atPattern = Pattern.compile(
            "(?:\\bat\\b\\s+|\\bLoc\\s*:\\s*)([A-Za-z0-9&.'*_\\-][A-Za-z0-9&.'*_\\- ]{2,39}?)$MERCHANT_TERMINATORS",
            Pattern.CASE_INSENSITIVE
        )
        atPattern.matcher(body).firstGroupOrNull()?.let { candidate ->
            // skip false positives like "at 10.30" (a time, not a merchant)
            if (candidate.any { it.isLetter() }) return candidate
        }

        // Masked account references: "To A/C No XXXXXXXXXX856", "from A/C ***1234"
        val accountPattern = Pattern.compile(
            "(?:to|from)\\s+A/?C\\s+(?:No\\.?\\s+)?([X*0-9]+)",
            Pattern.CASE_INSENSITIVE
        )
        accountPattern.matcher(body).firstGroupOrNull()?.let {
            return "A/C ***" + it.takeLast(3)
        }

        // Credits: "received from JOHN PERERA", debits: "sent/transfer to S PERERA"
        val party = if (type == TxType.CREDIT) "from" else "to"
        val partyPattern = Pattern.compile(
            "\\b$party\\s+([A-Za-z][A-Za-z&.' ]{2,39}?)$MERCHANT_TERMINATORS",
            Pattern.CASE_INSENSITIVE
        )
        partyPattern.matcher(body).firstGroupOrNull()?.let { candidate ->
            // Avoid capturing generic fragments like "your account"
            if (!candidate.lowercase().startsWith("your")) return candidate
        }

        return null
    }

    private fun Matcher.firstGroupOrNull(): String? =
        if (find()) group(1)?.trim()?.takeIf { it.isNotBlank() } else null
}
