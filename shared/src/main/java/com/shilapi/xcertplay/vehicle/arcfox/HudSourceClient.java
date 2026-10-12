package com.shilapi.xcertplay.vehicle.arcfox;
import android.content.*;import android.os.*;import android.util.Log;
/** Lease/sequence authenticated HUD producer. Missing arbiter never falls back to competing VHAL writes. */
public final class HudSourceClient implements AutoCloseable {
 public static final String SERVER="com.adayo.service.navi",COMPONENT="local.arcfox.hud.HudArbiterService",DESCRIPTOR="arcfox.hud.sources.v1";
 private final Context context;private final HandlerThread thread;private final Handler worker;private final IBinder lifetime=new Binder();
 private volatile boolean closed;private boolean bound;private IBinder remote;private long lease,sequence;private Bundle frame=new Bundle();
 private final ServiceConnection connection=new ServiceConnection(){public void onServiceConnected(ComponentName name,final IBinder binder){worker.post(new Runnable(){public void run(){if(closed)return;remote=binder;lease=0;register();send();}});}
  public void onServiceDisconnected(ComponentName name){worker.post(new Runnable(){public void run(){remote=null;lease=0;}});}
  public void onBindingDied(ComponentName name){worker.post(new Runnable(){public void run(){unbind();remote=null;lease=0;}});}
 };
 public HudSourceClient(Context app){context=app.getApplicationContext();thread=new HandlerThread("ArcfoxHudSource");thread.start();worker=new Handler(thread.getLooper());worker.post(tick);}
 public void update(final boolean active,final int icon,final int distance,final int bar,byte[] road){final byte[] bytes=road==null?null:road.clone();worker.post(new Runnable(){public void run(){if(closed)return;
  frame=new Bundle();frame.putBoolean("active",active);frame.putInt("icon",icon);frame.putInt("distance",distance);frame.putInt("bar",bar);frame.putByteArray("road",bytes);send();}});}
 private final Runnable tick=new Runnable(){public void run(){if(closed)return;bind();send();worker.postDelayed(this,2000);}};
 private void bind(){if(bound||closed)return;try{
  if(context.getPackageManager().checkSignatures(context.getPackageName(),SERVER)!=0)return;
  bound=context.bindService(new Intent().setComponent(new ComponentName(SERVER,COMPONENT)),connection,Context.BIND_AUTO_CREATE);
 }catch(Exception e){Log.w("ArcfoxHudSource","HUD arbiter binding unavailable",e);}}
 private Parcel packet(){Parcel p=Parcel.obtain();p.writeInterfaceToken(DESCRIPTOR);p.writeString(context.getPackageName());return p;}
 private void register(){if(remote==null||lease!=0)return;Parcel data=packet(),reply=Parcel.obtain();try{data.writeStrongBinder(lifetime);if(!remote.transact(1,data,reply,0))throw new RemoteException("registration rejected");reply.readException();lease=reply.readLong();sequence=0;}catch(Exception e){remote=null;lease=0;unbind();Log.w("ArcfoxHudSource","HUD lease unavailable",e);}finally{data.recycle();reply.recycle();}}
 private void send(){if(remote==null)return;register();if(remote==null||lease==0)return;Parcel data=packet(),reply=Parcel.obtain();try{data.writeLong(lease);data.writeLong(++sequence);data.writeBundle(frame);if(!remote.transact(2,data,reply,0))throw new RemoteException("update rejected");reply.readException();}catch(Exception e){remote=null;lease=0;unbind();Log.w("ArcfoxHudSource","HUD submission unavailable",e);}finally{data.recycle();reply.recycle();}}
 private void unbind(){if(bound){try{context.unbindService(connection);}catch(Exception ignored){}bound=false;}}
 @Override public void close(){if(closed)return;closed=true;worker.removeCallbacks(tick);worker.post(new Runnable(){public void run(){frame.putBoolean("active",false);send();unbind();remote=null;thread.quitSafely();}});}
}
