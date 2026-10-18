package cn.ifafu.ifafu.ui.timetable

/** Interval partitioning. Adjacent events share a lane; simultaneous events never cover one another. */
object TimetableLanes {
    data class Block(val id: Int, val day: Int, val start: Int, val end: Int, val agenda: Boolean)
    data class Placement(val id: Int, val left: Float, val width: Float)
    @JvmStatic fun arrange(blocks: List<Block>): List<Placement> = blocks.groupBy { it.day }.values.flatMap { day ->
        val result = mutableListOf<Placement>()
        var group = mutableListOf<Block>()
        var end = -1
        fun flush() {
            if (group.isEmpty()) return
            val laneEnds = mutableListOf<Int>()
            val lanes = mutableMapOf<Int, Int>()
            val weights = mutableListOf<Float>()
            group.sortedWith(compareBy<Block> { it.start }.thenBy { it.agenda }.thenBy { it.id }).forEach { b ->
                var lane = laneEnds.indexOfFirst { it <= b.start }
                if (lane < 0) { lane = laneEnds.size; laneEnds.add(0); weights.add(0f) }
                laneEnds[lane] = b.end; lanes[b.id] = lane
                weights[lane] = maxOf(weights[lane], if (b.agenda) 1f else 2.3f)
            }
            val sum = weights.sum()
            group.forEach { b ->
                val lane = lanes.getValue(b.id)
                result += Placement(b.id, weights.take(lane).sum() / sum, weights[lane] / sum)
            }
            group = mutableListOf(); end = -1
        }
        day.sortedWith(compareBy<Block> { it.start }.thenBy { it.id }).forEach { b ->
            if (b.start >= end) flush()
            group.add(b); end = maxOf(end, b.end)
        }
        flush(); result
    }
}
