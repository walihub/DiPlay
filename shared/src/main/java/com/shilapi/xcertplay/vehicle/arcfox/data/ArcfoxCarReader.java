package com.shilapi.xcertplay.vehicle.arcfox.data;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import java.lang.reflect.Method;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Read-only CarPropertyManager adapter. Matches the captured HS7013A BCM client.
 * No setter, raw Binder transaction, HAL ownership, or vendor-service restart. */
public final class ArcfoxCarReader implements AutoCloseable {
    public static final int SPEED = 0x1160010e;
    public static final int GEAR = 0x11400400;
    public interface Listener { void onValues(Value[] values); }
    public static final class Value {
        public final int id;
        public final Number number;
        public final long timestampNanos;
        public Value(int id, Number number, long timestampNanos) {
            this.id = id; this.number = number; this.timestampNanos = timestampNanos;
        }
    }
    private final Context context;
    private final int[] ids;
    private final Listener listener;
    private ScheduledExecutorService executor;
    private Object car;
    private Object manager;
    private Method getProperty;
    private long connectingAt;
    private long lastWarning;
    private long nextConnectAt;
    private long generation;
    private ServiceConnection connectionFor(final long token) { return new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            synchronized (ArcfoxCarReader.this) {
                if (executor == null || car == null || generation != token) return;
                try {
                    manager = car.getClass().getMethod("getCarManager", String.class).invoke(car, "property");
                    getProperty = manager.getClass().getMethod("getProperty", int.class, int.class);
                } catch (Exception e) { reset(); warn(e); }
            }
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            synchronized (ArcfoxCarReader.this) { if (generation == token) reset(); }
        }
    }; }
    public ArcfoxCarReader(Context context, int[] ids, Listener listener) {
        this.context = context.getApplicationContext(); this.ids = ids.clone(); this.listener = listener;
    }
    public synchronized void start(long periodMillis) {
        if (executor != null) return;
        nextConnectAt = 0;
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "arcfox-vehicle-read"); t.setDaemon(true); return t;
        });
        executor.scheduleWithFixedDelay(this::poll, 0, periodMillis, TimeUnit.MILLISECONDS);
    }
    private synchronized void poll() {
        if (executor == null) return;
        Value[] values = new Value[ids.length];
        try {
            if (manager == null) {
                if (car != null && SystemClock.elapsedRealtime() - connectingAt > 10000) reset();
                if (car == null && SystemClock.elapsedRealtime() >= nextConnectAt) {
                    nextConnectAt = SystemClock.elapsedRealtime() + 5000;
                    grantSpeedPermission();
                    connectingAt = SystemClock.elapsedRealtime();
                    Class<?> type = Class.forName("android.car.Car");
                    car = type.getMethod("createCar", Context.class, ServiceConnection.class, Handler.class)
                            .invoke(null, context, connectionFor(++generation), new Handler(Looper.getMainLooper()));
                    type.getMethod("connect").invoke(car);
                }
            } else {
                for (int i = 0; i < ids.length; i++) {
                    try {
                        Object p = getProperty.invoke(manager, ids[i], 0);
                        if (p == null || ((Number) p.getClass().getMethod("getStatus").invoke(p)).intValue() != 0) continue;
                        Object n = p.getClass().getMethod("getValue").invoke(p);
                        long time = ((Number) p.getClass().getMethod("getTimestamp").invoke(p)).longValue();
                        if (n instanceof Number) values[i] = new Value(ids[i], (Number) n, time);
                    } catch (Exception e) {
                        warn(e);
                        Throwable cause = e;
                        while (cause.getCause() != null) cause = cause.getCause();
                        if (cause instanceof android.os.DeadObjectException) {
                            reset(); values = new Value[ids.length]; break;
                        }
                    }
                }
            }
        } catch (Exception e) { reset(); warn(e); }
        try { listener.onValues(values); } catch (RuntimeException e) { warn(e); }
    }
    private void grantSpeedPermission() {
        boolean needed = false;
        for (int id : ids) if (id == SPEED) needed = true;
        if (!needed || context.checkSelfPermission("android.car.permission.CAR_SPEED") == 0 ||
            context.checkSelfPermission("android.permission.GRANT_RUNTIME_PERMISSIONS") != 0) return;
        try {
            android.content.pm.PackageManager.class.getMethod("grantRuntimePermission", String.class,
                    String.class, android.os.UserHandle.class).invoke(context.getPackageManager(),
                    context.getPackageName(), "android.car.permission.CAR_SPEED", android.os.Process.myUserHandle());
        } catch (Exception e) { warn(e); }
    }
    private void warn(Exception e) {
        long now = SystemClock.elapsedRealtime();
        if (lastWarning == 0 || now - lastWarning >= 30000) {
            lastWarning = now;
            Log.w("Arcfox-Vehicle", "Vehicle read unavailable", e);
        }
    }
    private void reset() {
        ++generation;
        Object old = car;
        car = null; manager = null; getProperty = null;
        if (old != null) try { old.getClass().getMethod("disconnect").invoke(old); } catch (Exception ignored) { }
    }
    @Override public synchronized void close() {
        ScheduledExecutorService old = executor;
        executor = null;
        if (old != null) old.shutdownNow();
        reset();
    }
}
