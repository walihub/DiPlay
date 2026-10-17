package local.arcfox.kuwo;

import android.content.*;
import android.media.*;
import android.graphics.Bitmap;
import android.media.session.*;
import android.os.*;
import android.util.Log;
import java.util.*;

/** Dudu-owned adapter for the unmodified, originally signed Kuwo MediaSession. */
public final class KuwoExternalVehicle {
 public static final String PACKAGE="cn.kuwo.kwmusiccar";
 public interface MetadataProvider { MediaMetadata resolve(Context context,MediaController controller); }
 public interface Sink { boolean send(MediaMetadata metadata,long position,boolean playing,boolean muted); }
 private static volatile MetadataProvider metadataProvider=KuwoExternalVehicle::resolveOriginal;
 private static Sink sink=new Instrument()::send;
 private static State current;
 private static final class State {
  final Context context;final MediaController controller;final MediaPolicy policy=new MediaPolicy();final KeyPolicy keys=new KeyPolicy();
  BroadcastReceiver receiver;long lastAttempt=-10000,lastSent=-10000;MediaMetadata metadata;int status=-1;long position=-1;
  boolean baseline;MediaMetadata rawMetadata,resolvedMetadata;Bitmap resolvedArt;
  State(Context c,MediaController selected){context=c.getApplicationContext();controller=selected;}
  void register(){
   receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent intent){
    String command=KeyPolicy.raw(intent.getAction(),intent.getIntExtra("InputKey",-1));
    if(command!=null)command(State.this,command);
   }};
   // The Watch calls update on its serial HandlerThread; handle keys on that same looper.
   Handler handler=Looper.myLooper()==null?new Handler(Looper.getMainLooper()):new Handler(Looper.myLooper());
   context.registerReceiver(receiver,new IntentFilter("adayo.keyEvent.onKeyDown.N6"),null,handler);
  }
  void dispose(){if(receiver!=null){try{context.unregisterReceiver(receiver);}catch(Exception ignored){}receiver=null;}}
 }
 public static void setMetadataProvider(MetadataProvider provider){metadataProvider=provider==null?KuwoExternalVehicle::resolveOriginal:provider;}
 private static MediaMetadata resolveOriginal(Context context,MediaController controller){
  MediaMetadata md=controller.getMetadata();if(md==null)return null;
  // A no-op completion is intentional: the Watch re-reads live metadata at its next 1s tick.
  // Never push a captured previous-song bitmap from a delayed download callback.
  Bitmap image=KuwoArtwork.get(context,controller,md,()->{});if(image==null)return md;
  State state=current;
  if(state==null||!state.controller.getSessionToken().equals(controller.getSessionToken()))return md;
  if(state.rawMetadata==md&&state.resolvedArt==image&&state.resolvedMetadata!=null)return state.resolvedMetadata;
  state.rawMetadata=md;state.resolvedArt=image;
  state.resolvedMetadata=new MediaMetadata.Builder(md).putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART,image).build();
  return state.resolvedMetadata;
 }
 private static boolean playing(MediaController c){PlaybackState state=c.getPlaybackState();return state!=null&&state.getState()==PlaybackState.STATE_PLAYING;}
 private static boolean media(MediaController c){MediaController.PlaybackInfo info=c.getPlaybackInfo();if(info==null)return true;int usage=info.getAudioAttributes().getUsage();return usage==AudioAttributes.USAGE_MEDIA||usage==AudioAttributes.USAGE_UNKNOWN;}
 public static synchronized void update(Context context,List<MediaController> controllers){
  if(!Build.FINGERPRINT.startsWith("AAOP_COCKPIT/")){stop();return;}
  try{
   List<MediaController> list=controllers==null?Collections.emptyList():controllers;
   MediaController candidate=null;
   // Prefer a playing original Kuwo session, retaining its token while it remains the candidate.
   if(current!=null)for(MediaController c:list)if(PACKAGE.equals(c.getPackageName())&&media(c)&&
    c.getSessionToken().equals(current.controller.getSessionToken())&&playing(c)){candidate=c;break;}
   if(candidate==null)for(MediaController c:list)if(PACKAGE.equals(c.getPackageName())&&media(c)&&playing(c)){candidate=c;break;}
   if(candidate==null&&current!=null)for(MediaController c:list)if(PACKAGE.equals(c.getPackageName())&&media(c)&&
    c.getSessionToken().equals(current.controller.getSessionToken())){candidate=c;break;}
   if(candidate==null)for(MediaController c:list)if(PACKAGE.equals(c.getPackageName())&&media(c)){candidate=c;break;}
   if(candidate==null){stop();return;}
   if(current==null||!candidate.getSessionToken().equals(current.controller.getSessionToken())){
    stop();State replacement=new State(context,candidate);current=replacement;
    try{replacement.register();}catch(Exception error){Log.w("Dudu-KuwoVehicle","Wheel observer unavailable",error);}
   }
   State state=current;boolean ownPlaying=playing(state.controller),preferred=true,first=true;
   Set<Object> rivals=new HashSet<>();
   for(MediaController c:list){if(!media(c)||!playing(c))continue;
    boolean own=PACKAGE.equals(c.getPackageName());
    if(first){preferred=own;first=false;}
    if(!own)rivals.add(c.getSessionToken());
   }
   if(!state.baseline){state.policy.baselineRivals(rivals);state.baseline=true;}
   if(state.policy.own(ownPlaying,preferred))state.policy.baselineRivals(rivals);else state.policy.rivals(rivals);
   // Metadata materialization serves Dudu's selected card as well as the instrument.
   MediaMetadata md=metadataProvider.resolve(state.context,state.controller);
   if(!selected()||!state.policy.owner||md==null)return;
   AudioManager audio=(AudioManager)state.context.getSystemService(Context.AUDIO_SERVICE);
   if(audio==null||audio.getMode()!=AudioManager.MODE_NORMAL)return;
   PlaybackState ps=state.controller.getPlaybackState();long now=SystemClock.elapsedRealtime();
   int status=ps==null?PlaybackState.STATE_NONE:ps.getState();
   long position=ps==null?0:MediaPolicy.position(ps.getPosition(),ps.getLastPositionUpdateTime(),ps.getPlaybackSpeed(),ownPlaying,now,md.getLong(MediaMetadata.METADATA_KEY_DURATION));
   boolean changed=md!=state.metadata||status!=state.status||position!=state.position;
   if((!changed&&now-state.lastSent<10000)||now-state.lastAttempt<1000)return;
   state.lastAttempt=now;
   if(sink.send(md,position,ownPlaying,audio.isStreamMute(AudioManager.STREAM_MUSIC))){state.metadata=md;state.status=status;state.position=position;state.lastSent=now;}
  }catch(Exception error){Log.w("Dudu-KuwoVehicle","Original session unavailable",error);}
 }
 private static synchronized void command(State expected,String command){
  State state=current;if(state!=expected||!selected()||!state.policy.owner||!playing(state.controller))return;
  AudioManager audio=(AudioManager)state.context.getSystemService(Context.AUDIO_SERVICE);
  if(audio==null||audio.getMode()!=AudioManager.MODE_NORMAL)return;
  try{
   long action="MEDIA_NEXT".equals(command)?PlaybackState.ACTION_SKIP_TO_NEXT:PlaybackState.ACTION_SKIP_TO_PREVIOUS;
   PlaybackState ps=state.controller.getPlaybackState();if(ps==null||(ps.getActions()&action)==0)return;
   if(!state.keys.accept(command,"external-session",SystemClock.elapsedRealtime()))return;
   // One route only: never also broadcast MEDIA_BUTTON to the original Kuwo SDK receiver.
   if(action==PlaybackState.ACTION_SKIP_TO_NEXT)state.controller.getTransportControls().skipToNext();
   else state.controller.getTransportControls().skipToPrevious();
  }catch(Exception error){Log.w("Dudu-KuwoVehicle","Wheel command unavailable",error);}
 }
 private static boolean selected(){try{return ((Integer)Class.forName("com.dudu.autoui.manage.music.s").getMethod("k").invoke(null))==6;}catch(Exception unavailable){return false;}}
 public static synchronized void stop(){if(current!=null){current.dispose();current=null;}}
 private KuwoExternalVehicle(){}
}
