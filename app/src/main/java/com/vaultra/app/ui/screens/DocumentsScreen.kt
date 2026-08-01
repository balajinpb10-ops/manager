package com.vaultra.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.vaultra.app.data.DocumentCategory
import com.vaultra.app.data.DocumentEntry
import com.vaultra.app.data.DocumentFolder
import com.vaultra.app.data.VaultDatabase
import com.vaultra.app.ui.theme.*
import com.vaultra.app.util.ImageStore
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

private enum class DocSort { NAME, CREATED, MODIFIED, CATEGORY, FOLDER, FAVORITE, RECENT }
private enum class DocViewMode { LIST, GRID }
private enum class DocFilterKind { CATEGORY, FOLDER, FAVORITES, IMAGES, PDFS, RECENT_ADDED, RECENT_UPDATED }
private data class DocFilters(val kind: DocFilterKind? = null, val value: String? = null)

@Composable
fun DocumentsScreen(db: VaultDatabase) {
    val docs by db.documentDao().getAll().collectAsState(initial = emptyList())
    val drafts by db.documentDao().drafts().collectAsState(initial = emptyList())
    val categories by db.documentCategoryDao().getAll().collectAsState(initial = emptyList())
    val folders by db.documentFolderDao().getAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var currentFolderId by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(DocSort.RECENT) }
    var sortAscending by remember { mutableStateOf(false) }
    var viewMode by remember { mutableStateOf(DocViewMode.LIST) }
    var activeFilter by remember { mutableStateOf<DocFilters?>(null) }
    var selectedCategoryChip by remember { mutableStateOf<String?>(null) }

    var showSortMenu by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showCategoryManager by remember { mutableStateOf(false) }
    var showFolderManager by remember { mutableStateOf(false) }
    var showDrafts by remember { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<DocumentEntry?>(null) }
    var detailTarget by remember { mutableStateOf<DocumentEntry?>(null) }

    var selectMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var confirmMultiDelete by remember { mutableStateOf(false) }

    // Back handling: close sheets first, then step out of a subfolder, then exit selection mode.
    BackHandler(enabled = showEditor || detailTarget != null || showFilterSheet || showCategoryManager || showFolderManager || showDrafts || selectMode || currentFolderId != null) {
        when {
            showEditor -> showEditor = false
            detailTarget != null -> detailTarget = null
            showFilterSheet -> showFilterSheet = false
            showCategoryManager -> showCategoryManager = false
            showFolderManager -> showFolderManager = false
            showDrafts -> showDrafts = false
            selectMode -> { selectMode = false; selectedIds = emptySet() }
            currentFolderId != null -> currentFolderId = folders.find { it.id == currentFolderId }?.parentFolderId
        }
    }

    val now = System.currentTimeMillis()
    val visibleDocs = remember(docs, search, sort, sortAscending, activeFilter, selectedCategoryChip, currentFolderId) {
        var list = docs.filter { d ->
            val matchesFolder = d.folderId == currentFolderId
            val matchesSearch = search.isBlank() ||
                d.title.contains(search, true) || d.holderName.contains(search, true) ||
                d.docNumber.contains(search, true) || d.tags.contains(search, true) || d.notes.contains(search, true) ||
                categories.find { it.id == d.categoryId }?.name?.contains(search, true) == true
            val matchesChip = selectedCategoryChip == null || d.categoryId == selectedCategoryChip
            val matchesFilter = when (activeFilter?.kind) {
                null -> true
                DocFilterKind.CATEGORY -> d.categoryId == activeFilter?.value
                DocFilterKind.FOLDER -> d.folderId == activeFilter?.value
                DocFilterKind.FAVORITES -> d.isFavorite
                DocFilterKind.IMAGES -> d.attachmentPaths.any { ImageStore.isImage(it) }
                DocFilterKind.PDFS -> d.attachmentPaths.any { !ImageStore.isImage(it) }
                DocFilterKind.RECENT_ADDED -> isWithinDaysDoc(d.createdAt, now, 7)
                DocFilterKind.RECENT_UPDATED -> isWithinDaysDoc(d.updatedAt, now, 7)
            }
            // Category/folder quick filters (from dashboard chips) bypass the current-folder scoping.
            val scoped = if (activeFilter?.kind == DocFilterKind.FAVORITES || activeFilter?.kind == DocFilterKind.IMAGES || activeFilter?.kind == DocFilterKind.PDFS ||
                activeFilter?.kind == DocFilterKind.RECENT_ADDED || activeFilter?.kind == DocFilterKind.RECENT_UPDATED) true else matchesFolder
            scoped && matchesSearch && matchesChip && matchesFilter
        }
        list = when (sort) {
            DocSort.NAME -> list.sortedBy { it.title.lowercase() }
            DocSort.CREATED -> list.sortedBy { it.createdAt }
            DocSort.MODIFIED -> list.sortedBy { it.updatedAt }
            DocSort.CATEGORY -> list.sortedBy { categories.find { c -> c.id == it.categoryId }?.name ?: "zzz" }
            DocSort.FOLDER -> list.sortedBy { folders.find { f -> f.id == it.folderId }?.name ?: "zzz" }
            DocSort.FAVORITE -> list.sortedByDescending { it.isFavorite }
            DocSort.RECENT -> list.sortedByDescending { it.updatedAt }
        }
        if (!sortAscending) list = list.reversed()
        list
    }

    val subfolders = remember(folders, currentFolderId) { folders.filter { it.parentFolderId == currentFolderId } }
    val currentFolder = remember(folders, currentFolderId) { folders.find { it.id == currentFolderId } }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("DOCUMENTS", fontWeight = FontWeight.Black, fontSize = 22.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                if (drafts.isNotEmpty()) {
                    TextButton(onClick = { showDrafts = true }) {
                        Icon(Icons.Filled.Drafts, contentDescription = null, tint = Warn, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp)); Text("${drafts.size}", color = Warn)
                    }
                }
                IconButton(onClick = { viewMode = if (viewMode == DocViewMode.LIST) DocViewMode.GRID else DocViewMode.LIST }) {
                    Icon(if (viewMode == DocViewMode.LIST) Icons.Filled.GridView else Icons.Filled.ViewList, contentDescription = "Toggle view", tint = TextDim)
                }
            }

            DocumentsDashboard(
                docs = docs, categories = categories, folders = folders,
                onQuickAction = { kind ->
                    when (kind) {
                        "add" -> { editTarget = null; showEditor = true }
                        "search" -> {}
                        "filter" -> showFilterSheet = true
                        "categories" -> showCategoryManager = true
                        "favorites" -> activeFilter = DocFilters(DocFilterKind.FAVORITES)
                    }
                }
            )
            Spacer(Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = search, onValueChange = { search = it },
                    placeholder = { Text("Search title, holder, number, tags…", fontSize = 12.5.sp) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextDim) },
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)
                )
                IconButton(onClick = { showFilterSheet = true }) {
                    Icon(Icons.Filled.FilterList, contentDescription = "Filter", tint = if (activeFilter != null) Accent2 else TextDim)
                }
                Box {
                    IconButton(onClick = { showSortMenu = true }) { Icon(Icons.Filled.Sort, contentDescription = "Sort", tint = TextDim) }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        listOf(
                            DocSort.NAME to "Name", DocSort.CREATED to "Created Date", DocSort.MODIFIED to "Modified Date",
                            DocSort.CATEGORY to "Category", DocSort.FOLDER to "Folder", DocSort.FAVORITE to "Favorite", DocSort.RECENT to "Recently Added"
                        ).forEach { (opt, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { sort = opt; showSortMenu = false })
                        }
                        HorizontalDivider(color = Line)
                        DropdownMenuItem(text = { Text(if (sortAscending) "Ascending ✓" else "Ascending") }, onClick = { sortAscending = true; showSortMenu = false })
                        DropdownMenuItem(text = { Text(if (!sortAscending) "Descending ✓" else "Descending") }, onClick = { sortAscending = false; showSortMenu = false })
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            // Category chip row
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { DocCategoryChip("All", selectedCategoryChip == null, null) { selectedCategoryChip = null } }
                items(categories, key = { it.id }) { cat ->
                    DocCategoryChip(cat.name, selectedCategoryChip == cat.id, cat) { selectedCategoryChip = if (selectedCategoryChip == cat.id) null else cat.id }
                }
                item {
                    Box(modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(BgCard).clickable { showCategoryManager = true }.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Icon(Icons.Filled.Settings, contentDescription = "Manage categories", tint = TextDim, modifier = Modifier.size(16.dp))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            // Folder breadcrumb + subfolder row
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { currentFolderId = null }) { Text("Root", color = if (currentFolder == null) Accent2 else TextDim, fontWeight = FontWeight.Bold) }
                if (currentFolder != null) { Text("›", color = TextDim); Text(currentFolder.name, color = Accent2, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp)) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { showFolderManager = true }) {
                    Icon(Icons.Filled.CreateNewFolder, contentDescription = "Manage folders", tint = TextDim, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp)); Text("Folders", color = TextDim, fontSize = 12.sp)
                }
            }
            if (subfolders.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(subfolders, key = { it.id }) { folder ->
                        Row(
                            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(BgCard).clickable { currentFolderId = folder.id }.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Folder, contentDescription = null, tint = if (folder.isFavorite) Warn else Accent2, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp)); Text(folder.name, color = TextPrimary, fontSize = 12.5.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            if (selectMode) {
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${selectedIds.size} selected", color = TextPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = {
                        scope.launch { visibleDocs.filter { selectedIds.contains(it.id) }.forEach { db.documentDao().upsert(it.copy(isFavorite = true, updatedAt = System.currentTimeMillis())) } }
                    }) { Icon(Icons.Filled.Star, contentDescription = "Favorite selected", tint = Warn) }
                    IconButton(onClick = { confirmMultiDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Delete selected", tint = Accent2) }
                    TextButton(onClick = { selectMode = false; selectedIds = emptySet() }) { Text("Done", color = Accent2) }
                }
            }

            if (visibleDocs.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = TextDim, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(10.dp))
                    Text(if (docs.isEmpty()) "No documents yet — tap + to add one." else "Nothing matches here.", color = TextDim, fontSize = 13.sp)
                }
            } else if (viewMode == DocViewMode.LIST) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                    items(visibleDocs, key = { it.id }) { doc ->
                        DocumentListCard(
                            doc = doc, category = categories.find { it.id == doc.categoryId },
                            selectMode = selectMode, selected = selectedIds.contains(doc.id),
                            onClick = { if (selectMode) selectedIds = if (selectedIds.contains(doc.id)) selectedIds - doc.id else selectedIds + doc.id else detailTarget = doc },
                            onLongClick = { selectMode = true; selectedIds = setOf(doc.id) },
                            onToggleFavorite = { scope.launch { db.documentDao().upsert(doc.copy(isFavorite = !doc.isFavorite, updatedAt = System.currentTimeMillis())) } }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            } else {
                LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                    items(visibleDocs, key = { it.id }) { doc ->
                        DocumentGridCard(
                            doc = doc, category = categories.find { it.id == doc.categoryId },
                            selectMode = selectMode, selected = selectedIds.contains(doc.id),
                            onClick = { if (selectMode) selectedIds = if (selectedIds.contains(doc.id)) selectedIds - doc.id else selectedIds + doc.id else detailTarget = doc },
                            onLongClick = { selectMode = true; selectedIds = setOf(doc.id) }
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(80.dp)) }
                }
            }
        }

        FloatingActionButton(
            onClick = { editTarget = null; showEditor = true },
            containerColor = Accent, contentColor = Color.White, shape = RoundedCornerShape(18.dp),
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
        ) { Icon(Icons.Filled.Add, contentDescription = "Add document") }
    }

    if (showFilterSheet) {
        DocumentFilterSheet(
            categories = categories, folders = folders, current = activeFilter,
            onApply = { activeFilter = it; showFilterSheet = false },
            onDismiss = { showFilterSheet = false }
        )
    }
    if (showCategoryManager) DocumentCategoryManagerSheet(db = db, onDismiss = { showCategoryManager = false })
    if (showFolderManager) DocumentFolderManagerSheet(db = db, onDismiss = { showFolderManager = false })
    if (showDrafts) {
        DocumentDraftsSheet(
            drafts = drafts, onDismiss = { showDrafts = false },
            onRestore = { d -> showDrafts = false; editTarget = d; showEditor = true },
            onDiscard = { d -> scope.launch { db.documentDao().delete(d) } }
        )
    }
    if (showEditor) {
        DocumentEditorSheet(db = db, categories = categories, folders = folders, defaultFolderId = currentFolderId, existing = editTarget, onDismiss = { showEditor = false })
    }
    detailTarget?.let { doc ->
        DocumentDetailSheet(
            doc = doc, category = categories.find { it.id == doc.categoryId }, folder = folders.find { it.id == doc.folderId },
            db = db, onDismiss = { detailTarget = null }, onEdit = { detailTarget = null; editTarget = doc; showEditor = true }
        )
    }
    if (confirmMultiDelete) {
        AlertDialog(
            onDismissRequest = { confirmMultiDelete = false },
            title = { Text("Delete ${selectedIds.size} document(s)?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val toDelete = docs.filter { selectedIds.contains(it.id) }
                        toDelete.forEach { d -> db.documentDao().delete(d); if (d.attachmentPaths.isNotEmpty()) ImageStore.deleteImages(d.attachmentPaths) }
                        selectedIds = emptySet(); selectMode = false; confirmMultiDelete = false
                    }
                }) { Text("Delete", color = Accent2) }
            },
            dismissButton = { TextButton(onClick = { confirmMultiDelete = false }) { Text("Cancel") } },
            containerColor = BgElev
        )
    }
}

private fun isWithinDaysDoc(t: Long, now: Long, days: Int): Boolean {
    val diff = now - t
    return diff in 0..(days.toLong() * 24 * 60 * 60 * 1000)
}

@Composable
private fun DocumentsDashboard(docs: List<DocumentEntry>, categories: List<DocumentCategory>, folders: List<DocumentFolder>, onQuickAction: (String) -> Unit) {
    val favoriteCount = docs.count { it.isFavorite }
    val imageCount = docs.sumOf { d -> d.attachmentPaths.count { ImageStore.isImage(it) } }
    val pdfCount = docs.sumOf { d -> d.attachmentPaths.count { !ImageStore.isImage(it) } }
    val totalBytes = remember(docs) { docs.sumOf { d -> d.attachmentPaths.sumOf { p -> File(p).let { if (it.exists()) it.length() else 0L } } } }

    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = BgCard)) {
        Column(Modifier.padding(16.dp)) {
            Text("Document Vault", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                DashboardStatDoc("Total", docs.size.toString(), TextPrimary)
                DashboardStatDoc("Categories", categories.size.toString(), Accent2)
                DashboardStatDoc("Favorites", favoriteCount.toString(), Warn)
                DashboardStatDoc("Folders", folders.size.toString(), Good)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                DashboardStatDoc("Images", imageCount.toString(), TextPrimary)
                DashboardStatDoc("PDFs", pdfCount.toString(), TextPrimary)
                DashboardStatDoc("Storage", formatBytes(totalBytes), TextPrimary)
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                QuickActionChip("Add", Icons.Filled.Add, Modifier.weight(1f)) { onQuickAction("add") }
                QuickActionChip("Filter", Icons.Filled.FilterList, Modifier.weight(1f)) { onQuickAction("filter") }
                QuickActionChip("Categories", Icons.Filled.Category, Modifier.weight(1f)) { onQuickAction("categories") }
                QuickActionChip("Favorites", Icons.Filled.Star, Modifier.weight(1f)) { onQuickAction("favorites") }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1) "%.1f MB".format(mb) else "%.0f KB".format(kb)
}

@Composable
private fun DashboardStatDoc(label: String, value: String, color: Color) {
    Column {
        Text(label, fontSize = 11.sp, color = TextDim)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun QuickActionChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(12.dp)).background(Bg).clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = Accent2, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = TextDim, fontSize = 10.5.sp)
    }
}

@Composable
private fun DocCategoryChip(label: String, selected: Boolean, category: DocumentCategory?, onClick: () -> Unit) {
    val color = category?.let { parseHexColor(it.colorHex) } ?: Accent2
    Row(
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(if (selected) color.copy(alpha = 0.22f) else BgCard).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (category != null) { Box(Modifier.size(8.dp).clip(CircleShape).background(color)); Spacer(Modifier.width(6.dp)) }
        Text(label, color = if (selected) color else TextDim, fontSize = 12.5.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DocumentListCard(
    doc: DocumentEntry, category: DocumentCategory?, selectMode: Boolean, selected: Boolean,
    onClick: () -> Unit, onLongClick: () -> Unit, onToggleFavorite: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) Accent.copy(alpha = 0.14f) else BgCard),
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selectMode) { Icon(if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, contentDescription = null, tint = if (selected) Accent else TextDim, modifier = Modifier.padding(end = 10.dp)) }
            DocCategoryBadge(category, size = 32.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(doc.title.ifBlank { doc.docType }, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(doc.docType, fontSize = 11.sp, color = TextDim)
                    if (doc.attachmentPaths.isNotEmpty()) Text("· ${doc.attachmentPaths.size} file${if (doc.attachmentPaths.size > 1) "s" else ""}", fontSize = 11.sp, color = TextDim)
                }
            }
            if (!selectMode) {
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(32.dp)) {
                    Icon(if (doc.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder, contentDescription = "Favorite", tint = if (doc.isFavorite) Warn else TextDim, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DocumentGridCard(doc: DocumentEntry, category: DocumentCategory?, selectMode: Boolean, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val firstImage = doc.attachmentPaths.firstOrNull { ImageStore.isImage(it) }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) Accent.copy(alpha = 0.14f) else BgCard),
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(90.dp).clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)).background(Bg), contentAlignment = Alignment.Center) {
                if (firstImage != null) AsyncImage(model = File(firstImage), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else DocCategoryBadge(category, size = 40.dp)
                if (doc.isFavorite) Icon(Icons.Filled.Star, contentDescription = "Favorite", tint = Warn, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(18.dp))
                if (selectMode) Icon(if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, contentDescription = null, tint = if (selected) Accent else Color.White, modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
            }
            Column(Modifier.padding(10.dp)) {
                Text(doc.title.ifBlank { doc.docType }, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(doc.docType, color = TextDim, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentFilterSheet(categories: List<DocumentCategory>, folders: List<DocumentFolder>, current: DocFilters?, onApply: (DocFilters?) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = BgElev) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 26.dp)) {
            Text("Filter Documents", fontSize = 19.sp, fontWeight = FontWeight.Black, color = TextPrimary)
            Spacer(Modifier.height(16.dp))
            FilterOptionRow("Favorites", Icons.Filled.Star) { onApply(DocFilters(DocFilterKind.FAVORITES)) }
            FilterOptionRow("Images", Icons.Filled.Image) { onApply(DocFilters(DocFilterKind.IMAGES)) }
            FilterOptionRow("PDFs", Icons.Filled.PictureAsPdf) { onApply(DocFilters(DocFilterKind.PDFS)) }
            FilterOptionRow("Recently Added", Icons.Filled.NewReleases) { onApply(DocFilters(DocFilterKind.RECENT_ADDED)) }
            FilterOptionRow("Recently Updated", Icons.Filled.Update) { onApply(DocFilters(DocFilterKind.RECENT_UPDATED)) }
            Spacer(Modifier.height(10.dp))
            Text("CATEGORY", fontSize = 11.sp, color = TextDim, fontWeight = FontWeight.Bold)
            categories.forEach { cat -> FilterOptionRow(cat.name, DocIcons.iconFor(cat.icon)) { onApply(DocFilters(DocFilterKind.CATEGORY, cat.id)) } }
            Spacer(Modifier.height(10.dp))
            Text("FOLDER", fontSize = 11.sp, color = TextDim, fontWeight = FontWeight.Bold)
            folders.forEach { f -> FilterOptionRow(f.name, Icons.Filled.Folder) { onApply(DocFilters(DocFilterKind.FOLDER, f.id)) } }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = { onApply(null) }, modifier = Modifier.fillMaxWidth()) { Text("Clear Filter") }
        }
    }
}

@Composable
private fun FilterOptionRow(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Accent2, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = TextPrimary, fontSize = 14.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentDraftsSheet(drafts: List<DocumentEntry>, onDismiss: () -> Unit, onRestore: (DocumentEntry) -> Unit, onDiscard: (DocumentEntry) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = BgElev) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 26.dp)) {
            Text("Drafts", fontSize = 19.sp, fontWeight = FontWeight.Black, color = TextPrimary)
            Spacer(Modifier.height(6.dp))
            Text("Unsaved documents are kept here automatically.", color = TextDim, fontSize = 12.5.sp)
            Spacer(Modifier.height(14.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 420.dp)) {
                items(drafts, key = { it.id }) { draft ->
                    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(BgCard).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(draft.title.ifBlank { "Untitled draft" }, color = TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Last edited " + SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(java.util.Date(draft.updatedAt)), color = TextDim, fontSize = 11.sp)
                        }
                        TextButton(onClick = { onRestore(draft) }) { Text("Restore", color = Accent2) }
                        IconButton(onClick = { onDiscard(draft) }) { Icon(Icons.Filled.Delete, contentDescription = "Discard draft", tint = TextDim) }
                    }
                }
            }
        }
    }
}
