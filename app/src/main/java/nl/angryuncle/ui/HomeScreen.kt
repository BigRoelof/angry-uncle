package nl.angryuncle.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import nl.angryuncle.game.BoardSize
import nl.angryuncle.game.Expression
import nl.angryuncle.game.HairStyle
import nl.angryuncle.game.MustacheStyle
import nl.angryuncle.game.UncleLook
import nl.angryuncle.game.drawUncle

private val mascot = UncleLook(
    skin = Color(0xFFF0C09A),
    hair = Color(0xFF3B2A20),
    hairStyle = HairStyle.Tufts,
    mustache = MustacheStyle.Walrus,
    glasses = false,
    width = 0.86f,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    boardSize: BoardSize,
    onBoardSizeChange: (BoardSize) -> Unit,
    headCount: Int,
    useMyHeads: Boolean,
    onUseMyHeadsChange: (Boolean) -> Unit,
    onMyHeads: () -> Unit,
    soundOn: Boolean,
    onSoundOnChange: (Boolean) -> Unit,
    onMascotPoked: () -> Unit,
    onPlay: () -> Unit,
) {
    // Poking the mascot makes him angry for a moment: a little preview of the game.
    var poked by remember { mutableStateOf(false) }
    LaunchedEffect(poked) {
        if (poked) {
            delay(900)
            poked = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        TextButton(onClick = { onSoundOnChange(!soundOn) }, modifier = Modifier.align(Alignment.End)) {
            Text(if (soundOn) "Sound on" else "Sound off", style = MaterialTheme.typography.titleMedium)
        }
        Text(
            "ANGRY\nUNCLE",
            fontSize = 56.sp,
            lineHeight = 54.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(16.dp))
        Canvas(
            Modifier
                .size(160.dp)
                .pointerInput(Unit) {
                    detectTapGestures {
                        if (!poked) onMascotPoked()
                        poked = true
                    }
                },
        ) {
            drawUncle(mascot, center, size.minDimension, if (poked) Expression.Angry else Expression.Calm, if (poked) 1f else 0f)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Pass the phone around and take turns tapping uncles. One of them is secretly angry. Whoever finds him loses!",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(32.dp))
        Text("Table size", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            BoardSize.entries.forEachIndexed { i, size ->
                SegmentedButton(
                    selected = size == boardSize,
                    onClick = { onBoardSizeChange(size) },
                    shape = SegmentedButtonDefaults.itemShape(i, BoardSize.entries.size),
                ) { Text("${size.label} · ${size.columns * size.rows}") }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("Who's at the table", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = !useMyHeads || headCount == 0,
                onClick = { onUseMyHeadsChange(false) },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
            ) { Text("Cartoon uncles") }
            SegmentedButton(
                selected = useMyHeads && headCount > 0,
                // With no heads yet, there is nothing to pick: go and make some.
                onClick = { if (headCount == 0) onMyHeads() else onUseMyHeadsChange(true) },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
            ) { Text(if (headCount == 0) "My heads" else "My heads · $headCount") }
        }
        TextButton(onClick = onMyHeads) {
            Text(if (headCount == 0) "Add your own heads ›" else "Edit my heads ›", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onPlay, modifier = Modifier.fillMaxWidth().height(64.dp)) {
            Text("PLAY", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
    }
}
