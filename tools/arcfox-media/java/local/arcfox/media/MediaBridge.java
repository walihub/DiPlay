package local.arcfox.media;
import android.content.Context;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.media.MediaMetadata;
import android.media.session.*;
import android.os.*;
import android.graphics.Bitmap;
import android.util.Log;
import java.lang.reflect.*;
import java.util.*;
/** Runs only while Dudu's Arcfox music plugin is active; never changes vehicle audio source. */
public final class MediaBridge {
 private static final Handler ui = new Handler(Looper.getMainLooper());
 private static final SourcePolicy policy = new SourcePolicy();
 private static final NativeLyrics nativeLyrics = new NativeLyrics();
 private static final CurrentTextLyrics currentTextLyrics = new CurrentTextLyrics();
 private static boolean nativeLyricSnapshotPending;
 private static Handler worker;
 private static Object plugin, binder;
 private static Context context;
 private static MediaController controller;
 private static int nativeStateSource=-1,nativeState=-1;
 private static Integer previousController;
 private static BroadcastReceiver yieldReceiver;
 private static long lastYieldAt=-1;
 private static final String YIELDED="com.shihab.diplay.action.ARCFOX_CARPLAY_MEDIA_YIELDED";
 private static final String COORDINATION="com.shihab.diplay.permission.ARCFOX_MEDIA_COORDINATION";
 private static List<String> lastNativeMetadata;
 private static String lastMetadata;private static Bitmap lastArt;private static int lastPlayState=-1;
 private static volatile long generation;
 private static volatile long revision;
 private static final ThreadLocal<Long> eventEpoch = new ThreadLocal<>();
 private static final String DIPLAY="com.shihab.diplay";
 private static final Runnable poll = new Runnable(){public void run(){ tick(); if(worker!=null) worker.postDelayed(this, 1000); }};
 public static synchronized void start(Context c,Object p) {
  stop(); context=c.getApplicationContext(); plugin=p;
  HandlerThread t=new HandlerThread("ArcfoxCardMedia"); t.start(); worker=new Handler(t.getLooper());
  registerYieldReceiver(context,worker,generation);worker.post(poll);
 }
 public static synchronized void stop(){
  generation++;
  final BroadcastReceiver oldReceiver=yieldReceiver;final Context oldContext=context;yieldReceiver=null;lastYieldAt=-1;
  if(oldReceiver!=null&&oldContext!=null)new Thread(()->{try{oldContext.unregisterReceiver(oldReceiver);}catch(IllegalArgumentException ignored){}},"ArcfoxCardReceiverCleanup").start();
  if(worker!=null){ worker.removeCallbacksAndMessages(null); worker.getLooper().quitSafely(); worker=null; }
  final Object previous=plugin;
  if(previous!=null)new Thread(()->{try{Object sdk=field(previous,"sdkInstance"),cb=field(previous,"mediaCallback");if(sdk!=null&&cb!=null)call(sdk,"removeMediaSysThirdCallback",new Class[]{Class.forName("com.arcfox.media.center.callback.ArcfoxMediaSysThirdCallback")},cb);}catch(Exception e){Log.w("ArcfoxCardMedia","callback cleanup",e);}},"ArcfoxCardCleanup").start();
  plugin=null; binder=null; controller=null;nativeStateSource=-1;nativeState=-1; policy.reset();nativeLyrics.reset();currentTextLyrics.reset();nativeLyricSnapshotPending=false;lastNativeMetadata=null;lastMetadata=null;lastArt=null;lastPlayState=-1;
 }
 private static void registerYieldReceiver(final Context owner,final Handler target,final long epoch){
  final BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent intent){
   if(intent==null||!YIELDED.equals(intent.getAction())||!DIPLAY.equals(intent.getStringExtra("origin")))return;
   final long observed=intent.getLongExtra("observedAt",-1);
   target.post(()->{
    long age=SystemClock.elapsedRealtime()-observed;
    if(epoch!=generation||plugin==null||observed<0||age<0||age>10000||observed<=lastYieldAt)return;
    lastYieldAt=observed;if(!policy.session)return;previousController=null;policy.session=false;clear();
    try{
     Object sdk=field(plugin,"sdkInstance");Object current=sdk==null?null:field(sdk,"mBinder");
     if(current==null||!((IInterface)current).asBinder().isBinderAlive()){binder=null;policy.disconnect();return;}
     int source=(Integer)call(current,"getLastMediaType",new Class[0]);
     if(epoch!=generation||plugin==null)return;
     binder=current;policy.nativeSource(source);clear();refresh();
    }catch(Exception e){Log.w("ArcfoxCardMedia","yield refresh",e);}
   });
  }};
  yieldReceiver=receiver;
  target.post(()->{
   if(epoch!=generation)return;
   try{owner.registerReceiver(receiver,new IntentFilter(YIELDED),COORDINATION,target);
    if(epoch!=generation)try{owner.unregisterReceiver(receiver);}catch(IllegalArgumentException ignored){}
   }catch(Exception e){Log.w("ArcfoxCardMedia","yield receiver registration",e);}
  });
 }
 private static Object call(Object o,String name,Class<?>[] types,Object... args)throws Exception {
  Method m=o.getClass().getMethod(name,types); m.setAccessible(true); return m.invoke(o,args);
 }
 private static Object field(Object o,String name)throws Exception { Field f=o.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(o); }
 private static Object manager()throws Exception {return Class.forName("com.dudu.autoui.manage.music.p").getMethod("w").invoke(null);}
 private static void render(final Runnable r) {
  Long queuedEpoch=eventEpoch.get();final long epoch=queuedEpoch==null?generation:queuedEpoch,version=revision; final boolean owner=policy.session;final int source=policy.source;
  ui.post(()->{if(epoch==generation && version==revision && plugin!=null && owner==policy.session && source==policy.source)try{if(selectedController()==101)r.run();}catch(Throwable e){Log.w("ArcfoxCardMedia","card update",e);}});
 }
 private static void clear(){ nativeLyrics.reset();currentTextLyrics.reset();nativeLyricSnapshotPending=false;revision++;lastNativeMetadata=null;lastMetadata=null;lastArt=null;lastPlayState=-1; render(()->{try{Object m=manager(); call(m,"a",new Class[]{String.class,String.class,String.class},"","","");call(m,"b",new Class[]{String.class,String.class,String.class},"",null,null);call(m,"a",new Class[]{int.class,int.class},0,0);call(m,"a",new Class[]{boolean.class,boolean.class},false,true);call(m,"a",new Class[]{List.class,int.class},Collections.emptyList(),0);}catch(Exception e){throw new RuntimeException(e);}}); }
 private static void tick(){
  eventEpoch.set(generation);
  try{tickCurrent();}finally{eventEpoch.remove();}
 }
 private static void tickCurrent(){
  final long epoch=generation; final Object current=plugin;
  if(plugin==null)return;
  try {
   Object sdk=field(current,"sdkInstance"); Object next=sdk==null?null:field(sdk,"mBinder");
   if(next==null || !((IInterface)next).asBinder().isBinderAlive()) { if(binder!=null){binder=null;policy.disconnect();nativeStateSource=-1;nativeState=-1;if(!policy.session)clear();} }
   else {
    boolean connected=binder!=next; binder=next;
    int source=(Integer)call(binder,"getLastMediaType",new Class[0]);
    if(epoch!=generation || current!=plugin)return;
    boolean wasSession=policy.session; int previousSource=policy.source;
    boolean changed=policy.nativeSource(source);
    if(changed && wasSession && previousSource>=0 && source>=0 && !policy.session)notifyNative(source,"source-change");
    if(changed && !policy.session)clear();
    else if(changed){revision++;lastNativeMetadata=null;lastMetadata=null;lastArt=null;lastPlayState=-1;}
    if((connected||changed)&&!policy.session) refresh();
   }
   MediaSessionManager sm=(MediaSessionManager)context.getSystemService(Context.MEDIA_SESSION_SERVICE);
   MediaController found=null;
   for(MediaController c:sm.getActiveSessions(null)) if(DIPLAY.equals(c.getPackageName())) {found=c;break;}
   if(epoch!=generation || current!=plugin)return;
   // Playing state alone cannot distinguish two consecutive DiPlay MediaSessions.
   // Invalidate queued metadata, artwork, lyric and control work before replacing the token.
   boolean replaced=policy.session && found!=null &&
       (controller==null || !Objects.equals(controller.getSessionToken(),found.getSessionToken()));
   controller=found;
   if(replaced)clear();
   PlaybackState state=found==null?null:found.getPlaybackState();
   boolean playing=state!=null && state.getState()==PlaybackState.STATE_PLAYING;
   boolean changed=policy.sessionState(found!=null,playing);
   if(changed) {clear(); if(policy.session)selectController(true); else {refresh();scheduleRestore();}}
   if(policy.session && found!=null)session(found,state);
  }catch(SecurityException denied){Log.w("ArcfoxCardMedia","MEDIA_CONTENT_CONTROL unavailable; native route remains active");}
  catch(Throwable e){Log.w("ArcfoxCardMedia","refresh",e);}
 }
 private static void refresh()throws Exception{
  if(binder==null||policy.source<0)return;
  Object cb=field(plugin,"mediaCallback");Class<?> type=Class.forName("com.arcfox.media.center.callback.IMediaSysThirdCallback");
  call(binder,"getMediaInfo",new Class[]{int.class,type},policy.source,cb);
  call(binder,"getPlayStatus",new Class[]{int.class,type},policy.source,cb);
 }
 private static void session(MediaController c,PlaybackState state){
  final MediaMetadata md=c.getMetadata(); final PlaybackState ps=state;
  List<NativeLyrics.Row> textRows=currentTextLyrics.update(md==null?null:md.getString(MediaMetadata.METADATA_KEY_TITLE));
  Bitmap image=md==null?null:md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);if(image==null&&md!=null)image=md.getBitmap(MediaMetadata.METADATA_KEY_ART);
  final Bitmap art=image;
  String identity=md==null?"":String.valueOf(md.getString(MediaMetadata.METADATA_KEY_MEDIA_ID))+"\n"+md.getString(MediaMetadata.METADATA_KEY_TITLE)+"\n"+md.getString(MediaMetadata.METADATA_KEY_ARTIST)+"\n"+md.getString(MediaMetadata.METADATA_KEY_ALBUM)+"\n"+md.getLong(MediaMetadata.METADATA_KEY_DURATION);
  boolean sameArt=art==lastArt || (art!=null&&lastArt!=null&&!art.isRecycled()&&!lastArt.isRecycled()&&art.sameAs(lastArt));
  final boolean changed=!identity.equals(lastMetadata)||!sameArt;
  if(changed){lastMetadata=identity;lastArt=art;}
  final int status=ps==null?-1:ps.getState();final boolean stateChanged=status!=lastPlayState;lastPlayState=status;
  render(()->{try{Object m=manager();
   if(changed){String title=md==null?"":md.getString(MediaMetadata.METADATA_KEY_TITLE);String artist=md==null?"":md.getString(MediaMetadata.METADATA_KEY_ARTIST);
    call(m,"a",new Class[]{String.class,String.class,String.class},title,artist,"");
    call(m,"a",new Class[]{String.class,Bitmap.class,boolean.class},null,art,true);
   }
   long duration=md==null?0:md.getLong(MediaMetadata.METADATA_KEY_DURATION),position=ps==null?0:ps.getPosition();
   if(ps!=null&&status==PlaybackState.STATE_PLAYING)position+=(long)((SystemClock.elapsedRealtime()-ps.getLastPositionUpdateTime())*ps.getPlaybackSpeed());
   call(m,"a",new Class[]{int.class,int.class},bounded(position),bounded(duration));
   if(stateChanged)call(m,"a",new Class[]{boolean.class,boolean.class},status==PlaybackState.STATE_PLAYING,true);
  }catch(Exception e){throw new RuntimeException(e);}});
  // Original Dudu title events disable its lyric view. Restore lyrics after that event,
  // including artwork/artist metadata changes where the current text itself is unchanged.
  publishLyrics(textRows!=null?textRows:changed?currentTextLyrics.snapshot():null);
 }
 private static int bounded(long value){return (int)Math.max(0,Math.min(Integer.MAX_VALUE,value));}
 private static void callback(Object callback,Runnable event){
  final Handler target=worker;final long epoch=generation,version=revision;if(target==null)return;
  target.post(()->{if(epoch!=generation||version!=revision||plugin==null)return;eventEpoch.set(epoch);try{if(!CallbackFence.accepts(epoch,generation,callback,field(plugin,"mediaCallback")))return;event.run();}catch(Exception e){Log.w("ArcfoxCardMedia","callback rejected",e);}finally{eventEpoch.remove();}});
 }
 public static void info(Object callback,int source,Object data){callback(callback,()->{
  if(!policy.accepts(source))return;
  boolean confirmed=nativeLyricSnapshotPending;nativeLyricSnapshotPending=false;
  List<NativeLyrics.Row> rows;
  if(NativeLyrics.identified(data))rows=nativeLyrics.metadata(data,confirmed);
  else{nativeLyrics.reset();rows=Collections.emptyList();}
  try{
   String title=data==null?"":(String)call(data,"getName",new Class[0]);String artist=data==null?"":(String)call(data,"getArtist",new Class[0]);String cover=data==null?"":(String)call(data,"getCover",new Class[0]);
   String id=data==null?"":String.valueOf(optional(data,"getId"));
   List<String> frame=Arrays.asList(id,title,artist,cover);boolean changed=!frame.equals(lastNativeMetadata);
   if(changed){if(selectedController()==101)lastNativeMetadata=frame;render(()->{try{Object m=manager();call(m,"a",new Class[]{String.class,String.class,String.class},title,artist,cover);call(m,"b",new Class[]{String.class,String.class,String.class},cover,null,null);}catch(Exception e){throw new RuntimeException(e);}});}
   publishLyrics(rows!=null?rows:changed?nativeLyrics.snapshot():null);
  }catch(Exception e){throw new RuntimeException(e);}
 });}
 private static Object optional(Object object,String name){try{return call(object,name,new Class[0]);}catch(Exception absent){return null;}}
 public static void state(Object callback,int source,int state){callback(callback,()->{
  if(source!=policy.source)return;
  boolean edge=source==nativeStateSource&&nativeState!=3&&state==3;nativeStateSource=source;nativeState=state;
  if(edge){selectController(false);}
  if(edge&&policy.session){policy.session=false;clear();notifyNative(source,"native-play");try{refresh();}catch(Exception e){Log.w("ArcfoxCardMedia","native refresh",e);}}
  if(!policy.accepts(source))return;render(()->{try{call(manager(),"a",new Class[]{boolean.class,boolean.class},state==3,true);}catch(Exception e){throw new RuntimeException(e);}});
 });}
 public static void lyric(Object callback,int source,Object data){callback(callback,()->{
  if(!policy.accepts(source))return;
  if(!NativeLyrics.identified(data)){
   if(binder!=null&&nativeLyrics.requestSnapshot(data))try{
    int actual=(Integer)call(binder,"getLastMediaType",new Class[0]);
    if(actual!=source)return;nativeLyricSnapshotPending=true;refresh();
   }catch(Exception e){nativeLyricSnapshotPending=false;Log.w("ArcfoxCardMedia","anonymous lyric snapshot",e);}
   return;
  }
  publishLyrics(nativeLyrics.lyrics(data));
 });}
 private static void publishLyrics(List<NativeLyrics.Row> rows){
  if(rows==null)return;
  render(()->{try{
   List<Object> output=new ArrayList<>();Class<?> rowClass=Class.forName("com.dudu.autoui.common.y0.c.a");
   for(NativeLyrics.Row row:rows){Object item=rowClass.getConstructor().newInstance();rowClass.getField("a").set(item,String.format(Locale.ROOT,"%02d:%02d.%03d",row.start/60000,(row.start/1000)%60,row.start%1000));rowClass.getField("b").setLong(item,row.start);rowClass.getField("c").setLong(item,row.end);rowClass.getField("d").set(item,row.text);output.add(item);}
   call(manager(),"a",new Class[]{List.class,int.class},output,0);
  }catch(Exception e){throw new RuntimeException(e);}});
 }
 public static void progress(Object callback,int source,long position,long total){progress(callback,source,position,total,null);}
 public static void progress(Object callback,int source,long position,long total,String line){callback(callback,()->{if(!policy.accepts(source))return;if(line!=null)publishLyrics(nativeLyrics.currentLine(line));render(()->{try{call(manager(),"a",new Class[]{int.class,int.class},bounded(position),bounded(total));}catch(Exception e){throw new RuntimeException(e);}});});}
 public static void connected(Object callback,boolean value){callback(callback,()->{if(!value){binder=null;policy.disconnect();nativeStateSource=-1;nativeState=-1;if(!policy.session)clear();}else if(worker!=null){worker.removeCallbacks(poll);worker.post(poll);}});}
 private static int selectedController()throws Exception{return (Integer)Class.forName("com.dudu.autoui.manage.music.s").getMethod("k").invoke(null);}
 private static void setController(int value)throws Exception{
  Class.forName("com.dudu.autoui.common.e1.p0").getMethod("b",String.class,int.class).invoke(null,"SDATA_MUSIC_CONTROLLER",value);
  call(manager(),"s",new Class[0]);
 }
 private static void selectController(boolean phone){
  final long epoch=generation;
  if(!phone)previousController=null;
  ui.post(()->{if(epoch!=generation||plugin==null)return;try{int old=selectedController();if(old==101)return;if(phone&&previousController==null)previousController=old;setController(101);}catch(Exception e){Log.w("ArcfoxCardMedia","controller selection",e);}});
 }
 private static void scheduleRestore(){
  if(previousController==null||worker==null)return;final long epoch=generation;
  worker.postDelayed(()->{if(epoch!=generation||policy.session||controller!=null||nativeState==3)return;ui.post(()->{if(epoch!=generation||policy.session||previousController==null)return;try{int old=previousController;previousController=null;if(selectedController()==101)setController(old);}catch(Exception e){Log.w("ArcfoxCardMedia","controller restore",e);}});},1500);
 }
 private static void notifyNative(int source,String reason){
  Intent i=new Intent("com.shihab.diplay.action.ARCFOX_NATIVE_MEDIA_SELECTED").setPackage(DIPLAY);
  i.putExtra("sourceId",source).putExtra("reason",reason).putExtra("observedAt",SystemClock.elapsedRealtime()).putExtra("origin","com.dudu.autoui");
  context.sendBroadcast(i,"com.shihab.diplay.permission.ARCFOX_MEDIA_COORDINATION");
 }
 public static synchronized void command(int event){
  if(worker==null)return;
  final long epoch=generation,version=revision;final boolean session=policy.session;final int source=policy.source;final MediaController selected=controller;
  worker.post(()->{if(epoch!=generation||version!=revision||session!=policy.session||source!=policy.source)return;try{
   if(session && selected!=null && controller!=null && selected.getSessionToken().equals(controller.getSessionToken())){
    if(binder!=null&&source>=0){int actual=(Integer)call(binder,"getLastMediaType",new Class[0]);if(actual>=0&&actual!=source)return;}
    boolean alive=false;for(MediaController c:((MediaSessionManager)context.getSystemService(Context.MEDIA_SESSION_SERVICE)).getActiveSessions(null))if(selected.getSessionToken().equals(c.getSessionToken()))alive=true;
    if(!alive)return; PlaybackState s=selected.getPlaybackState();if(s==null)return;long actions=s.getActions();MediaController.TransportControls t=selected.getTransportControls();
    if(event==5 && (actions&PlaybackState.ACTION_SKIP_TO_PREVIOUS)!=0)t.skipToPrevious();
    else if(event==6 && (actions&PlaybackState.ACTION_SKIP_TO_NEXT)!=0)t.skipToNext();
    else if(event==3){if(s.getState()==PlaybackState.STATE_PLAYING && (actions&PlaybackState.ACTION_PAUSE)!=0)t.pause();else if(s.getState()!=PlaybackState.STATE_PLAYING && (actions&PlaybackState.ACTION_PLAY)!=0)t.play();}
   } else if(!session && binder!=null && source>=0){ int actual=(Integer)call(binder,"getLastMediaType",new Class[0]);if(actual==source)call(binder,"sendMediaEvent",new Class[]{int.class,int.class},source,event); }
  }catch(Throwable e){Log.w("ArcfoxCardMedia","command rejected",e);}});
 }
}
