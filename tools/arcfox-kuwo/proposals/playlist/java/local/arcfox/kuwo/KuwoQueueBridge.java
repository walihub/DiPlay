package local.arcfox.kuwo;
import android.os.*;
import android.util.Log;
import java.lang.reflect.*;
import java.util.*;
/** Exposes the original music queue through the original session, never a second session. */
public final class KuwoQueueBridge {
 private static final Handler main=new Handler(Looper.getMainLooper());
 private static Object session;private static List<Object> songs=Collections.emptyList();
 private static String identity="";private static long generation=System.currentTimeMillis();
 private static Object state()throws Exception{return Class.forName("cn.kuwo.mod.playcontrol.PlayerStateManager").getMethod("u0").invoke(null);}
 private static Object call(Object object,String name)throws Exception{return object.getClass().getMethod(name).invoke(object);}
 private static String field(Object song,String name)throws Exception{Object value=song.getClass().getField(name).get(song);return value==null?"":String.valueOf(value);}
 private static List<Object> actual()throws Exception{
  Object manager=state(),status=call(manager,"y0");
  if(((Number)call(status,"q")).intValue()!=1)return Collections.emptyList(); // FM/MV do not expose a selectable music queue.
  return new ArrayList<>((List<?>)call(manager,"w0"));
 }
 private static String identity(List<Object> list)throws Exception{StringBuilder key=new StringBuilder();for(Object song:list)key.append(field(song,"h")).append('\0').append(field(song,"i")).append('\0').append(field(song,"j")).append('\n');return key.toString();}
 public static synchronized boolean publish(Object compat){if(!Build.FINGERPRINT.startsWith("AAOP_COCKPIT/"))return false;
  try{List<Object> list=actual();String key=identity(list);if(session!=compat||!key.equals(identity)){generation+=Math.max(1,songs.size()+1);identity=key;songs=list;session=compat;}
   Class<?> builder=Class.forName("android.support.v4.media.MediaDescriptionCompat$b"),description=Class.forName("android.support.v4.media.MediaDescriptionCompat"),item=Class.forName("android.support.v4.media.session.MediaSessionCompat$QueueItem");
   List<Object> queue=new ArrayList<>();for(int i=0;i<songs.size();i++){Object b=builder.getConstructor().newInstance();builder.getMethod("f",String.class).invoke(b,field(songs.get(i),"h"));builder.getMethod("i",CharSequence.class).invoke(b,field(songs.get(i),"i"));builder.getMethod("h",CharSequence.class).invoke(b,field(songs.get(i),"j"));Bundle extras=new Bundle();extras.putBoolean("arcfox.queue.full",true);builder.getMethod("c",Bundle.class).invoke(b,extras);Object d=builder.getMethod("a").invoke(b);queue.add(item.getConstructor(description,long.class).newInstance(d,generation+i));}
   compat.getClass().getMethod("n",List.class).invoke(compat,queue);return true;
  }catch(Exception failure){songs=Collections.emptyList();identity="";try{compat.getClass().getMethod("n",List.class).invoke(compat,Collections.emptyList());}catch(Exception ignored){}Log.w("Kuwo-Queue","Original queue unavailable",failure);return true;}
 }
 public static void select(long queueId){if(!Build.FINGERPRINT.startsWith("AAOP_COCKPIT/"))return;final Object expected=session;final long version=generation;
  main.post(()->{synchronized(KuwoQueueBridge.class){try{if(session!=expected||version!=generation)return;long index=queueId-version;if(index<0||index>=songs.size())return;
   List<Object> live=actual();if(!identity(live).equals(identity)){publish(expected);return;}
   Class<?> player=Class.forName("cn.kuwo.kwmusiccar.util.p0"),music=Class.forName("cn.kuwo.base.bean.Music");Object controller=player.getMethod("E").invoke(null);
   // Original UI play3 path: validates ads, original list membership, then w2(list,index,0).
   player.getMethod("i0",music).invoke(controller,live.get((int)index));
  }catch(Exception failure){Log.w("Kuwo-Queue","Queue selection rejected",failure);}}});
 }
 private KuwoQueueBridge(){}
}
