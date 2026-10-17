package local.arcfox.media;
/** Reject callbacks queued by an exited plugin or an obsolete SDK callback instance. */
public final class CallbackFence {
 private CallbackFence(){}
 public static boolean accepts(long queued,long current,Object callback,Object active){
  return queued==current && callback!=null && callback==active;
 }
}
