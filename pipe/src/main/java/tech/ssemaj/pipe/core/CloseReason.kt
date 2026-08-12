package tech.ssemaj.pipe.core

enum class CloseReason(private val wire: Int) {
    HOST_CLOSED(0), PROVIDER_CLOSED(1), PEER_DIED(2);

    fun toWire(): Int = wire

    companion object {
        fun fromWire(v: Int): CloseReason = entries.firstOrNull { it.wire == v } ?: PEER_DIED
    }
}
