package com.quark.app.theme

import androidx.compose.foundation.interaction.MutableInteractionSource
import kotlin.test.Test
import kotlin.test.assertNotSame

class NoIndicationTest {

    @Test
    fun creates_a_fresh_node_for_each_clickable() {
        val first = NoIndication.create(MutableInteractionSource())
        val second = NoIndication.create(MutableInteractionSource())

        assertNotSame(first, second)
    }
}
