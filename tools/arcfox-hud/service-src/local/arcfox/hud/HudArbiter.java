package local.arcfox.hud;
import android.app.*;import android.content.*;import android.os.*;import android.util.Log;
import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.AtomicLong;
/** All source changes and physical writes run on one serial queue. No client writes vehicle properties. */
public final class HudArbiter {
 private static final String TAG="ArcfoxHudArbiter",NATIVE="com.autonavi.amapauto",DESCRIPTOR="arcfox.hud.sources.v1";
 private static final int STATUS=557853441,ICON=557853245,DISTANCE=557853246,BAR=557853247,ROAD=560997119;
 private static Context context;private static Handler worker;private static Object car,properties;
 private static final HudSelection selection=new HudSelection();private static final AtomicLong leases=new AtomicLong();
 private static final Map<String,IBinder> lifetimes=new HashMap<String,IBinder>();
 private static long retryAt;private static String committed;private static long lastWrite;private static boolean touched;
 private static final Binder endpoint=new Binder(){@Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException{
  if(code==INTERFACE_TRANSACTION){reply.writeString(DESCRIPTOR);return true;}
  data.enforceInterface(DESCRIPTOR);final String name=data.readString();final int uid=Binder.getCallingUid();
  if(context==null||!("com.shihab.diplay".equals(name)||"com.autonavi.amaparcfox".equals(name)))throw new SecurityException("source not allowed");
  String[] packages=context.getPackageManager().getPackagesForUid(uid);
  if(packages==null||!Arrays.asList(packages).contains(name)||context.getPackageManager().checkSignatures(uid,android.os.Process.myUid())!=0)throw new SecurityException("source identity mismatch");
  if(code==1){final IBinder token=data.readStrongBinder();if(token==null)throw new IllegalArgumentException("missing lifetime");
   FutureTask<Long> task=new FutureTask<Long>(new Callable<Long>(){public Long call(){final long lease=leases.incrementAndGet();
    HudSelection.Source s=selection.get(name);selection.active(s,false);s.lease=lease;s.sequence=0;s.road=null;s.icon=s.distance=s.bar=0;lifetimes.put(name,token);
    try{token.linkToDeath(new IBinder.DeathRecipient(){public void binderDied(){worker.post(new Runnable(){public void run(){
     HudSelection.Source s=selection.get(name);if(s.lease==lease){selection.active(s,false);lifetimes.remove(name);render();}}});}},0);}
    catch(RemoteException e){lifetimes.remove(name);}return lease;}});
   worker.post(task);try{long lease=task.get(3,TimeUnit.SECONDS);reply.writeNoException();reply.writeLong(lease);}catch(Exception e){task.cancel(false);throw new IllegalStateException("source registration unavailable",e);}return true;
  }
  if(code==2){final long lease=data.readLong(),seq=data.readLong();final Bundle b=data.readBundle(HudArbiter.class.getClassLoader());
   if(b==null)throw new IllegalArgumentException("missing frame");final byte[] road=b.getByteArray("road");if(road!=null&&road.length>256)throw new IllegalArgumentException("road packet too large");
   worker.post(new Runnable(){public void run(){HudSelection.Source s=selection.get(name);IBinder token=lifetimes.get(name);
    if(s.lease!=lease||seq<=s.sequence||token==null||!token.isBinderAlive())return;s.sequence=seq;
    selection.active(s,b.getBoolean("active"));s.icon=Math.max(0,Math.min(255,b.getInt("icon")));s.distance=Math.max(0,Math.min(2000000,b.getInt("distance")));s.bar=Math.max(0,Math.min(100,b.getInt("bar")));s.road=road;render();}});
   reply.writeNoException();return true;
  }return super.onTransact(code,data,reply,flags);
 }};
 public static IBinder binder(){return endpoint;}
 public static synchronized void attach(Context app){if(worker!=null)return;context=app.getApplicationContext();HandlerThread t=new HandlerThread("ArcfoxHudOwner");t.start();worker=new Handler(t.getLooper());worker.post(tick);}
 private static boolean arcfox(){return Build.FINGERPRINT!=null&&Build.FINGERPRINT.startsWith("AAOP_COCKPIT/");}
 public static boolean nativeInt(final int id,final int value){if(!arcfox()||(id!=STATUS&&id!=ICON&&id!=DISTANCE&&id!=BAR))return false;
  if(worker!=null)worker.post(new Runnable(){public void run(){HudSelection.Source s=selection.get(NATIVE);if(id==STATUS){selection.active(s,value==0);if(value!=0){s.icon=s.distance=s.bar=0;s.road=null;}}else if(id==ICON)s.icon=Math.max(0,Math.min(255,value));else if(id==DISTANCE)s.distance=Math.max(0,Math.min(2000000,value));else s.bar=Math.max(0,Math.min(100,value));render();}});return true;}
 public static boolean nativeRoad(byte[] packet){if(!arcfox()||packet==null||packet.length<4||packet[0]!=3||packet[1]!=24||packet[2]!=7)return false;
  final byte[] bytes=packet.clone();if(worker!=null)worker.post(new Runnable(){public void run(){selection.get(NATIVE).road=bytes;render();}});return true;}
 private static String foreground(){
  try{Class<?> atm=Class.forName("android.app.ActivityTaskManager");Object service=atm.getMethod("getService").invoke(null);
   for(String method:new String[]{"getFocusedRootTaskInfo","getFocusedStackInfo"})try{java.lang.reflect.Method reflected=service.getClass().getMethod(method);reflected.setAccessible(true);Object info=reflected.invoke(service);if(info!=null){ComponentName top=(ComponentName)info.getClass().getField("topActivity").get(info);if(top!=null)return top.getPackageName();}}catch(Exception ignored){}
  }catch(Exception ignored){}
  try{List<ActivityManager.RunningTaskInfo> tasks=((ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE)).getRunningTasks(1);if(!tasks.isEmpty()&&tasks.get(0).topActivity!=null)return tasks.get(0).topActivity.getPackageName();}catch(Exception ignored){}
  return null;
 }
 private static final Runnable tick=new Runnable(){public void run(){render();worker.postDelayed(this,500);}};
 private static Object manager()throws Exception{
  if(properties!=null)return properties;Class<?> type=Class.forName("android.car.Car");if(car==null)car=type.getMethod("createCar",Context.class).invoke(null,context);
  if(!Boolean.TRUE.equals(type.getMethod("isConnected").invoke(car))){if(!Boolean.TRUE.equals(type.getMethod("isConnecting").invoke(car)))type.getMethod("connect").invoke(car);throw new IllegalStateException("car connecting");}
  properties=type.getMethod("getCarManager",String.class).invoke(car,"property");return properties;
 }
 private static void integer(Object manager,int id,int value)throws Exception{manager.getClass().getMethod("setIntProperty",int.class,int.class,int.class).invoke(manager,id,0,value);}
 private static void render(){
  HudSelection.Source s=selection.choose(foreground());String key=s==null?"stopped":s.name+":"+s.lease+":"+s.icon+":"+s.distance+":"+s.bar+":"+Arrays.toString(s.road);
  if(SystemClock.elapsedRealtime()<retryAt||!arcfox()||(!touched&&s==null)||(key.equals(committed)&&SystemClock.elapsedRealtime()-lastWrite<5000))return;
  try{Object m=manager();touched=true;
   if(s==null){integer(m,STATUS,1);integer(m,ICON,0);integer(m,DISTANCE,0);integer(m,BAR,0);}
   else{integer(m,ICON,s.icon);integer(m,DISTANCE,Math.max(0,s.distance));integer(m,BAR,Math.max(0,Math.min(100,s.bar)));
    byte[] road=s.road==null?new byte[]{3,24,7,0}:s.road;
    try{m.getClass().getMethod("setProperty",Class.class,int.class,int.class,Object.class).invoke(m,byte[].class,ROAD,0,road);}catch(Exception e){Log.w(TAG,"optional road write failed",e);}
    integer(m,STATUS,0);
   }
   committed=key;lastWrite=SystemClock.elapsedRealtime();Log.i(TAG,"owner="+(s==null?"none":s.name)+" frame="+key);
  }catch(Exception e){properties=null;committed=null;retryAt=SystemClock.elapsedRealtime()+2000;Log.w(TAG,"HUD write unavailable",e);}
 }
}
