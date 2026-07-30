package com.vaultra.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
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
import com.vaultra.app.data.TodoEntry
import com.vaultra.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun TodoCalendarView(
    tasks: List<TodoEntry>,
    categories: List<TodoCategory>,
    onTaskClick: (TodoEntry) -> Unit
) {
    var monthCal by remember { mutableStateOf(Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }) }
    var selectedDay by remember { mutableStateOf<Calendar?>(null) }

    val tasksByDay = remember(tasks) {
        val map = mutableMapOf<String, MutableList<TodoEntry>>()
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        tasks.forEach { t ->
            t.dueAt?.let { due ->
                val key = fmt.format(java.util.Date(due))
                map.getOrPut(key) { mutableListOf() }.add(t)
            }
        }
        map
    }
    val dayFmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }

    Column(Modifier.fillMaxSize()) {
        // Month header
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { monthCal = (monthCal.clone() as Calendar).apply { add(Calendar.MONTH, -1) } }) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous month", tint = Accent2)
            }
            Text(
                SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(monthCal.time),
                color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 16.sp,
                modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            IconButton(onClick = { monthCal = (monthCal.clone() as Calendar).apply { add(Calendar.MONTH, 1) } }) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "Next month", tint = Accent2)
            }
        }

        // Weekday labels
        Row(Modifier.fillMaxWidth()) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach { d ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(d, color = TextDim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(4.dp))

        val firstDayOfWeek = (monthCal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }.get(Calendar.DAY_OF_WEEK) - 1
        val daysInMonth = (monthCal.clone() as Calendar).getActualMaximum(Calendar.DAY_OF_MONTH)
        val today = Calendar.getInstance()
        val cells = firstDayOfWeek + daysInMonth
        val rows = (cells + 6) / 7

        for (row in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val cellIndex = row * 7 + col
                    val dayNum = cellIndex - firstDayOfWeek + 1
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (dayNum in 1..daysInMonth) {
                            val cellCal = (monthCal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, dayNum) }
                            val key = dayFmt.format(cellCal.time)
                            val dayTasks = tasksByDay[key].orEmpty()
                            val isToday = today.get(Calendar.YEAR) == cellCal.get(Calendar.YEAR) && today.get(Calendar.DAY_OF_YEAR) == cellCal.get(Calendar.DAY_OF_YEAR)
                            val isSelected = selectedDay?.let { dayFmt.format(it.time) == key } == true

                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) Accent.copy(alpha = 0.22f) else if (isToday) BgCard else Color.Transparent)
                                    .clickable { selectedDay = cellCal },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    dayNum.toString(), fontSize = 13.sp,
                                    color = if (isToday) Accent2 else TextPrimary,
                                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                if (dayTasks.isNotEmpty()) {
                                    Spacer(Modifier.height(2.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        dayTasks.take(3).forEach { t ->
                                            val cat = categories.find { it.id == t.categoryId }
                                            val dotColor = cat?.let { parseHexColor(it.colorHex) } ?: priorityColor(t.priority)
                                            Box(Modifier.size(5.dp).clip(CircleShape).background(dotColor))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        val daySelected = selectedDay
        if (daySelected != null) {
            val key = dayFmt.format(daySelected.time)
            val dayTasks = tasksByDay[key].orEmpty()
            Text(
                SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(daySelected.time),
                color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp
            )
            Spacer(Modifier.height(8.dp))
            if (dayTasks.isEmpty()) {
                Text("No tasks due this day.", color = TextDim, fontSize = 12.5.sp)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    items(dayTasks, key = { it.id }) { t ->
                        TaskCard(task = t, category = categories.find { it.id == t.categoryId }, onClick = { onTaskClick(t) }, onToggleComplete = {}, onTogglePin = {}, onToggleFavorite = {})
                    }
                }
            }
        } else {
            Text("Tap a day to see its tasks.", color = TextDim, fontSize = 12.5.sp)
        }
    }
}

fun priorityColor(priority: Int): Color = when (priority) {
    2 -> Accent
    1 -> Warn
    else -> Good
}

fun priorityLabel(priority: Int): String = when (priority) {
    2 -> "High"
    1 -> "Medium"
    else -> "Low"
}
