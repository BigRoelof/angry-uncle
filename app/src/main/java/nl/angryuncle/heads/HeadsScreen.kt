package nl.angryuncle.heads

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import nl.angryuncle.game.Expression
import nl.angryuncle.game.Sounds
import nl.angryuncle.game.drawPhotoHead

/** The user's own heads. Tap one to see it get angry, long-press to delete it. */
@Composable
fun HeadsScreen(
    heads: List<CustomHead>,
    sounds: Sounds,
    onAdd: () -> Unit,
    onDelete: (CustomHead) -> Unit,
    onBack: () -> Unit,
) {
    var deleting by remember { mutableStateOf<CustomHead?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(horizontal = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Menu", style = MaterialTheme.typography.titleMedium) }
        }
        Text(
            "My heads",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Text(
            if (heads.isEmpty()) {
                "Add friends and family: one normal photo and one angry one. They stay on this phone."
            } else {
                "Tap a head to make it angry. Long-press to delete."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        )
        Spacer(Modifier.height(12.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(heads, key = { it.id }) { head ->
                HeadCard(head, onPoke = { sounds.yell(head.yell) }, onLongPress = { deleting = head })
            }
        }

        Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).height(56.dp)) {
            Text("Add a head", style = MaterialTheme.typography.titleMedium)
        }
    }

    deleting?.let { head ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${head.name.ifBlank { "this head" }}?") },
            text = { Text("Both photos are removed from this phone.") },
            confirmButton = {
                TextButton(onClick = { onDelete(head); deleting = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep") } },
        )
    }
}

@Composable
private fun HeadCard(head: CustomHead, onPoke: () -> Unit, onLongPress: () -> Unit) {
    var angry by remember { mutableStateOf(false) }
    LaunchedEffect(angry) {
        if (angry) {
            delay(1200)
            angry = false
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                .pointerInput(head) { detectTapGestures(onTap = { angry = true; onPoke() }, onLongPress = { onLongPress() }) },
        ) {
            val e = if (angry) Expression.Angry else Expression.Calm
            drawPhotoHead(head, center, size.minDimension * 0.88f, e, redness = if (angry) 1f else 0f)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            head.name.ifBlank { "No name" },
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
