package local.arcfox.kuwo;
import android.content.*;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.*;
import android.os.*;
import android.util.Log;
import java.lang.reflect.*;
import java.util.*;
/** Replaces only the Arcfox Kuwo-car plugin's obsolete SDK; selection is owned by KuwoAutoSelect. */
public final class KuwoCardBridge {
 private static final String PACKAGE="cn.kuwo.kwmusiccar",TAG="Dudu-Kuwo";
 private static final Handler ui=new Handler(Looper.getMainLooper());
 private static volatile Instance current;
 private static final class Instance {
  final Context context;final Object owner,card;final Handler worker;
  final KuwoLyrics lyrics=new KuwoLyrics();MediaController.Callback mediaCallback;long controllerGeneration;boolean lyricSnapshotPending;
  volatile boolean stopped;MediaController controller;long revision;String previous="";Bitmap previousArt;int previousState=-999;
  Instance(Context c,Object o,Object callback){context=c.getApplicationContext();owner=o;card=callback;HandlerThread t=new HandlerThread(TAG);t.start();worker=new Handler(t.getLooper());}
  boolean selected(){try{Object manager=Class.forName("com.dudu.autoui.manage.music.p").getMethod("w").invoke(null);Field f=manager.getClass().getDeclaredField("c");f.setAccessible(true);return f.get(manager)==owner;}catch(Exception e){return false;}}
  void render(Runnable action){final long version=revision;ui.post(()->{if(stopped||current!=this||version!=revision||!selected())return;try{action.run();}catch(Exception e){Log.w(TAG,"Card callback failed",e);}});}
  void applyLyrics(List<KuwoLyrics.Row> rows){try{invoke("a",new Class[]{List.class,int.class},KuwoLyrics.model(rows),0);}catch(Exception e){Log.w(TAG,"Lyric model unavailable",e);}}
  void publishLyrics(){final List<KuwoLyrics.Row> rows=lyrics.rows();render(()->applyLyrics(rows));}
  void clear(){lyrics.track("");revision++;previous="";previousArt=null;previousState=-999;render(()->{invoke("a",new Class[]{String.class,String.class,String.class},"","","");invoke("b",new Class[]{String.class,String.class,String.class},"",null,null);invoke("a",new Class[]{int.class,int.class},0,0);invoke("a",new Class[]{boolean.class,boolean.class},false,true);invoke("a",new Class[]{List.class,int.class},Collections.emptyList(),0);});}
  void invoke(String name,Class<?>[] types,Object...args){try{Method m=card.getClass().getMethod(name,types);m.setAccessible(true);m.invoke(card,args);}catch(Exception e){throw new IllegalStateException(e);}}
  MediaController find(){MediaController found=null;MediaSessionManager m=(MediaSessionManager)context.getSystemService(Context.MEDIA_SESSION_SERVICE);if(m==null)return null;
   for(MediaController c:m.getActiveSessions(null))if(PACKAGE.equals(c.getPackageName())){
    PlaybackState s=c.getPlaybackState();if(s!=null&&s.getState()==PlaybackState.STATE_PLAYING)return c;
    if(controller!=null&&controller.getSessionToken().equals(c.getSessionToken()))found=c;
    else if(found==null)found=c;
   }return found;
  }
  final Runnable poll=new Runnable(){public void run(){if(stopped||current!=Instance.this)return;refresh();if(!stopped)worker.postDelayed(this,1000);}};
  void refresh(){if(stopped||current!=this)return;try{
   MediaController next=find();if(next==null){if(controller!=null){unbind();clear();}return;}
   if(controller==null||!controller.getSessionToken().equals(next.getSessionToken())){unbind();controller=next;clear();bind(next);}else controller=next;
   final MediaMetadata md=next.getMetadata();final PlaybackState ps=next.getPlaybackState();
   String identity=md==null?"":text(md,MediaMetadata.METADATA_KEY_MEDIA_ID)+"\n"+text(md,MediaMetadata.METADATA_KEY_TITLE)+"\n"+text(md,MediaMetadata.METADATA_KEY_ARTIST)+"\n"+text(md,MediaMetadata.METADATA_KEY_ALBUM)+"\n"+md.getLong(MediaMetadata.METADATA_KEY_DURATION);
   final Bundle extras=next.getExtras();
   final boolean trackChanged=lyrics.track(identity,rawLyrics(extras));if(trackChanged)revision++;
   // New-plugin attachment often follows the original player's completed lyric load.
   // Recheck both live values before taking the one allowed bootstrap snapshot.
   final boolean bootstrap=lyricSnapshotPending&&md!=null&&!text(md,MediaMetadata.METADATA_KEY_TITLE).isEmpty()&&identity.equals(identity(next.getMetadata()))&&Objects.equals(rawLyrics(extras),rawLyrics(next.getExtras()));
   if(bootstrap)lyricSnapshotPending=false;
   final boolean lyricChanged=acceptLyrics(extras,md,false,bootstrap);
   final List<KuwoLyrics.Row> lyricRows=lyrics.rows();
   final Bitmap art=KuwoArtwork.get(context,next,md,()->worker.post(this::refresh));
   boolean same=art==previousArt||(art!=null&&previousArt!=null&&!art.isRecycled()&&!previousArt.isRecycled()&&art.sameAs(previousArt));
   final boolean changed=!identity.equals(previous)||!same;previous=identity;previousArt=art;
   final int state=ps==null?PlaybackState.STATE_NONE:ps.getState();final boolean stateChanged=state!=previousState;previousState=state;
   final long duration=md==null?0:md.getLong(MediaMetadata.METADATA_KEY_DURATION),position=ps==null?0:MediaPolicy.position(ps.getPosition(),ps.getLastPositionUpdateTime(),ps.getPlaybackSpeed(),state==PlaybackState.STATE_PLAYING,SystemClock.elapsedRealtime(),duration);
   render(()->{
    if(changed){invoke("a",new Class[]{String.class,String.class,String.class},text(md,MediaMetadata.METADATA_KEY_TITLE),text(md,MediaMetadata.METADATA_KEY_ARTIST),"");invoke("a",new Class[]{String.class,Bitmap.class,boolean.class},null,art,true);}
    // Title events reset the real Dudu lyric-active flag. Reapply rows after every title/art event.
    if(changed||trackChanged||lyricChanged)applyLyrics(lyricRows);
    invoke("a",new Class[]{int.class,int.class},bounded(position),bounded(duration));
    if(stateChanged)invoke("a",new Class[]{boolean.class,boolean.class},state==PlaybackState.STATE_PLAYING,true);
   });
  }catch(Exception e){Log.w(TAG,"MediaSession unavailable",e);}}
  static String rawLyrics(Bundle extras){return extras==null?null:extras.getString("AUDIO_LYRIC");}
  boolean acceptLyrics(Bundle extras,MediaMetadata md,boolean fresh){return acceptLyrics(extras,md,fresh,false);}
  boolean acceptLyrics(Bundle extras,MediaMetadata md,boolean fresh,boolean bootstrap){if(extras==null||md==null)return false;
   String tagged=extras.getString("ARCFOX_LYRIC_MEDIA_ID");
   if(tagged!=null){String id=text(md,MediaMetadata.METADATA_KEY_MEDIA_ID);if(id.isEmpty()||!id.equals(tagged)||!text(md,MediaMetadata.METADATA_KEY_TITLE).equals(extras.getString("ARCFOX_LYRIC_TITLE"))||!text(md,MediaMetadata.METADATA_KEY_ARTIST).equals(extras.getString("ARCFOX_LYRIC_ARTIST")))return false;return lyrics.update(identity(md),rawLyrics(extras));}
   // Unmodified Kuwo has no owner marker. Only a new callback in the live session is eligible.
   if((!fresh&&!bootstrap)||text(md,MediaMetadata.METADATA_KEY_TITLE).isEmpty())return false;
   if(bootstrap)return lyrics.snapshot(identity(md),rawLyrics(extras));
   return lyrics.original(identity(md),rawLyrics(extras));
  }
  void unbind(){controllerGeneration++;if(controller!=null&&mediaCallback!=null)try{controller.unregisterCallback(mediaCallback);}catch(Exception ignored){}controller=null;mediaCallback=null;}
  void bind(MediaController bound){lyricSnapshotPending=true;final long generation=controllerGeneration;final MediaSession.Token token=bound.getSessionToken();
   mediaCallback=new MediaController.Callback(){boolean valid(){return !stopped&&current==Instance.this&&controllerGeneration==generation&&controller!=null&&token.equals(controller.getSessionToken());}
    @Override public void onExtrasChanged(Bundle extras){if(!valid())return;refresh();if(!valid())return;if(!Objects.equals(rawLyrics(extras),rawLyrics(bound.getExtras())))return;if(acceptLyrics(extras,bound.getMetadata(),true))publishLyrics();}
    @Override public void onMetadataChanged(MediaMetadata metadata){if(valid())refresh();}
    @Override public void onPlaybackStateChanged(PlaybackState playback){if(valid())refresh();}
    @Override public void onSessionDestroyed(){if(valid()){unbind();clear();}}
   };bound.registerCallback(mediaCallback,worker);
  }
  void command(int event){final MediaSession.Token token=controller==null?null:controller.getSessionToken();final long version=revision;
   worker.post(()->{if(stopped||current!=this||version!=revision||!selected())return;try{
    MediaController active=find();if(active==null||token==null||!token.equals(active.getSessionToken()))return;
    dispatch(active,event);
   }catch(Exception e){Log.w(TAG,"Transport unavailable",e);}});
  }
  void stop(){unbind();stopped=true;worker.removeCallbacksAndMessages(null);worker.getLooper().quitSafely();}
 }
 private static String identity(MediaMetadata md){return md==null?"":text(md,MediaMetadata.METADATA_KEY_MEDIA_ID)+"\n"+text(md,MediaMetadata.METADATA_KEY_TITLE)+"\n"+text(md,MediaMetadata.METADATA_KEY_ARTIST)+"\n"+text(md,MediaMetadata.METADATA_KEY_ALBUM)+"\n"+md.getLong(MediaMetadata.METADATA_KEY_DURATION);}
 public static String text(MediaMetadata md,String key){String value=md==null?null:md.getString(key);return value==null?"":value;}
 private static int bounded(long value){return (int)Math.max(0,Math.min(Integer.MAX_VALUE,value));}
 public static void dispatch(MediaController c,int event){PlaybackState state=c.getPlaybackState();if(state==null)return;long a=state.getActions();MediaController.TransportControls t=c.getTransportControls();
  if(event==5&&(a&PlaybackState.ACTION_SKIP_TO_PREVIOUS)!=0)t.skipToPrevious();
  else if(event==6&&(a&PlaybackState.ACTION_SKIP_TO_NEXT)!=0)t.skipToNext();
  else if(event==1&&(a&PlaybackState.ACTION_PLAY)!=0)t.play();
  else if(event==2&&(a&PlaybackState.ACTION_PAUSE)!=0)t.pause();
 }
 public static synchronized boolean start(Context c,Object owner,Object callback){if(!Build.FINGERPRINT.startsWith("AAOP_COCKPIT/"))return false;
  if(current!=null)current.stop();current=new Instance(c,owner,callback);current.clear();current.worker.post(current.poll);return true;
 }
 public static synchronized boolean stop(Object owner){if(!Build.FINGERPRINT.startsWith("AAOP_COCKPIT/"))return false;Instance i=current;if(i!=null&&i.owner==owner){i.stop();current=null;}return true;}
 public static synchronized boolean refresh(Object owner){if(!Build.FINGERPRINT.startsWith("AAOP_COCKPIT/"))return false;Instance i=current;if(i!=null&&i.owner==owner)i.worker.post(i::refresh);return true;}
 public static synchronized boolean command(Object owner,int event){if(!Build.FINGERPRINT.startsWith("AAOP_COCKPIT/"))return false;Instance i=current;if(i!=null&&i.owner==owner)i.command(event);return true;}
}
