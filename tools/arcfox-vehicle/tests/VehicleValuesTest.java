import local.arcfox.vehicle.VehicleValues;
import com.shilapi.xcertplay.vehicle.arcfox.data.ArcfoxCarReader;
public class VehicleValuesTest {
    private static ArcfoxCarReader.Value value(int index, double n) {
        return new ArcfoxCarReader.Value(VehicleValues.IDS[index], n, 1_000_000_000L);
    }
    private static void require(boolean b) { if (!b) throw new AssertionError(); }
    public static void main(String[] args) {
        require(VehicleValues.speedKmh(value(0, 10), 1000) == 36);
        require(VehicleValues.speedKmh(value(0, -10), 1000) == 36);
        require(VehicleValues.speedKmh(value(0, Double.NaN), 1000) == null);
        require(VehicleValues.speedKmh(value(0, 10), 3001) == null);
        require(VehicleValues.speedKmh(null, 1000) == null);
        ArcfoxCarReader.Value[] doors = new ArcfoxCarReader.Value[6];
        for (int i=1; i<6; i++) doors[i] = value(i, 0);
        doors[4] = value(4, 1); // left rear property 0x2140032c, not 0x2140032b
        boolean[] decoded = VehicleValues.doors(doors);
        require(decoded[3] && !decoded[4] && !decoded[0]);
        doors[1] = value(1, 2); // tailgate moving/unknown must not be shown closed
        require(VehicleValues.doors(doors) == null);
        doors[1] = null;
        require(VehicleValues.doors(doors) == null);
        require(VehicleValues.doors(new ArcfoxCarReader.Value[0]) == null);
        ArcfoxCarReader.Value[] tyre = new ArcfoxCarReader.Value[VehicleValues.IDS.length];
        for (int i=0; i<4; i++) { tyre[6+i] = value(6+i, 24+i%2); tyre[10+i] = value(10+i, 62+i); }
        VehicleValues.Tyres t = VehicleValues.tyres(tyre);
        require(t.available());
        for (int i=0; i<4; i++) {
            require(Math.abs(t.bar[i] - (24+i%2)/10f)<0.0001);
            require(t.celsius[i] == 22+i);
        }
        require(Math.abs(t.bar[0] - 2.4f) < 0.0001f);
        require(Math.abs(t.bar[1] - 2.5f) < 0.0001f);
        require(Math.abs(t.bar[2] - 2.4f) < 0.0001f);
        require(Math.abs(t.bar[3] - 2.5f) < 0.0001f);
        tyre[6]=value(6, Double.NaN); tyre[7]=value(7,65535); tyre[8]=null; tyre[9]=value(9, -1);
        tyre[10]=value(10,255); tyre[11]=value(11,65535); tyre[12]=value(12,62.5); tyre[13]=null;
        require(!VehicleValues.tyres(tyre).available());
        require(!VehicleValues.tyres(new ArcfoxCarReader.Value[0]).available());
        tyre[10]=value(10,0); require(VehicleValues.tyres(tyre).celsius[0] == -40);
        tyre[11]=new ArcfoxCarReader.Value(VehicleValues.IDS[10],62,1000000000L);
        require(VehicleValues.tyres(tyre).celsius[1] == null); // wrong wheel never relabelled
        System.out.println("VehicleValues: units, offset40, wheel order, partial/invalid/missing inputs passed");
    }
}
