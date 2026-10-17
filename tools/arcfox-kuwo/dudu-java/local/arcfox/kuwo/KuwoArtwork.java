package local.arcfox.kuwo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.net.Uri;
import android.os.SystemClock;
import android.util.Log;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.LongSupplier;

/** Public artwork already supplied by the original, unmodified Kuwo MediaSession. */
public final class KuwoArtwork {
    static final int MAX_BYTES=2*1024*1024, MAX_SIDE=512, MAX_SOURCE_SIDE=8192;
    static final long MAX_SOURCE_PIXELS=4L*1024*1024, RETRY_MS=2000;
    static final int MAX_ENTRIES=4, MAX_ATTEMPTS=2, MAX_CALLBACKS=4;
    private static final String TAG="Dudu-KuwoArtwork";
    private static final Engine SHARED=new Engine(new ThreadPoolExecutor(1,1,0L,TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<Runnable>(2),r->{Thread t=new Thread(r,TAG);t.setDaemon(true);return t;},
        new ThreadPoolExecutor.AbortPolicy()),KuwoArtwork::read,SystemClock::elapsedRealtime);
    private KuwoArtwork(){}

    /**
     * Does not wait for network or decoding. Returns a bounded Bitmap, or null while loading.
     * onReady only requests a fresh consumer refresh: callers must reselect their current session
     * and call get again, never inject an old captured Bitmap. Cached Bitmaps must not be recycled.
     */
    public static Bitmap get(Context context,MediaController controller,MediaMetadata metadata,Runnable onReady){
        return SHARED.get(context,controller,metadata,onReady);
    }

    interface Fetcher {byte[] read(Context context,Uri uri)throws Exception;}
    static final class Key {
        final Object token;final List<String> track;final String uri;
        Key(Object t,List<String> k,String u){token=t;track=k;uri=u;}
        @Override public boolean equals(Object o){if(!(o instanceof Key))return false;Key k=(Key)o;return Objects.equals(token,k.token)&&track.equals(k.track)&&uri.equals(k.uri);}
        @Override public int hashCode(){return Objects.hash(token,track,uri);}
    }
    static final class Entry {
        final Key key;final LinkedHashSet<Runnable> callbacks=new LinkedHashSet<>();
        Bitmap bitmap;boolean loading;int attempts;long retryAt;
        Entry(Key k){key=k;}
    }
    static final class Engine {
        final Executor executor;final Fetcher fetcher;final LongSupplier clock;
        final LinkedHashMap<Key,Entry> cache=new LinkedHashMap<>(8,0.75f,true);
        Engine(Executor e,Fetcher f,LongSupplier c){executor=e;fetcher=f;clock=c;}
        Bitmap get(Context context,MediaController controller,MediaMetadata metadata,Runnable onReady){
            if(context==null||controller==null||metadata==null)return null;
            final Bitmap embedded=embedded(metadata);
            final Key key=key(controller,metadata,embedded);
            if(key==null||!current(controller,key))return null;
            if(valid(embedded)&&embedded.getWidth()<=MAX_SIDE&&embedded.getHeight()<=MAX_SIDE)return embedded;
            final Entry entry;
            synchronized(this){
                Entry existing=cache.get(key);
                if(existing==null){
                    if(cache.size()>=MAX_ENTRIES){
                        Iterator<Entry> items=cache.values().iterator();boolean removed=false;
                        while(items.hasNext()){Entry candidate=items.next();if(!candidate.loading){items.remove();candidate.callbacks.clear();removed=true;break;}}
                        if(!removed)return null;
                    }
                    cache.put(key,existing=new Entry(key));
                }
                entry=existing;
                if(valid(entry.bitmap))return entry.bitmap;
                if(entry.loading){if(onReady!=null&&entry.callbacks.size()<MAX_CALLBACKS)entry.callbacks.add(onReady);return null;}
                if(entry.attempts>=MAX_ATTEMPTS||clock.getAsLong()<entry.retryAt)return null;
                if(onReady!=null&&entry.callbacks.size()<MAX_CALLBACKS)entry.callbacks.add(onReady);
                entry.loading=true;entry.attempts++;
            }
            final Context app=context.getApplicationContext();
            try{executor.execute(()->load(app,controller,entry,embedded));}
            catch(RejectedExecutionException full){synchronized(this){entry.loading=false;entry.attempts--;entry.callbacks.clear();entry.retryAt=clock.getAsLong()+RETRY_MS;}}
            return null;
        }
        private void load(Context app,MediaController controller,Entry entry,Bitmap embedded){
            Bitmap image=null;String failure=null;
            try{
                if(!current(controller,entry.key))return;
                image=valid(embedded)?scale(embedded):decode(fetcher.read(app,Uri.parse(entry.key.uri)));
                if(image==null)failure="invalid-image";
            }catch(Exception error){failure=error.getClass().getSimpleName();}
            finally{
                final List<Runnable> ready;
                synchronized(this){
                    entry.loading=false;
                    if(cache.get(entry.key)!=entry){return;}
                    entry.bitmap=image;entry.retryAt=image==null?clock.getAsLong()+RETRY_MS:Long.MAX_VALUE;
                    ready=new ArrayList<>(entry.callbacks);entry.callbacks.clear();
                }
                if(failure!=null)Log.w(TAG,"Artwork unavailable: "+failure);
                if(image!=null&&current(controller,entry.key))for(Runnable callback:ready){try{callback.run();}catch(RuntimeException error){Log.w(TAG,"Refresh callback failed",error);}}
            }
        }
        private boolean current(MediaController controller,Key expected){
            try{MediaMetadata metadata=controller.getMetadata();return metadata!=null&&expected.equals(key(controller,metadata,embedded(metadata)));}
            catch(RuntimeException ended){return false;}
        }
    }
    private static Key key(MediaController controller,MediaMetadata metadata,Bitmap embedded){
        String uri=uri(metadata);
        if(valid(embedded))uri="embedded:"+uri;
        Object token=controller.getSessionToken();
        if(uri.isEmpty()||token==null)return null;
        return new Key(token,track(metadata),uri);
    }
    private static List<String> track(MediaMetadata m){return Arrays.asList(text(m,MediaMetadata.METADATA_KEY_MEDIA_ID),
        text(m,MediaMetadata.METADATA_KEY_TITLE),text(m,MediaMetadata.METADATA_KEY_ARTIST),
        text(m,MediaMetadata.METADATA_KEY_ALBUM),Long.toString(m.getLong(MediaMetadata.METADATA_KEY_DURATION)));}
    private static String text(MediaMetadata m,String key){String value=m.getString(key);return value==null?"":value;}
    private static Bitmap embedded(MediaMetadata m){
        for(String key:new String[]{MediaMetadata.METADATA_KEY_ALBUM_ART,MediaMetadata.METADATA_KEY_ART,MediaMetadata.METADATA_KEY_DISPLAY_ICON}){
            Bitmap b=m.getBitmap(key);if(valid(b))return b;
        }return null;
    }
    private static boolean valid(Bitmap b){return b!=null&&!b.isRecycled()&&b.getWidth()>0&&b.getHeight()>0&&
        b.getWidth()<=MAX_SOURCE_SIDE&&b.getHeight()<=MAX_SOURCE_SIDE&&(long)b.getWidth()*b.getHeight()<=MAX_SOURCE_PIXELS;}
    private static String uri(MediaMetadata m){
        for(String key:new String[]{MediaMetadata.METADATA_KEY_ALBUM_ART_URI,MediaMetadata.METADATA_KEY_ART_URI,MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI}){
            String value=m.getString(key);if(value==null||value.length()>8192)continue;
            String scheme=Uri.parse(value).getScheme();
            if("http".equalsIgnoreCase(scheme)||"https".equalsIgnoreCase(scheme)||"content".equalsIgnoreCase(scheme)||"file".equalsIgnoreCase(scheme))return value;
        }return "";
    }
    static Bitmap decode(byte[] bytes){
        if(bytes==null||bytes.length==0||bytes.length>MAX_BYTES)return null;
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
        BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
        if(bounds.outWidth<1||bounds.outHeight<1||bounds.outWidth>MAX_SOURCE_SIDE||bounds.outHeight>MAX_SOURCE_SIDE||
           (long)bounds.outWidth*bounds.outHeight>MAX_SOURCE_PIXELS)return null;
        int sample=1;while(Math.max(bounds.outWidth,bounds.outHeight)/sample>MAX_SIDE*2)sample*=2;
        BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=sample;
        Bitmap decoded=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
        if(!valid(decoded)){if(decoded!=null&&!decoded.isRecycled())decoded.recycle();return null;}
        Bitmap result=scale(decoded);if(result!=decoded)decoded.recycle();return result;
    }
    private static Bitmap scale(Bitmap source){
        int largest=Math.max(source.getWidth(),source.getHeight());if(largest<=MAX_SIDE)return source;
        return Bitmap.createScaledBitmap(source,Math.max(1,source.getWidth()*MAX_SIDE/largest),Math.max(1,source.getHeight()*MAX_SIDE/largest),true);
    }
    static byte[] read(Context context,Uri uri)throws Exception{
        String scheme=uri.getScheme();
        if("content".equalsIgnoreCase(scheme)||"file".equalsIgnoreCase(scheme)){
            try(InputStream input=context.getContentResolver().openInputStream(uri)){return bounded(input,System.nanoTime());}
        }
        if(!"http".equalsIgnoreCase(scheme)&&!"https".equalsIgnoreCase(scheme))throw new IOException("unsupported scheme");
        URL url=new URL(uri.toString());long started=System.nanoTime();
        for(int redirects=0;redirects<=3;redirects++){
            if(System.nanoTime()-started>5_000_000_000L)throw new IOException("read deadline");
            HttpURLConnection connection=(HttpURLConnection)url.openConnection();
            connection.setConnectTimeout(1500);connection.setReadTimeout(2000);connection.setInstanceFollowRedirects(false);
            try{
                int status=connection.getResponseCode();
                if(status>=300&&status<=399){String location=connection.getHeaderField("Location");if(location==null||redirects==3)throw new IOException("redirect limit");
                    URL next=new URL(url,location);if(!"http".equalsIgnoreCase(next.getProtocol())&&!"https".equalsIgnoreCase(next.getProtocol()))throw new IOException("unsupported redirect");
                    url=next;continue;}
                if(status<200||status>=300)throw new IOException("HTTP "+status);
                if(connection.getContentLengthLong()>MAX_BYTES)throw new IOException("oversize");
                try(InputStream input=connection.getInputStream()){return bounded(input,started);}
            }finally{connection.disconnect();}
        }
        throw new IOException("redirect limit");
    }
    private static byte[] bounded(InputStream input,long started)throws IOException{
        if(input==null)throw new IOException("missing stream");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] chunk=new byte[16384];int count;
        while((count=input.read(chunk))!=-1){
            if(System.nanoTime()-started>5_000_000_000L)throw new IOException("read deadline");
            if(count>MAX_BYTES-bytes.size())throw new IOException("oversize");bytes.write(chunk,0,count);
        }
        return bytes.toByteArray();
    }
}
