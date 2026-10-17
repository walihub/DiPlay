package local.arcfox.vehicle;

import android.content.Context;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Updates the existing card cache directly, without invoking the reporting/history callback. */
public final class TyreCardBridge {
    private final Object manager, data, state, bus;
    private final Method post, readySetter;
    private final Field sourceField;
    private final TyreCardSource source;
    private final Method[] pressures = new Method[4], temperatures = new Method[4];
    private Object previousSource;
    private boolean lastReady;

    public TyreCardBridge(Context context, Object bus, Method post) throws Exception {
        this.bus = bus; this.post = post;
        Class<?> type = Class.forName("com.dudu.autoui.manage.w.i.f");
        manager = type.getMethod("k").invoke(null);
        data = type.getMethod("d").invoke(manager);
        state = type.getMethod("e").invoke(manager);
        readySetter = state.getClass().getMethod("a", boolean.class);
        sourceField = type.getDeclaredField("c"); sourceField.setAccessible(true);
        source = new TyreCardSource(context);
        Class<?> model = Class.forName("com.dudu.autoui.common.s0.c");
        String[] p = {"b", "d", "a", "c"}, t = {"b", "d", "a", "c"};
        for (int i = 0; i < 4; i++) {
            pressures[i] = model.getMethod(p[i], Float.class);
            temperatures[i] = model.getMethod(t[i], Integer.class);
        }
    }
    public void update(VehicleValues.Tyres values) throws Exception {
        Object current = sourceField.get(manager);
        if (current != source && !values.available()) return;
        if (current != source) { previousSource = current; sourceField.set(manager, source); }
        for (int i = 0; i < 4; i++) {
            pressures[i].invoke(data, values.bar[i]);
            temperatures[i].invoke(data, values.celsius[i]);
        }
        boolean ready = values.available();
        // On loss, render the empty cache while the card still accepts data, then mark unavailable.
        if (ready) source.ready(true);
        post.invoke(bus, data);
        source.ready(ready);
        readySetter.invoke(state, ready);
        if (ready != lastReady) { post.invoke(bus, state); lastReady = ready; }
    }
    public void close() throws Exception {
        update(new VehicleValues.Tyres());
        if (sourceField.get(manager) == source) sourceField.set(manager, previousSource);
    }
}
