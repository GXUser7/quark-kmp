package com.quark.app.theme

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.Modifier

/**
 * No ripple, no highlight.
 *
 * Controls in this player show press and hover by changing their own fill, and
 * the default ripple would draw Material's circle on top of every one of them.
 */
object NoIndication : IndicationNodeFactory {
    private object Node : Modifier.Node()

    override fun create(interactionSource: InteractionSource): DelegatableNode = Node

    override fun hashCode(): Int = -1

    override fun equals(other: Any?): Boolean = other === this
}
