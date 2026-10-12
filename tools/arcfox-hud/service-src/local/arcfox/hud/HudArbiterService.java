package local.arcfox.hud;
import android.app.Service;import android.content.Intent;import android.os.*;
public final class HudArbiterService extends Service {
 @Override public void onCreate(){super.onCreate();HudArbiter.attach(this);}
 @Override public IBinder onBind(Intent intent){return HudArbiter.binder();}
}
