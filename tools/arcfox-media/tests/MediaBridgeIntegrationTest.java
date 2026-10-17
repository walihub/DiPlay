import local.arcfox.media.MediaBridge;
import android.os.*;
import android.content.*;
import android.media.*;
import android.media.session.*;
import com.arcfox.media.center.callback.*;
import com.dudu.autoui.manage.music.p;
import java.lang.reflect.*;
public class MediaBridgeIntegrationTest {
 public static class Service implements IInterface {
  public int source=3,commands,snapshots;public Object reply; public IBinder asBinder(){return ()->true;}
  public int getLastMediaType(){return source;}
  public void getMediaInfo(int s,IMediaSysThirdCallback c){snapshots++;MediaBridge.info(c,s,reply==null?new Info():reply);}
  public void getPlayStatus(int s,IMediaSysThirdCallback c){}
  public void sendMediaEvent(int s,int event){commands++;}
 }
 public static class Sdk {private final Service mBinder=new Service();public void removeMediaSysThirdCallback(ArcfoxMediaSysThirdCallback cb){}}
 public static class Plugin {private final Sdk sdkInstance=new Sdk();private final ArcfoxMediaSysThirdCallback mediaCallback=new ArcfoxMediaSysThirdCallback();}
 public static class Info {public String getName(){return "Native";}public String getArtist(){return "Artist";}public String getCover(){return "native.jpg";}}
 static void check(boolean b){if(!b)throw new AssertionError(p.w().events.toString());}
 static void tick()throws Exception {Method m=MediaBridge.class.getDeclaredMethod("tick");m.setAccessible(true);m.invoke(null);Handler.flush();}
 static long count(String v){return p.w().events.stream().filter(v::equals).count();}
 static void tickQueued()throws Exception {Method m=MediaBridge.class.getDeclaredMethod("tick");m.setAccessible(true);m.invoke(null);}
 static void testPlayingSessionReplacement()throws Exception {
  Context ctx=new Context();Plugin plugin=new Plugin();com.dudu.autoui.common.e1.p0.selected=101;
  MediaController old=new MediaController();old.metadata.values.put(MediaMetadata.METADATA_KEY_TITLE,"old initial");old.metadata.values.put(MediaMetadata.METADATA_KEY_ALBUM_ART,new android.graphics.Bitmap());ctx.manager.sessions.add(old);
  MediaBridge.start(ctx,plugin);Handler.flush();p.w().events.clear();
  old.metadata.values.put(MediaMetadata.METADATA_KEY_TITLE,"old pending text");tickQueued();MediaBridge.command(6);
  MediaController fresh=new MediaController();fresh.metadata.values.put(MediaMetadata.METADATA_KEY_TITLE,"new session text");fresh.metadata.values.put(MediaMetadata.METADATA_KEY_ALBUM_ART,new android.graphics.Bitmap());
  ctx.manager.sessions.clear();ctx.manager.sessions.add(fresh);tickQueued();Handler.flush();
  check(!p.w().events.contains("info:old pending text"));check(p.w().events.contains("info:new session text"));check(old.controls.next==0);
  check(p.w().lyricVisible);check(p.w().lyricRows.size()==1);com.dudu.autoui.common.y0.c.a row=(com.dudu.autoui.common.y0.c.a)p.w().lyricRows.get(0);check(row.d.equals("new session text"));
  check(count("bitmap")==1);MediaBridge.command(6);Handler.flush();check(fresh.controls.next==1);
  plugin.sdkInstance.mBinder.source=5;tick();p.w().events.clear();
  MediaController noSteal=new MediaController();noSteal.metadata.values.put(MediaMetadata.METADATA_KEY_TITLE,"new token must not steal native source");ctx.manager.sessions.clear();ctx.manager.sessions.add(noSteal);tick();
  check(!p.w().events.contains("info:new token must not steal native source"));MediaBridge.command(6);Handler.flush();check(noSteal.controls.next==0);check(plugin.sdkInstance.mBinder.commands==1);MediaBridge.stop();
 }
 static void testCarPlayCurrentText()throws Exception {
  Context ctx=new Context();Plugin plugin=new Plugin();com.dudu.autoui.common.e1.p0.selected=101;
  MediaController phone=new MediaController();phone.metadata.values.put(MediaMetadata.METADATA_KEY_TITLE,"received line 1");ctx.manager.sessions.add(phone);
  MediaBridge.start(ctx,plugin);Handler.flush();
  check(p.w().lyricRows.size()==1);com.dudu.autoui.common.y0.c.a row=(com.dudu.autoui.common.y0.c.a)p.w().lyricRows.get(0);check(row.b==0&&row.c==Long.MAX_VALUE&&row.d.equals("received line 1"));check(p.w().lyricVisible);
  long changes=p.w().events.stream().filter(e->e.startsWith("lyrics:")).count();tick();check(p.w().events.stream().filter(e->e.startsWith("lyrics:")).count()==changes);
  phone.metadata.values.put(MediaMetadata.METADATA_KEY_TITLE,"received line 2");tick();check(p.w().lyricRows.size()==1);row=(com.dudu.autoui.common.y0.c.a)p.w().lyricRows.get(0);check(row.d.equals("received line 2"));check(p.w().events.contains("info:received line 2"));check(p.w().lyricVisible);check(p.w().events.lastIndexOf("info:received line 2")<p.w().events.lastIndexOf("lyrics:1"));
  phone.metadata.values.put(MediaMetadata.METADATA_KEY_ALBUM,"metadata changed, same received text");tick();check(p.w().lyricVisible);
  phone.metadata.values.put(MediaMetadata.METADATA_KEY_TITLE,"z".repeat(3000));tick();row=(com.dudu.autoui.common.y0.c.a)p.w().lyricRows.get(0);check(row.d.length()==2048);
  phone.metadata.values.put(MediaMetadata.METADATA_KEY_TITLE,"");tick();check(p.w().lyricRows.isEmpty());
  phone.metadata.values.put(MediaMetadata.METADATA_KEY_TITLE,"before source change");tick();plugin.sdkInstance.mBinder.source=5;tick();check(p.w().lyricRows.isEmpty());
  ctx.manager.sessions.clear();tick();check(p.w().lyricRows.isEmpty());MediaBridge.stop();
 }
 static void testNativeLyrics()throws Exception {
  Context ctx=new Context();Plugin plugin=new Plugin();com.dudu.autoui.common.e1.p0.selected=101;
  MediaBridge.start(ctx,plugin);Handler.flush();p.w().events.clear();
  NativeLyricsTest.Track one=new NativeLyricsTest.Track("one",java.util.Arrays.asList(new NativeLyricsTest.LyricRow("00:01.250","one line"),new NativeLyricsTest.LyricRow("00:04.500","second line")));
  MediaBridge.info(plugin.mediaCallback,3,one);MediaBridge.lyric(plugin.mediaCallback,3,one);Handler.flush();
  check(p.w().lyricRows.size()==2);check(p.w().lyricOffset==0);check(p.w().lyricVisible);check(p.w().events.lastIndexOf("info:one")<p.w().events.lastIndexOf("lyrics:2"));
  com.dudu.autoui.common.y0.c.a row=(com.dudu.autoui.common.y0.c.a)p.w().lyricRows.get(0);check(row.b==1250&&row.c==4500&&row.d.equals("one line")&&row.a.equals("00:01.250"));
  NativeLyricsTest.Track changedCover=new NativeLyricsTest.Track("one",one.rows){public String getCover(){return "updated-cover.png";}};
  MediaBridge.info(plugin.mediaCallback,3,changedCover);Handler.flush();check(p.w().lyricVisible);
  long updates=p.w().events.stream().filter(e->e.startsWith("lyrics:")).count();
  MediaBridge.lyric(plugin.mediaCallback,3,one);MediaBridge.lyric(plugin.mediaCallback,4,new NativeLyricsTest.Track("other",null));Handler.flush();check(p.w().events.stream().filter(e->e.startsWith("lyrics:")).count()==updates);
  NativeLyricsTest.Track anonymous=new NativeLyricsTest.Track("",java.util.Arrays.asList(new NativeLyricsTest.LyricRow("00:01.000","late anonymous old text")));
  plugin.sdkInstance.mBinder.reply=one;int snapshots=plugin.sdkInstance.mBinder.snapshots;
  MediaBridge.lyric(plugin.mediaCallback,3,anonymous);Handler.flush();check(plugin.sdkInstance.mBinder.snapshots==snapshots+1);row=(com.dudu.autoui.common.y0.c.a)p.w().lyricRows.get(0);check(row.d.equals("one line"));
  MediaBridge.lyric(plugin.mediaCallback,3,anonymous);Handler.flush();check(plugin.sdkInstance.mBinder.snapshots==snapshots+1);
  NativeLyricsTest.Track two=new NativeLyricsTest.Track("two",null);plugin.sdkInstance.mBinder.reply=two;MediaBridge.info(plugin.mediaCallback,3,two);Handler.flush();check(p.w().lyricRows.isEmpty());
  MediaBridge.lyric(plugin.mediaCallback,3,one);Handler.flush();check(p.w().lyricRows.isEmpty());
  MediaBridge.progress(plugin.mediaCallback,3,2345,10000,"live line");Handler.flush();check(p.w().lyricRows.size()==1);row=(com.dudu.autoui.common.y0.c.a)p.w().lyricRows.get(0);check(row.d.equals("live line"));check(p.w().events.contains("progress:2345:10000"));
  updates=p.w().events.stream().filter(e->e.startsWith("lyrics:")).count();MediaBridge.progress(plugin.mediaCallback,3,2450,10000,"live line");Handler.flush();check(p.w().events.stream().filter(e->e.startsWith("lyrics:")).count()==updates);
  plugin.sdkInstance.mBinder.source=5;tick();check(p.w().lyricRows.isEmpty());MediaBridge.lyric(plugin.mediaCallback,3,one);Handler.flush();check(p.w().lyricRows.isEmpty());
  MediaBridge.stop();
 }
 static Intent yieldIntent(long observed,String origin){return new Intent("com.shihab.diplay.action.ARCFOX_CARPLAY_MEDIA_YIELDED").putExtra("origin",origin).putExtra("observedAt",observed);}
 static void testYield()throws Exception {
  Context ctx=new Context();Plugin plugin=new Plugin();com.dudu.autoui.common.e1.p0.selected=101;
  MediaController phone=new MediaController();phone.metadata.values.put(MediaMetadata.METADATA_KEY_TITLE,"YieldPhone");ctx.manager.sessions.add(phone);
  MediaBridge.start(ctx,plugin);Handler.flush();check(ctx.receiverPermission.equals("com.shihab.diplay.permission.ARCFOX_MEDIA_COORDINATION"));
  phone.state.state=4;tick();p.w().events.clear();int before=plugin.sdkInstance.mBinder.snapshots;
  ctx.deliver(yieldIntent(900,"com.shihab.diplay"));Handler.flush();
  check(plugin.sdkInstance.mBinder.snapshots==before+1);check(p.w().events.contains("info:Native"));
  tick();check(p.w().events.get(p.w().events.size()-1).equals("info:Native")||!p.w().events.contains("info:YieldPhone"));
  before=plugin.sdkInstance.mBinder.snapshots;ctx.deliver(yieldIntent(900,"com.shihab.diplay"));Handler.flush();check(plugin.sdkInstance.mBinder.snapshots==before);
  phone.state.state=3;tick();p.w().events.clear();
  ctx.deliver(yieldIntent(900,"com.shihab.diplay"));Handler.flush();SystemClock.now=20000;ctx.deliver(yieldIntent(9000,"com.shihab.diplay"));Handler.flush();SystemClock.now=1000;ctx.deliver(yieldIntent(1001,"com.shihab.diplay"));ctx.deliver(yieldIntent(950,"wrong.origin"));Handler.flush();check(plugin.sdkInstance.mBinder.snapshots==before);check(p.w().events.isEmpty());
  BroadcastReceiver old=ctx.receivers.get(0);MediaBridge.stop();Plugin next=new Plugin();MediaBridge.start(ctx,next);Handler.flush();int fresh=next.sdkInstance.mBinder.snapshots;
  old.onReceive(ctx,yieldIntent(999,"com.shihab.diplay"));Handler.flush();check(next.sdkInstance.mBinder.snapshots==fresh);
  ctx.manager.sessions.clear();tick();fresh=next.sdkInstance.mBinder.snapshots;ctx.deliver(yieldIntent(999,"com.shihab.diplay"));Handler.flush();check(next.sdkInstance.mBinder.snapshots==fresh);
  MediaBridge.stop();
 }
 public static void main(String[] args)throws Exception {
  Context ctx=new Context();Plugin first=new Plugin();MediaBridge.start(ctx,first);Handler.flush();p.w().events.clear();
  MediaBridge.info(first.mediaCallback,3,new Info());Handler.flush();check(p.w().events.isEmpty());check(com.dudu.autoui.common.e1.p0.selected==7);
  com.dudu.autoui.common.e1.p0.selected=101;
  MediaBridge.info(first.mediaCallback,4,new Info());Handler.flush();check(p.w().events.isEmpty());
  MediaBridge.info(first.mediaCallback,3,new Info());MediaBridge.state(first.mediaCallback,3,3);MediaBridge.progress(first.mediaCallback,3,100,1000);Handler.flush();
  check(p.w().events.contains("info:Native"));check(p.w().events.contains("state:true"));check(p.w().events.contains("progress:100:1000"));
  com.dudu.autoui.common.e1.p0.selected=7;MediaController phone=new MediaController();phone.metadata.values.put(MediaMetadata.METADATA_KEY_TITLE,"Phone");phone.metadata.values.put(MediaMetadata.METADATA_KEY_DURATION,2000L);phone.metadata.values.put(MediaMetadata.METADATA_KEY_ALBUM_ART,new android.graphics.Bitmap());ctx.manager.sessions.add(phone);tick();
  check(p.w().events.contains("info:Phone"));check(com.dudu.autoui.common.e1.p0.selected==101);check(p.w().selections==1);check(count("bitmap")>0);check(!p.w().events.contains("cover:arcfox-diplay"));long art=count("bitmap");tick();check(count("bitmap")==art);check(p.w().selections==1);
  MediaBridge.state(first.mediaCallback,3,4);Handler.flush();
  MediaBridge.command(6);Handler.flush();check(phone.controls.next==1);
  phone.state.actions=0;MediaBridge.command(6);Handler.flush();check(phone.controls.next==1);phone.state.actions=54;
  ctx.manager.sessions.clear();tick();Handler.flushDelayed();check(com.dudu.autoui.common.e1.p0.selected==7);
  phone.state.state=4;ctx.manager.sessions.add(phone);tick();phone.state.state=3;tick();check(com.dudu.autoui.common.e1.p0.selected==101);
  first.sdkInstance.mBinder.source=5;tick();check(ctx.broadcasts.size()==1);check(ctx.broadcasts.get(0).extras.get("sourceId").equals(5));
  tick();check(ctx.broadcasts.size()==1);MediaBridge.info(first.mediaCallback,3,new Info());Handler.flush();
  MediaBridge.command(6);first.sdkInstance.mBinder.source=6;Handler.flush();check(first.sdkInstance.mBinder.commands==0);
  MediaBridge.info(first.mediaCallback,5,new Info()); // queued before exit must not leak into the next instance
  MediaBridge.stop();Plugin second=new Plugin();MediaBridge.start(ctx,second);Handler.flush();p.w().events.clear();
  MediaBridge.info(first.mediaCallback,3,new Info());MediaBridge.progress(first.mediaCallback,3,999,999);Handler.flush();check(p.w().events.isEmpty());
  ctx.manager.sessions.clear();tick();p.w().events.clear();
  second.sdkInstance.mBinder.source=-1;tick();check(p.w().events.contains("info:"));
  MediaBridge.stop();testYield();testNativeLyrics();testCarPlayCurrentText();testPlayingSessionReplacement();System.out.println("MediaBridgeIntegrationTest passed (compiled bridge, real reflection signatures, owner filtering, artwork dedup, commands/actions, native broadcast, stale callback, idle clear, signed yield receiver/source unchanged/paused/duplicates/expiry/old generation, PLAYING token replacement invalidates queued metadata/lyrics/control)");
 }
}
