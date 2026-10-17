package local.arcfox.kuwo;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.session.*;
import android.os.*;
import android.util.Log;
import java.lang.reflect.*;
import java.util.*;
/** Application-context monitor shared across Dudu plugin recreation. Never starts a player. */
public final class KuwoAutoSelect {
 public static final int CONTROLLER=6; // p.i(): packed-switch 2, fifth branch -> plugin/n1.
 private static final Handler ui=new Handler(Looper.getMainLooper());
 private static volatile Watch current;
 private static final class Watch {
  final Context context;final Handler worker;final AutoSelectionPolicy policy=new AutoSelectionPolicy();
  volatile boolean stopped;volatile long revision;
  Watch(Context c){context=c.getApplicationContext();HandlerThread t=new HandlerThread("Dudu-KuwoAuto");t.start();worker=new Handler(t.getLooper());}
  List<MediaController> music(){
   MediaSessionManager m=(MediaSessionManager)context.getSystemService(Context.MEDIA_SESSION_SERVICE);
   List<MediaController> result=new ArrayList<>();if(m==null)return result;
   for(MediaController c:m.getActiveSessions(null)){
    MediaController.PlaybackInfo info=c.getPlaybackInfo();
    if(info!=null){int usage=info.getAudioAttributes().getUsage();if(usage!=AudioAttributes.USAGE_MEDIA&&usage!=AudioAttributes.USAGE_UNKNOWN)continue;}
    result.add(c);
   }return result;
  }
  final Runnable poll=new Runnable(){public void run(){if(stopped||current!=Watch.this)return;tick();if(!stopped)worker.postDelayed(this,1000);}};
  void tick(){if(stopped||current!=this)return;try{
   List<MediaController> controllers=music();
   KuwoExternalVehicle.update(context,controllers);
   List<AutoSelectionPolicy.State> states=new ArrayList<>();
   for(MediaController c:controllers){PlaybackState s=c.getPlaybackState();states.add(new AutoSelectionPolicy.State(c.getSessionToken(),c.getPackageName(),s!=null&&s.getState()==PlaybackState.STATE_PLAYING));}
   final Set<Object> playingAtObservation=new HashSet<>();for(AutoSelectionPolicy.State s:states)if(s.playing)playingAtObservation.add(s.token);
   final int expected=selected();
   final Object token=policy.candidate(states,expected,SystemClock.elapsedRealtime());final long version=++revision;
   if(token==null)return;
   if(expected==CONTROLLER)return;
   ui.post(()->{if(stopped||current!=this||revision!=version)return;try{
    // Playback may have paused or a different source/user selection may have won while queued.
    if(selected()!=expected)return;
    boolean alive=false;for(MediaController c:music()){PlaybackState s=c.getPlaybackState();if(s==null||s.getState()!=PlaybackState.STATE_PLAYING)continue;
     if("com.shihab.diplay".equals(c.getPackageName()))return;
     if(!playingAtObservation.contains(c.getSessionToken()))return;
     if(token.equals(c.getSessionToken())&&AutoSelectionPolicy.KUWO.equals(c.getPackageName()))alive=true;
    }
    if(!alive)return;
    select();
   }catch(Exception e){Log.w("Dudu-KuwoAuto","selection unavailable",e);}});
  }catch(Exception e){Log.w("Dudu-KuwoAuto","session observation unavailable",e);}}
  void stop(){stopped=true;revision++;worker.removeCallbacksAndMessages(null);worker.getLooper().quitSafely();}
 }
 private static int selected()throws Exception{return (Integer)Class.forName("com.dudu.autoui.manage.music.s").getMethod("k").invoke(null);}
 private static void select()throws Exception{
  Class.forName("com.dudu.autoui.common.e1.p0").getMethod("b",String.class,int.class).invoke(null,"SDATA_MUSIC_CONTROLLER",CONTROLLER);
  Object manager=Class.forName("com.dudu.autoui.manage.music.p").getMethod("w").invoke(null);
  manager.getClass().getMethod("s").invoke(manager);
 }
 public static synchronized void watch(Context context){
  if(!Build.FINGERPRINT.startsWith("AAOP_COCKPIT/")||current!=null)return;
  current=new Watch(context);current.worker.post(current.poll);
 }
 public static synchronized void stop(){if(current!=null){current.stop();current=null;}KuwoExternalVehicle.stop();}
}
