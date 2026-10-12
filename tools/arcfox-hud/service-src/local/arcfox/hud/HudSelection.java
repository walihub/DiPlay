package local.arcfox.hud;
import java.util.*;
/** Foreground active navigation wins; unrelated foreground apps preserve a live owner. */
public final class HudSelection {
 public static final class Source {
  public final String name; public boolean active; public long used;
  public int icon,distance,bar; public byte[] road; public long lease,sequence;
  public Source(String name){this.name=name;}
 }
 private final Map<String,Source> sources=new LinkedHashMap<String,Source>();
 private String owner; private long order;
 public Source get(String name){Source s=sources.get(name);if(s==null){s=new Source(name);sources.put(name,s);}return s;}
 public void active(Source s,boolean active){if(active&&!s.active)s.used=++order;s.active=active;}
 public Source choose(String foreground){
  Source next=sources.get(foreground);
  if(next==null||!next.active)next=sources.get(owner);
  if(next==null||!next.active){next=null;for(Source s:sources.values())if(s.active&&(next==null||s.used>next.used))next=s;}
  String name=next==null?null:next.name;
  if(!Objects.equals(owner,name)){owner=name;if(next!=null)next.used=++order;}
  return next;
 }
}
