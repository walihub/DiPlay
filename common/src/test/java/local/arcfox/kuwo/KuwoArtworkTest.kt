package local.arcfox.kuwo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class KuwoArtworkTest {
    private val context get() = RuntimeEnvironment.getApplication() as Context
    private fun metadata(id: String, uri: String) = MediaMetadata.Builder()
        .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, id)
        .putString(MediaMetadata.METADATA_KEY_TITLE, id)
        .putString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, uri).build()
    private fun png(width: Int = 4, height: Int = 4): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.GREEN)
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it); bitmap.recycle() }.toByteArray()
    }
    private class Tasks : Executor {
        val tasks = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { tasks.addLast(command) }
        fun drain() { while (tasks.isNotEmpty()) tasks.removeFirst().run() }
    }
    private fun controller(current: AtomicReference<MediaMetadata>): Pair<MediaController, MediaSession> {
        val session = MediaSession(context, "KuwoArtworkTest")
        val result = mock(MediaController::class.java)
        `when`(result.sessionToken).thenReturn(session.sessionToken)
        `when`(result.metadata).thenAnswer { current.get() }
        return result to session
    }

    @Test fun oneDecodeIsSharedByBothConsumersAndCallbackOnlyRequestsFreshRefresh() {
        val tasks = Tasks(); var reads = 0; var firstReady = 0; var secondReady = 0
        val engine = KuwoArtwork.Engine(tasks, { _, _ -> reads++; png(1024, 512) }, { 0 })
        val metadata = metadata("one", "https://original.example/cover.png")
        val current = AtomicReference(metadata); val (controller, session) = controller(current)
        try {
            assertNull(engine.get(context, controller, metadata) { firstReady++ })
            assertNull(engine.get(context, controller, metadata) { secondReady++ })
            assertEquals(1, tasks.tasks.size)
            tasks.drain()
            assertEquals(1, reads); assertEquals(1, firstReady); assertEquals(1, secondReady)
            val image = engine.get(context, controller, metadata, null)
            assertNotNull(image); assertEquals(512, image!!.width); assertEquals(256, image.height)
            assertSame(image, engine.get(context, controller, metadata, null)); assertEquals(1, reads)
        } finally { session.release() }
    }

    @Test fun oldTrackAndUriRequestsCannotNotifyOrReturnArtworkForTheNewTrack() {
        val tasks = Tasks(); var reads = 0; var oldReady = 0; var newReady = 0
        val engine = KuwoArtwork.Engine(tasks, { _, _ -> reads++; png() }, { 0 })
        val old = metadata("old", "https://original.example/old.png")
        val current = AtomicReference(old); val (controller, session) = controller(current)
        try {
            engine.get(context, controller, old) { oldReady++ }
            val fresh = metadata("new", "https://original.example/new.png"); current.set(fresh)
            assertNull(engine.get(context, controller, fresh) { newReady++ })
            tasks.drain()
            assertEquals(0, oldReady); assertEquals(1, newReady); assertEquals(1, reads)
            assertNotNull(engine.get(context, controller, fresh, null))
            // Same track, changed URI is a different request as well.
            val changed = metadata("new", "https://original.example/replaced.png"); current.set(changed)
            assertNull(engine.get(context, controller, changed, null));tasks.drain();assertEquals(2, reads)
            assertNull(engine.get(context, controller, fresh, null)) // Stale caller snapshot cannot return its cached old image.
        } finally { session.release() }
    }

    @Test fun successfulOldDownloadThatFinishesAfterTrackChangeDoesNotNotifyTheOldOwner() {
        val tasks = Tasks();var ready = 0
        val old = metadata("old", "https://original.example/old.png"); val current = AtomicReference(old)
        val (controller, session) = controller(current)
        val engine = KuwoArtwork.Engine(tasks, { _, _ -> current.set(metadata("new", "https://original.example/new.png"));png() }, { 0 })
        try {engine.get(context, controller, old) { ready++ };tasks.drain();assertEquals(0,ready)} finally {session.release()}
    }

    @Test fun malformedAndOversizeResponsesNeverSucceedAndAttemptsAreBounded() {
        val tasks = Tasks();var reads = 0;var time = 0L;var ready = 0
        val bad = metadata("bad", "https://original.example/bad.png");val current = AtomicReference(bad)
        val (controller, session) = controller(current)
        val engine = KuwoArtwork.Engine(tasks, { _, _ -> reads++; byteArrayOf(1,2,3) }, { time })
        try {
            repeat(10) {engine.get(context, controller, bad) {ready++}};tasks.drain();assertEquals(1,reads)
            engine.get(context, controller, bad, null);tasks.drain();assertEquals(1,reads)
            time=2000;engine.get(context, controller, bad, null);tasks.drain();assertEquals(2,reads)
            time=20000;repeat(10){engine.get(context, controller, bad, null)};tasks.drain();assertEquals(2,reads);assertEquals(0,ready)
            assertNull(KuwoArtwork.decode(ByteArray(KuwoArtwork.MAX_BYTES+1)))
            assertNull(KuwoArtwork.decode(png(3000,2000)))
            assertNull(KuwoArtwork.decode(png(8193,1)))
        } finally {session.release()}
    }

    @Test fun distinctSessionTokensNeverShareResultsEvenForTheSameTrackAndUri() {
        val tasks=Tasks();var reads=0
        val engine=KuwoArtwork.Engine(tasks,{_,_->reads++;png()},{0})
        val md=metadata("same","https://original.example/same.png");val current=AtomicReference(md)
        val(first,one)=controller(current);val(second,two)=controller(current)
        try{engine.get(context,first,md,null);engine.get(context,second,md,null);tasks.drain();assertEquals(2,reads)}finally{one.release();two.release()}
    }

    @Test fun productionReaderUsesTheOriginalHttpPathAndRejectsOversizeHeaders() {
        fun server(body: ByteArray, advertised: Int, action: (String, AtomicReference<String>) -> Unit) {
            val listener=java.net.ServerSocket(0,1,java.net.InetAddress.getByName("127.0.0.1"))
            val request=AtomicReference("")
            val thread=Thread {
                listener.accept().use { socket ->
                    val lines=socket.getInputStream().bufferedReader();request.set(lines.readLine())
                    while(!lines.readLine().isNullOrEmpty()) {}
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\nContent-Length: $advertised\r\nConnection: close\r\n\r\n".toByteArray())
                        write(body);flush()
                    }
                }
            }.apply {isDaemon=true;start()}
            try{action("http://127.0.0.1:${listener.localPort}/original-cover?provided=1",request)}finally{listener.close();thread.join(3000)}
        }
        val bytes=png()
        server(bytes,bytes.size) { original,request ->
            assertArrayEquals(bytes,KuwoArtwork.read(context,Uri.parse(original)))
            assertEquals("GET /original-cover?provided=1 HTTP/1.1",request.get())
        }
        server(byteArrayOf(),KuwoArtwork.MAX_BYTES+1) { original,_ ->
            assertThrows(java.io.IOException::class.java){KuwoArtwork.read(context,Uri.parse(original))}
        }
        val content=Uri.parse("content://original.kuwo.cover/artwork")
        org.robolectric.Shadows.shadowOf(context.contentResolver).registerInputStream(content,java.io.ByteArrayInputStream(bytes))
        assertArrayEquals(bytes,KuwoArtwork.read(context,content))
        val oversized=Uri.parse("content://original.kuwo.cover/oversized")
        org.robolectric.Shadows.shadowOf(context.contentResolver).registerInputStream(oversized,java.io.ByteArrayInputStream(ByteArray(KuwoArtwork.MAX_BYTES+1)))
        assertThrows(java.io.IOException::class.java){KuwoArtwork.read(context,oversized)}
    }

    @Test fun originalFileUriLoadsWithTheProductionReaderAndEmbeddedBitmapNeedsNoFetch() {
        val file = java.io.File.createTempFile("kuwo-artwork", ".png", context.cacheDir)
        file.writeBytes(png())
        try {assertNotNull(KuwoArtwork.decode(KuwoArtwork.read(context,Uri.fromFile(file))))}finally{file.delete()}
        val tasks=Tasks();var reads=0
        val engine=KuwoArtwork.Engine(tasks,{_,_->reads++;png()},{0})
        val bitmap=Bitmap.createBitmap(4,4,Bitmap.Config.ARGB_8888)
        val md=MediaMetadata.Builder().putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON,bitmap).build()
        val current=AtomicReference(md);val(controller,session)=controller(current)
        try{assertSame(bitmap,engine.get(context,controller,md,null));assertTrue(tasks.tasks.isEmpty());assertEquals(0,reads)}finally{session.release();bitmap.recycle()}
    }
}
