import local.arcfox.vehicle.*;
import com.dudu.autoui.manage.w.i.f;
public class TyreCardBridgeTest {
    public static final class Bus {
        int calls;
        Float[] renderedPressure;
        Integer[] renderedTemperature;
        public void post(Object event) {
            calls++;
            // Matches card readiness gating: it renders model data only when manager.i() is true.
            if (event instanceof com.dudu.autoui.manage.w.i.j.a && f.k().i()) {
                renderedPressure=f.k().d().p.clone(); renderedTemperature=f.k().d().t.clone();
            }
        }
    }
    private static void require(boolean ok) { if (!ok) throw new AssertionError(); }
    public static void main(String[] args) throws Exception {
        Bus bus = new Bus();
        TyreCardBridge bridge = new TyreCardBridge(null,bus,Bus.class.getMethod("post",Object.class));
        Object original = f.k().c;
        bridge.update(new VehicleValues.Tyres());
        require(f.k().c == original && bus.calls == 0); // no input: existing source remains intact
        VehicleValues.Tyres values = new VehicleValues.Tyres();
        for (int i=0;i<4;i++) { values.bar[i]=2.4f+i*0.03f; values.celsius[i]=22+i; }
        bridge.update(values);
        require(f.k().i() && f.k().e().ready);
        for (int i=0;i<4;i++) {
            require(bus.renderedPressure[i].equals(values.bar[i]));
            require(bus.renderedTemperature[i].equals(values.celsius[i]));
        }
        values.bar[2]=null; values.celsius[2]=null; bridge.update(values);
        require(bus.renderedPressure[2]==null && bus.renderedTemperature[2]==null);
        bridge.update(new VehicleValues.Tyres());
        require(!f.k().i() && !f.k().e().ready);
        for(int i=0;i<4;i++) require(bus.renderedPressure[i]==null && bus.renderedTemperature[i]==null);
        bridge.update(values); require(f.k().i()); // reconnect restores readiness
        bridge.close(); require(f.k().c==original && !f.k().e().ready);
        System.out.println("TyreCardBridge: card gate, four-wheel mapping, partial loss, disconnect/reconnect, source restore passed");
    }
}
