package local.arcfox.phone;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcel;
import android.util.Log;

import java.lang.reflect.Method;

/** The Bluetooth-process writer for the OEM phone lamp and PhoneLink status. */
final class PhoneOutputs implements ServiceConnection {
    private static final String TAG = "ArcfoxPhoneOutputs";
    private static final String INTERFACE = "com.adayo.phonelinkproxy.IPhoneLink";
    private static final int CAR_PHONE_LAMP = 557845456;
    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private IBinder phoneLink;
    private boolean bound;
    private Object car;
    private Object propertyManager;
    private boolean stopped;

    PhoneOutputs(Context context) { this.context = context; }

    void start() {
        if (bound || stopped) return;
        Intent intent = new Intent("com.adayo.action.PhoneLinkService");
        intent.setClassName("com.adayo.phonelink", "com.adayo.phonelink.PhoneLinkService");
        try {
            bound = context.bindService(intent, this, Context.BIND_AUTO_CREATE);
            if (!bound) handler.postDelayed(this::start, 2000);
        } catch (RuntimeException e) {
            Log.w(TAG, "PhoneLink service bind failed", e);
            handler.postDelayed(this::start, 2000);
        }
    }

    void stop() {
        stopped = true;
        handler.removeCallbacksAndMessages(null);
        if (bound) {
            try { context.unbindService(this); } catch (RuntimeException e) {
                Log.w(TAG, "PhoneLink service unbind failed", e);
            }
            bound = false;
        }
        phoneLink = null;
        if (car != null) {
            try { car.getClass().getMethod("disconnect").invoke(car); }
            catch (Exception e) { Log.w(TAG, "Car disconnect failed", e); }
        }
        car = null;
        propertyManager = null;
    }

    @Override public void onServiceConnected(ComponentName name, IBinder service) {
        phoneLink = service;
    }

    @Override public void onServiceDisconnected(ComponentName name) {
        phoneLink = null;
    }

    @Override public void onBindingDied(ComponentName name) {
        phoneLink = null;
        if (bound) {
            try { context.unbindService(this); } catch (RuntimeException ignored) { }
            bound = false;
        }
        if (!stopped) handler.postDelayed(this::start, 1000);
    }

    @Override public void onNullBinding(ComponentName name) { onBindingDied(name); }

    /** 1 ringing, 2 dialing, 3 active, 0 idle. Returns false for retry. */
    boolean write(int phase, boolean nativeCallActive) {
        Boolean nativeCarPlay = nativeCarPlayConnected();
        if (nativeCallActive || nativeCarPlay == null || nativeCarPlay) return false;
        if (phase != 0 && !report(true, phase == 1 ? 5 : 2)) return false;
        boolean firstStop = phase == 1 || report(false, 5);
        boolean secondStop = phase == 2 || phase == 3 || report(false, 2);
        boolean lamp = setLamp(phase == 1 ? 1 : phase == 0 ? 0 : 2);
        Boolean confirmed = phoneCallActive();
        return firstStop && secondStop && lamp && confirmed != null && confirmed == (phase != 0);
    }

    boolean clear(boolean nativeCallActive) {
        return write(0, nativeCallActive);
    }

    private boolean report(boolean active, int volume) {
        IBinder b = phoneLink;
        if (b == null) return false;
        Parcel input = Parcel.obtain();
        Parcel output = Parcel.obtain();
        try {
            input.writeInterfaceToken(INTERFACE);
            input.writeInt(active ? 1 : 0);
            input.writeInt(3);
            input.writeInt(volume);
            if (!b.transact(47, input, output, 0)) return false;
            output.readException();
            return true;
        } catch (Exception e) {
            Log.w(TAG, "PhoneLink report failed", e);
            return false;
        } finally {
            input.recycle();
            output.recycle();
        }
    }

    private Boolean nativeCarPlayConnected() {
        IBinder b = phoneLink;
        if (b == null) return null;
        Parcel input = Parcel.obtain();
        Parcel output = Parcel.obtain();
        try {
            input.writeInterfaceToken(INTERFACE);
            input.writeInt(3);
            if (!b.transact(5, input, output, 0)) return null;
            output.readException();
            if (output.readInt() == 0) return null;
            return output.readByte() != 0;
        } catch (Exception e) {
            Log.w(TAG, "PhoneLink native state unavailable", e);
            return null;
        } finally {
            input.recycle();
            output.recycle();
        }
    }

    private Boolean phoneCallActive() {
        IBinder b = phoneLink;
        if (b == null) return null;
        Parcel input = Parcel.obtain();
        Parcel output = Parcel.obtain();
        try {
            input.writeInterfaceToken(INTERFACE);
            input.writeInt(3);
            if (!b.transact(5, input, output, 0)) return null;
            output.readException();
            if (output.readInt() == 0) return null;
            output.readByte(); // CarPlay connected
            output.readByte(); // navigation
            return output.readByte() != 0;
        } catch (Exception e) {
            Log.w(TAG, "PhoneLink call state unavailable", e);
            return null;
        } finally {
            input.recycle();
            output.recycle();
        }
    }

    private boolean setLamp(int value) {
        try {
            if (propertyManager == null) {
                Class<?> carClass = Class.forName("android.car.Car");
                Method createCar = carClass.getMethod("createCar", Context.class);
                if (car == null) car = createCar.invoke(null, context);
                if (!Boolean.TRUE.equals(carClass.getMethod("isConnected").invoke(car))) {
                    if (!Boolean.TRUE.equals(carClass.getMethod("isConnecting").invoke(car)))
                        carClass.getMethod("connect").invoke(car);
                    return false;
                }
                propertyManager = carClass.getMethod("getCarManager", String.class)
                        .invoke(car, "property");
            }
            propertyManager.getClass().getMethod("setIntProperty", int.class, int.class, int.class)
                    .invoke(propertyManager, CAR_PHONE_LAMP, 0, value);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "OEM phone lamp update failed", e);
            propertyManager = null;
            return false;
        }
    }
}
