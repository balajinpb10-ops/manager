package com.vaultra.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaultra.app.data.DiaryEntry
import com.vaultra.app.data.VaultDatabase
import com.vaultra.app.ui.theme.*
import kotlinx.coroutines.launch

private enum class ProductivitySection { HOME, TASKS, DIARY }

@Composable
fun ProductivityScreen(db: VaultDatabase, onBack: () -> Unit) {
    var section by remember { mutableStateOf(ProductivitySection.HOME) }
    val tasks by db.todoDao().active().collectAsState(emptyList())
    val diary by db.diaryDao().active().collectAsState(emptyList())
    var addDiary by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Cascading back: sub-section -> this HOME list -> back out to the More hub.
    BackHandler(enabled = true) {
        if (section != ProductivitySection.HOME) section = ProductivitySection.HOME else onBack()
    }

    when (section) {
        ProductivitySection.HOME -> Box(Modifier.fillMaxSize().padding(20.dp)) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Accent2) }
                    Text("PRODUCTIVITY", fontWeight = FontWeight.Black, fontSize = 22.sp, color = TextPrimary)
                }
                Spacer(Modifier.height(16.dp))
                ModuleCard("To-Do Manager", "${tasks.count { !it.isCompleted }} open tasks · categories, priorities and progress", Icons.Default.CheckCircle) { section = ProductivitySection.TASKS }
                Spacer(Modifier.height(12.dp))
                ModuleCard("Private Diary", "${diary.size} encrypted journal entries", Icons.Default.MenuBook) { section = ProductivitySection.DIARY }
                Spacer(Modifier.height(18.dp))
                Text("Everything in these modules is stored locally in your encrypted vault.", color = TextDim, fontSize = 12.sp)
            }
        }
        ProductivitySection.TASKS -> TodoScreen(db = db, onBack = { section = ProductivitySection.HOME })
        ProductivitySection.DIARY -> Box(Modifier.fillMaxSize().padding(20.dp)) {
            DiaryList(diary, { section = ProductivitySection.HOME }) { addDiary = true }
            if (addDiary) DiaryEditor(onDismiss = { addDiary = false }) { title, body, mood ->
                scope.launch { db.diaryDao().upsert(DiaryEntry(java.util.UUID.randomUUID().toString(), title, body, mood, "", "", false, false, System.currentTimeMillis(), System.currentTimeMillis())); addDiary = false }
            }
        }
    }
}

@Composable private fun ModuleCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, click: () -> Unit) =
    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(BgCard).clickable(onClick = click).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Accent2); Spacer(Modifier.width(16.dp)); Column { Text(title, color = TextPrimary, fontWeight = FontWeight.Bold); Text(subtitle, color = TextDim, fontSize = 12.sp) }
    }

@Composable private fun DiaryList(entries: List<DiaryEntry>, back: () -> Unit, add: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TextButton(onClick = back) { Text("‹ Back", color = Accent2) }
        Text("PRIVATE DIARY", fontSize = 22.sp, fontWeight = FontWeight.Black, color = TextPrimary, modifier = Modifier.padding(start = 4.dp))
        if (entries.isEmpty()) Text("Your journal is private and encrypted. Add your first entry.", color = TextDim, modifier = Modifier.padding(top = 28.dp))
        else LazyColumn(Modifier.weight(1f).padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { items(entries, key = { it.id }) { e -> Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(BgCard).padding(14.dp)) { Text(e.title, color = TextPrimary, fontWeight = FontWeight.Bold); Text(e.mood, color = TextDim, fontSize = 11.sp) } } }
        FloatingActionButton(onClick = add, containerColor = Accent, modifier = Modifier.align(Alignment.End)) { Icon(Icons.Default.Add, "Add diary entry") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun DiaryEditor(onDismiss: () -> Unit, save: (String, String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var mood by remember { mutableStateOf("Happy") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = BgElev) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("New Diary Entry", fontSize = 19.sp, fontWeight = FontWeight.Black, color = TextPrimary)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text("Entry") }, modifier = Modifier.fillMaxWidth(), minLines = 4)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(onClick = { if (title.isNotBlank()) save(title, body, mood); onDismiss() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Accent)) { Text("Save") }
            }
        }
    }
}
