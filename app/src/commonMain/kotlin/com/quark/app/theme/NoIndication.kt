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
    // A Modifier.Node can belong to only one modifier chain. Indications are
    // created lazily on the first pointer event, so sharing one node appeared
    // to work until a second clickable was hovered after opening a sheet.
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        object : Modifier.Node() {}

    override fun hashCode(): Int = -1

    override fun equals(other: Any?): Boolean = other === this
}
