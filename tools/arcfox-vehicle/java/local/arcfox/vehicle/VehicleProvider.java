package local.arcfox.vehicle;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import com.shilapi.xcertplay.vehicle.arcfox.data.ArcfoxCarReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/** In-process read-only adapter for the byte-verified Dudu fix51 baseline.
 * Uses existing desktop events; does not register keys, change audio or touch navigation services. */
public final class VehicleProvider extends ContentProvider {
    private ArcfoxCarReader reader;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Object bus;
    private Method post;
    private Constructor<?> speedEvent, doorEvent;
    private TyreCardBridge tyres;
    private long lastError;
    private long lastTyreLog;
    @Override public boolean onCreate() {
        if (!Build.FINGERPRINT.startsWith("AAOP_COCKPIT/")) return true;
        main.postDelayed(() -> {
            try {
                Class<?> type = Class.forName("org.greenrobot.eventbus.c");
                bus = type.getMethod("d").invoke(null);
                post = type.getMethod("b", Object.class);
                speedEvent = Class.forName("com.dudu.autoui.manage.i.g.u").getConstructor(int.class);
                doorEvent = Class.forName("com.dudu.autoui.manage.i.g.f").getConstructor(
                    boolean.class, boolean.class, boolean.class, boolean.class, boolean.class);
                reader = new ArcfoxCarReader(getContext(), VehicleValues.IDS, this::receive);
                reader.start(1000);
            } catch (Exception e) { Log.w("Dudu-ArcfoxVehicle", "Vehicle adapter unavailable", e); }
        }, 2000);
        return true;
    }
    private void receive(ArcfoxCarReader.Value[] values) {
        Integer speed = VehicleValues.speedKmh(values[0], SystemClock.elapsedRealtime());
        boolean[] doors = VehicleValues.doors(values);
        VehicleValues.Tyres tyreValues = VehicleValues.tyres(values);
        long now = SystemClock.elapsedRealtime();
        if (lastTyreLog == 0 || now - lastTyreLog >= 30000) {
            lastTyreLog = now;
            StringBuilder raw = new StringBuilder("TPMS raw; wheel pressure=raw/10 bar; temperature=raw-40 C:");
            for (int i = 6; i < values.length; i++) raw.append(' ').append(Integer.toHexString(VehicleValues.IDS[i]))
                .append('=').append(values[i] == null ? "unavailable" : values[i].number);
            Log.i("Dudu-ArcfoxVehicle", raw.toString());
        }
        // One bounded UI post per second; no external broadcasts or cloud report calls.
        main.post(() -> {
            try {
                if (speed != null) post.invoke(bus, speedEvent.newInstance(speed));
                if (doors != null) post.invoke(bus, doorEvent.newInstance(
                    doors[0], doors[1], doors[2], doors[3], doors[4]));
                if (tyres == null) tyres = new TyreCardBridge(getContext(), bus, post);
                tyres.update(tyreValues);
            } catch (Exception e) {
                long errorTime = SystemClock.elapsedRealtime();
                if (lastError == 0 || errorTime - lastError > 30000) {
                    lastError = errorTime; Log.w("Dudu-ArcfoxVehicle", "Desktop event unavailable", e);
                }
            }
        });
    }
    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { return null; }
    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri u, String s, String[] a) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException(); }
    @Override public void shutdown() {
        main.removeCallbacksAndMessages(null);
        if (reader != null) reader.close();
        if (tyres != null) try { tyres.close(); } catch (Exception e) { Log.w("Dudu-ArcfoxVehicle", "Card cleanup", e); }
        super.shutdown();
    }
}
