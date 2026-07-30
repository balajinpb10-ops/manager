package com.vaultra.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaultra.app.data.CardEntry
import com.vaultra.app.data.VaultDatabase
import com.vaultra.app.ui.theme.*
import com.vaultra.app.util.CardNumberVisualTransformation
import com.vaultra.app.util.CardValidators
import com.vaultra.app.util.ImageStore
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun CardsScreen(db: VaultDatabase) {
    val cards by db.cardDao().getAll().collectAsState(initial = emptyList())
    var editTarget by remember { mutableStateOf<CardEntry?>(null) }
    var showAddEdit by remember { mutableStateOf(false) }
    var detailTarget by remember { mutableStateOf<CardEntry?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("MY CARDS", fontWeight = FontWeight.Black, fontSize = 22.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                InfoButton(
                    title = "Your Cards",
                    purpose = "Securely store your debit and credit card details for quick reference.",
                    howToUse = "Tap + to add a card. Tap a card to view full details, copy the number, or edit it.",
                    tips = "Mark a card as favorite to keep it pinned at the top.",
                    securityNote = "Card numbers and CVVs are encrypted at rest the same as your passwords — AES-256 via SQLCipher."
                )
            }

            if (cards.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(modifier = Modifier.size(70.dp).clip(RoundedCornerShape(20.dp)).background(BgCard), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.CreditCard, contentDescription = null, tint = TextDim, modifier = Modifier.size(30.dp))
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("No cards yet", color = TextPrimary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("Tap the + button to add your first card.", color = TextDim, fontSize = 12.5.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(cards, key = { it.id }) { card ->
                        CardVisual(card = card, onClick = { detailTarget = card })
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }

        FloatingActionButton(
            onClick = { editTarget = null; showAddEdit = true },
            containerColor = Accent, contentColor = Color.White, shape = RoundedCornerShape(18.dp),
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
        ) { Icon(Icons.Filled.Add, contentDescription = "Add card") }
    }

    if (showAddEdit) {
        CardFormSheet(existing = editTarget, db = db, onDismiss = { showAddEdit = false })
    }
    detailTarget?.let { card ->
        CardDetailSheet(
            card = card, db = db,
            onDismiss = { detailTarget = null },
            onEdit = { detailTarget = null; editTarget = card; showAddEdit = true }
        )
    }
}

/** The visual, wallet-style card mockup — matches the app's dark red/black brand. */
@Composable
private fun CardVisual(card: CardEntry, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(press3D(interaction))
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF1C1C20), Color(0xFF3A0C14), Color(0xFF5A0E1A))))
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(20.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(card.nickname.ifBlank { card.bankName.ifBlank { "Card" } }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            if (card.isFavorite) Icon(Icons.Filled.Star, contentDescription = "Favorite", tint = Accent2, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(22.dp))
        Box(modifier = Modifier.size(width = 40.dp, height = 30.dp).clip(RoundedCornerShape(6.dp)).background(Brush.linearGradient(listOf(Color(0xFFE8C468), Color(0xFFC9A24B)))))
        Spacer(Modifier.height(14.dp))
        Text(
            "•••• •••• •••• ${card.cardNumber.filter { it.isDigit() }.takeLast(4).ifBlank { "····" }}",
            color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.5.sp
        )
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Column {
                Text("CARD HOLDER", color = Color.White.copy(alpha = 0.6f), fontSize = 9.sp)
                Text(card.cardholderName.ifBlank { "—" }.uppercase(), color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("EXPIRES", color = Color.White.copy(alpha = 0.6f), fontSize = 9.sp)
                Text("${card.expiryMonth}/${card.expiryYear}", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(card.network, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CardFormSheet(existing: CardEntry?, db: VaultDatabase, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var nickname by remember { mutableStateOf(existing?.nickname ?: "") }
    var bankName by remember { mutableStateOf(existing?.bankName ?: "") }
    var holder by remember { mutableStateOf(existing?.cardholderName ?: "") }
    var number by remember { mutableStateOf(existing?.cardNumber?.filter { it.isDigit() } ?: "") }
    var month by remember { mutableStateOf(existing?.expiryMonth ?: "") }
    var year by remember { mutableStateOf(existing?.expiryYear ?: "") }
    var cvv by remember { mutableStateOf(existing?.cvv ?: "") }
    var favorite by remember { mutableStateOf(existing?.isFavorite ?: false) }
    var images by remember { mutableStateOf(existing?.images ?: emptyList()) }
    val originalImages = remember { existing?.images ?: emptyList() }

    var numberErr by remember { mutableStateOf<String?>(null) }
    var expiryErr by remember { mutableStateOf<String?>(null) }
    var cvvErr by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = BgElev) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 26.dp)) {
            Text(if (existing == null) "Add Card" else "Edit Card", fontWeight = FontWeight.Black, fontSize = 19.sp, color = TextPrimary)
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(nickname, { nickname = it }, label = { Text("Nickname") }, placeholder = { Text("e.g. Personal Visa") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(bankName, { bankName = it }, label = { Text("Bank / Issuer") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(holder, { holder = it }, label = { Text("Cardholder name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = number,
                onValueChange = { input -> number = input.filter { it.isDigit() }.take(19); numberErr = null },
                label = { Text("Card number") }, singleLine = true, isError = numberErr != null,
                visualTransformation = CardNumberVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            numberErr?.let { Spacer(Modifier.height(4.dp)); Text(it, color = Accent2, fontSize = 11.5.sp) }
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(month, { month = it.filter { c -> c.isDigit() }.take(2); expiryErr = null }, label = { Text("MM") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(year, { year = it.filter { c -> c.isDigit() }.take(4); expiryErr = null }, label = { Text("YY") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(cvv, { cvv = it.filter { c -> c.isDigit() }.take(4); cvvErr = null }, label = { Text("CVV") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            expiryErr?.let { Spacer(Modifier.height(4.dp)); Text(it, color = Accent2, fontSize = 11.5.sp) }
            cvvErr?.let { Spacer(Modifier.height(4.dp)); Text(it, color = Accent2, fontSize = 11.5.sp) }

            Spacer(Modifier.height(14.dp))
            AttachmentsPicker(
                images = images,
                onImagesAdded = { added -> images = images + added },
                onImageRemoved = { path -> images = images - path },
                subfolder = "cards"
            )

            Spacer(Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Mark as favorite", color = TextPrimary, fontSize = 14.sp)
                Switch(checked = favorite, onCheckedChange = { favorite = it }, colors = vaultraSwitchColors())
            }

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        // Any photo picked during this session but never saved shouldn't linger on disk.
                        val abandoned = images - originalImages.toSet()
                        if (abandoned.isNotEmpty()) ImageStore.deleteImages(abandoned)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Cancel") }
                Button(
                    onClick = {
                        val nErr = CardValidators.cardNumberError(number)
                        val eErr = CardValidators.expiryError(month, year)
                        val cErr = CardValidators.cvvError(cvv)
                        numberErr = nErr; expiryErr = eErr; cvvErr = cErr
                        if (nErr != null || eErr != null || cErr != null) return@Button

                        val card = CardEntry(
                            id = existing?.id ?: UUID.randomUUID().toString(),
                            nickname = nickname.trim(), bankName = bankName.trim(), cardholderName = holder.trim(),
                            cardNumber = number.trim(), expiryMonth = month.trim(), expiryYear = year.trim(),
                            cvv = cvv.trim(), network = CardValidators.detectNetwork(number),
                            isFavorite = favorite, updatedAt = System.currentTimeMillis(),
                            images = images
                        )
                        val removed = originalImages - images.toSet()
                        scope.launch {
                            db.cardDao().upsert(card)
                            if (removed.isNotEmpty()) ImageStore.deleteImages(removed)
                            onDismiss()
                            Toast.makeText(context, if (existing == null) "Card added" else "Card updated", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Accent)
                ) { Text("Save", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CardDetailSheet(card: CardEntry, db: VaultDatabase, onDismiss: () -> Unit, onEdit: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var showNumber by remember { mutableStateOf(false) }
    var showCvv by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun copy(label: String, value: String) {
        clipboard.setText(AnnotatedString(value))
        Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = BgElev) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 26.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(card.nickname.ifBlank { card.bankName }, fontWeight = FontWeight.Black, fontSize = 18.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = TextDim) }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Accent2) }
            }
            Spacer(Modifier.height(14.dp))
            CardVisual(card = card, onClick = {})
            Spacer(Modifier.height(14.dp))

            DetailBox("CARD NUMBER", if (showNumber) CardValidators.formatCardNumber(card.cardNumber) else "•••• •••• •••• ${card.cardNumber.filter { it.isDigit() }.takeLast(4)}") {
                IconButton(onClick = { showNumber = !showNumber }) { Icon(if (showNumber) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = "Toggle number", tint = Accent2) }
                IconButton(onClick = { copy("Card number", card.cardNumber) }) { Icon(Icons.Filled.ContentCopy, contentDescription = "Copy", tint = Accent2) }
            }
            Spacer(Modifier.height(10.dp))
            DetailBox("CVV", if (showCvv) card.cvv else "•••") {
                IconButton(onClick = { showCvv = !showCvv }) { Icon(if (showCvv) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = "Toggle CVV", tint = Accent2) }
                IconButton(onClick = { copy("CVV", card.cvv) }) { Icon(Icons.Filled.ContentCopy, contentDescription = "Copy", tint = Accent2) }
            }
            if (card.images.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                AttachmentsGallery(card.images)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this card?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { scope.launch { db.cardDao().delete(card); ImageStore.deleteImages(card.images); confirmDelete = false; onDismiss() } }) { Text("Delete", color = Accent2) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun DetailBox(label: String, value: String, actions: @Composable RowScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(BgCard).padding(14.dp)) {
        Text(label, fontSize = 11.sp, color = TextDim, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, color = TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
            actions()
        }
    }
}
