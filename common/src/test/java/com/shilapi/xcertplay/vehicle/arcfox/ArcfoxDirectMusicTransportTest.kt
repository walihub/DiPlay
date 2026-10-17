package com.shilapi.xcertplay.vehicle.arcfox

import android.graphics.Bitmap
import android.media.AudioManager
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArcfoxDirectMusicTransportTest {
    private fun packet(cover: ByteArray? = null, replace: Boolean = false, progress: Long = 1501L) =
        ArcfoxMusicPacket(ArcfoxMusicSync.Song("Song", "Artist", "Album", 5500, true, 1), progress, cover, replace)

    @Test fun ringingCommunicationAndVoiceYieldToOemTransport() {
        assertTrue(ArcfoxDirectMusicTransport.canPublishDirect(AudioManager.MODE_NORMAL, false))
        assertFalse(ArcfoxDirectMusicTransport.canPublishDirect(AudioManager.MODE_NORMAL, true))
        assertFalse(ArcfoxDirectMusicTransport.canPublishDirect(AudioManager.MODE_RINGTONE, false))
        assertFalse(ArcfoxDirectMusicTransport.canPublishDirect(AudioManager.MODE_IN_CALL, false))
        assertFalse(ArcfoxDirectMusicTransport.canPublishDirect(AudioManager.MODE_IN_COMMUNICATION, false))
        assertFalse(ArcfoxDirectMusicTransport.canPublishDirect(999, false))
    }

    @Test fun encodesProvenInstrumentProtocolAndRetainsCoverOnProgress() {
        val sent = mutableListOf<JSONObject>()
        val sender = ArcfoxDirectMusicSender({ sent += JSONObject(it); true }, { "diplay_music_test.png" })
        assertTrue(sender.send(packet(byteArrayOf(1), true), false))
        assertTrue(sender.send(packet(progress = 3500), true))
        val first = sent[0].getJSONObject("content")
        assertEquals("meidainfo", sent[0].getJSONObject("command").getString("id"))
        assertEquals(19, first.getInt("mediatype"))
        assertEquals(5, first.getInt("totalplaytime"))
        assertEquals(2, first.getInt("curplaytime"))
        assertEquals("play", first.getString("status"))
        assertEquals(1, first.getInt("mute"))
        assertEquals("diplay_music_test.png", sent[1].getJSONObject("content").getString("albumpic"))
        assertEquals(0, sent[1].getJSONObject("content").getInt("mute"))
        assertEquals(3, sent[1].getJSONObject("content").getInt("curplaytime"))
    }

    @Test fun explicitCoverClearAndProgressAreBounded() {
        val sent = mutableListOf<JSONObject>()
        val sender = ArcfoxDirectMusicSender({ sent += JSONObject(it); true }, { "diplay_music_test.png" })
        sender.send(packet(byteArrayOf(1), true), false)
        assertTrue(sender.send(packet(replace = true, progress = Long.MAX_VALUE), false))
        assertEquals("", sent.last().getJSONObject("content").getString("albumpic"))
        assertEquals(5, sent.last().getJSONObject("content").getInt("curplaytime"))
    }

    @Test fun unavailableRejectedAndFailedTransportReturnFallback() {
        var calls = 0
        val unavailable = ArcfoxDirectMusicSender({ calls++; false }, { error("cover denied") })
        assertFalse(unavailable.send(packet(byteArrayOf(1), true), false))
        assertEquals(1, calls)
        assertFalse(unavailable.send(packet(), false))
        assertEquals(2, calls)
        val failed = ArcfoxDirectMusicSender({ throw SecurityException("denied") }, { "unused" })
        assertFalse(failed.send(packet(), false))
    }

    @Test fun failedArtworkDoesNotFreezeFixedNativeRouteAndRecovers() {
        val sent = mutableListOf<JSONObject>()
        var available = false
        val sender = ArcfoxDirectMusicSender({ sent += JSONObject(it); true }, {
            if (!available) throw java.io.IOException("directory not writable")
            "diplay_music_recovered.png"
        })
        assertTrue(sender.send(packet(), false)) // First text packet fixes the native route.
        assertTrue(sender.send(packet(byteArrayOf(1), true), false))
        assertTrue(sender.send(packet(byteArrayOf(1), progress = 3501), false))
        assertEquals(3, sent.size)
        assertEquals("", sent[1].getJSONObject("content").getString("albumpic"))
        assertEquals(4, sent[2].getJSONObject("content").getInt("curplaytime"))
        available = true
        assertTrue(sender.send(packet(byteArrayOf(1), progress = 4501), false))
        assertEquals("diplay_music_recovered.png", sent.last().getJSONObject("content").getString("albumpic"))
    }

    @Test fun failedArtworkOnNextSongCannotRetainPreviousArtwork() {
        val sent = mutableListOf<JSONObject>()
        var available = true
        val sender = ArcfoxDirectMusicSender({ sent += JSONObject(it); true }, {
            if (!available) throw IllegalArgumentException("unsupported image")
            "diplay_music_previous.png"
        })
        assertTrue(sender.send(packet(byteArrayOf(1), true), false))
        available = false
        val next = packet(byteArrayOf(2), true).let { it.copy(song = it.song.copy(title = "Next", artworkId = 2)) }
        assertTrue(sender.send(next, false))
        assertEquals("Next", sent.last().getJSONObject("content").getString("songname"))
        assertEquals("", sent.last().getJSONObject("content").getString("albumpic"))
    }

    @Test fun eachPacketCanRecoverMissingCoverAndRetryFailedSend() {
        var writes = 0
        var succeed = false
        val sender = ArcfoxDirectMusicSender({ succeed }, { writes++; "diplay_music_test.png" })
        assertFalse(sender.send(packet(byteArrayOf(1), true), false))
        succeed = true
        assertTrue(sender.send(packet(byteArrayOf(1)), false))
        assertEquals(2, writes)
    }

    @Test fun coverStoreWritesPngOnceAndRecoversDeletedFile() {
        val directory = Files.createTempDirectory("arcfox-cover-test").toFile()
        try {
            val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
            val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            bitmap.recycle()
            val store = ArcfoxMusicCoverStore(directory)
            val name = store.write(bytes)
            val file = java.io.File(directory, name)
            assertTrue(file.isFile)
            val modified = file.lastModified()
            assertEquals(name, store.write(bytes))
            assertEquals(modified, file.lastModified())
            assertTrue(file.delete())
            assertEquals(name, store.write(bytes))
            assertTrue(file.length() > 0L)
            assertEquals(1, directory.listFiles()!!.size)
        } finally { directory.deleteRecursively() }
    }

    @Test fun corruptCachedCoverIsReplacedRatherThanReportedAsReady() {
        val directory = Files.createTempDirectory("arcfox-cover-corrupt").toFile()
        try {
            val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
            val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            bitmap.recycle()
            val store = ArcfoxMusicCoverStore(directory)
            val name = store.write(bytes)
            val target = java.io.File(directory, name)
            target.writeText("not an image")
            assertEquals(name, store.write(bytes))
            val repaired = android.graphics.BitmapFactory.decodeFile(target.absolutePath)
            assertNotNull(repaired)
            repaired?.recycle()
        } finally { directory.deleteRecursively() }
    }

    @Test fun invalidPayloadCannotSucceedBecauseItsCacheFilenameExists() {
        val directory = Files.createTempDirectory("arcfox-cover-invalid").toFile()
        try {
            val bytes = byteArrayOf(1, 2, 3)
            val digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
            java.io.File(directory, "${ArcfoxMusicCoverStore.PREFIX}$digest.png").writeText("not an image")
            assertThrows(IllegalArgumentException::class.java) { ArcfoxMusicCoverStore(directory).write(bytes) }
        } finally { directory.deleteRecursively() }
    }

    @Test fun invalidCoverIsRejectedWithoutDeletingForeignFiles() {
        val directory = Files.createTempDirectory("arcfox-cover-test").toFile()
        try {
            val foreign = java.io.File(directory, "native_album.png").apply { writeText("untouched") }
            assertThrows(IllegalArgumentException::class.java) { ArcfoxMusicCoverStore(directory).write(byteArrayOf(1)) }
            assertEquals("untouched", foreign.readText())
            assertEquals(1, directory.listFiles()!!.size)
        } finally { directory.deleteRecursively() }
    }

    @Test fun unsafeCoverPathNeverDispatches() {
        val sender = ArcfoxDirectMusicSender({ fail("unsafe path dispatched"); true }, { "../foreign.png" })
        assertFalse(sender.send(packet(byteArrayOf(1), true), false))
    }
}
