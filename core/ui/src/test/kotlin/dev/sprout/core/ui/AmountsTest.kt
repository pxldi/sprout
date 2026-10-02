/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.ui

import org.junit.After
import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AmountsTest {

    private val original = Locale.getDefault()

    @After fun restore() = Locale.setDefault(original)

    @Test
    fun `a comma reads as the decimal point`() {
        assertEquals(2.5, parseAmount("2,5"))
        assertEquals(2.5, parseAmount(" 2.5 "))
    }

    @Test
    fun `text that is not a non-negative number is no amount`() {
        assertNull(parseAmount("two"))
        assertNull(parseAmount("-1"))
        assertNull(parseAmount(""))
    }

    @Test
    fun `a whole number shows no decimals`() {
        Locale.setDefault(Locale.US)
        assertEquals("20", formatAmount(20.0))
        assertEquals("2.5", formatAmount(2.5))
    }

    @Test
    fun `what the dialog starts from reads back as the same amount`() {
        for (locale in listOf(Locale.US, Locale.GERMANY, Locale.FRANCE)) {
            Locale.setDefault(locale)
            for (amount in listOf(8000.0, 2.5, 1234.75, 0.0)) {
                assertEquals(amount, parseAmount(formatAmount(amount)), "$amount in $locale")
            }
        }
    }
}
