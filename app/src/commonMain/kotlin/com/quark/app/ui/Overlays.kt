package com.quark.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.quark.app.i18n.strings
import com.quark.app.theme.Glass
import com.quark.app.theme.Quark
import com.quark.app.theme.Radius
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// --- Messages ------------------------------------------------------------------

/**
 * The short notes at the bottom of the window — "Added to queue", "Fail!" —
 * that the Flutter build showed as snack bars and with its state indicator.
 */
class Messages(private val scope: CoroutineScope) {
    class Message(val text: String, val error: Boolean)

    var current by mutableStateOf<Message?>(null)
        private set

    private var timer: Job? = null

    fun show(text: String, error: Boolean = false) {
        val message = Message(text, error)
        current = message
        timer?.cancel()
        timer = scope.launch {
            delay(if (error) 5_000 else 2_600)
            if (current === message) current = null
        }
    }

    fun dismiss() {
        current = null
    }
}

val LocalMessages = staticCompositionLocalOf<Messages> { error("No message host") }

@Composable
fun MessageLayer(messages: Messages, modifier: Modifier = Modifier) {
    val message = messages.current
    // The last message stays drawn while it fades out.
    val last = remember { arrayOfNulls<Messages.Message>(1) }
    if (message != null) last[0] = message
    Box(modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = message != null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            val shown = last[0] ?: return@AnimatedVisibility
            val colors = Quark.colors
            Row(
                Modifier
                    .widthIn(max = 520.dp)
                    .clip(RoundedCornerShape(Radius.control))
                    .background(colors.menu)
                    .clickable(indication = null, interactionSource = null) { messages.dismiss() }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QText(
                    text = shown.text,
                    style = Quark.type.body,
                    color = if (shown.error) colors.danger else colors.text,
                    maxLines = 3,
                )
            }
        }
    }
}

// --- Dialogs -------------------------------------------------------------------

/**
 * One dialog at a time, drawn inside the window over a scrim like the Flutter
 * build's `WarningMessage` and `GlassOverlay`, rather than as a platform window.
 */
class DialogHost {
    private var shown by mutableStateOf<(@Composable (dismiss: () -> Unit) -> Unit)?>(null)

    val isOpen: Boolean get() = shown != null

    fun show(content: @Composable (dismiss: () -> Unit) -> Unit) {
        shown = content
    }

    fun dismiss() {
        shown = null
    }

    @Composable
    fun Layer() {
        val content = shown
        val last = remember { arrayOfNulls<@Composable (dismiss: () -> Unit) -> Unit>(1) }
        if (content != null) last[0] = content
        AnimatedVisibility(visible = content != null, enter = fadeIn(), exit = fadeOut()) {
            val current = last[0] ?: return@AnimatedVisibility
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(indication = null, interactionSource = null) { dismiss() },
                contentAlignment = Alignment.Center,
            ) {
                GlassSurface(
                    shape = RoundedCornerShape(Radius.panel),
                    glass = Glass.Panel,
                    modifier = Modifier
                        .padding(20.dp)
                        .widthIn(max = 460.dp)
                        .fillMaxWidth()
                        // Clicks inside the dialog must not reach the scrim.
                        .clickable(indication = null, interactionSource = null) {},
                ) {
                    Box(Modifier.padding(22.dp)) { current(::dismiss) }
                }
            }
        }
    }
}

val LocalDialogs = staticCompositionLocalOf<DialogHost> { error("No dialog host") }

/** A title, a line of text and the buttons under it. */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirm: String,
    dismiss: () -> Unit,
    danger: Boolean = false,
    onConfirm: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        QText(title, Quark.type.panelTitle)
        if (message.isNotEmpty()) QText(message, Quark.type.body, color = Quark.colors.textSecondary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
            PillButton(strings.cancel, dismiss)
            PillButton(confirm, { dismiss(); onConfirm() }, danger = danger)
        }
    }
}

/** Asks for one line of text: a playlist's name, a description. */
@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    placeholder: String,
    confirm: String,
    dismiss: () -> Unit,
    singleLine: Boolean = true,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val submit = {
        if (text.isNotBlank() || !singleLine) {
            dismiss()
            onConfirm(text.trim())
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        QText(title, Quark.type.panelTitle)
        QTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = placeholder,
            singleLine = singleLine,
            onSubmit = submit,
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
            PillButton(strings.cancel, dismiss)
            PillButton(confirm, submit)
        }
    }
}

/** A list to pick one entry from, such as the playlist to add a track to. */
@Composable
fun ChoiceDialog(
    title: String,
    choices: List<Pair<String, () -> Unit>>,
    dismiss: () -> Unit,
    loading: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            QText(title, Quark.type.panelTitle, Modifier.weight(1f))
            CircleButton(Icons.Filled.Close, dismiss, diameter = 28.dp, iconSize = 18.dp, filled = false)
        }
        if (loading) {
            QText(strings.loading, Quark.type.body, color = Quark.colors.textSecondary)
        }
        Column(
            Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            choices.forEach { (label, action) ->
                ListRow(label, onClick = { dismiss(); action() })
            }
        }
    }
}

/** One line of a menu or a list of choices, with an optional glyph. */
@Composable
fun ListRow(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    danger: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val colors = Quark.colors
    val tint = when {
        !enabled -> colors.textMuted.copy(alpha = 0.4f)
        danger -> colors.danger
        else -> colors.text
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (hovered && enabled) colors.rowHover else Color.Transparent)
            .hoverable(interaction, enabled)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) QIcon(icon, Modifier.size(18.dp), if (danger || !enabled) tint else colors.textSecondary)
        QText(text, Quark.type.body, color = tint, maxLines = 1)
    }
}

// --- Text field -------------------------------------------------------------------

/** The one text field of the interface: a rounded well with a placeholder. */
@Composable
fun QTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    password: Boolean = false,
    leading: ImageVector? = null,
    onSubmit: (() -> Unit)? = null,
) {
    val colors = Quark.colors
    Row(
        modifier
            .clip(RoundedCornerShape(Radius.control))
            .background(colors.control)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (leading != null) QIcon(leading, Modifier.size(18.dp), colors.textMuted)
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                QText(placeholder, Quark.type.body, color = colors.textMuted, maxLines = if (singleLine) 1 else 3)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                minLines = if (singleLine) 1 else 3,
                textStyle = Quark.type.body.copy(color = colors.text),
                cursorBrush = SolidColor(colors.text),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(imeAction = if (onSubmit != null) ImeAction.Done else ImeAction.Default),
                keyboardActions = KeyboardActions(onDone = { onSubmit?.invoke() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty() && singleLine && !password) {
            CircleButton(
                icon = Icons.Filled.Close,
                onClick = { onValueChange("") },
                diameter = 20.dp,
                iconSize = 14.dp,
                filled = false,
            )
        }
    }
}

/** A centred message filling its space: empty lists, errors, loading. */
@Composable
fun Placeholder(
    text: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            QText(
                text,
                Quark.type.body.copy(textAlign = TextAlign.Center),
                color = Quark.colors.textSecondary,
                modifier = Modifier.widthIn(max = 420.dp),
            )
            if (action != null) PillButton(action, onAction)
        }
    }
}

/** Vertical breathing room, spelled once. */
@Composable
fun Gap(height: Int) = Spacer(Modifier.size(height.dp))
