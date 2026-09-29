package com.quark.app.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Sync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quark.app.browse.TopBar
import com.quark.app.i18n.strings
import com.quark.app.library.SyncState
import com.quark.app.shell.Shell
import com.quark.app.shell.shell
import com.quark.app.theme.Glass
import com.quark.app.theme.Quark
import com.quark.app.theme.Radius
import com.quark.app.ui.Gap
import com.quark.app.ui.GlassSurface
import com.quark.app.ui.PillButton
import com.quark.app.ui.QText
import com.quark.app.ui.QTextField
import com.quark.app.ui.bottomInset
import com.quark.network.quark.QuarkProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Which of the signed-out forms is showing. */
private enum class Form { Login, Register, Forgot }

/**
 * The quark account (`auth.dart`, `forgot_pass_page.dart`): signing in or up,
 * getting back in without the password, and once in, the profile, e-mail
 * verification, the password and the playlist sync.
 */
@Composable
fun AccountScreen() {
    val shell = shell
    val s = strings
    val settings by shell.app.settings.settings.collectAsState()

    Column(Modifier.fillMaxSize()) {
        TopBar(s.quarkAccount, onBack = { shell.navigator.pop() })
        Box(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = bottomInset + 16.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            GlassSurface(
                RoundedCornerShape(Radius.panel),
                Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(20.dp),
                Glass.Card,
            ) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (settings.account.isSignedIn) Profile(shell) else SignedOut(shell)
                }
            }
        }
    }
}

/** Runs [block], showing [busy] meanwhile and the failure's message after. */
private fun CoroutineScope.submit(
    setBusy: (Boolean) -> Unit,
    setError: (String?) -> Unit,
    fallback: String,
    block: suspend () -> Unit,
) {
    setBusy(true)
    setError(null)
    launch {
        try {
            block()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            setError(e.message ?: fallback)
        } finally {
            setBusy(false)
        }
    }
}

@Composable
private fun SignedOut(shell: Shell) {
    val s = strings
    val account = shell.app.integrations.account
    val scope = rememberCoroutineScope()
    var form by remember { mutableStateOf(Form.Login) }
    var email by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var codeSent by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val afterSignIn: suspend () -> Unit = {
        shell.app.syncAccount()
        shell.cache.forgetAll("")
    }

    QText(
        when (form) {
            Form.Login -> s.login
            Form.Register -> s.register
            Form.Forgot -> s.resetPassword
        },
        Quark.type.heading,
    )
    QText(s.accountHint, Quark.type.body, color = Quark.colors.textSecondary)

    when (form) {
        Form.Login -> {
            QTextField(email, { email = it }, Modifier.fillMaxWidth(), placeholder = s.emailOrUsername)
            QTextField(password, { password = it }, Modifier.fillMaxWidth(), placeholder = s.password, password = true)
            PillButton(
                if (busy) s.loading else s.login,
                {
                    scope.submit({ busy = it }, { error = it }, s.somethingWentWrong) {
                        account.login(email.trim(), password)
                        afterSignIn()
                    }
                },
                icon = Icons.AutoMirrored.Filled.Login,
                accent = true,
                enabled = email.isNotBlank() && password.isNotBlank() && !busy,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Form.Register -> {
            QTextField(email, { email = it }, Modifier.fillMaxWidth(), placeholder = s.email)
            QTextField(username, { username = it }, Modifier.fillMaxWidth(), placeholder = s.username)
            QTextField(password, { password = it }, Modifier.fillMaxWidth(), placeholder = s.password, password = true)
            PillButton(
                if (busy) s.loading else s.register,
                {
                    scope.submit({ busy = it }, { error = it }, s.somethingWentWrong) {
                        account.register(email.trim(), password, username.trim())
                        if (!account.isLoggedIn) account.login(email.trim(), password)
                        afterSignIn()
                    }
                },
                accent = true,
                enabled = email.isNotBlank() && username.isNotBlank() && password.length >= 6 && !busy,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Form.Forgot -> {
            QTextField(email, { email = it }, Modifier.fillMaxWidth(), placeholder = s.email)
            if (!codeSent) {
                PillButton(
                    if (busy) s.loading else s.sendCode,
                    {
                        scope.submit({ busy = it }, { error = it }, s.somethingWentWrong) {
                            account.forgotPassword(email.trim())
                            codeSent = true
                        }
                    },
                    accent = true,
                    enabled = email.isNotBlank() && !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                QText(s.codeSent, Quark.type.label, color = Quark.colors.textSecondary)
                QTextField(code, { code = it }, Modifier.fillMaxWidth(), placeholder = s.code)
                QTextField(password, { password = it }, Modifier.fillMaxWidth(), placeholder = s.newPassword, password = true)
                PillButton(
                    if (busy) s.loading else s.resetPassword,
                    {
                        scope.submit({ busy = it }, { error = it }, s.somethingWentWrong) {
                            account.resetPassword(email.trim(), code.trim(), password)
                            shell.messages.show(s.passwordChanged)
                            codeSent = false
                            form = Form.Login
                        }
                    },
                    accent = true,
                    enabled = code.isNotBlank() && password.length >= 6 && !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    error?.let { QText(it, Quark.type.label, color = Quark.colors.danger) }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (form != Form.Login) PillButton(s.haveAccount, { form = Form.Login; error = null }, height = 36.dp)
        if (form != Form.Register) PillButton(s.noAccount, { form = Form.Register; error = null }, height = 36.dp)
        if (form == Form.Login) PillButton(s.forgotPassword, { form = Form.Forgot; error = null }, height = 36.dp)
    }
}

@Composable
private fun Profile(shell: Shell) {
    val s = strings
    val app = shell.app
    val account = app.integrations.account
    val settings by app.settings.settings.collectAsState()
    val sync by app.cloudSync.state.collectAsState()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var code by remember { mutableStateOf("") }
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var generation by remember { mutableStateOf(0) }
    val profile by produceState<QuarkProfile?>(null, generation) {
        value = runCatching { account.me() }.getOrNull()
    }

    QText(s.signedInAs(settings.account.username.ifBlank { settings.account.email }), Quark.type.heading)
    profile?.email?.let { QText(it, Quark.type.body, color = Quark.colors.textSecondary) }

    if (profile?.emailVerified == false) {
        Gap(4)
        QText(s.verifyEmail, Quark.type.panelTitle)
        QText(s.verifyEmailHint, Quark.type.label, color = Quark.colors.textMuted)
        QTextField(code, { code = it }, Modifier.fillMaxWidth(), placeholder = s.code)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(s.verifyEmail, {
                scope.submit({ busy = it }, { error = it }, s.somethingWentWrong) {
                    account.verifyEmail(code.trim())
                    generation++
                }
            }, accent = true, enabled = code.isNotBlank() && !busy)
            PillButton(s.sendCode, {
                scope.submit({ busy = it }, { error = it }, s.somethingWentWrong) {
                    account.resendVerificationCode()
                    shell.messages.show(s.codeSent)
                }
            }, enabled = !busy)
        }
    }

    Gap(4)
    QText(s.syncPlaylists, Quark.type.panelTitle)
    QText(
        when (val current = sync) {
            SyncState.Idle -> s.syncPlaylistsHint
            SyncState.Running -> s.syncing
            is SyncState.Done -> s.synced(current.downloaded, current.uploaded)
            is SyncState.Failed -> current.message
        },
        Quark.type.label,
        color = if (sync is SyncState.Failed) Quark.colors.danger else Quark.colors.textMuted,
    )
    PillButton(s.syncNow, { shell.attempt { app.cloudSync.run() } }, icon = Icons.Filled.Sync, enabled = sync != SyncState.Running)

    Gap(4)
    QText(s.changePassword, Quark.type.panelTitle)
    QTextField(oldPassword, { oldPassword = it }, Modifier.fillMaxWidth(), placeholder = s.currentPassword, password = true)
    QTextField(newPassword, { newPassword = it }, Modifier.fillMaxWidth(), placeholder = s.newPassword, password = true)
    PillButton(s.changePassword, {
        scope.submit({ busy = it }, { error = it }, s.somethingWentWrong) {
            account.changePassword(oldPassword, newPassword)
            oldPassword = ""
            newPassword = ""
            shell.messages.show(s.passwordChanged)
        }
    }, enabled = oldPassword.isNotBlank() && newPassword.length >= 6 && !busy)

    error?.let { QText(it, Quark.type.label, color = Quark.colors.danger) }

    Gap(8)
    PillButton(s.logout, {
        scope.submit({ busy = it }, { error = it }, s.somethingWentWrong) {
            account.logout()
            app.settings.update { it.copy(vk = it.vk.copy(userId = "")) }
        }
    }, icon = Icons.AutoMirrored.Filled.Logout, danger = true, modifier = Modifier.fillMaxWidth())
}
