package com.vaultra.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaultra.app.data.TodoCategory
import com.vaultra.app.data.TodoEntry
import com.vaultra.app.ui.theme.*

@Composable
fun TodoStatsView(tasks: List<TodoEntry>, categories: List<TodoCategory>) {
    val total = tasks.size
    val completed = tasks.count { it.isCompleted }
    val pending = total - completed
    val rate = if (total > 0) completed.toFloat() / total else 0f

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            StatCard("Total", total.toString(), TextPrimary, Modifier.weight(1f))
            StatCard("Completed", completed.toString(), Good, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            StatCard("Pending", pending.toString(), Warn, Modifier.weight(1f))
            StatCard("Completion", "${(rate * 100).toInt()}%", Accent2, Modifier.weight(1f))
        }

        Spacer(Modifier.height(18.dp))
        Text("CATEGORY DISTRIBUTION", fontSize = 11.sp, color = TextDim, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))

        val grouped = categories.map { cat -> cat to tasks.count { it.categoryId == cat.id } }.filter { it.second > 0 }
        val uncategorized = tasks.count { it.categoryId == null || categories.none { c -> c.id == it.categoryId } }
        val maxCount = (grouped.maxOfOrNull { it.second } ?: 0).coerceAtLeast(uncategorized).coerceAtLeast(1)

        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BgCard).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (grouped.isEmpty() && uncategorized == 0) {
                Text("No tasks yet to break down.", color = TextDim, fontSize = 12.5.sp)
            } else {
                grouped.sortedByDescending { it.second }.forEach { (cat, count) ->
                    CategoryBar(cat.name, count, maxCount, parseHexColor(cat.colorHex))
                }
                if (uncategorized > 0) {
                    CategoryBar("Uncategorized", uncategorized, maxCount, TextDim)
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Text("BY PRIORITY", fontSize = 11.sp, color = TextDim, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BgCard).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val byPriority = listOf(2, 1, 0).map { p -> p to tasks.count { it.priority == p } }
            val maxP = (byPriority.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
            byPriority.forEach { (p, count) -> CategoryBar(priorityLabel(p), count, maxP, priorityColor(p)) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatCard(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(16.dp)).background(BgCard).padding(16.dp)
    ) {
        Text(label, fontSize = 12.sp, color = TextDim)
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Black, color = color)
    }
}

@Composable
private fun CategoryBar(label: String, count: Int, maxCount: Int, color: androidx.compose.ui.graphics.Color) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(label, color = TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(count.toString(), color = TextDim, fontSize = 13.sp)
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Line)) {
            Box(
                Modifier
                    .fillMaxWidth(count.toFloat() / maxCount)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}
