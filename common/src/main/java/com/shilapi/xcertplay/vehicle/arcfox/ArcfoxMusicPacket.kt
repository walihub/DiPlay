package com.shilapi.xcertplay.vehicle.arcfox

/**
 * A local snapshot. [cover] retains current bytes for native service/file recovery; [includeCover] requests a
 * replace/clear of the native cached file rather than retransmitting artwork every progress tick.
 */
internal data class ArcfoxMusicPacket(
    val song: ArcfoxMusicSync.Song,
    val progressMs: Long,
    val cover: ByteArray?,
    val includeCover: Boolean,
)

/** A dispatch is not an acknowledgement from the instrument cluster. */
internal enum class ArcfoxMusicDispatch { UNAVAILABLE, DISPATCHED }
