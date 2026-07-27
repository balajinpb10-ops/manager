package com.vaultra.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaultra.app.data.Entry
import com.vaultra.app.data.VaultDatabase
import com.vaultra.app.ui.theme.*

private val CATEGORIES = listOf("All", "Login", "Banking", "Social", "Work", "Wifi", "Other")

@Composable
fun VaultListScreen(db: VaultDatabase) {
    val entries by db.entryDao().getAll().collectAsState(initial = emptyList())
    var search by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("All") }
    var addEditTarget by remember { mutableStateOf<Entry?>(null) }
    var showAddEdit by remember { mutableStateOf(false) }
    var detailTarget by remember { mutableStateOf<Entry?>(null) }

    val filtered = remember(entries, search, category) {
        entries.filter { e ->
            (category == "All" || e.category == category) &&
                (search.isBlank() || e.name.contains(search, true) || e.username.contains(search, true))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("MY VAULT", fontWeight = FontWeight.Black, fontSize = 22.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                InfoButton(
                    title = "Your Vault",
                    purpose = "This is where all your saved passwords, logins, and notes live — encrypted on this device only.",
                    howToUse = "Tap the + button to add an entry. Tap any card to view, copy, or edit it. Use the search bar or category chips to filter.",
                    tips = "Swipe through categories to quickly find work, banking, or social logins.",
                    securityNote = "Every entry is encrypted at rest with AES-256. Nothing here is ever sent to a server."
                )
            }
            OutlinedTextField(
                value = search, onValueChange = { search = it },
                placeholder = { Text("Search entries", color = TextDim) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextDim) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(12.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(CATEGORIES) { c ->
                    val selected = c == category
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(if (selected) Accent else BgCard)
                            .clickable { category = c }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(c, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Color.White else TextDim)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            if (filtered.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier.size(70.dp).clip(RoundedCornerShape(20.dp)).background(BgCard),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.Shield, contentDescription = null, tint = TextDim, modifier = Modifier.size(30.dp)) }
                    Spacer(Modifier.height(14.dp))
                    Text(if (entries.isEmpty()) "Vault is empty" else "No matches", color = TextPrimary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (entries.isEmpty()) "Tap the + button to add your first password." else "Try a different search or category.",
                        color = TextDim, fontSize = 12.5.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(filtered, key = { it.id }) { entry ->
                        EntryCard(entry = entry, onClick = { detailTarget = entry })
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }

        FloatingActionButton(
            onClick = { addEditTarget = null; showAddEdit = true },
            containerColor = Accent,
            contentColor = Color.White,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
        ) { Icon(Icons.Filled.Add, contentDescription = "Add entry") }
    }

    if (showAddEdit) {
        AddEditEntrySheet(
            existing = addEditTarget,
            db = db,
            onDismiss = { showAddEdit = false }
        )
    }

    detailTarget?.let { entry ->
        EntryDetailSheet(
            entry = entry,
            db = db,
            onDismiss = { detailTarget = null },
            onEdit = { detailTarget = null; addEditTarget = entry; showAddEdit = true }
        )
    }
}

@Composable
private fun EntryCard(entry: Entry, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(press3D(interaction))
            .clip(RoundedCornerShape(16.dp))
            .background(BgCard)
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(colorForName(entry.name)),
            contentAlignment = Alignment.Center
        ) {
            Text(entry.name.take(2).uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp)
        }
        Spacer(Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.name, fontWeight = FontWeight.Bold, fontSize = 14.5.sp, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                entry.username.ifBlank { "No username" }, fontSize = 12.5.sp, color = TextDim,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextDim)
    }
}
