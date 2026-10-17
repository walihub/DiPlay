package local.arcfox.kuwo;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.os.SystemClock;
import android.util.Log;
import org.json.JSONObject;
import java.io.*;
import java.lang.reflect.*;
import java.security.MessageDigest;
/** Exact OEM message 114 ABI. No callbacks, source switching, Link broadcasts or audio control. */
public final class Instrument {
 public interface Wire { boolean send(String json); }
 public interface Art { String write(Bitmap bitmap)throws Exception; }
 private final Wire wire; private final Art art;
 private String identity="",cover="";private Bitmap previous;private long lastCoverWarning=-30000;
 public Instrument(){this(new Hidl(),new CoverStore(new File("/mnt/ota/media")));}
 public Instrument(Wire w,Art a){wire=w;art=a;}
 public boolean send(MediaMetadata md,long position,boolean playing,boolean muted){
  try{
   String title=text(md,MediaMetadata.METADATA_KEY_TITLE),artist=text(md,MediaMetadata.METADATA_KEY_ARTIST);
   String track=text(md,MediaMetadata.METADATA_KEY_MEDIA_ID)+"\n"+title+"\n"+artist+"\n"+text(md,MediaMetadata.METADATA_KEY_ALBUM)+"\n"+text(md,MediaMetadata.METADATA_KEY_ALBUM_ART_URI)+"\n"+text(md,MediaMetadata.METADATA_KEY_ART_URI);
   if(!track.equals(identity)){identity=track;cover="";previous=null;}
   Bitmap image=md==null?null:md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
   if(image==null&&md!=null)image=md.getBitmap(MediaMetadata.METADATA_KEY_ART);
   // Metadata is incremental in practice: a text/URI-only update on the same track must not
   // erase a successfully published cover while the original loader catches up.
   if(image!=null&&(image!=previous||cover.isEmpty()||!new File("/mnt/ota/media",cover).isFile())){
    try{String name=art.write(image);if(name==null||!name.matches("kuwo_music_[a-f0-9]{64}\\.png"))throw new IOException("invalid cover filename");cover=name;previous=image;}
    catch(Exception e){previous=null;long now=SystemClock.elapsedRealtime();
     if(now-lastCoverWarning>=30000){lastCoverWarning=now;Log.w("Kuwo-Arcfox","Instrument cover file unavailable",e);}
    }
   }
   long duration=md==null?0:md.getLong(MediaMetadata.METADATA_KEY_DURATION);
   String json=encode(title,artist,duration,position,playing,muted,cover);
   return json.getBytes("UTF-8").length<=65536&&wire.send(json);
  }catch(Exception e){return false;}
 }
 static String text(MediaMetadata m,String key){if(m==null)return "";String s=m.getString(key);return s==null?"":s;}
 public static String encode(String title,String artist,long duration,long position,boolean playing,boolean muted,String cover)throws Exception{
  // Type 19 is the proven third-party instrument display lane, not a registered OEM source ID.
  JSONObject content=new JSONObject().put("mediatype",19).put("songname",title).put("singername",artist)
   .put("totalplaytime",MediaPolicy.seconds(duration)).put("curplaytime",MediaPolicy.seconds(duration>0?Math.min(duration,Math.max(0,position)):Math.max(0,position)))
   .put("status",playing?"play":"pause").put("mute",muted?0:1).put("albumpic",cover).put("singlelyric","").put("lyric","");
  return new JSONObject().put("command",new JSONObject().put("id","meidainfo").put("sub","id3info")).put("content",content).toString();
 }
 public static final class Hidl implements Wire {
  private static final String SERVICE="android.hardware.fgecomservice@1.0::IFgeComService";
  private Object remote;
  public boolean send(String json){Object req=null,reply=null;try{
   Class<?> parcel=Class.forName("android.os.HwParcel"),blob=Class.forName("android.os.HwBlob"),binder=Class.forName("android.os.IHwBinder");
   if(remote==null)remote=Class.forName("android.os.HwBinder").getMethod("getService",String.class,String.class,boolean.class).invoke(null,SERVICE,"default",false);
   if(remote==null)return false;
   req=parcel.getConstructor().newInstance();reply=parcel.getConstructor().newInstance();
   parcel.getMethod("writeInterfaceToken",String.class).invoke(req,SERVICE);
   Object data=blob.getConstructor(int.class).newInstance(40);
   blob.getMethod("putInt32",long.class,int.class).invoke(data,0L,114);
   blob.getMethod("putString",long.class,String.class).invoke(data,8L,json);
   blob.getMethod("putString",long.class,String.class).invoke(data,24L,"");
   parcel.getMethod("writeBuffer",blob).invoke(req,data);
   binder.getMethod("transact",int.class,parcel,parcel,int.class).invoke(remote,1,req,reply,0);
   parcel.getMethod("verifySuccess").invoke(reply);return true;
  }catch(Exception e){remote=null;return false;}
  finally{release(req,"releaseTemporaryStorage");release(reply,"release");}}
  private static void release(Object o,String method){if(o!=null)try{o.getClass().getMethod(method).invoke(o);}catch(Exception ignored){}}
 }
 public static final class CoverStore implements Art {
  private final File directory;
  public CoverStore(File dir){directory=dir;}
  public String write(Bitmap bitmap)throws Exception{
   if(bitmap.isRecycled()||bitmap.getWidth()<=0||bitmap.getHeight()<=0||bitmap.getWidth()>4096||bitmap.getHeight()>4096||(long)bitmap.getWidth()*bitmap.getHeight()>4194304)throw new IOException("invalid bitmap bounds");
   ByteArrayOutputStream output=new ByteArrayOutputStream();
   if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,output))throw new IOException("encode failed");
   byte[] bytes=output.toByteArray();if(bytes.length>2097152)throw new IOException("cover too large");
   StringBuilder hash=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))hash.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
   String name="kuwo_music_"+hash+".png";
   if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("shared directory unavailable");
   File target=new File(directory,name);
   if(target.isFile()&&target.canRead()&&target.length()>0)return name;
   File temp=File.createTempFile("kuwo_music_",".tmp",directory);
   try{try(FileOutputStream stream=new FileOutputStream(temp)){stream.write(bytes);stream.getFD().sync();}
    if(!temp.setReadable(true,false)||!temp.renameTo(target))throw new IOException("publish cover failed");
   }finally{temp.delete();}
   File[] files=directory.listFiles();if(files!=null)for(File file:files)if(!file.equals(target)&&file.getName().matches("kuwo_music_[a-f0-9]{64}\\.png")&&System.currentTimeMillis()-file.lastModified()>86400000L)file.delete();
   return name;
  }
 }
}
