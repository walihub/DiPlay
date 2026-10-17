package local.arcfox.vehicle;
import android.content.Context;

/** Readiness source used by the existing desktop card's manager.i() check. */
public final class TyreCardSource extends com.dudu.autoui.manage.w.i.g {
    private volatile boolean ready;
    public TyreCardSource(Context context) { super(context, null); }
    public void ready(boolean value) { ready = value; }
    @Override public boolean e() { return ready; }
    @Override public void c() { ready = false; }
    @Override public String f() { return "Arcfox vehicle TPMS"; }
}
