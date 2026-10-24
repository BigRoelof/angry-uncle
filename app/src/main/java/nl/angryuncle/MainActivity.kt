package nl.angryuncle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import nl.angryuncle.game.BoardSize
import nl.angryuncle.game.Face
import nl.angryuncle.game.Sounds
import nl.angryuncle.heads.AddHeadScreen
import nl.angryuncle.heads.CustomHead
import nl.angryuncle.heads.HeadStore
import nl.angryuncle.heads.HeadsScreen
import nl.angryuncle.ui.AngryUncleTheme
import nl.angryuncle.ui.GameScreen
import nl.angryuncle.ui.HomeScreen

private enum class Screen { Home, Game, Heads, AddHead }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            AngryUncleTheme {
                val scope = rememberCoroutineScope()
                var screen by rememberSaveable { mutableStateOf(Screen.Home) }
                var boardSize by rememberSaveable { mutableStateOf(BoardSize.Medium) }
                var useMyHeads by rememberSaveable { mutableStateOf(false) }
                var heads by remember { mutableStateOf<List<CustomHead>>(emptyList()) }
                var headsVersion by remember { mutableIntStateOf(0) }
                val sounds = remember { Sounds(applicationContext) }
                DisposableEffect(Unit) { onDispose { sounds.release() } }
                var soundOn by remember { mutableStateOf(sounds.enabled) }
                LaunchedEffect(headsVersion) { heads = HeadStore.loadAll(this@MainActivity) }

                // AddHead handles its own back steps; the other screens go back to wherever they came from.
                BackHandler(enabled = screen == Screen.Game || screen == Screen.Heads) { screen = Screen.Home }

                when (screen) {
                    Screen.Home -> HomeScreen(
                        boardSize = boardSize,
                        onBoardSizeChange = { boardSize = it },
                        headCount = heads.size,
                        useMyHeads = useMyHeads,
                        onUseMyHeadsChange = { useMyHeads = it },
                        onMyHeads = { screen = Screen.Heads },
                        soundOn = soundOn,
                        onSoundOnChange = { soundOn = it; sounds.enabled = it },
                        onMascotPoked = { sounds.yell(null) },
                        onPlay = { screen = Screen.Game },
                    )
                    Screen.Game -> GameScreen(
                        boardSize = boardSize,
                        cast = if (useMyHeads) heads.map(Face::Photo) else emptyList(),
                        sounds = sounds,
                        onExit = { screen = Screen.Home },
                    )
                    Screen.Heads -> HeadsScreen(
                        heads = heads,
                        sounds = sounds,
                        onAdd = { screen = Screen.AddHead },
                        onDelete = { head ->
                            scope.launch {
                                HeadStore.delete(this@MainActivity, head.id)
                                headsVersion++
                            }
                        },
                        onBack = { screen = Screen.Home },
                    )
                    Screen.AddHead -> AddHeadScreen(
                        onSaved = {
                            headsVersion++
                            // Someone who just made a head wants to play with it.
                            useMyHeads = true
                            screen = Screen.Heads
                        },
                        onCancel = { screen = Screen.Heads },
                    )
                }
            }
        }
    }
}
