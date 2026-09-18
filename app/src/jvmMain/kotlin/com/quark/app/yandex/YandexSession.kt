package com.quark.app.yandex

import com.quark.core.settings.SettingsStore
import com.quark.core.settings.StreamQuality
import com.quark.network.yandex.YandexClient
import com.quark.network.yandex.YandexException
import com.quark.network.yandex.YandexMusic
import com.quark.network.yandex.YandexQuality
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Where the Yandex account stands, for the UI to show and act on. */
sealed interface YandexState {
    data object SignedOut : YandexState
    data object SigningIn : YandexState
    data class SignedIn(val displayName: String, val login: String) : YandexState

    /** The token was refused; the user has to sign in again. */
    data class Failed(val message: String) : YandexState
}

/**
 * The Yandex Music account: one client, signed in from the stored token.
 *
 * The Dart build kept this on a singleton with a dozen static caches reachable
 * from anywhere, including from the track model. Here the session is an object
 * the application owns and hands to whatever needs it.
 */
class YandexSession(
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
    http: HttpClient = YandexClient.defaultHttpClient(),
) {
    private val client = YandexClient(http, token = settings.current.yandex.token)

    /** Present only while signed in; every call needs the account id. */
    @Volatile
    var api: YandexMusic? = null
        private set

    private val _state = MutableStateFlow<YandexState>(YandexState.SignedOut)
    val state: StateFlow<YandexState> = _state.asStateFlow()

    val quality: YandexQuality
        get() = when (settings.current.yandex.quality) {
            StreamQuality.Normal -> YandexQuality.Normal
            StreamQuality.High -> YandexQuality.High
            StreamQuality.Lossless -> YandexQuality.Lossless
        }

    init {
        if (settings.current.yandex.isAuthorised) scope.launch { signIn(client.token) }
    }

    /**
     * Signs in with [token] and remembers who it belongs to.
     *
     * The account details are stored as well as the token because the Dart
     * build showed them in the sidebar before the first request came back, and
     * a cold start with no network should still say whose account it is.
     */
    suspend fun signIn(token: String): YandexState {
        if (token.isEmpty()) {
            _state.value = YandexState.SignedOut
            return _state.value
        }

        _state.value = YandexState.SigningIn
        client.token = token

        return try {
            val music = YandexMusic(client)
            val status = music.authorise()
            api = music

            settings.update {
                it.copy(
                    yandex = it.yandex.copy(
                        token = token,
                        uid = status.account.uid,
                        login = status.account.login,
                        displayName = status.account.displayName,
                        fullName = status.account.fullName,
                        email = status.account.email,
                    )
                )
            }

            YandexState.SignedIn(
                displayName = status.account.displayName.ifBlank { status.account.login },
                login = status.account.login,
            ).also { _state.value = it }
        } catch (e: YandexException) {
            api = null
            YandexState.Failed(e.message ?: "Sign-in failed").also { _state.value = it }
        } catch (e: Exception) {
            api = null
            YandexState.Failed(e.message ?: "Sign-in failed").also { _state.value = it }
        }
    }

    fun signOut() {
        api = null
        client.token = ""
        _state.value = YandexState.SignedOut
        settings.update { it.copy(yandex = com.quark.core.settings.YandexSettings()) }
    }
}
