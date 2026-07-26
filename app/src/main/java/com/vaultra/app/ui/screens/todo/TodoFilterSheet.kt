package com.vaultra.app.ui.screens.todo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaultra.app.data.TodoCategory
import com.vaultra.app.ui.theme.*
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoFilterSheet(
    categories: List<TodoCategory>,
    current: TodoFilters,
    onApply: (TodoFilters) -> Unit,
    onDismiss: () -> Unit
) {
    var categoryId by remember { mutableStateOf(current.categoryId) }
    var priority by remember { mutableStateOf(current.priority) }
    var status by remember { mutableStateOf(current.status) }
    var dateRange by remember { mutableStateOf(dateRangeLabel(current.dateFrom)) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = BgElev) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("Filter Tasks", fontSize = 18.sp, fontWeight = FontWeight.Black, color = TextPrimary)
            Spacer(Modifier.height(18.dp))

            Text("CATEGORY", fontSize = 11.sp, color = TextDim, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(selected = categoryId == null, onClick = { categoryId = null }, label = { Text("Any", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Accent.copy(alpha = 0.25f), selectedLabelColor = Accent2, containerColor = BgCard, labelColor = TextDim))
                }
                items(categories, key = { it.id }) { cat ->
                    FilterChip(
                        selected = categoryId == cat.id, onClick = { categoryId = cat.id },
                        leadingIcon = { Icon(iconForKey(cat.icon), null, tint = colorFromHex(cat.colorHex), modifier = Modifier.size(14.dp)) },
                        label = { Text(cat.name, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colorFromHex(cat.colorHex).copy(alpha = 0.22f), selectedLabelColor = colorFromHex(cat.colorHex), containerColor = BgCard, labelColor = TextDim)
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Text("PRIORITY", fontSize = 11.sp, color = TextDim, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = priority == null, onClick = { priority = null }, label = { Text("Any", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Accent.copy(alpha = 0.25f), selectedLabelColor = Accent2, containerColor = BgCard, labelColor = TextDim))
                AllPriorities.forEach { p ->
                    FilterChip(
                        selected = priority == p, onClick = { priority = p },
                        label = { Text(priorityLabel(p), fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = priorityColor(p).copy(alpha = 0.22f), selectedLabelColor = priorityColor(p), containerColor = BgCard, labelColor = TextDim)
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Text("STATUS", fontSize = 11.sp, color = TextDim, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(selected = status == null, onClick = { status = null }, label = { Text("Any", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Accent.copy(alpha = 0.25f), selectedLabelColor = Accent2, containerColor = BgCard, labelColor = TextDim))
                }
                items(AllStatuses) { s ->
                    FilterChip(
                        selected = status == s, onClick = { status = s },
                        label = { Text(s, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = statusColor(s).copy(alpha = 0.22f), selectedLabelColor = statusColor(s), containerColor = BgCard, labelColor = TextDim)
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Text("DUE DATE", fontSize = 11.sp, color = TextDim, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Any", "Today", "This Week", "Overdue").forEach { label ->
                    FilterChip(
                        selected = dateRange == label, onClick = { dateRange = label },
                        label = { Text(label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Accent.copy(alpha = 0.22f), selectedLabelColor = Accent2, containerColor = BgCard, labelColor = TextDim)
                    )
                }
            }

            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { categoryId = null; priority = null; status = null; dateRange = "Any"; onApply(TodoFilters()) },
                    modifier = Modifier.weight(1f)
                ) { Text("Clear") }
                Button(
                    onClick = {
                        val (from, to) = dateRangeMillis(dateRange)
                        onApply(TodoFilters(categoryId = categoryId, priority = priority, status = status, dateFrom = from, dateTo = to))
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                ) { Text("Apply", fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun dateRangeLabel(from: Long?): String = if (from == null) "Any" else "Custom"

private fun dateRangeMillis(label: String): Pair<Long?, Long?> {
    val now = System.currentTimeMillis()
    return when (label) {
        "Today" -> startOfDay(now) to endOfDay(now)
        "This Week" -> {
            val cal = Calendar.getInstance()
            val start = startOfDay(now)
            cal.timeInMillis = now
            cal.add(Calendar.DAY_OF_YEAR, 7)
            start to endOfDay(cal.timeInMillis)
        }
        "Overdue" -> 0L to startOfDay(now) - 1
        else -> null to null
    }
}
