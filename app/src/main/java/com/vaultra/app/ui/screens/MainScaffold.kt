package com.vaultra.app.ui.screens

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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.vaultra.app.crypto.CryptoManager
import com.vaultra.app.data.VaultDatabase
import com.vaultra.app.ui.screens.FuelScreen
import com.vaultra.app.ui.theme.Accent2
import com.vaultra.app.ui.theme.Bg
import com.vaultra.app.ui.theme.BgElev
import com.vaultra.app.ui.theme.TextDim

enum class Tab { VAULT, FUEL, CARDS, DOCUMENTS, SETTINGS, MORE }

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

    Scaffold(
        containerColor = Bg,
        bottomBar = {
            NavigationBar(containerColor = BgElev) {
                NavigationBarItem(
                    selected = tab == Tab.VAULT,
                    onClick = { tab = Tab.VAULT },
                    icon = { Icon(Icons.Filled.Shield, contentDescription = "Vault") },
                    label = { Text("VAULT") },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Accent2, selectedTextColor = Accent2, unselectedIconColor = TextDim, unselectedTextColor = TextDim, indicatorColor = BgElev)
                )
                NavigationBarItem(
                    selected = tab == Tab.FUEL,
                    onClick = { tab = Tab.FUEL },
                    icon = { Icon(Icons.Filled.LocalGasStation, contentDescription = "Fuel") },
                    label = { Text("FUEL") },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Accent2, selectedTextColor = Accent2, unselectedIconColor = TextDim, unselectedTextColor = TextDim, indicatorColor = BgElev)
                )
                NavigationBarItem(
                    selected = tab == Tab.CARDS,
                    onClick = { tab = Tab.CARDS },
                    icon = { Icon(Icons.Filled.CreditCard, contentDescription = "Cards") },
                    label = { Text("CARDS") },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Accent2, selectedTextColor = Accent2, unselectedIconColor = TextDim, unselectedTextColor = TextDim, indicatorColor = BgElev)
                )
                NavigationBarItem(
                    selected = tab == Tab.DOCUMENTS,
                    onClick = { tab = Tab.DOCUMENTS },
                    icon = { Icon(Icons.Filled.Badge, contentDescription = "Documents") },
                    label = { Text("DOCS") },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Accent2, selectedTextColor = Accent2, unselectedIconColor = TextDim, unselectedTextColor = TextDim, indicatorColor = BgElev)
                )
                NavigationBarItem(
                    selected = tab == Tab.MORE,
                    onClick = { tab = Tab.MORE },
                    icon = { Icon(Icons.Filled.MoreHoriz, contentDescription = "More") },
                    label = { Text("MORE") },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Accent2, selectedTextColor = Accent2, unselectedIconColor = TextDim, unselectedTextColor = TextDim, indicatorColor = BgElev)
                )
                NavigationBarItem(
                    selected = tab == Tab.SETTINGS,
                    onClick = { tab = Tab.SETTINGS },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
                    label = { Text("SETTINGS") },
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
                    Tab.MORE -> ProductivityScreen(db = db)
                    Tab.SETTINGS -> SettingsScreen(
                        activity = activity,
                        cryptoManager = cryptoManager,
                        db = db,
                        sessionKey = sessionKey,
                        onSessionKeyChanged = onSessionKeyChanged,
                        onDbChanged = onDbChanged,
                        onLock = onLock
                    )
                }
            }
        }
    }
}
