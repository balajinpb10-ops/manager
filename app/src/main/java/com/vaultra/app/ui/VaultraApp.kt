package com.vaultra.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import com.vaultra.app.crypto.CryptoManager
import com.vaultra.app.data.VaultDatabase
import com.vaultra.app.ui.screens.MainScaffold
import com.vaultra.app.ui.screens.SetupScreen
import com.vaultra.app.ui.screens.UnlockScreen

enum class AppPhase { SETUP, UNLOCK, UNLOCKED }

@Composable
fun VaultraApp(activity: FragmentActivity) {
    val cryptoManager = remember { CryptoManager(activity) }
    var phase by remember {
        mutableStateOf(if (cryptoManager.isSetupComplete()) AppPhase.UNLOCK else AppPhase.SETUP)
    }
    var sessionKey by remember { mutableStateOf<ByteArray?>(null) }
    var db by remember { mutableStateOf<VaultDatabase?>(null) }

    fun onUnlocked(key: ByteArray) {
        sessionKey = key
        db = VaultDatabase.getInstance(activity, key)
        phase = AppPhase.UNLOCKED
    }

    fun onLock() {
        VaultDatabase.close()
        db = null
        sessionKey = null
        phase = AppPhase.UNLOCK
    }

    when (phase) {
        AppPhase.SETUP -> SetupScreen(
            cryptoManager = cryptoManager,
            onComplete = { key -> onUnlocked(key) }
        )
        AppPhase.UNLOCK -> UnlockScreen(
            activity = activity,
            cryptoManager = cryptoManager,
            onUnlocked = { key -> onUnlocked(key) }
        )
        AppPhase.UNLOCKED -> {
            val database = db
            if (database == null) {
                phase = AppPhase.UNLOCK
            } else {
                MainScaffold(
                    activity = activity,
                    cryptoManager = cryptoManager,
                    db = database,
                    sessionKey = sessionKey!!,
                    onSessionKeyChanged = { newKey -> sessionKey = newKey },
                    onDbChanged = { newDb -> db = newDb },
                    onLock = { onLock() }
                )
            }
        }
    }
}
