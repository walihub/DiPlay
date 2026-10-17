package com.shilapi.xcertplay

/** Only successfully decoded artwork enters this bounded replay cache. No nearest-id guessing. */
internal class NowPlayingArtworkPayloads(private val maximumEntries: Int = 4) {
    private val payloads = LinkedHashMap<Int, ByteArray>()
    fun decoded(id: Int, bytes: ByteArray?) {
        payloads.remove(id)
        if (bytes != null && bytes.isNotEmpty()) payloads[id] = bytes
        while (payloads.size > maximumEntries) payloads.remove(payloads.keys.first())
    }
    fun matching(id: Int?): ByteArray? = id?.let(payloads::get)
    fun remove(id: Int) { payloads.remove(id) }
    fun clear() = payloads.clear()
}
