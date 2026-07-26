package com.vaultra.app.ui.screens.todo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaultra.app.data.TodoCategory
import com.vaultra.app.data.TodoEntry
import com.vaultra.app.data.TodoPriority
import com.vaultra.app.ui.theme.*

@Composable
fun TodoStatsView(tasks: List<TodoEntry>, categories: List<TodoCategory>) {
    val total = tasks.size
    val completed = tasks.count { it.isCompleted }
    val pending = total - completed
    val overdue = tasks.count { it.isOverdue() }
    val completionRate = if (total > 0) completed.toFloat() / total else 0f

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = BgCard)) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { completionRate }, modifier = Modifier.size(72.dp),
                            color = Accent, trackColor = Line, strokeWidth = 7.dp
                        )
                        Text("${(completionRate * 100).toInt()}%", color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 16.sp)
                    }
                    Spacer(Modifier.width(20.dp))
                    Column {
                        Text("Completion Rate", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("$completed of $total tasks completed", color = TextDim, fontSize = 12.sp)
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Total", total.toString(), TextPrimary, Modifier.weight(1f))
                StatTile("Completed", completed.toString(), Good, Modifier.weight(1f))
                StatTile("Pending", pending.toString(), Warn, Modifier.weight(1f))
                StatTile("Overdue", overdue.toString(), Accent2, Modifier.weight(1f))
            }
        }

        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(BgCard).padding(16.dp)) {
                Text("PRIORITY BREAKDOWN", fontSize = 11.sp, color = TextDim, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                listOf(TodoPriority.HIGH, TodoPriority.MEDIUM, TodoPriority.LOW).forEach { p ->
                    val count = tasks.count { it.priority == p }
                    DistributionRow(label = priorityLabel(p), count = count, total = total, color = priorityColor(p))
                }
            }
        }

        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(BgCard).padding(16.dp)) {
                Text("CATEGORY-WISE DISTRIBUTION", fontSize = 11.sp, color = TextDim, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                categories.forEach { cat ->
                    val count = tasks.count { it.categoryId == cat.id }
                    if (count > 0) DistributionRow(label = cat.name, count = count, total = total, color = colorFromHex(cat.colorHex))
                }
                val uncategorized = tasks.count { it.categoryId == null }
                if (uncategorized > 0) DistributionRow(label = "Uncategorized", count = uncategorized, total = total, color = TextDim)
                if (total == 0) Text("No tasks yet.", color = TextDim, fontSize = 12.sp)
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun StatTile(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(14.dp)).background(BgCard).padding(vertical = 14.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(2.dp))
        Text(label, color = TextDim, fontSize = 11.sp)
    }
}

@Composable
private fun DistributionRow(label: String, count: Int, total: Int, color: androidx.compose.ui.graphics.Color) {
    val fraction = if (total > 0) count.toFloat() / total else 0f
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = TextPrimary, fontSize = 13.sp)
            Text(count.toString(), color = TextDim, fontSize = 12.sp)
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Line)) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
        }
    }
}
