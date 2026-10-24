package nl.angryuncle.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RoundTest {
    private val round = Round.new(BoardSize.Medium, rng = Random(42))
    private val safeId = round.uncles.first { it.id != round.angryId }.id

    @Test
    fun `new round fills the board with exactly one angry uncle`() {
        assertEquals(24, round.uncles.size)
        assertTrue(round.angryId in round.uncles.indices)
        assertEquals(24, round.remaining)
    }

    @Test
    fun `a cast is spread evenly over the board`() {
        val cast = listOf("a", "b", "c", "d", "e").map { Face.Cartoon(UncleLook.random(Random(it.hashCode()))) }
        val counts = Round.new(BoardSize.Medium, cast, Random(1)).uncles.groupingBy { it.face }.eachCount()
        assertEquals(cast.toSet(), counts.keys)
        assertTrue(counts.values.all { it in 4..5 })
    }

    @Test
    fun `tapping a calm uncle removes it`() {
        val (next, result) = round.tap(safeId)
        assertEquals(TapResult.Safe, result)
        assertFalse(next.isOnBoard(safeId))
        assertEquals(1, next.safeTaps)
        assertFalse(next.lost)
    }

    @Test
    fun `tapping the same uncle twice is ignored`() {
        val (once, _) = round.tap(safeId)
        val (twice, result) = once.tap(safeId)
        assertEquals(TapResult.Ignored, result)
        assertEquals(once, twice)
    }

    @Test
    fun `tapping the angry uncle loses and freezes the board`() {
        val (lost, result) = round.tap(round.angryId)
        assertEquals(TapResult.Angry, result)
        assertTrue(lost.lost)
        assertEquals(TapResult.Ignored, lost.tap(safeId).second)
    }

    @Test
    fun `angry uncle is the last one standing when everyone else is tapped`() {
        var r = round
        round.uncles.filter { it.id != round.angryId }.forEach { r = r.tap(it.id).first }
        assertEquals(1, r.remaining)
        assertTrue(r.isOnBoard(round.angryId))
    }
}
