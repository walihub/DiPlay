package android.os;
/** Compile-only signature; execution uses the vehicle framework implementation. */
public abstract class HwBinder implements IHwBinder {
    public HwBinder() {}
    public abstract void onTransact(int code, HwParcel request, HwParcel reply, int flags);
    public IHwInterface queryLocalInterface(String descriptor) { return null; }
}
