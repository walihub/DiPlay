package local.arcfox.kuwo;
import java.util.*;
/** Session lists are ordered by Android media priority. Metadata is deliberately not an input. */
public final class AutoSelectionPolicy {
 public static final String KUWO="cn.kuwo.kwmusiccar";
 public static final class State {
  public final Object token;public final String pkg;public final boolean playing;
  public State(Object t,String p,boolean active){token=t;pkg=p;playing=active;}
 }
 private final Map<Object,Boolean> previous=new HashMap<>();
 private boolean rivalStarted;private Object pending;private int expected;private long since;
 public Object observe(List<State> sessions){
  State first=null;Map<Object,Boolean> next=new HashMap<>();
  for(State s:sessions){next.put(s.token,s.playing);if(first==null&&s.playing&&!Boolean.TRUE.equals(previous.get(s.token)))first=s;}
  rivalStarted=first!=null&&!KUWO.equals(first.pkg);
  Object select=null;
  if(first!=null&&KUWO.equals(first.pkg)&&!Boolean.TRUE.equals(previous.get(first.token)))select=first.token;
  previous.clear();previous.putAll(next);return select;
 }
 /** Wait briefly for existing CarPlay ownership to yield before recreating Dudu plugins. */
 public Object candidate(List<State> sessions,int selection,long now){
  Object edge=observe(sessions);
  if(rivalStarted)pending=null;
  if(edge!=null){pending=edge;expected=selection;since=now;}
  if(pending==null)return null;
  if(selection!=expected||now<since||now-since>5000){pending=null;return null;}
  boolean alive=false,phone=false;
  for(State s:sessions)if(s.playing){if(pending.equals(s.token)&&KUWO.equals(s.pkg))alive=true;if("com.shihab.diplay".equals(s.pkg))phone=true;}
  if(!alive){pending=null;return null;}
  if(phone)return null;
  Object result=pending;pending=null;return result;
 }
}
