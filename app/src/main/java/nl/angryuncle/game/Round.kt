package nl.angryuncle.game

import kotlin.random.Random

/** One uncle on the board. [id] is also its cell index, row by row. */
data class Uncle(
    val id: Int,
    val face: Face,
    /** Small offsets (fractions of a cell) and tilt (degrees), so the pile looks packed rather than ruled. */
    val jitterX: Float,
    val jitterY: Float,
    val tilt: Float,
)

enum class BoardSize(val columns: Int, val rows: Int, val label: String) {
    Small(3, 4, "Small"),
    Medium(4, 6, "Medium"),
    Large(5, 8, "Large"),
}

sealed interface TapResult {
    data object Ignored : TapResult
    data object Safe : TapResult
    data object Angry : TapResult
}

/** The state of one game: which uncles are left and which one is secretly angry. */
data class Round(
    val columns: Int,
    val rows: Int,
    val uncles: List<Uncle>,
    val angryId: Int,
    val removed: Set<Int> = emptySet(),
    val lost: Boolean = false,
) {
    val remaining get() = uncles.size - removed.size
    val safeTaps get() = removed.size

    fun isOnBoard(id: Int) = id in uncles.indices && id !in removed

    /** Taps uncle [id]. Taps after the game is lost, or on an uncle that already flew off, are ignored. */
    fun tap(id: Int): Pair<Round, TapResult> = when {
        lost || !isOnBoard(id) -> this to TapResult.Ignored
        id == angryId -> copy(lost = true) to TapResult.Angry
        else -> copy(removed = removed + id) to TapResult.Safe
    }

    companion object {
        /**
         * A fresh board. With a [cast] of faces, each one is repeated about equally often and shuffled;
         * without one, every seat gets a random cartoon uncle.
         */
        fun new(size: BoardSize, cast: List<Face> = emptyList(), rng: Random = Random.Default): Round {
            val count = size.columns * size.rows
            val faces = if (cast.isEmpty()) {
                List(count) { Face.Cartoon(UncleLook.random(rng)) }
            } else {
                List(count) { cast[it % cast.size] }.shuffled(rng)
            }
            val uncles = List(count) { id ->
                Uncle(
                    id = id,
                    face = faces[id],
                    jitterX = (rng.nextFloat() - 0.5f) * 0.12f,
                    jitterY = (rng.nextFloat() - 0.5f) * 0.12f,
                    tilt = (rng.nextFloat() - 0.5f) * 16f,
                )
            }
            return Round(size.columns, size.rows, uncles, angryId = rng.nextInt(count))
        }
    }
}
