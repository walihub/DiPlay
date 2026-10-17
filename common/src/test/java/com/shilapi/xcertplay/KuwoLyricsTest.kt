package com.shilapi.xcertplay
import local.arcfox.kuwo.KuwoLyrics
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class KuwoLyricsTest {
 private val json = """{"resultCode":20000,"AUDIO_LYRIC":[{"startTime":1000,"time":2000,"text":"第一行"},{"startTime":4000,"time":1000,"text":"Second"}]}"""
 @Test fun millisecondsAndDuduModelArePreservedAndSeekCanGoBackward() {
  val rows = KuwoLyrics.parse(json)
  assertEquals(2, rows.size); assertEquals(1000L,rows[0].start);assertEquals(3000L,rows[0].end)
  assertEquals(-1,KuwoLyrics.lineAt(rows,500));assertEquals(0,KuwoLyrics.lineAt(rows,1500))
  assertEquals(-1,KuwoLyrics.lineAt(rows,3500));assertEquals(1,KuwoLyrics.lineAt(rows,4500))
  assertEquals(0,KuwoLyrics.lineAt(rows,1500))
  val model = KuwoLyrics.model(rows)[0] as com.dudu.autoui.common.y0.c.a
  assertEquals(1000L,model.start);assertEquals(3000L,model.end);assertEquals("第一行",model.text)
 }
 @Test fun invalidEmptyAndFailurePayloadsClearRatherThanRetainOldLines() {
  for (bad in listOf("", "{}", "not json", json.replace("20000","20003"),json.replace("2000","-1"),json.replace("1000","2147483648"))) assertTrue(KuwoLyrics.parse(bad).isEmpty())
  val state=KuwoLyrics();state.track("A");assertTrue(state.update("A",json));assertEquals(2,state.rows().size)
  assertFalse(state.update("A",json));assertFalse(state.update("B",json))
  assertTrue(state.update("A",""));assertTrue(state.rows().isEmpty())
  state.update("A",json);state.track("B");assertTrue(state.rows().isEmpty());assertFalse(state.update("A",json))
 }
 @Test fun originalExtrasRejectRetainedAndKnownOldPayloadsButAcceptFreshCurrentContent() {
  val state=KuwoLyrics()
  state.track("A", json)
  assertFalse(state.original("A",json)) // Initial retained extras have no provable owner.
  val freshA=json.replace("第一行","A-new")
  assertTrue(state.original("A",freshA));assertEquals("A-new",state.rows()[0].text)
  state.track("B",freshA);assertTrue(state.rows().isEmpty())
  assertFalse(state.original("B",freshA));assertFalse(state.original("A",json))
  val freshB=json.replace("第一行","B-new")
  assertTrue(state.original("B",freshB));assertEquals("B-new",state.rows()[0].text)
  assertFalse(state.original("B",freshA)) // Known old callback cannot restore previous song.
 }
 @Test fun failureClearsAndLocalSongsUseFullTrackIdentityRatherThanOnlyRid() {
  val state=KuwoLyrics();state.track("0|Local A|Singer")
  assertTrue(state.original("0|Local A|Singer",json));state.track("0|Local B|Singer",json)
  assertTrue(state.rows().isEmpty());assertFalse(state.original("0|Local A|Singer",json))
  val next=json.replace("第一行","B");assertTrue(state.original("0|Local B|Singer",next))
  assertTrue(state.original("0|Local B|Singer", "{\"resultCode\":20002,\"AUDIO_LYRIC\":[]}"));assertTrue(state.rows().isEmpty())
 }
 @Test fun oneInitialSnapshotCanShowAlreadyLoadedLyricsButNextTrackStillRejectsRetainedCache() {
  val state=KuwoLyrics();state.track("A",json)
  assertTrue(state.snapshot("A",json));assertEquals(2,state.rows().size)
  state.track("B",json);assertTrue(state.rows().isEmpty())
  assertFalse(state.original("B",json))
  assertTrue(state.original("B",json.replace("第一行","B-new")))
 }

}
