package local.arcfox.vehicle;
import com.shilapi.xcertplay.vehicle.arcfox.data.ArcfoxCarReader;

public final class VehicleValues {
    private VehicleValues() { }
    // Speed, tailgate, front left, front right, rear left, rear right.
    // Rear IDs are reversed numerically; never infer wheel/door order from sorted IDs.
    public static final int[] IDS = { ArcfoxCarReader.SPEED, 0x2140041c,
        0x21400329, 0x2140032a, 0x2140032c, 0x2140032b,
        0x21400bf8, 0x21400bf9, 0x21400bfa, 0x21400bfb,
        0x21400bfc, 0x21400bfd, 0x21400bfe, 0x21400bff,
        0x21400bee, 0x21400bef,
        0x21400bf0, 0x21400bf1, 0x21400bf2, 0x21400bf3,
        0x21400bf4, 0x21400bf5, 0x21400bf6, 0x21400bf7 };
    public static Integer speedKmh(ArcfoxCarReader.Value value, long nowMillis) {
        if (value == null || value.id != ArcfoxCarReader.SPEED) return null;
        double speed = Math.abs(value.number.doubleValue());
        long time = value.timestampNanos / 1000000;
        if (Double.isNaN(speed) || Double.isInfinite(speed) || speed > 83.334 || time <= 0 ||
            nowMillis - time < 0 || nowMillis - time > 2000) return null;
        return (int) Math.round(speed * 3.6);
    }
    public static boolean[] doors(ArcfoxCarReader.Value[] values) {
        if (values.length < 6) return null;
        boolean[] result = new boolean[5];
        for (int i = 1; i < 6; i++) {
            ArcfoxCarReader.Value value = values[i];
            if (value == null || value.id != IDS[i]) return null;
            int raw = value.number.intValue();
            // Only stable closed/open values. Movement/fault/unavailable are not 'closed'.
            if (raw != 0 && raw != 1) return null;
            result[i - 1] = raw == 1;
        }
        return result;
    }
    public static final class Tyres {
        public final Float[] bar = new Float[4];
        public final Integer[] celsius = new Integer[4];
        public boolean available() {
            for (int i = 0; i < 4; i++) if (bar[i] != null || celsius[i] != null) return true;
            return false;
        }
    }
    public static Tyres tyres(ArcfoxCarReader.Value[] values) {
        Tyres result = new Tyres();
        for (int wheel = 0; wheel < 4; wheel++) {
            Integer pressure = integerAt(values, 6 + wheel);
            Integer temperature = integerAt(values, 10 + wheel);
            // Independent wheel properties use tenths of bar (24 -> 2.4), not the packed
            // TPMS_SNGTIREP_CB field whose separate raw values were 243..255.
            // The vendor ENABLE/unit enum is not inferred; HAL status is checked by ArcfoxCarReader.
            if (pressure != null && pressure >= 0 && pressure <= 1000)
                result.bar[wheel] = pressure / 10.0f;
            // User-selected raw +40 encoding. 0xff/0xffff are not displayed as extreme temperatures.
            if (temperature != null && temperature >= 0 && temperature < 255)
                result.celsius[wheel] = temperature - 40;
        }
        return result;
    }
    private static Integer integerAt(ArcfoxCarReader.Value[] values, int index) {
        if (index >= values.length) return null;
        ArcfoxCarReader.Value value = values[index];
        if (value == null || value.id != IDS[index] || value.timestampNanos <= 0) return null;
        double raw = value.number.doubleValue();
        if (Double.isNaN(raw) || Double.isInfinite(raw) || raw != Math.rint(raw) ||
            raw < Integer.MIN_VALUE || raw > Integer.MAX_VALUE) return null;
        return (int) raw;
    }
}
