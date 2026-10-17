import local.arcfox.media.SourcePolicy;
import local.arcfox.media.CallbackFence;
public class SourcePolicyTest {
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] ignored){
  SourcePolicy p=new SourcePolicy();check(!p.accepts(0));
  p.sessionState(true,true);check(p.session);
  p.nativeSource(3);check(p.session); // first native snapshot cannot override active DiPlay
  p.disconnect();p.nativeSource(3);check(p.session); // rebind baseline is not a source switch
  p.nativeSource(-1);check(p.session); // idle native source cannot steal a live session
  p.nativeSource(3);p.nativeSource(5);check(!p.session);check(p.accepts(5));
  p.sessionState(true,true);check(!p.session); // continuing playback cannot steal owner
  p.sessionState(true,false);p.sessionState(true,true);check(p.session);
  p.sessionState(false,false);check(!p.session);check(p.accepts(5));
  p.nativeSource(-1);check(!p.accepts(5));check(!p.accepts(4));
  Object old=new Object(),fresh=new Object();
  check(CallbackFence.accepts(1,1,old,old));
  check(!CallbackFence.accepts(1,2,old,fresh));
  check(!CallbackFence.accepts(2,2,old,fresh));
  for(int i=0;i<10000;i++){
   p.nativeSource(i%7);check(p.accepts(i%7));check(!p.accepts((i+1)%7));
   check(!CallbackFence.accepts(i,i+1,old,fresh));
  }
  p.reset();check(!p.session);check(p.source==-1);p.sessionState(true,true);check(p.session);
  System.out.println("SourcePolicyTest passed (baseline, idle, rebind, callback generations, 10000 source transitions)");
 }
}
