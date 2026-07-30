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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaultra.app.data.DocumentCategory
import com.vaultra.app.data.DocumentFolder
import com.vaultra.app.data.VaultDatabase
import com.vaultra.app.ui.theme.*
import kotlinx.coroutines.launch
import java.util.UUID

/** Icon keys a document category can be assigned, mapped to a Material icon. */
object DocIcons {
    val MAP: Map<String, ImageVector> = linkedMapOf(
        "personal" to Icons.Filled.Person,
        "education" to Icons.Filled.School,
        "banking" to Icons.Filled.AccountBalance,
        "vehicle" to Icons.Filled.DirectionsCar,
        "employment" to Icons.Filled.Work,
        "medical" to Icons.Filled.MonitorHeart,
        "property" to Icons.Filled.House,
        "folder" to Icons.Filled.Folder,
        "badge" to Icons.Filled.Badge,
        "star" to Icons.Filled.Star,
        "insurance" to Icons.Filled.Shield,
        "family" to Icons.Filled.FamilyRestroom
    )
    fun iconFor(key: String): ImageVector = MAP[key] ?: Icons.Filled.Folder
}

val DocColorPalette = listOf(
    "#E63950", "#4C6FFF", "#2ECC71", "#FF9F45", "#7C5CFC",
    "#22C1C3", "#FFD24C", "#9AA0A6", "#FF6B9D", "#22A8E0"
)

@Composable
fun DocCategoryBadge(category: DocumentCategory?, size: Dp = 28.dp) {
    val color = category?.let { parseHexColor(it.colorHex) } ?: TextDim
    val icon = DocIcons.iconFor(category?.icon ?: "folder")
    Box(modifier = Modifier.size(size).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = category?.name ?: "Uncategorized", tint = Color.White, modifier = Modifier.size(size * 0.55f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentCategoryManagerSheet(db: VaultDatabase, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val categories by db.documentCategoryDao().getAll().collectAsState(initial = emptyList())
    var editTarget by remember { mutableStateOf<DocumentCategory?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<DocumentCategory?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = BgElev) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 26.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Categories", fontSize = 19.sp, fontWeight = FontWeight.Black, color = TextPrimary, modifier = Modifier.weight(1f))
                TextButton(onClick = { editTarget = null; showEditor = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = Accent2, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp)); Text("New", color = Accent2)
                }
            }
            Spacer(Modifier.height(10.dp))
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories, key = { it.id }) { cat ->
                    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(BgCard).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        DocCategoryBadge(cat)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(cat.name, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                            if (cat.isBuiltIn) Text("Built-in", color = TextDim, fontSize = 11.sp)
                        }
                        IconButton(onClick = { editTarget = cat; showEditor = true }) { Icon(Icons.Filled.Edit, contentDescription = "Edit category", tint = TextDim) }
                        IconButton(onClick = { deleteTarget = cat }) { Icon(Icons.Filled.Delete, contentDescription = "Delete category", tint = Accent2) }
                    }
                }
                if (categories.isEmpty()) item { Text("No categories yet. Tap New to add one.", color = TextDim, modifier = Modifier.padding(top = 12.dp)) }
            }
        }
    }

    if (showEditor) {
        DocCategoryEditorDialog(
            existing = editTarget,
            onDismiss = { showEditor = false },
            onSave = { name, color, icon ->
                scope.launch {
                    db.documentCategoryDao().upsert(
                        DocumentCategory(
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
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete \"${cat.name}\"?") },
            text = { Text("Documents in this category will become uncategorized. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val affected = db.documentDao().all().filter { it.categoryId == cat.id }
                        affected.forEach { db.documentDao().upsert(it.copy(categoryId = null)) }
                        db.documentCategoryDao().delete(cat)
                        deleteTarget = null
                        Toast.makeText(context, "Category deleted", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Delete", color = Accent2) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
            containerColor = BgElev
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DocCategoryEditorDialog(existing: DocumentCategory?, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var color by remember { mutableStateOf(existing?.colorHex ?: DocColorPalette.first()) }
    var icon by remember { mutableStateOf(existing?.icon ?: "folder") }

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
                    DocColorPalette.forEach { hex ->
                        val selected = hex.equals(color, ignoreCase = true)
                        Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(parseHexColor(hex)).clickable { color = hex }, contentAlignment = Alignment.Center) {
                            if (selected) Icon(Icons.Filled.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("ICON", fontSize = 11.sp, color = TextDim, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DocIcons.MAP.forEach { (key, vector) ->
                        val selected = key == icon
                        Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(if (selected) parseHexColor(color) else BgCard).clickable { icon = key }, contentAlignment = Alignment.Center) {
                            Icon(vector, contentDescription = key, tint = if (selected) Color.White else TextDim, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onSave(name.trim(), color, icon) }) { Text("Save", color = Accent2, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = BgElev
    )
}

/** Folder manager: create/rename/delete/favorite, flat list showing nesting via indentation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentFolderManagerSheet(db: VaultDatabase, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val folders by db.documentFolderDao().getAll().collectAsState(initial = emptyList())
    var editTarget by remember { mutableStateOf<DocumentFolder?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<DocumentFolder?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun depthOf(folder: DocumentFolder): Int {
        var depth = 0
        var current = folder
        val seen = mutableSetOf(current.id)
        while (current.parentFolderId != null) {
            val parent = folders.find { it.id == current.parentFolderId } ?: break
            if (!seen.add(parent.id)) break
            current = parent
            depth++
        }
        return depth
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = BgElev) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 26.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Folders", fontSize = 19.sp, fontWeight = FontWeight.Black, color = TextPrimary, modifier = Modifier.weight(1f))
                TextButton(onClick = { editTarget = null; showEditor = true }) {
                    Icon(Icons.Filled.CreateNewFolder, contentDescription = null, tint = Accent2, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp)); Text("New", color = Accent2)
                }
            }
            Spacer(Modifier.height(10.dp))
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(folders, key = { it.id }) { folder ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = (depthOf(folder) * 18).dp).clip(RoundedCornerShape(14.dp)).background(BgCard).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Folder, contentDescription = null, tint = if (folder.isFavorite) Warn else Accent2, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(folder.name, color = TextPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        IconButton(onClick = { scope.launch { db.documentFolderDao().upsert(folder.copy(isFavorite = !folder.isFavorite, updatedAt = System.currentTimeMillis())) } }) {
                            Icon(if (folder.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder, contentDescription = "Favorite folder", tint = if (folder.isFavorite) Warn else TextDim)
                        }
                        IconButton(onClick = { editTarget = folder; showEditor = true }) { Icon(Icons.Filled.Edit, contentDescription = "Rename folder", tint = TextDim) }
                        IconButton(onClick = { deleteTarget = folder }) { Icon(Icons.Filled.Delete, contentDescription = "Delete folder", tint = Accent2) }
                    }
                }
                if (folders.isEmpty()) item { Text("No folders yet. Tap New to create one.", color = TextDim, modifier = Modifier.padding(top = 12.dp)) }
            }
        }
    }

    if (showEditor) {
        DocFolderEditorDialog(
            existing = editTarget,
            allFolders = folders,
            onDismiss = { showEditor = false },
            onSave = { name, parentId ->
                scope.launch {
                    db.documentFolderDao().upsert(
                        DocumentFolder(
                            id = editTarget?.id ?: UUID.randomUUID().toString(),
                            name = name, parentFolderId = parentId,
                            isFavorite = editTarget?.isFavorite ?: false,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    showEditor = false
                }
            }
        )
    }

    deleteTarget?.let { folder ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete \"${folder.name}\"?") },
            text = { Text("Documents and subfolders inside will move to the root level. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        db.documentDao().all().filter { it.folderId == folder.id }.forEach { db.documentDao().upsert(it.copy(folderId = null)) }
                        db.documentFolderDao().all().filter { it.parentFolderId == folder.id }.forEach { db.documentFolderDao().upsert(it.copy(parentFolderId = null)) }
                        db.documentFolderDao().delete(folder)
                        deleteTarget = null
                        Toast.makeText(context, "Folder deleted", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Delete", color = Accent2) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
            containerColor = BgElev
        )
    }
}

@Composable
private fun DocFolderEditorDialog(existing: DocumentFolder?, allFolders: List<DocumentFolder>, onDismiss: () -> Unit, onSave: (String, String?) -> Unit) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var parentId by remember { mutableStateOf(existing?.parentFolderId) }
    var menuOpen by remember { mutableStateOf(false) }
    val eligibleParents = allFolders.filter { it.id != existing?.id }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New Folder" else "Rename Folder") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Folder name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Box {
                    OutlinedButton(onClick = { menuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(eligibleParents.find { it.id == parentId }?.name ?: "Root level (no parent)")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Root level (no parent)") }, onClick = { parentId = null; menuOpen = false })
                        eligibleParents.forEach { f ->
                            DropdownMenuItem(text = { Text(f.name) }, onClick = { parentId = f.id; menuOpen = false })
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onSave(name.trim(), parentId) }) { Text("Save", color = Accent2, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = BgElev
    )
}
