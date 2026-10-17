package local.arcfox.kuwo;
import java.util.HashSet;
import java.util.Set;
/** Worker-confined ownership. Metadata and progress never acquire ownership. */
public final class MediaPolicy {
 private boolean playing, initialized;
 public boolean owner;
 private Set<Object> rivals = new HashSet<>();
 public boolean own(boolean next, boolean initiallyPreferred) {
  boolean edge=next&&!playing;
  if(!initialized){owner=next&&initiallyPreferred;initialized=true;}
  else if(edge)owner=true;
  playing=next;return edge&&owner;
 }
 public boolean rivals(Set<?> next) {
  boolean takeover=false;
  for(Object token:next)if(!rivals.contains(token))takeover=true;
  rivals=new HashSet<Object>(next);
  if(takeover)owner=false;
  return takeover;
 }
 public void baselineRivals(Set<?> next){rivals=new HashSet<Object>(next);}
 public boolean playing(){return playing;}
 public static long position(long position,long updated,float speed,boolean playing,long now,long duration){
  long p=Math.max(0,position);
  if(playing&&updated>0&&now>=updated&&Float.isFinite(speed)&&speed>0){
   double value=(now-updated)*(double)speed;
   p=value>=Long.MAX_VALUE-p?Long.MAX_VALUE:p+(long)value;
  }
  return duration>0?Math.min(duration,p):p;
 }
 public static int seconds(long millis){long m=Math.max(0,millis);return (int)Math.min(Integer.MAX_VALUE,m/1000+(m%1000>500?1:0));}
}
