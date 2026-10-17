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
  volatile java.util.List<QueueEntry> queue=Collections.emptyList();
  final String queueOwner=java.util.UUID.randomUUID().toString();long queueVersion;
  volatile boolean stopped;MediaController controller;long revision;String previous="";Bitmap previousArt;int previousState=-999;
  Instance(Context c,Object o,Object callback){context=c.getApplicationContext();owner=o;card=callback;HandlerThread t=new HandlerThread(TAG);t.start();worker=new Handler(t.getLooper());}
  boolean selected(){try{Object manager=Class.forName("com.dudu.autoui.manage.music.p").getMethod("w").invoke(null);Field f=manager.getClass().getDeclaredField("c");f.setAccessible(true);return f.get(manager)==owner;}catch(Exception e){return false;}}
  void render(Runnable action){final long version=revision;ui.post(()->{if(stopped||current!=this||version!=revision||!selected())return;try{action.run();}catch(Exception e){Log.w(TAG,"Card callback failed",e);}});}
  void clear(){queue=Collections.emptyList();queueVersion++;revision++;previous="";previousArt=null;previousState=-999;render(()->{invoke("a",new Class[]{String.class,String.class,String.class},"","","");invoke("b",new Class[]{String.class,String.class,String.class},"",null,null);invoke("a",new Class[]{int.class,int.class},0,0);invoke("a",new Class[]{boolean.class,boolean.class},false,true);});}
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
   MediaController next=find();if(next==null){if(controller!=null){controller=null;clear();}return;}
   if(controller==null||!controller.getSessionToken().equals(next.getSessionToken())){controller=next;clear();}else controller=next;
   updateQueue(next);
   final MediaMetadata md=next.getMetadata();final PlaybackState ps=next.getPlaybackState();
   String identity=md==null?"":text(md,MediaMetadata.METADATA_KEY_MEDIA_ID)+"\n"+text(md,MediaMetadata.METADATA_KEY_TITLE)+"\n"+text(md,MediaMetadata.METADATA_KEY_ARTIST)+"\n"+text(md,MediaMetadata.METADATA_KEY_ALBUM)+"\n"+md.getLong(MediaMetadata.METADATA_KEY_DURATION);
   Bitmap bitmap=md==null?null:md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);if(bitmap==null&&md!=null)bitmap=md.getBitmap(MediaMetadata.METADATA_KEY_ART);final Bitmap art=bitmap;
   boolean same=art==previousArt||(art!=null&&previousArt!=null&&!art.isRecycled()&&!previousArt.isRecycled()&&art.sameAs(previousArt));
   final boolean changed=!identity.equals(previous)||!same;previous=identity;previousArt=art;
   final int state=ps==null?PlaybackState.STATE_NONE:ps.getState();final boolean stateChanged=state!=previousState;previousState=state;
   final long duration=md==null?0:md.getLong(MediaMetadata.METADATA_KEY_DURATION),position=ps==null?0:MediaPolicy.position(ps.getPosition(),ps.getLastPositionUpdateTime(),ps.getPlaybackSpeed(),state==PlaybackState.STATE_PLAYING,SystemClock.elapsedRealtime(),duration);
   render(()->{
    if(changed){invoke("a",new Class[]{String.class,String.class,String.class},text(md,MediaMetadata.METADATA_KEY_TITLE),text(md,MediaMetadata.METADATA_KEY_ARTIST),"");invoke("a",new Class[]{String.class,Bitmap.class,boolean.class},null,art,true);}
    invoke("a",new Class[]{int.class,int.class},bounded(position),bounded(duration));
    if(stateChanged)invoke("a",new Class[]{boolean.class,boolean.class},state==PlaybackState.STATE_PLAYING,true);
   });
  }catch(Exception e){Log.w(TAG,"MediaSession unavailable",e);}}
  void updateQueue(MediaController next){
   List<MediaSession.QueueItem> live=next.getQueue();List<QueueEntry> entries=new ArrayList<>();
   if(live!=null)for(MediaSession.QueueItem item:live){android.media.MediaDescription d=item.getDescription();
    if(d==null||d.getExtras()==null||!d.getExtras().getBoolean("arcfox.queue.full",false)){entries.clear();break;}
    entries.add(new QueueEntry(item.getQueueId(),String.valueOf(d.getTitle()==null?"":d.getTitle()),String.valueOf(d.getSubtitle()==null?"":d.getSubtitle()),""));
   }
   List<QueueEntry> before=queue;boolean same=before.size()==entries.size();
   if(same)for(int i=0;i<before.size();i++){QueueEntry a=before.get(i),b=entries.get(i);if(a.id!=b.id||!a.title.equals(b.title)||!a.artist.equals(b.artist)){same=false;break;}}
   if(same)return;
   queueVersion++;List<QueueEntry> result=new ArrayList<>();for(QueueEntry entry:entries)result.add(new QueueEntry(entry.id,entry.title,entry.artist,queueOwner+":"+queueVersion+":"+entry.id));
   queue=Collections.unmodifiableList(result);
  }
  void choose(int index,String path){final List<QueueEntry> expected=queue;final MediaSession.Token token=controller==null?null:controller.getSessionToken();
   if(index<0||index>=expected.size()||!expected.get(index).path.equals(path))return;
   final QueueEntry selectedEntry=expected.get(index);worker.post(()->{if(stopped||current!=this||!selected()||queue!=expected)return;
    try{MediaController active=find();if(active==null||token==null||!token.equals(active.getSessionToken()))return;
     updateQueue(active);if(queue!=expected)return;active.getTransportControls().skipToQueueItem(selectedEntry.id);
    }catch(Exception e){Log.w(TAG,"Queue selection rejected",e);}
   });
  }
  void command(int event){final MediaSession.Token token=controller==null?null:controller.getSessionToken();final long version=revision;
   worker.post(()->{if(stopped||current!=this||version!=revision||!selected())return;try{
    MediaController active=find();if(active==null||token==null||!token.equals(active.getSessionToken()))return;
    dispatch(active,event);
   }catch(Exception e){Log.w(TAG,"Transport unavailable",e);}});
  }
  void stop(){stopped=true;worker.removeCallbacksAndMessages(null);worker.getLooper().quitSafely();}
 }
 private static final class QueueEntry {final long id;final String title,artist,path;QueueEntry(long i,String t,String a,String p){id=i;title=t;artist=a;path=p;}}
 public static boolean supportsPlaylist(Object owner){Instance i=current;return Build.FINGERPRINT.startsWith("AAOP_COCKPIT/")&&i!=null&&i.owner==owner&&!i.stopped;}
 public static List<Object> playlist(Object owner){Instance i=current;List<Object> result=new ArrayList<>();if(!supportsPlaylist(owner)||!i.selected())return result;
  List<QueueEntry> queue=i.queue;try{Class<?> type=Class.forName("com.dudu.autoui.manage.music.n");for(int n=0;n<queue.size();n++){QueueEntry entry=queue.get(n);Object item=type.getConstructor().newInstance();type.getMethod("a",int.class).invoke(item,n);type.getMethod("b",int.class).invoke(item,queue.size());type.getMethod("c",String.class).invoke(item,entry.title);type.getMethod("a",String.class).invoke(item,entry.artist);type.getMethod("b",String.class).invoke(item,entry.path);result.add(item);}}catch(Exception e){Log.w(TAG,"Queue model unavailable",e);result.clear();}return result;
 }
 public static void selectPlaylist(Object owner,int index,String path){Instance i=current;if(supportsPlaylist(owner)&&i.selected())i.choose(index,path);}
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
