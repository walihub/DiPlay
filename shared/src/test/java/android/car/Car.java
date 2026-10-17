package android.car;

import android.content.Context;
import android.content.ServiceConnection;
import android.os.Handler;
import java.util.concurrent.ConcurrentHashMap;

/** Test-only fixture for the reflection boundary, never packaged in the app. */
public final class Car {
    public static volatile Car last;
    public static final ConcurrentHashMap<Integer, Property> properties = new ConcurrentHashMap<>();
    public static volatile boolean dead;
    public final ServiceConnection connection;
    public volatile boolean disconnected;
    private Car(ServiceConnection connection) { this.connection = connection; }
    public static Car createCar(Context context, ServiceConnection connection, Handler handler) {
        return last = new Car(connection);
    }
    public void connect() { connection.onServiceConnected(null, null); }
    public Object getCarManager(String name) {
        if (!name.equals("property")) throw new AssertionError(name);
        return new Manager();
    }
    public void disconnect() { disconnected = true; }
    public static final class Manager {
        public Property getProperty(int id, int area) throws android.os.DeadObjectException {
            if (area != 0) throw new AssertionError(area);
            if (dead) throw new android.os.DeadObjectException();
            return properties.get(id);
        }
    }
    public static final class Property {
        private final int status; private final Object value;
        public Property(int status, Object value) { this.status = status; this.value = value; }
        public int getStatus() { return status; }
        public Object getValue() { return value; }
        public long getTimestamp() { return 1000000000L; }
    }
}
