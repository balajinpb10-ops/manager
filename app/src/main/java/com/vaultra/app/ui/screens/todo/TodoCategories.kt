package com.vaultra.app.ui.screens.todo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaultra.app.data.TodoCategory
import com.vaultra.app.data.VaultDatabase
import com.vaultra.app.ui.theme.*
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoCategoryManagerScreen(db: VaultDatabase, onBack: () -> Unit) {
    val categories by db.todoCategoryDao().getAll().collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<TodoCategory?>(null) }
    var creating by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<TodoCategory?>(null) }

    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = { Text("CATEGORIES", fontSize = 20.sp, fontWeight = FontWeight.Black, color = TextPrimary) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Accent2) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }, containerColor = Accent) {
                Icon(Icons.Default.Add, "Add category")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(categories, key = { it.id }) { cat ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(BgCard)
                        .clickable { editing = cat }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(38.dp).clip(CircleShape).background(colorFromHex(cat.colorHex).copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(iconForKey(cat.icon), contentDescription = null, tint = colorFromHex(cat.colorHex), modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(cat.name, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        if (cat.isBuiltIn) Text("Built-in", color = TextDim, fontSize = 11.sp)
                    }
                    IconButton(onClick = { editing = cat }) { Icon(Icons.Default.Edit, "Edit", tint = TextDim) }
                    IconButton(onClick = { pendingDelete = cat }) { Icon(Icons.Default.Delete, "Delete", tint = Accent2) }
                }
            }
            if (categories.isEmpty()) {
                item { Text("No categories yet. Tap + to add one.", color = TextDim, modifier = Modifier.padding(top = 24.dp)) }
            }
        }
    }

    if (creating) {
        CategoryEditorDialog(
            initial = null,
            onDismiss = { creating = false },
            onSave = { name, color, icon ->
                scope.launch {
                    db.todoCategoryDao().upsert(
                        TodoCategory(id = UUID.randomUUID().toString(), name = name, colorHex = color, icon = icon, isBuiltIn = false, updatedAt = System.currentTimeMillis())
                    )
                }
                creating = false
            }
        )
    }
    editing?.let { cat ->
        CategoryEditorDialog(
            initial = cat,
            onDismiss = { editing = null },
            onSave = { name, color, icon ->
                scope.launch { db.todoCategoryDao().upsert(cat.copy(name = name, colorHex = color, icon = icon, updatedAt = System.currentTimeMillis())) }
                editing = null
            }
        )
    }
    pendingDelete?.let { cat ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = BgElev,
            title = { Text("Delete \"${cat.name}\"?", color = TextPrimary) },
            text = { Text("Tasks in this category will become uncategorized. This can't be undone.", color = TextDim) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val orphaned = db.todoDao().all().filter { it.categoryId == cat.id }
                        orphaned.forEach { db.todoDao().upsert(it.copy(categoryId = null, updatedAt = System.currentTimeMillis())) }
                        db.todoCategoryDao().delete(cat)
                    }
                    pendingDelete = null
                }) { Text("Delete", color = Accent) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel", color = TextDim) } }
        )
    }
}

@Composable
private fun CategoryEditorDialog(initial: TodoCategory?, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var color by remember { mutableStateOf(initial?.colorHex ?: TodoCategoryColors.first()) }
    var icon by remember { mutableStateOf(initial?.icon ?: "custom") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgElev,
        title = { Text(if (initial == null) "New Category" else "Edit Category", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("Category name") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent2, cursorColor = Accent2)
                )
                Spacer(Modifier.height(14.dp))
                Text("Color", fontSize = 12.sp, color = TextDim, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                LazyVerticalGrid(columns = GridCells.Fixed(6), modifier = Modifier.height(80.dp)) {
                    items(TodoCategoryColors) { hex ->
                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(colorFromHex(hex))
                                .clickable { color = hex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (color == hex) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Icon", fontSize = 12.sp, color = TextDim, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                LazyVerticalGrid(columns = GridCells.Fixed(6), modifier = Modifier.height(140.dp)) {
                    items(TodoCategoryIcons.entries.toList()) { (key, vector) ->
                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(if (icon == key) colorFromHex(color).copy(alpha = 0.3f) else BgCard)
                                .clickable { icon = key },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(vector, contentDescription = key, tint = if (icon == key) colorFromHex(color) else TextDim, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onSave(name.trim(), color, icon) }) { Text("Save", color = Accent2, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextDim) } }
    )
}
