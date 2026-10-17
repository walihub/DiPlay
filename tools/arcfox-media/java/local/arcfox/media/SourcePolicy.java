package local.arcfox.media;
/** Worker-confined owner state; one owner for display and commands. */
public final class SourcePolicy {
 public volatile int source=-1;
 public volatile boolean session;
 private boolean playing;
 public boolean nativeSource(int next){
  if(next==source)return false;
  int old=source;source=next;
  if(old>=0 && next>=0)session=false;
  return true;
 }
 public boolean sessionState(boolean present,boolean active){
  boolean changed=false;
  if(!present&&session){session=false;changed=true;}
  if(present&&active&&!playing){session=true;changed=true;}
  playing=present&&active;return changed;
 }
 public boolean accepts(int value){return !session&&source>=0&&source==value;}
 public void disconnect(){source=-1;}
 public void reset(){source=-1;session=false;playing=false;}
}
