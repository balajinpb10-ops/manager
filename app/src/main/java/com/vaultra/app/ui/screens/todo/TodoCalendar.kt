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
import com.vaultra.app.data.TodoEntry
import com.vaultra.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun TodoCalendarView(tasks: List<TodoEntry>, onTaskClick: (TodoEntry) -> Unit) {
    var monthCursor by remember { mutableStateOf(Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }) }
    var selectedDay by remember { mutableStateOf<Calendar?>(null) }

    val tasksByDay = remember(tasks) {
        tasks.filter { it.dueAt != null }.groupBy { startOfDay(it.dueAt!!) }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { monthCursor = shiftMonth(monthCursor, -1); selectedDay = null }) {
                Icon(Icons.Default.ChevronLeft, "Previous month", tint = Accent2)
            }
            Text(
                SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(monthCursor.time),
                color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp
            )
            IconButton(onClick = { monthCursor = shiftMonth(monthCursor, 1); selectedDay = null }) {
                Icon(Icons.Default.ChevronRight, "Next month", tint = Accent2)
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach { d ->
                Text(d, color = TextDim, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        Spacer(Modifier.height(4.dp))

        val cells = remember(monthCursor) { buildMonthGrid(monthCursor) }
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.padding(horizontal = 8.dp).height(((cells.size / 7 + 1) * 52).dp)
        ) {
            items(cells) { cell ->
                if (cell == null) {
                    Box(Modifier.padding(3.dp).size(44.dp))
                } else {
                    val dayStart = startOfDay(cell.timeInMillis)
                    val dayTasks = tasksByDay[dayStart].orEmpty()
                    val isToday = isSameDay(cell.timeInMillis, System.currentTimeMillis())
                    val isSelected = selectedDay?.let { isSameDay(it.timeInMillis, cell.timeInMillis) } == true
                    Column(
                        modifier = Modifier
                            .padding(3.dp)
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) Accent.copy(alpha = 0.25f) else if (isToday) BgCard else Color.Transparent)
                            .clickable { selectedDay = cell },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            cell.get(Calendar.DAY_OF_MONTH).toString(),
                            color = if (isToday) Accent2 else TextPrimary, fontSize = 13.sp,
                            fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                        if (dayTasks.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                dayTasks.take(3).forEach { t ->
                                    Box(Modifier.size(4.dp).clip(CircleShape).background(priorityColor(t.priority)))
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        val dayList = selectedDay?.let { tasksByDay[startOfDay(it.timeInMillis)].orEmpty() } ?: emptyList()
        if (selectedDay != null) {
            Text(
                SimpleDateFormat("EEEE, dd MMM", Locale.getDefault()).format(selectedDay!!.time),
                color = TextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(6.dp))
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (selectedDay != null && dayList.isEmpty()) {
                item { Text("No tasks due this day.", color = TextDim, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp)) }
            }
            items(dayList, key = { it.id }) { task ->
                CalendarTaskRow(task, onClick = { onTaskClick(task) })
            }
        }
    }
}

@Composable
private fun CalendarTaskRow(task: TodoEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(priorityColor(task.priority)))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(task.title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            task.dueAt?.let { Text(formatDueTime(it), color = TextDim, fontSize = 11.sp) }
        }
    }
}

private fun shiftMonth(cal: Calendar, delta: Int): Calendar =
    (cal.clone() as Calendar).apply { add(Calendar.MONTH, delta) }

/** Builds a 7-wide grid of Calendar cells for the given month, with nulls for the
 *  leading blank days before the 1st (so weekday columns line up). */
private fun buildMonthGrid(monthCursor: Calendar): List<Calendar?> {
    val first = (monthCursor.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
    val leadingBlanks = first.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
    val daysInMonth = first.getActualMaximum(Calendar.DAY_OF_MONTH)
    val cells = mutableListOf<Calendar?>()
    repeat(leadingBlanks) { cells.add(null) }
    for (day in 1..daysInMonth) {
        cells.add((first.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, day) })
    }
    while (cells.size % 7 != 0) cells.add(null)
    return cells
}
