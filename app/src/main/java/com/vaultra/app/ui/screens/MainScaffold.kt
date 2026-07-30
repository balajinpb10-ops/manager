package com.vaultra.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.vaultra.app.crypto.CryptoManager
import com.vaultra.app.data.VaultDatabase
import com.vaultra.app.ui.theme.Accent
import com.vaultra.app.ui.theme.Accent2
import com.vaultra.app.ui.theme.Bg
import com.vaultra.app.ui.theme.BgElev
import com.vaultra.app.ui.theme.TextDim
import com.vaultra.app.ui.theme.TextPrimary

/** SETTINGS (and the other More-hub pages) are reached via the MORE tab now, not shown
 *  directly in the bottom bar — see bug fix #6 ("Navigation Order"). */
enum class Tab { VAULT, FUEL, CARDS, DOCUMENTS, MORE, SETTINGS }

@Composable
fun MainScaffold(
    activity: FragmentActivity,
    cryptoManager: CryptoManager,
    db: VaultDatabase,
    sessionKey: ByteArray,
    onSessionKeyChanged: (ByteArray) -> Unit,
    onDbChanged: (VaultDatabase) -> Unit,
    onLock: () -> Unit
) {
    var tab by remember { mutableStateOf(Tab.VAULT) }
    var showExitConfirm by remember { mutableStateOf(false) }

    // Bug fix #1 (Back Navigation): back goes to the previous screen instead of exiting the
    // app immediately. Only from the Home/Vault tab does back show an exit confirmation.
    // Nested screens (e.g. Documents folder browsing, open sheets) register their own
    // BackHandler further down the tree and consume the press first, before this one runs.
    BackHandler(enabled = true) {
        when {
            tab == Tab.SETTINGS -> tab = Tab.MORE
            tab != Tab.VAULT -> tab = Tab.VAULT
            else -> showExitConfirm = true
        }
    }

    val highlightedTab = if (tab == Tab.SETTINGS) Tab.MORE else tab

    Scaffold(
        containerColor = Bg,
        bottomBar = {
            NavigationBar(containerColor = BgElev) {
                NavigationBarItem(
                    selected = highlightedTab == Tab.VAULT,
                    onClick = { tab = Tab.VAULT },
                    icon = { Icon(Icons.Filled.Shield, contentDescription = "Vault") },
                    label = { Text("VAULT") },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Accent2, selectedTextColor = Accent2, unselectedIconColor = TextDim, unselectedTextColor = TextDim, indicatorColor = BgElev)
                )
                NavigationBarItem(
                    selected = highlightedTab == Tab.FUEL,
                    onClick = { tab = Tab.FUEL },
                    icon = { Icon(Icons.Filled.LocalGasStation, contentDescription = "Fuel") },
                    label = { Text("FUEL") },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Accent2, selectedTextColor = Accent2, unselectedIconColor = TextDim, unselectedTextColor = TextDim, indicatorColor = BgElev)
                )
                NavigationBarItem(
                    selected = highlightedTab == Tab.CARDS,
                    onClick = { tab = Tab.CARDS },
                    icon = { Icon(Icons.Filled.CreditCard, contentDescription = "Cards") },
                    label = { Text("CARDS") },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Accent2, selectedTextColor = Accent2, unselectedIconColor = TextDim, unselectedTextColor = TextDim, indicatorColor = BgElev)
                )
                NavigationBarItem(
                    selected = highlightedTab == Tab.DOCUMENTS,
                    onClick = { tab = Tab.DOCUMENTS },
                    icon = { Icon(Icons.Filled.Badge, contentDescription = "Documents") },
                    label = { Text("DOCS") },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Accent2, selectedTextColor = Accent2, unselectedIconColor = TextDim, unselectedTextColor = TextDim, indicatorColor = BgElev)
                )
                NavigationBarItem(
                    selected = highlightedTab == Tab.MORE,
                    onClick = { tab = Tab.MORE },
                    icon = { Icon(Icons.Filled.MoreHoriz, contentDescription = "More") },
                    label = { Text("MORE") },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Accent2, selectedTextColor = Accent2, unselectedIconColor = TextDim, unselectedTextColor = TextDim, indicatorColor = BgElev)
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    (fadeIn(tween(220)) + scaleIn(initialScale = 0.94f, animationSpec = tween(220))) togetherWith
                        (fadeOut(tween(150)) + scaleOut(targetScale = 1.04f, animationSpec = tween(150)))
                },
                label = "tabTransition"
            ) { targetTab ->
                when (targetTab) {
                    Tab.VAULT -> VaultListScreen(db = db)
                    Tab.FUEL -> FuelScreen(db = db)
                    Tab.CARDS -> CardsScreen(db = db)
                    Tab.DOCUMENTS -> DocumentsScreen(db = db)
                    Tab.MORE -> MoreScreen(
                        db = db,
                        onOpenSettings = { tab = Tab.SETTINGS }
                    )
                    Tab.SETTINGS -> SettingsScreen(
                        activity = activity,
                        cryptoManager = cryptoManager,
                        db = db,
                        sessionKey = sessionKey,
                        onSessionKeyChanged = onSessionKeyChanged,
                        onDbChanged = onDbChanged,
                        onLock = onLock,
                        onBack = { tab = Tab.MORE }
                    )
                }
            }
        }
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text("Exit Vaultra?", color = TextPrimary) },
            text = { Text("Are you sure you want to close the app?") },
            confirmButton = { TextButton(onClick = { activity.finish() }) { Text("Exit", color = Accent, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { showExitConfirm = false }) { Text("Cancel") } },
            containerColor = BgElev
        )
    }
}
