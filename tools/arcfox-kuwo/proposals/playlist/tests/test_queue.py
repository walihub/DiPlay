#!/usr/bin/env python3
"""Execute production queue publisher against exact-original ABI fixtures, no APK/Gradle."""
from pathlib import Path
import tempfile,subprocess,sys,shutil
ROOT=Path(__file__).resolve().parents[5]
sys.path.insert(0,str(ROOT/'tools/arcfox-kuwo/proposals/playlist'))
from queue_patch import patch_kuwo,patch_dudu
KW=ROOT/'build/kuwo-analysis/decoded';DU=ROOT/'build/kuwo-analysis/dudu'
# Verify ABI against input APK, not only the fixtures.
checks={
 'cn/kuwo/mod/playcontrol/PlayerStateManager.smali':['public static u0()','public w0()Ljava/util/List;','public y0()','->q()I'],
 'cn/kuwo/kwmusiccar/util/p0.smali':['public static E()','public i0(Lcn/kuwo/base/bean/Music;)V','->N(Lcn/kuwo/base/bean/Music;)V','->w2(Lcn/kuwo/base/bean/MusicList;II)Z'],
 'android/support/v4/media/MediaDescriptionCompat$b.smali':['public f(Ljava/lang/String;)','public h(Ljava/lang/CharSequence;)','public i(Ljava/lang/CharSequence;)','public c(Landroid/os/Bundle;)','public a()'],
 'android/support/v4/media/session/MediaSessionCompat$c$b.smali':['public f(J)V','->A(J)V'],
 'cn/kuwo/base/bean/Music.smali':['.field public h:J','.field public i:Ljava/lang/String;','.field public j:Ljava/lang/String;']}
for path,signatures in checks.items():
 text=(KW/'smali'/path).read_text()
 for signature in signatures:assert signature in text,(path,signature)
assert 'LrcRow' in (DU/'smali/com/dudu/autoui/common/y0/c/a.smali').read_text()
assert '.method public e()Ljava/util/List;' in (DU/'smali/com/dudu/autoui/manage/music/l.smali').read_text()
java=Path('/Users/h/Documents/arcfox/.android-tools/jdk21/Contents/Home/bin')
fixtures={
'android/os/Build.java':'package android.os; public class Build { public static String FINGERPRINT="AAOP_COCKPIT/test"; }',
'android/os/Looper.java':'package android.os; public class Looper { public static Looper getMainLooper(){return new Looper();} }',
'android/os/Handler.java':'package android.os; public class Handler {public Handler(Looper l){} public void post(Runnable r){r.run();}}',
'android/os/Bundle.java':'package android.os; public class Bundle {public boolean full;public void putBoolean(String k,boolean v){full=v;}}',
'android/util/Log.java':'package android.util; public class Log {public static int w(String t,String s,Throwable e){return 0;}}',
'cn/kuwo/base/bean/Music.java':'package cn.kuwo.base.bean; public class Music {public long h;public String i,j;public Music(long id,String t){h=id;i=t;j="Artist";}}',
'cn/kuwo/mod/playcontrol/PlayerStateManager.java':'''package cn.kuwo.mod.playcontrol; import java.util.*; public class PlayerStateManager { public static PlayerStateManager instance=new PlayerStateManager();public List<Object> list=new ArrayList<>();public int type=1;public static PlayerStateManager u0(){return instance;}public List<Object> w0(){return list;}public PlayerStateManager y0(){return this;}public int q(){return type;}}''',
'cn/kuwo/kwmusiccar/util/p0.java':'package cn.kuwo.kwmusiccar.util; import cn.kuwo.base.bean.Music; public class p0 {static p0 instance=new p0();public static Music selected;public static p0 E(){return instance;}public void i0(Music music){selected=music;}}',
'android/support/v4/media/MediaDescriptionCompat.java':'''package android.support.v4.media; import android.os.Bundle; public class MediaDescriptionCompat {public String id;public CharSequence title,artist;public Bundle extras;public static class b {MediaDescriptionCompat d=new MediaDescriptionCompat();public b f(String id){d.id=id;return this;}public b i(CharSequence t){d.title=t;return this;}public b h(CharSequence a){d.artist=a;return this;}public b c(Bundle x){d.extras=x;return this;}public MediaDescriptionCompat a(){return d;}}}''',
'android/support/v4/media/session/MediaSessionCompat.java':'''package android.support.v4.media.session; import java.util.*; import android.support.v4.media.MediaDescriptionCompat;public class MediaSessionCompat {public List<QueueItem> queue;public void n(List<QueueItem> items){queue=items;}public static class QueueItem {public MediaDescriptionCompat d;public long id;public QueueItem(MediaDescriptionCompat d,long id){this.d=d;this.id=id;}}}''',
'Test.java':'''import local.arcfox.kuwo.KuwoQueueBridge;import android.support.v4.media.session.MediaSessionCompat;import cn.kuwo.mod.playcontrol.PlayerStateManager;import cn.kuwo.base.bean.Music;import cn.kuwo.kwmusiccar.util.p0;import java.util.*;public class Test {static void ok(boolean b){if(!b)throw new AssertionError();}public static void main(String[] args){PlayerStateManager state=PlayerStateManager.u0();Music a=new Music(11,"First"),b=new Music(22,"Second");state.list=new ArrayList<>(Arrays.asList(a,b));MediaSessionCompat session=new MediaSessionCompat();ok(KuwoQueueBridge.publish(session));ok(session.queue.size()==2);ok(session.queue.get(0).d.extras.full);long id=session.queue.get(1).id;KuwoQueueBridge.select(id);ok(p0.selected==b);p0.selected=null;state.list=new ArrayList<>(Arrays.asList(b,a));KuwoQueueBridge.select(id);ok(p0.selected==null);ok(session.queue.get(1).id!=id);KuwoQueueBridge.select(id);ok(p0.selected==null);KuwoQueueBridge.select(session.queue.get(1).id);ok(p0.selected==a);long unchanged=session.queue.get(0).id;KuwoQueueBridge.publish(session);ok(session.queue.get(0).id==unchanged);state.type=5;KuwoQueueBridge.publish(session);ok(session.queue.isEmpty());android.os.Build.FINGERPRINT="BYD";ok(!KuwoQueueBridge.publish(session));System.out.println("Queue real-list/stale-id/reorder/same-list/nonmusic/nonArcfox checks passed");}}'''}
with tempfile.TemporaryDirectory(prefix='kuwo-queue-check-') as directory:
 temp=Path(directory)
 for path,text in fixtures.items():
  p=temp/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text)
 source=ROOT/'tools/arcfox-kuwo/proposals/playlist/java/local/arcfox/kuwo/KuwoQueueBridge.java'
 subprocess.run([str(java/'javac'),'-d',str(temp),str(source),*[str(p) for p in temp.rglob('*.java')]],check=True)
 subprocess.run([str(java/'java'),'-cp',str(temp),'Test'],check=True)
 for source_base,paths,patch in [(KW,['cn/kuwo/mod/mediaSession/KwMediaSessionService.smali','cn/kuwo/mod/mediaSession/KwMediaSessionService$d.smali'],patch_kuwo),(DU,['com/dudu/autoui/manage/music/plugin/n1.smali'],patch_dudu)]:
  for path in paths:
   p=temp/'smali'/path;p.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source_base/'smali'/path,p)
  patch(temp)
print('PASS: exact APK queue/selection ABI, both patch functions, production queue lifecycle')
