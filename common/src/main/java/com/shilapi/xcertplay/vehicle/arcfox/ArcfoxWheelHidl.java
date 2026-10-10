package com.shilapi.xcertplay.vehicle.arcfox;

import android.os.HwBinder;
import android.os.HwParcel;
import android.os.IHwInterface;
import android.util.Log;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Consumer;

/** Minimal passive client of the HS7013A HIDL ABI; no vendor messages or unregister calls. */
public final class ArcfoxWheelHidl {
    private static final String SERVICE = "android.hardware.fgecomservice@1.0::IFgeComService";
    private static final String CALLBACK = "android.hardware.fgecomservice@1.0::IFgeComServiceCallback";
    private static final String BASE = "android.hidl.base@1.0::IBase";
    private static final Class<?>[] NONE = new Class<?>[0];
    private final Callback callback;
    private Object remote;
    private boolean registered;

    public ArcfoxWheelHidl(Consumer<String> receive) { callback = new Callback(receive); }

    public boolean ensureConnected() throws Exception {
        try {
            if (remote != null) transact(256921159, BASE, request -> {}, reply -> null);
            else remote = HwBinder.class.getMethod("getService", String.class, String.class, boolean.class)
                    .invoke(null, SERVICE, "default", false);
            if (remote == null) return false;
            if (!registered) registered = (Boolean) transact(4, SERVICE, request -> {
                call(request, "writeStrongBinder", new Class<?>[]{Class.forName("android.os.IHwBinder")}, callback);
                call(request, "writeInt16", new Class<?>[]{short.class}, (short) 14);
            }, reply -> call(reply, "readBool", NONE));
            return registered;
        } catch (Exception error) {
            remote = null;
            registered = false;
            throw error;
        }
    }

    private interface ParcelAction { Object apply(Object parcel) throws Exception; }
    private interface RequestAction { void apply(Object parcel) throws Exception; }

    private Object transact(int code, String descriptor, RequestAction write, ParcelAction read) throws Exception {
        Class<?> parcel = Class.forName("android.os.HwParcel");
        Object request = parcel.getConstructor().newInstance(), reply = parcel.getConstructor().newInstance();
        try {
            call(request, "writeInterfaceToken", new Class<?>[]{String.class}, descriptor);
            write.apply(request);
            Class.forName("android.os.IHwBinder").getMethod("transact", int.class, parcel, parcel, int.class)
                    .invoke(remote, code, request, reply, 0);
            call(reply, "verifySuccess", NONE);
            return read.apply(reply);
        } finally {
            try { call(request, "releaseTemporaryStorage", NONE); }
            finally { call(reply, "release", NONE); }
        }
    }

    private static Object call(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = target.getClass().getMethod(name, types);
        return method.invoke(target, args);
    }

    /** Matches the extracted IFgeComServiceCallback Stub's transactions and 40-byte MessageCallback. */
    private static final class Callback extends HwBinder {
        private final Consumer<String> receive;
        Callback(Consumer<String> receive) { this.receive = receive; }
        @Override public IHwInterface queryLocalInterface(String descriptor) { return null; }

        @Override public void onTransact(int code, HwParcel request, HwParcel reply, int flags) {
            try {
                boolean oneWay = (flags & 1) != 0;
                boolean expectedOneWay = code == 1 || code == 256462420 || code == 257120595;
                if (oneWay != expectedOneWay) {
                    call(reply, "writeStatus", new Class<?>[]{int.class}, Integer.MIN_VALUE);
                    call(reply, "send", NONE);
                    return;
                }
                call(request, "enforceInterface", new Class<?>[]{String.class}, code == 1 ? CALLBACK : BASE);
                if (code == 1) {
                    Object blob = call(request, "readBuffer", new Class<?>[]{long.class}, 40L);
                    String content = readString(request, blob, 8L);
                    readString(request, blob, 24L); // Complete the parcel's embedded-string reads.
                    receive.accept(content);
                    return;
                }
                if (oneWay) return; // Instrumentation/sysprops callbacks carry no navigation data.
                call(reply, "writeStatus", new Class<?>[]{int.class}, 0);
                switch (code) {
                    case 256067662:
                        call(reply, "writeStringVector", new Class<?>[]{ArrayList.class},
                                new ArrayList<>(Arrays.asList(CALLBACK, BASE)));
                        break;
                    case 256136003:
                        call(reply, "writeString", new Class<?>[]{String.class}, CALLBACK);
                        break;
                    case 256398152:
                        Object header = blob(16), hashes = blob(64);
                        call(header, "putInt32", new Class<?>[]{long.class, int.class}, 8L, 2);
                        call(header, "putBool", new Class<?>[]{long.class, boolean.class}, 12L, false);
                        call(hashes, "putInt8Array", new Class<?>[]{long.class, byte[].class}, 0L, new byte[32]);
                        call(hashes, "putInt8Array", new Class<?>[]{long.class, byte[].class}, 32L,
                                new byte[]{-67,-38,-74,24,77,122,52,109,-90,-96,125,-64,-126,-116,-15,-102,
                                        105,111,76,-86,54,17,-59,31,46,20,86,90,20,-76,15,-39});
                        call(header, "putBlob", new Class<?>[]{long.class, Class.forName("android.os.HwBlob")}, 0L, hashes);
                        writeBlob(reply, header);
                        break;
                    case 257049926:
                        Object info = blob(24);
                        call(info, "putInt32", new Class<?>[]{long.class, int.class}, 0L, android.os.Process.myPid());
                        call(info, "putInt64", new Class<?>[]{long.class, long.class}, 8L, 0L);
                        call(info, "putInt32", new Class<?>[]{long.class, int.class}, 16L, 0);
                        writeBlob(reply, info);
                        break;
                    case 256131655: // debug
                    case 256921159: // ping
                        break;
                    default:
                        return;
                }
                call(reply, "send", NONE);
            } catch (Exception error) {
                Log.w("DiPlay-ArcfoxWheel", "HIDL callback unavailable", error);
            }
        }

        private static Object blob(int size) throws Exception {
            return Class.forName("android.os.HwBlob").getConstructor(int.class).newInstance(size);
        }
        private static void writeBlob(Object parcel, Object blob) throws Exception {
            call(parcel, "writeBuffer", new Class<?>[]{Class.forName("android.os.HwBlob")}, blob);
        }
        private static String readString(Object request, Object blob, long offset) throws Exception {
            String value = (String) call(blob, "getString", new Class<?>[]{long.class}, offset);
            long handle = (Long) call(blob, "handle", NONE);
            call(request, "readEmbeddedBuffer", new Class<?>[]{long.class,long.class,long.class,boolean.class},
                    (long) value.getBytes(StandardCharsets.UTF_8).length + 1, handle, offset, false);
            return value;
        }
    }
}
