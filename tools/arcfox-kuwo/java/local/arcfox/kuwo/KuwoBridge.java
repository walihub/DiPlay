package local.arcfox.kuwo;
import android.content.*;
import android.media.*;
import android.media.session.*;
import android.os.*;
import android.util.Log;
import java.util.*;
/** Optional adapter tied to the original KwMediaSessionService lifecycle, not an extra service. */
public final class KuwoBridge {
 private static final String TAG="Kuwo-Arcfox",PACKAGE="cn.kuwo.kwmusiccar",ACTION="cn.kuwo.kwmusicauto.action.MEDIA_BUTTON";
 private static final Handler worker;
 static{HandlerThread t=new HandlerThread(TAG);t.start();worker=new Handler(t.getLooper());}
 private static final KeyPolicy keys=new KeyPolicy();
 private static volatile Instance current;
 private static final class Instance {
  final Context context;final Object metadataSource;final ArtworkRefreshPolicy artworkPolicy=new ArtworkRefreshPolicy();final MediaController controller;final MediaPolicy policy=new MediaPolicy();final Instrument output=new Instrument();
  final MediaController.Callback callback;final BroadcastReceiver receiver;
  volatile boolean stopped;boolean baseline;long lastSent=-10000,lastAttempt=-10000;boolean dirty=true;long lastPosition=-1;int lastState=-1;
  final Map<MediaSession.Token,Boolean> rivals=new HashMap<>();
  Instance(Context c,MediaSession.Token token){context=c.getApplicationContext();metadataSource=c;controller=new MediaController(context,token);
   callback=new MediaController.Callback(){public void onMetadataChanged(MediaMetadata m){dirty=true;refresh();}public void onPlaybackStateChanged(PlaybackState p){dirty=true;refresh();}public void onSessionDestroyed(){dispose();}};
   receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){final String command=KeyPolicy.raw(i.getAction(),i.getIntExtra("InputKey",-1));if(command!=null)worker.post(()->raw(command));}};
  }
  void begin(){
   controller.registerCallback(callback,worker);
   context.registerReceiver(receiver,new IntentFilter("adayo.keyEvent.onKeyDown.N6"),null,worker);
   refresh();worker.postDelayed(tick,1000);
  }
  final Runnable tick=new Runnable(){public void run(){if(stopped)return;refresh();if(!stopped)worker.postDelayed(this,1000);}};
  void refresh(){if(stopped||current!=this)return;try{
   // Artwork serves every observer of the original session, including a selected but paused
   // launcher card. Materialize it independently of who owns physical instrument publishing.
   MediaMetadata md=controller.getMetadata();if(md!=null)requestArtwork(md);
   PlaybackState state=controller.getPlaybackState();boolean playing=state!=null&&state.getState()==PlaybackState.STATE_PLAYING;
   Set<Object> active=new HashSet<>();boolean preferred=true,first=true;
   MediaSessionManager manager=(MediaSessionManager)context.getSystemService(Context.MEDIA_SESSION_SERVICE);
   if(manager!=null)for(MediaController candidate:manager.getActiveSessions(null)){
    PlaybackState ps=candidate.getPlaybackState();if(ps==null||ps.getState()!=PlaybackState.STATE_PLAYING)continue;
    MediaController.PlaybackInfo info=candidate.getPlaybackInfo();if(info!=null){int usage=info.getAudioAttributes().getUsage();if(usage!=AudioAttributes.USAGE_MEDIA&&usage!=AudioAttributes.USAGE_UNKNOWN)continue;}
    // Multiple sessions belonging to Kuwo are not independent competing audio sources.
    boolean own=controller.getSessionToken().equals(candidate.getSessionToken());
    if(!own&&PACKAGE.equals(candidate.getPackageName()))continue;
    if(first){preferred=own;first=false;}
    if(!own)active.add(candidate.getSessionToken());
   }
   if(!baseline){policy.baselineRivals(active);baseline=true;}
   boolean ownEdge=policy.own(playing,preferred);
   // If own playback just started, it is the newest explicit action. Otherwise a new rival wins.
   if(ownEdge)policy.baselineRivals(active);
   else policy.rivals(active);
   // Observing a new session is not proof it obtained audio focus or started now. Never issue
   // pause from this observer; the original player and OEM focus stack retain audio control.
   if(!policy.owner)return;
   AudioManager audio=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
   if(audio==null||audio.getMode()!=AudioManager.MODE_NORMAL)return;
   if(md==null)return;
   long now=SystemClock.elapsedRealtime(),duration=md.getLong(MediaMetadata.METADATA_KEY_DURATION);
   long position=state==null?0:MediaPolicy.position(state.getPosition(),state.getLastPositionUpdateTime(),state.getPlaybackSpeed(),playing,now,duration);
   int status=state==null?PlaybackState.STATE_NONE:state.getState();
   if(!dirty&&status==lastState&&position==lastPosition&&now-lastSent<10000)return;
   if(now-lastAttempt<1000)return;
   lastAttempt=now;
   if(output.send(md,position,playing,audio.isStreamMute(AudioManager.STREAM_MUSIC))){dirty=false;lastSent=now;lastPosition=position;lastState=status;}
  }catch(SecurityException denied){Log.w(TAG,"Media observation permission unavailable",denied);}
  catch(Exception e){Log.w(TAG,"Media snapshot unavailable",e);}}
  void requestArtwork(MediaMetadata md){
   String id=md.getString(MediaMetadata.METADATA_KEY_MEDIA_ID),title=md.getString(MediaMetadata.METADATA_KEY_TITLE);
   if((id==null||id.isEmpty())&&(title==null||title.isEmpty()))return;
   String uri=md.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI);
   if(uri==null||uri.isEmpty())uri=md.getString(MediaMetadata.METADATA_KEY_ART_URI);
   String identity=String.valueOf(md.getString(MediaMetadata.METADATA_KEY_MEDIA_ID))+"\n"+
    String.valueOf(md.getString(MediaMetadata.METADATA_KEY_TITLE))+"\n"+String.valueOf(md.getString(MediaMetadata.METADATA_KEY_ARTIST));
   boolean has=md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)!=null||md.getBitmap(MediaMetadata.METADATA_KEY_ART)!=null;
   if(!artworkPolicy.request(identity,uri,has,SystemClock.elapsedRealtime()))return;
   // Verified 7.1.8.21 F(true)->G(true,true)->m(true,true)->h(...): use the player's existing
   // loader/cache/authentication. Its completion callback F(false) republishes the bitmap on
   // this same original session, so both the instrument and launcher receive the result.
   new Handler(Looper.getMainLooper()).post(()->{
    if(stopped||current!=this)return;
    try{metadataSource.getClass().getMethod("F",boolean.class).invoke(metadataSource,true);}
    catch(Exception e){Log.w(TAG,"Original artwork refresh unavailable",e);}
   });
  }
  void raw(String command){
   if(stopped||current!=this)return;refresh();PlaybackState s=controller.getPlaybackState();
   AudioManager audio=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
   if(!policy.owner||s==null||s.getState()!=PlaybackState.STATE_PLAYING||audio==null||audio.getMode()!=AudioManager.MODE_NORMAL)return;
   long action="MEDIA_NEXT".equals(command)?PlaybackState.ACTION_SKIP_TO_NEXT:PlaybackState.ACTION_SKIP_TO_PREVIOUS;
   if((s.getActions()&action)==0)return;
   context.sendBroadcast(new Intent(ACTION).setPackage(PACKAGE).putExtra("EXTRA",command).putExtra("Hardware_MediaButton",true).putExtra("arcfox_raw",true));
  }
  void dispose(){if(stopped)return;stopped=true;worker.removeCallbacks(tick);
   try{controller.unregisterCallback(callback);}catch(Exception ignored){}
   try{context.unregisterReceiver(receiver);}catch(Exception ignored){}
   // Do not send empty metadata: another source may already own the physical instrument.
   if(current==this)current=null;
  }
 }
 public static boolean vehicle(){return Build.FINGERPRINT.startsWith("AAOP_COCKPIT/");}
 /** compat is the original support MediaSession, never a newly-created competing session. */
 public static void attach(Context context,Object compat){
  if(!vehicle()||compat==null)return;
  try{
   Object token=compat.getClass().getMethod("c").invoke(compat);
   Object nativeToken=token.getClass().getMethod("e").invoke(token);
   if(!(nativeToken instanceof MediaSession.Token))return;
   worker.post(()->{try{
    if(current!=null&&current.controller.getSessionToken().equals(nativeToken))return;
    if(current!=null)current.dispose();
    current=new Instance(context,(MediaSession.Token)nativeToken);current.begin();
   }catch(Exception e){if(current!=null)current.dispose();Log.w(TAG,"Adapter attach failed",e);}});
  }catch(Exception e){Log.w(TAG,"Original MediaSession token unavailable",e);}
 }
 public static void detach(Object compat){
  if(!vehicle()||compat==null)return;
  try{Object token=compat.getClass().getMethod("c").invoke(compat);Object nativeToken=token.getClass().getMethod("e").invoke(token);
   worker.post(()->{if(current!=null&&current.controller.getSessionToken().equals(nativeToken))current.dispose();});
  }catch(Exception e){Log.w(TAG,"Adapter detach failed",e);}
 }
 /** Original SDK/MediaSession receiver converges here, so raw and standard paths execute once. */
 public static boolean allow(Intent intent){
  if(!vehicle()||intent==null||!ACTION.equals(intent.getAction()))return true;
  String command=intent.getStringExtra("EXTRA");
  if(!"MEDIA_NEXT".equals(command)&&!"MEDIA_PRE".equals(command))return true;
  return keys.accept(command,intent.getBooleanExtra("arcfox_raw",false)?"raw":"standard",SystemClock.elapsedRealtime());
 }
}
