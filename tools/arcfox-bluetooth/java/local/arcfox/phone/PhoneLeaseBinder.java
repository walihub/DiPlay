package local.arcfox.phone;

import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;

import java.util.List;
import java.util.Locale;

/** Owns the target phone's HFP lease independently of DiPlay's process. */
public final class PhoneLeaseBinder extends Binder implements ServiceConnection {
    private static final String TAG = "ArcfoxPhoneLease";
    private static final String DESCRIPTOR = "arcfox.phone.lease.v1";
    private static final String CLIENT = "com.shihab.diplay";
    private static final String VENDOR = "com.nforetek.bt.aidl.UiCommand";
    private static final String PREFS = "arcfox_phone_lease";
    private final Context context;
    private final SharedPreferences prefs;
    private final PhoneOutputs outputs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable publishTask = this::publishQueued;
    private final Runnable recoverTask = this::recoverPending;
    private final Runnable rebindTask = this::bindVendor;
    private IBinder vendor;
    private IBinder lifetime;
    private IBinder.DeathRecipient death;
    private String target;
    private long lease;
    private boolean restoreNeeded;
    private boolean bound;
    private int callPhase;
    private int publishedPhase = -1;
    private long publishedAt;
    private boolean outputsTouched;
    private boolean stopped;
    private boolean receiverRegistered;
    private final BroadcastReceiver hfpStates = new BroadcastReceiver() {
        @Override public void onReceive(Context ignored, Intent intent) {
            android.bluetooth.BluetoothDevice device = (android.bluetooth.BluetoothDevice) intent.getParcelableExtra(
                    "android.bluetooth.device.extra.DEVICE");
            if (device == null) return;
            synchronized (PhoneLeaseBinder.this) {
                String watched = target != null ? target : prefs.getString("target", null);
                if (watched == null || !watched.equals(device.getAddress())) return;
                int state = intent.getIntExtra("android.bluetooth.profile.extra.STATE", -1);
                if (state == 0) {
                    prefs.edit().putBoolean("observedDisconnected", true).commit();
                    if (lease == 0) handler.post(recoverTask);
                } else if (state == 2) {
                    if (lease == 0) handler.post(recoverTask);
                    else handler.post(() -> status(lease));
                }
            }
        }
    };

    public PhoneLeaseBinder(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.outputs = new PhoneOutputs(context);
        attachInterface(null, DESCRIPTOR);
    }

    public void start() {
        try {
            context.registerReceiver(hfpStates, new IntentFilter(
                    "android.bluetooth.headsetclient.profile.action.CONNECTION_STATE_CHANGED"));
            receiverRegistered = true;
        } catch (RuntimeException e) {
            Log.w(TAG, "HFP event registration unavailable; polling remains active", e);
        }
        outputs.start();
        bindVendor();
        handler.postDelayed(recoverTask, 5000);
    }

    private synchronized void bindVendor() {
        if (bound || stopped) return;
        Intent intent = new Intent("com.nforetek.bt.CUSTOMER_SERVICE");
        intent.setClassName("com.nforetek.bt.customer.service",
                "com.nforetek.bt.customer.service.BtService");
        try {
            bound = context.bindService(intent, this, Context.BIND_AUTO_CREATE);
            if (!bound) handler.postDelayed(rebindTask, 2000);
        } catch (RuntimeException e) {
            Log.e(TAG, "Cannot bind vendor Bluetooth service", e);
            handler.postDelayed(rebindTask, 2000);
        }
    }

    public synchronized void stop() {
        stopped = true;
        handler.removeCallbacks(publishTask);
        handler.removeCallbacks(recoverTask);
        handler.removeCallbacks(rebindTask);
        if (receiverRegistered) {
            try { context.unregisterReceiver(hfpStates); } catch (RuntimeException ignored) { }
            receiverRegistered = false;
        }
        if (lease != 0) releaseInternal();
        if (bound) {
            try { context.unbindService(this); } catch (RuntimeException e) {
                Log.w(TAG, "Vendor unbind failed", e);
            }
            bound = false;
        }
        vendor = null;
        outputs.stop();
    }

    @Override public synchronized void onServiceConnected(ComponentName name, IBinder service) {
        vendor = service;
        recoverPending();
    }

    @Override public synchronized void onServiceDisconnected(ComponentName name) {
        vendor = null;
    }

    @Override public synchronized void onBindingDied(ComponentName name) {
        vendor = null;
        if (bound) {
            try { context.unbindService(this); } catch (RuntimeException ignored) { }
            bound = false;
        }
        if (!stopped) handler.postDelayed(rebindTask, 1000);
    }

    @Override public void onNullBinding(ComponentName name) { onBindingDied(name); }

    @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
            throws RemoteException {
        if (code == INTERFACE_TRANSACTION) {
            reply.writeString(DESCRIPTOR);
            return true;
        }
        if (code < 1 || code > 4) return super.onTransact(code, data, reply, flags);
        data.enforceInterface(DESCRIPTOR);
        String claimedPackage = data.readString();
        if (!isAuthorized(claimedPackage)) {
            reply.writeNoException();
            if (code == 1) reply.writeLong(0L);
            else reply.writeInt(0);
            return true;
        }
        switch (code) {
            case 1: {
                long id = acquire(data.readStrongBinder(), data.readString());
                reply.writeNoException();
                reply.writeLong(id);
                return true;
            }
            case 2: {
                int state = status(data.readLong());
                reply.writeNoException();
                reply.writeInt(state);
                return true;
            }
            case 3: {
                boolean accepted = updateCall(data.readLong(), data.readInt());
                reply.writeNoException();
                reply.writeInt(accepted ? 1 : 0);
                return true;
            }
            case 4: {
                boolean accepted = release(data.readLong());
                reply.writeNoException();
                reply.writeInt(accepted ? 1 : 0);
                return true;
            }
            default: return false;
        }
    }

    private boolean isAuthorized(String claimedPackage) {
        if (!CLIENT.equals(claimedPackage)) return false;
        PackageManager pm = context.getPackageManager();
        String[] packages = pm.getPackagesForUid(Binder.getCallingUid());
        if (packages == null) return false;
        for (String name : packages) {
            if (CLIENT.equals(name)) {
                return pm.checkSignatures(CLIENT, context.getPackageName())
                        == PackageManager.SIGNATURE_MATCH;
            }
        }
        return false;
    }

    private synchronized long acquire(IBinder token, String address) {
        if (stopped) return 0;
        if (token == null) return 0;
        if (address != null) {
            address = address.toUpperCase(Locale.US);
            if (!BluetoothAdapter.checkBluetoothAddress(address)) return 0;
        }
        if (vendor == null) return 0;
        if (lease != 0) return token == lifetime ? lease : 0;
        if (prefs.getBoolean("pending", false) || hasNativeCall()) return 0;
        boolean connected;
        if (address == null) {
            List<String> addresses = connectedAddresses();
            if (addresses == null || !addresses.isEmpty()) return 0;
            connected = false;
        } else {
            Boolean connectedState = isHfpConnected(address);
            if (connectedState == null) return 0;
            connected = connectedState;
        }
        long id = Math.max(1, prefs.getLong("generation", 0) + 1);
        IBinder.DeathRecipient recipient = () -> {
            synchronized (PhoneLeaseBinder.this) {
                if (lifetime == token && lease == id) releaseInternal();
            }
        };
        try {
            token.linkToDeath(recipient, 0);
        } catch (RemoteException e) {
            return 0;
        }
        if (!prefs.edit().putBoolean("pending", true).putLong("generation", id)
                .putString("target", address).putBoolean("restore", connected)
                .putBoolean("observedDisconnected", false)
                .putBoolean("disconnectIssued", false).commit()) {
            token.unlinkToDeath(recipient, 0);
            return 0;
        }
        lifetime = token;
        death = recipient;
        target = address;
        lease = id;
        restoreNeeded = connected;
        callPhase = 0;
        publishedPhase = -1;
        outputsTouched = false;
        if (connected && !requestDisconnect(address))
            Log.w(TAG, "Target HFP disconnect pending vendor readiness");
        return id;
    }

    private synchronized int status(long id) {
        if (stopped) return 0;
        if (id == 0 || id != lease || vendor == null) return 0;
        if (target == null) {
            List<String> addresses = connectedAddresses();
            return addresses != null && addresses.isEmpty() && !hasNativeCall() ? 1 : 0;
        }
        Boolean connected = isHfpConnected(target);
        if (connected == null) return 0;
        if (connected) {
            if (!hasNativeCall()) {
                if (!prefs.edit().putBoolean("restore", true)
                        .putBoolean("observedDisconnected", false).commit()) return 0;
                restoreNeeded = true;
                requestDisconnect(target);
            }
            return 0;
        }
        if (!prefs.edit().putBoolean("observedDisconnected", true).commit()) return 0;
        return hasNativeCall() ? 0 : 1;
    }

    private synchronized boolean updateCall(long id, int phase) {
        if (stopped) return false;
        if (id == 0 || id != lease || phase < 0 || phase > 3) return false;
        if (phase == 0 && !outputsTouched) return true;
        if (phase == publishedPhase && SystemClock.elapsedRealtime() - publishedAt < 10000)
            return true;
        if (!prefs.edit().putBoolean("touched", true).commit()) return false;
        outputsTouched = true;
        callPhase = phase;
        handler.removeCallbacks(publishTask);
        handler.post(publishTask);
        return true;
    }

    private synchronized boolean release(long id) {
        if (id == 0) return false;
        if (lease == 0 && id == prefs.getLong("generation", 0)) return true;
        if (id != lease) return false;
        releaseInternal();
        return true;
    }

    private void releaseInternal() {
        IBinder oldToken = lifetime;
        IBinder.DeathRecipient oldDeath = death;
        String oldTarget = target;
        boolean reconnect = restoreNeeded;
        boolean clearOutputs = outputsTouched;
        lifetime = null;
        death = null;
        target = null;
        lease = 0;
        restoreNeeded = false;
        callPhase = 0;
        publishedPhase = -1;
        outputsTouched = false;
        if (oldToken != null && oldDeath != null) oldToken.unlinkToDeath(oldDeath, 0);
        if (clearOutputs) outputs.clear(hasNativeCall());
        if (reconnect && oldTarget != null) {
            Boolean connected = isHfpConnected(oldTarget);
            if (connected != null && !connected) {
                prefs.edit().putBoolean("observedDisconnected", true).commit();
                requestHfp(98, oldTarget);
            }
        }
        recoverPending();
    }

    private synchronized void publishQueued() {
        if (stopped || lease == 0 || !outputsTouched) return;
        if (status(lease) != 1 || !outputs.write(callPhase, hasNativeCall()))
            handler.postDelayed(publishTask, 1000);
        else {
            publishedPhase = callPhase;
            publishedAt = SystemClock.elapsedRealtime();
        }
    }

    private synchronized void recoverPending() {
        if (stopped) return;
        if (lease != 0 || !prefs.getBoolean("pending", false)) return;
        boolean phoneDone = !prefs.getBoolean("touched", false)
                || outputs.clear(hasNativeCall());
        String pendingTarget = prefs.getString("target", null);
        boolean hfpDone = !prefs.getBoolean("restore", false) || pendingTarget == null;
        if (!hfpDone && vendor != null) {
            Boolean connected = isHfpConnected(pendingTarget);
            if (connected != null) {
                if (!connected) {
                    prefs.edit().putBoolean("observedDisconnected", true).commit();
                    requestHfp(98, pendingTarget);
                }
                hfpDone = connected && (prefs.getBoolean("observedDisconnected", false)
                        || !prefs.getBoolean("disconnectIssued", false));
            }
        }
        if (phoneDone && hfpDone) {
            handler.removeCallbacks(recoverTask);
            prefs.edit().remove("pending").remove("target").remove("restore")
                    .remove("observedDisconnected").remove("disconnectIssued")
                    .remove("touched").commit();
        } else {
            handler.removeCallbacks(recoverTask);
            handler.postDelayed(recoverTask, 1000);
        }
    }

    private Boolean isHfpConnected(String address) {
        if (!vendorHfpReady()) return null;
        Parcel input = Parcel.obtain();
        Parcel output = Parcel.obtain();
        try {
            input.writeInterfaceToken(VENDOR);
            input.writeString(address);
            IBinder b = vendor;
            if (b == null || !b.transact(95, input, output, 0)) return null;
            output.readException();
            return output.readInt() != 0;
        } catch (Exception e) {
            Log.w(TAG, "HFP status unavailable", e);
            return null;
        } finally {
            input.recycle();
            output.recycle();
        }
    }

    private boolean vendorHfpReady() {
        return connectedAddresses() != null;
    }

    private List<String> connectedAddresses() {
        Parcel input = Parcel.obtain();
        Parcel output = Parcel.obtain();
        try {
            input.writeInterfaceToken(VENDOR);
            IBinder b = vendor;
            if (b == null || !b.transact(215, input, output, 0)) return null;
            output.readException();
            return output.createStringArrayList();
        } catch (Exception e) {
            Log.w(TAG, "Vendor HFP not ready", e);
            return null;
        } finally {
            input.recycle();
            output.recycle();
        }
    }

    private boolean hasNativeCall() {
        return hasNativeCallList(101) || hasNativeCallList(103);
    }

    private boolean hasNativeCallList(int transaction) {
        Parcel input = Parcel.obtain();
        Parcel output = Parcel.obtain();
        try {
            input.writeInterfaceToken(VENDOR);
            IBinder b = vendor;
            if (b == null || !b.transact(transaction, input, output, 0)) return true;
            output.readException();
            int count = output.readInt();
            if (count < 0) return transaction == 101;
            for (int i = 0; i < count; i++) {
                if (output.readInt() == 0) continue;
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                output.readParcelable(adapter == null ? null : adapter.getClass().getClassLoader());
                output.readInt(); // call id
                output.readString(); // UUID
                int state = output.readInt();
                output.readString(); // phone number
                output.readInt(); // multiparty
                output.readInt(); // outgoing
                if (state != 7) return true;
            }
            return false;
        } catch (Exception e) {
            Log.w(TAG, "Cannot establish native call state", e);
            return true;
        } finally {
            input.recycle();
            output.recycle();
        }
    }

    private boolean requestHfp(int transaction, String address) {
        return Boolean.TRUE.equals(requestHfpResult(transaction, address));
    }

    private boolean requestDisconnect(String address) {
        boolean previouslyIssued = prefs.getBoolean("disconnectIssued", false);
        if (!previouslyIssued && !prefs.edit().putBoolean("disconnectIssued", true).commit())
            return false;
        Boolean result = requestHfpResult(99, address);
        if (Boolean.FALSE.equals(result) && !previouslyIssued)
            prefs.edit().putBoolean("disconnectIssued", false).commit();
        return Boolean.TRUE.equals(result);
    }

    private Boolean requestHfpResult(int transaction, String address) {
        Parcel input = Parcel.obtain();
        Parcel output = Parcel.obtain();
        try {
            input.writeInterfaceToken(VENDOR);
            input.writeString(address);
            IBinder b = vendor;
            if (b == null || !b.transact(transaction, input, output, 0)) return null;
            output.readException();
            return output.readInt() != 0;
        } catch (Exception e) {
            Log.w(TAG, "HFP transaction failed: " + transaction, e);
            return null;
        } finally {
            input.recycle();
            output.recycle();
        }
    }
}
