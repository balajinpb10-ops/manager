package com.vaultra.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaultra.app.data.FuelEntry
import com.vaultra.app.data.FuelDao
import com.vaultra.app.data.VaultDatabase
import com.vaultra.app.ui.theme.*
import com.vaultra.app.util.ImageStore
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.UUID

private val FUEL_TYPES = listOf("Petrol", "Diesel", "Electric", "Hybrid")
private val VEHICLE_TYPES = listOf("Car", "Motorcycle", "SUV", "Truck", "Van", "Other")

@Composable
fun FuelScreen(db: VaultDatabase) {
    val fuelEntries by db.fuelDao().getAll().collectAsState(initial = emptyList())
    var search by remember { mutableStateOf(TextFieldValue("")) }
    var showSheet by remember { mutableStateOf(false) }
    var editEntry by remember { mutableStateOf<FuelEntry?>(null) }
    var selectedReceipt by remember { mutableStateOf<Uri?>(null) }
    val receiptPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> selectedReceipt = uri }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val filtered = remember(fuelEntries, search.text) {
        fuelEntries.filter { entry ->
            search.text.isBlank() || entry.vehicleName.contains(search.text, true) ||
                    entry.station.contains(search.text, true) ||
                    entry.fuelType.contains(search.text, true)
        }
    }

    val stats = remember(fuelEntries) {
        val totalAmount = fuelEntries.sumOf { it.totalAmount }
        val totalDistance = fuelEntries.sumOf { it.distance }
        val avgMileage = fuelEntries.fold(0.0) { acc, item -> acc + if (item.distance > 0) item.fuelQuantity / item.distance else 0.0 }
        val avgFuelEfficiency = if (fuelEntries.isEmpty()) 0.0 else fuelEntries.sumOf { if (it.distance > 0) it.distance / it.fuelQuantity else 0.0 } / fuelEntries.size
        val highestPrice = fuelEntries.maxOfOrNull { it.pricePerLiter } ?: 0.0
        val lowestPrice = fuelEntries.minOfOrNull { it.pricePerLiter } ?: 0.0
        FuelStats(totalAmount, totalDistance, if (fuelEntries.isEmpty()) 0.0 else avgFuelEfficiency, if (fuelEntries.isEmpty()) 0.0 else totalAmount / totalDistance, highestPrice, lowestPrice)
    }

    Box(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text("FUEL MANAGEMENT", fontWeight = FontWeight.Black, fontSize = 22.sp, color = TextPrimary)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextDim) },
                placeholder = { Text("Search fuel records", color = TextDim) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(14.dp))
            FuelStatsCard(stats)
            Spacer(Modifier.height(14.dp))
            Text("Fuel History", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
            Spacer(Modifier.height(10.dp))
            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(top = 60.dp), contentAlignment = Alignment.TopCenter) {
                    Text("No fuel entries yet. Add your first refill record.", color = TextDim)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                    items(filtered, key = { it.id }) { entry ->
                        FuelCard(entry = entry, onEdit = { editEntry = it; showSheet = true }, onDelete = {
                            scope.launch { db.fuelDao().delete(it) }
                        })
                    }
                    item { Spacer(Modifier.height(100.dp)) }
                }
            }
        }

        FloatingActionButton(
            containerColor = Accent,
            contentColor = Color.White,
            onClick = { editEntry = null; showSheet = true },
            modifier = Modifier.align(Alignment.BottomEnd)
        ) { Icon(Icons.Default.Add, contentDescription = "Add fuel") }
    }

    if (showSheet) {
        FuelEntrySheet(
            existing = editEntry,
            db = db,
            onDismiss = { showSheet = false; selectedReceipt = null },
            onPickReceipt = { receiptPicker.launch(arrayOf("image/*", "application/pdf")) },
            selectedReceiptUri = selectedReceipt,
            onSaved = { saved -> if (saved) { showSheet = false; selectedReceipt = null } }
        )
    }
}

@Composable
private fun FuelStatsCard(stats: FuelStats) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(BgCard)
            .padding(16.dp)
    ) {
        Text("Dashboard", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextDim)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            StatItem(Modifier.weight(1f), "Monthly Cost", "₹${stats.totalAmount.format()}")
            StatItem(Modifier.weight(1f), "Distance", "${stats.totalDistance} km")
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            StatItem(Modifier.weight(1f), "Avg Mileage", "${stats.averageMileage.format()} km/L")
            StatItem(Modifier.weight(1f), "Cost / km", "₹${stats.costPerKm.format()}")
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            StatItem(Modifier.weight(1f), "Max Price", "₹${stats.highestPrice.format()}")
            StatItem(Modifier.weight(1f), "Min Price", "₹${stats.lowestPrice.format()}")
        }
    }
}

@Composable
private fun StatItem(modifier: Modifier, title: String, value: String) {
    Column(modifier = modifier) {
        Text(title, fontSize = 12.sp, color = TextDim)
        Spacer(Modifier.height(4.dp))
        Text(value, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
    }
}

@Composable
private fun FuelCard(entry: FuelEntry, onEdit: (FuelEntry) -> Unit, onDelete: (FuelEntry) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(BgCard)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.vehicleName, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                Spacer(Modifier.height(4.dp))
                Text(entry.station, fontSize = 12.sp, color = TextDim)
            }
            IconButton(onClick = { onEdit(entry) }) { Icon(Icons.Default.AttachFile, contentDescription = "Edit fuel") }
            IconButton(onClick = { onDelete(entry) }) { Icon(Icons.Default.Receipt, contentDescription = "Delete fuel") }
        }
        Spacer(Modifier.height(8.dp))
        Text("${entry.distance} km · ${entry.fuelQuantity.format()} L · ₹${entry.totalAmount.format()}", color = TextDim, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        Text(SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(entry.timestamp)), color = TextDim, fontSize = 11.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FuelEntrySheet(
    existing: FuelEntry?,
    db: VaultDatabase,
    onDismiss: () -> Unit,
    onPickReceipt: () -> Unit,
    selectedReceiptUri: Uri?,
    onSaved: (Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var vehicleName by remember { mutableStateOf(existing?.vehicleName ?: "") }
    var vehicleType by remember { mutableStateOf(existing?.vehicleType ?: VEHICLE_TYPES.first()) }
    var fuelType by remember { mutableStateOf(existing?.fuelType ?: FUEL_TYPES.first()) }
    var odometer by remember { mutableStateOf(existing?.odometer?.toString() ?: "") }
    var previousOdometer by remember { mutableStateOf(existing?.previousOdometer?.toString() ?: "") }
    var fuelQuantity by remember { mutableStateOf(existing?.fuelQuantity?.toString() ?: "") }
    var pricePerLiter by remember { mutableStateOf(existing?.pricePerLiter?.toString() ?: "") }
    var station by remember { mutableStateOf(existing?.station ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var receiptPath by remember { mutableStateOf(existing?.receiptPath) }
    var location by remember { mutableStateOf(existing?.location ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    val distance = (odometer.toLongOrNull() ?: 0L) - (previousOdometer.toLongOrNull() ?: 0L)
    val totalAmount = (fuelQuantity.toDoubleOrNull() ?: 0.0) * (pricePerLiter.toDoubleOrNull() ?: 0.0)
    val mileage = if (fuelQuantity.toDoubleOrNull() ?: 0.0 > 0) distance.toDouble() / (fuelQuantity.toDoubleOrNull() ?: 1.0) else 0.0

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = BgElev) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(20.dp)
        ) {
            Text(if (existing == null) "New Fuel Record" else "Edit Fuel Record", fontWeight = FontWeight.Black, fontSize = 19.sp, color = TextPrimary)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(value = vehicleName, onValueChange = { vehicleName = it }, label = { Text("Vehicle Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            DropdownMenuField("Vehicle Type", VEHICLE_TYPES, vehicleType) { vehicleType = it }
            Spacer(Modifier.height(10.dp))
            DropdownMenuField("Fuel Type", FUEL_TYPES, fuelType) { fuelType = it }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(value = odometer, onValueChange = { odometer = it.filter { ch -> ch.isDigit() } }, label = { Text("Odometer Reading") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(value = previousOdometer, onValueChange = { previousOdometer = it.filter { ch -> ch.isDigit() } }, label = { Text("Previous Odometer") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(value = fuelQuantity, onValueChange = { fuelQuantity = it.filter { ch -> ch.isDigit() || ch == '.' } }, label = { Text("Fuel Quantity (L)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(value = pricePerLiter, onValueChange = { pricePerLiter = it.filter { ch -> ch.isDigit() || ch == '.' } }, label = { Text("Price per Liter") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(value = station, onValueChange = { station = it }, label = { Text("Fuel Station") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("GPS Location (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            selectedReceiptPathText(receiptPath, selectedReceiptUri)
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onPickReceipt) {
                Icon(Icons.Default.AttachFile, contentDescription = null, tint = Accent2)
                Spacer(Modifier.width(6.dp))
                Text("Upload Receipt", color = Accent2)
            }
            Spacer(Modifier.height(12.dp))
            Text("Distance: ${distance} km · Mileage: ${mileage.format()} km/L · Total ₹${totalAmount.format()}", color = TextDim, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            error?.let { Text(it, color = Accent2, fontSize = 12.sp) }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(onClick = {
                    if (vehicleName.isBlank()) { error = "Vehicle name is required"; return@Button }
                    if (odometer.toLongOrNull() == null) { error = "Enter a valid odometer"; return@Button }
                    if (fuelQuantity.toDoubleOrNull() == null) { error = "Enter fuel quantity"; return@Button }
                    if (pricePerLiter.toDoubleOrNull() == null) { error = "Enter price per liter"; return@Button }
                    scope.launch {
                        var finalReceiptPath = receiptPath
                        selectedReceiptUri?.let { uri ->
                            val imported = ImageStore.importImages(context, listOf(uri), "fuel")
                            if (imported.isNotEmpty()) finalReceiptPath = imported.first()
                        }
                        db.fuelDao().upsert(
                            FuelEntry(
                                id = existing?.id ?: UUID.randomUUID().toString(),
                                vehicleName = vehicleName.trim(),
                                vehicleType = vehicleType,
                                fuelType = fuelType,
                                odometer = odometer.toLong(),
                                previousOdometer = previousOdometer.toLongOrNull() ?: 0L,
                                distance = distance.coerceAtLeast(0L),
                                fuelQuantity = fuelQuantity.toDouble(),
                                pricePerLiter = pricePerLiter.toDouble(),
                                totalAmount = totalAmount,
                                station = station.trim(),
                                timestamp = existing?.timestamp ?: System.currentTimeMillis(),
                                notes = notes.trim(),
                                receiptPath = finalReceiptPath,
                                location = location.trim().ifBlank { null },
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                        onSaved(true)
                    }
                }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun selectedReceiptPathText(receiptPath: String?, selectedReceiptUri: Uri?) {
    val label = selectedReceiptUri?.let { "Receipt selected" } ?: receiptPath?.let { "Receipt attached" } ?: "No receipt attached"
    Text(label, color = TextDim, fontSize = 12.sp)
}

@Composable
private fun DropdownMenuField(label: String, options: List<String>, selected: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, fontSize = 12.sp, color = TextDim)
        Spacer(Modifier.height(6.dp))
        Box(modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .clickable { expanded = true }
            .padding(14.dp)
        ) {
            Text(selected, fontSize = 14.sp, color = TextPrimary)
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(text = { Text(option) }, onClick = { expanded = false; onSelected(option) })
                }
            }
        }
    }
}

private data class FuelStats(
    val totalAmount: Double,
    val totalDistance: Long,
    val averageMileage: Double,
    val costPerKm: Double,
    val highestPrice: Double,
    val lowestPrice: Double
)

private fun Double.format(): String = String.format(Locale.getDefault(), "%.2f", this)

private fun Long.format(): String = this.toString()
