package com.cashguard.app.notification

import com.cashguard.app.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Sample messages below follow the real alert wording used by each bank.
 * The exact templates vary over time, so the parser is keyword-driven —
 * these tests lock down the important shapes.
 */
class BankMessageParserTest {

    private fun parse(sender: String, body: String) =
        BankMessageParser.parse("com.android.messaging", sender, body)

    // ---------- Licensed Commercial Banks ----------

    @Test
    fun `BOC withdrawal with balance`() {
        val p = parse(
            "BOC",
            "Withdrawal Rs 500.00 From A/C No XXXXXXXXXX856. " +
                "Balance available Rs 83,750.00 - Thank you for banking with BOC"
        )
        assertNotNull(p)
        assertEquals(500.0, p!!.amount, 0.001)
        assertEquals(TxType.DEBIT, p.type)
        assertEquals(83750.0, p.balanceAfter!!, 0.001)
        assertEquals("BOC", p.source)
    }

    @Test
    fun `BOC salary deposit`() {
        val p = parse(
            "BOC",
            "No Book Deposit S/A Rs 28775.00 To A/C No XXXXXXXXXX856. " +
                "Balance available Rs 28,849.03 - Thank you for banking with BOC"
        )
        assertNotNull(p)
        assertEquals(28775.0, p!!.amount, 0.001)
        assertEquals(TxType.CREDIT, p.type)
        assertEquals(28849.03, p.balanceAfter!!, 0.001)
    }

    @Test
    fun `Peoples Bank debit`() {
        val p = parse(
            "PeoplesBank",
            "Dear Customer, Your Account XXXX1234 has been Debited with Rs. 2,500.00 " +
                "on 09.07.2026. Available Balance Rs. 10,250.00"
        )
        assertNotNull(p)
        assertEquals(2500.0, p!!.amount, 0.001)
        assertEquals(TxType.DEBIT, p.type)
        assertEquals(10250.0, p.balanceAfter!!, 0.001)
        assertEquals("People's Bank", p.source)
    }

    @Test
    fun `ComBank card purchase without balance`() {
        val p = parse(
            "COMBANK",
            "Purchase at KEELLS SUPER for LKR 2,500.00 on 09/07/26 from card ending #4321"
        )
        assertNotNull(p)
        assertEquals(2500.0, p!!.amount, 0.001)
        assertEquals(TxType.DEBIT, p.type)
        assertNull(p.balanceAfter)
        assertEquals("KEELLS SUPER", p.merchant)
        assertEquals("Commercial Bank", p.source)
    }

    @Test
    fun `Sampath debit with Avl Bal`() {
        val p = parse(
            "Sampath",
            "Your A/C **1234 is debited with LKR 5,000.00 on 09-Jul-26 at 14:32. " +
                "Avl Bal: LKR 20,000.00"
        )
        assertNotNull(p)
        assertEquals(5000.0, p!!.amount, 0.001)
        assertEquals(TxType.DEBIT, p.type)
        assertEquals(20000.0, p.balanceAfter!!, 0.001)
        assertEquals("Sampath Bank", p.source)
    }

    @Test
    fun `HNB debit with short Bal`() {
        val p = parse(
            "HNB",
            "HNB: LKR 2,000.00 debited from A/C ***1234 on 09/07/26. Bal: LKR 15,000.00"
        )
        assertNotNull(p)
        assertEquals(2000.0, p!!.amount, 0.001)
        assertEquals(TxType.DEBIT, p.type)
        assertEquals(15000.0, p.balanceAfter!!, 0.001)
        assertEquals("HNB", p.source)
    }

    @Test
    fun `Seylan ATM withdrawal`() {
        val p = parse(
            "Seylan",
            "Rs 10,000.00 withdrawn from your account at SEYLAN ATM UNION PLACE. " +
                "Available balance Rs 9,000.00"
        )
        assertNotNull(p)
        assertEquals(10000.0, p!!.amount, 0.001)
        assertEquals(TxType.DEBIT, p.type)
        assertEquals(9000.0, p.balanceAfter!!, 0.001)
        assertEquals("Seylan Bank", p.source)
    }

    @Test
    fun `DFCC credit`() {
        val p = parse(
            "DFCC",
            "LKR 45,000.00 credited to your account 1234567890. Available Balance LKR 51,200.50"
        )
        assertNotNull(p)
        assertEquals(45000.0, p!!.amount, 0.001)
        assertEquals(TxType.CREDIT, p.type)
        assertEquals(51200.50, p.balanceAfter!!, 0.001)
        assertEquals("DFCC Bank", p.source)
    }

    @Test
    fun `NTB transfer out mentions beneficiary credit later`() {
        val p = parse(
            "NTB",
            "Rs 7,500.00 debited from your A/C and credited to beneficiary A/C ***9012. " +
                "Avl Bal Rs 12,000.00"
        )
        assertNotNull(p)
        // "debited" appears before "credited" — this is money leaving MY account
        assertEquals(TxType.DEBIT, p!!.type)
        assertEquals(7500.0, p.amount, 0.001)
        assertEquals(12000.0, p.balanceAfter!!, 0.001)
        assertEquals("Nations Trust Bank", p.source)
    }

    @Test
    fun `NDB payment`() {
        val p = parse(
            "NDB",
            "Payment of Rs. 3,200.00 made from A/C ***4567 on 09/07/2026. Balance Rs. 8,800.00"
        )
        assertNotNull(p)
        assertEquals(3200.0, p!!.amount, 0.001)
        assertEquals(TxType.DEBIT, p.type)
        assertEquals(8800.0, p.balanceAfter!!, 0.001)
        assertEquals("NDB", p.source)
    }

    @Test
    fun `Pan Asia amount-after-currency format`() {
        val p = parse(
            "PanAsia",
            "Your account has been credited with 15,000.00 LKR. Current balance 22,340.00 LKR"
        )
        assertNotNull(p)
        assertEquals(15000.0, p!!.amount, 0.001)
        assertEquals(TxType.CREDIT, p.type)
        assertEquals(22340.0, p.balanceAfter!!, 0.001)
        assertEquals("Pan Asia Bank", p.source)
    }

    @Test
    fun `Cargills and Amana and Union recognised by sender`() {
        listOf(
            "Cargills" to "Cargills Bank",
            "AmanaBank" to "Amana Bank",
            "UnionBank" to "Union Bank"
        ).forEach { (sender, expected) ->
            val p = parse(sender, "Rs 1,000.00 debited from your account. Avl Bal Rs 5,000.00")
            assertNotNull("$sender should parse", p)
            assertEquals(expected, p!!.source)
        }
    }

    // ---------- Licensed Specialised Banks ----------

    @Test
    fun `NSB deposit`() {
        val p = parse(
            "NSB",
            "Your account has been credited with Rs. 25,000.00 on 10.07.2026. " +
                "Account Balance Rs. 50,250.00"
        )
        assertNotNull(p)
        assertEquals(25000.0, p!!.amount, 0.001)
        assertEquals(TxType.CREDIT, p.type)
        assertEquals(50250.0, p.balanceAfter!!, 0.001)
        assertEquals("NSB", p.source)
    }

    @Test
    fun `specialised banks recognised by sender`() {
        listOf(
            "RDB" to "RDB",
            "SDB" to "SDB Bank",
            "SMIB" to "SMIB",
            "HDFC" to "HDFC Bank"
        ).forEach { (sender, expected) ->
            val p = parse(sender, "Rs 2,000.00 debited from your account. Balance Rs 6,000.00")
            assertNotNull("$sender should parse", p)
            assertEquals(expected, p!!.source)
            assertEquals(TxType.DEBIT, p.type)
        }
    }

    // ---------- Foreign Banks ----------

    @Test
    fun `HSBC card transaction`() {
        val p = parse(
            "HSBC",
            "You have spent LKR 4,750.00 at PIZZA HUT on your HSBC credit card ending 7890"
        )
        assertNotNull(p)
        assertEquals(4750.0, p!!.amount, 0.001)
        assertEquals(TxType.DEBIT, p.type)
        assertNull(p.balanceAfter)
        assertEquals("PIZZA HUT", p.merchant)
        assertEquals("HSBC", p.source)
    }

    @Test
    fun `Standard Chartered debit`() {
        val p = parse(
            "StanChart",
            "LKR 12,000.00 has been debited from your account. Available balance is LKR 88,000.00"
        )
        assertNotNull(p)
        assertEquals(12000.0, p!!.amount, 0.001)
        assertEquals(88000.0, p.balanceAfter!!, 0.001)
        assertEquals("Standard Chartered", p.source)
    }

    @Test
    fun `Bank of China is not mistaken for Bank of Ceylon`() {
        val p = parse(
            "Bank of China",
            "CNY equivalent LKR 30,000.00 credited to your account. Balance LKR 95,000.00"
        )
        assertNotNull(p)
        assertEquals("Bank of China", p!!.source)
        assertEquals(TxType.CREDIT, p.type)
    }

    @Test
    fun `Indian Overseas Bank is not mistaken for Indian Bank`() {
        val p = parse("IOB", "Rs 5,500.00 debited from your account. Balance Rs 44,500.00")
        assertNotNull(p)
        assertEquals("Indian Overseas Bank", p!!.source)
    }

    @Test
    fun `remaining foreign banks recognised by sender`() {
        listOf(
            "Citibank" to "Citibank",
            "Deutsche" to "Deutsche Bank",
            "SBI" to "State Bank of India",
            "IndianBank" to "Indian Bank",
            "HabibBank" to "Habib Bank",
            "MCB" to "MCB Bank",
            "PublicBank" to "Public Bank"
        ).forEach { (sender, expected) ->
            val p = parse(sender, "Rs 3,000.00 debited from your account. Avl Bal Rs 9,000.00")
            assertNotNull("$sender should parse", p)
            assertEquals(expected, p!!.source)
        }
    }

    // ---------- Filtering & edge cases ----------

    @Test
    fun `OTP messages are ignored`() {
        assertNull(
            parse("COMBANK", "Your OTP for online purchase of Rs 5,000.00 is 123456. Do not share.")
        )
    }

    @Test
    fun `promotional messages without transaction keywords are ignored`() {
        assertNull(parse("HNB", "Get a personal loan up to Rs 5,000,000 at 12% interest. Apply now!"))
        assertNull(parse("Mom", "See you at dinner tonight"))
    }

    @Test
    fun `amounts with thousands separators parse correctly`() {
        val p = parse(
            "BOC",
            "Withdrawal Rs 1,234,567.89 From A/C No XXXXXXXXXX856. " +
                "Balance available Rs 2,000,000.00"
        )
        assertNotNull(p)
        assertEquals(1234567.89, p!!.amount, 0.001)
        assertEquals(2000000.0, p.balanceAfter!!, 0.001)
    }

    @Test
    fun `balance-only mention is not taken as the amount`() {
        // Amount appears after the balance phrase — must still pick the right figures
        val p = parse(
            "Sampath",
            "Avl Bal: LKR 20,000.00 after purchase of LKR 5,000.00 at ODEL on 09-Jul-26"
        )
        assertNotNull(p)
        assertEquals(5000.0, p!!.amount, 0.001)
        assertEquals(20000.0, p.balanceAfter!!, 0.001)
    }

    @Test
    fun `unknown sender with bank-like transaction still parses with sender as source`() {
        val p = parse("MyNewBank", "Rs 750.00 debited from your account. Balance Rs 4,250.00")
        assertNotNull(p)
        assertEquals("MyNewBank", p!!.source)
    }

    @Test
    fun `unrecognised sender falls back to body-text bank name`() {
        // A bank changes its SMS sender ID, but the message body still names it —
        // the parser should still resolve the correct bank via bodyTokens.
        val p = parse(
            "AlertsSvc-9821",
            "Dear Customer, your Sampath Bank account has been debited with LKR 1,500.00. " +
                "Avl Bal LKR 8,500.00"
        )
        assertNotNull(p)
        assertEquals("Sampath Bank", p!!.source)
    }

    @Test
    fun `completely unrecognised bank still captures the transaction under the raw sender id`() {
        // Neither the sender id nor the body match any known bank — this must
        // not be silently dropped; it's captured under whatever name the SMS used.
        val p = parse(
            "MCB-9924",
            "Rs 12,000.00 has been debited from your account. Available balance Rs 48,000.00"
        )
        assertNotNull(p)
        assertEquals("MCB Bank", p!!.source) // "MCB" sender token still matches here

        val p2 = parse(
            "MyLocalCoopBank",
            "Rs 2,200.00 debited from your account. Balance Rs 9,800.00"
        )
        assertNotNull(p2)
        assertEquals("MyLocalCoopBank", p2!!.source) // no match at all -> raw sender id
    }
}
