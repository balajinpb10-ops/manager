package com.vaultra.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaultra.app.data.TodoCategory
import com.vaultra.app.data.VaultDatabase
import com.vaultra.app.ui.theme.*
import kotlinx.coroutines.launch
import java.util.UUID

/** Icon keys a category can be assigned, and the Material icon each maps to. */
object TodoIcons {
    val MAP: Map<String, ImageVector> = linkedMapOf(
        "personal" to Icons.Filled.Person,
        "work" to Icons.Filled.Work,
        "shopping" to Icons.Filled.ShoppingCart,
        "study" to Icons.Filled.School,
        "health" to Icons.Filled.MonitorHeart,
        "finance" to Icons.Filled.AttachMoney,
        "travel" to Icons.Filled.Flight,
        "home" to Icons.Filled.Home,
        "fitness" to Icons.Filled.FitnessCenter,
        "food" to Icons.Filled.Restaurant,
        "pet" to Icons.Filled.Pets,
        "music" to Icons.Filled.MusicNote,
        "book" to Icons.Filled.Book,
        "event" to Icons.Filled.Event,
        "folder" to Icons.Filled.Folder,
        "star" to Icons.Filled.Star,
        "custom" to Icons.Filled.Label
    )

    fun iconFor(key: String): ImageVector = MAP[key] ?: Icons.Filled.Label
}

/** Fixed swatch palette users pick a category color from. */
val TodoColorPalette = listOf(
    "#E63950", "#4C6FFF", "#FF9F45", "#7C5CFC", "#2ECC71",
    "#FFD24C", "#22C1C3", "#FF6B9D", "#5A6EE0", "#22A8E0", "#9AA0A6"
)

fun parseHexColor(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (_: Exception) {
    Accent
}

/** Small round icon badge used throughout the To-Do module to represent a category. */
@Composable
fun CategoryBadge(category: TodoCategory?, size: androidx.compose.ui.unit.Dp = 28.dp) {
    val color = category?.let { parseHexColor(it.colorHex) } ?: TextDim
    val icon = TodoIcons.iconFor(category?.icon ?: "custom")
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = category?.name ?: "Uncategorized", tint = Color.White, modifier = Modifier.size(size * 0.55f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagerSheet(db: VaultDatabase, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val categories by db.todoCategoryDao().getAll().collectAsState(initial = emptyList())
    var editTarget by remember { mutableStateOf<TodoCategory?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<TodoCategory?>(null) }
    val scope = rememberCoroutineScope()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = BgElev) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 26.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Categories", fontSize = 19.sp, fontWeight = FontWeight.Black, color = TextPrimary, modifier = Modifier.weight(1f))
                TextButton(onClick = { editTarget = null; showEditor = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = Accent2, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New", color = Accent2)
                }
            }
            Spacer(Modifier.height(10.dp))
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories, key = { it.id }) { cat ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(BgCard).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CategoryBadge(cat)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(cat.name, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                            if (cat.isBuiltIn) Text("Built-in", color = TextDim, fontSize = 11.sp)
                        }
                        IconButton(onClick = { editTarget = cat; showEditor = true }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit category", tint = TextDim)
                        }
                        IconButton(onClick = { deleteTarget = cat }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete category", tint = Accent2)
                        }
                    }
                }
                if (categories.isEmpty()) {
                    item { Text("No categories yet. Tap New to add one.", color = TextDim, modifier = Modifier.padding(top = 12.dp)) }
                }
            }
        }
    }

    if (showEditor) {
        CategoryEditorDialog(
            existing = editTarget,
            onDismiss = { showEditor = false },
            onSave = { name, color, icon ->
                scope.launch {
                    db.todoCategoryDao().upsert(
                        TodoCategory(
                            id = editTarget?.id ?: UUID.randomUUID().toString(),
                            name = name, colorHex = color, icon = icon,
                            isBuiltIn = editTarget?.isBuiltIn ?: false,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    showEditor = false
                }
            }
        )
    }

    deleteTarget?.let { cat ->
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete \"${cat.name}\"?") },
            text = { Text("Tasks in this category will become uncategorized. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val affected = db.todoDao().all().filter { it.categoryId == cat.id }
                        affected.forEach { db.todoDao().upsert(it.copy(categoryId = null)) }
                        db.todoCategoryDao().delete(cat)
                        deleteTarget = null
                        Toast.makeText(context, "Category deleted", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Delete", color = Accent2) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryEditorDialog(existing: TodoCategory?, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var color by remember { mutableStateOf(existing?.colorHex ?: TodoColorPalette.first()) }
    var icon by remember { mutableStateOf(existing?.icon ?: "custom") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New Category" else "Edit Category") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it }, label = { Text("Category name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(14.dp))
                Text("COLOR", fontSize = 11.sp, color = TextDim, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TodoColorPalette.forEach { hex ->
                        val selected = hex.equals(color, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(parseHexColor(hex))
                                .clickable { color = hex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) Icon(Icons.Filled.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("ICON", fontSize = 11.sp, color = TextDim, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TodoIcons.MAP.forEach { (key, vector) ->
                        val selected = key == icon
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (selected) parseHexColor(color) else BgCard)
                                .clickable { icon = key },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(vector, contentDescription = key, tint = if (selected) Color.White else TextDim, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onSave(name.trim(), color, icon) }) { Text("Save", color = Accent2, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = BgElev
    )
}
