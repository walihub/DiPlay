package local.arcfox.kuwo;
import java.util.*;
import org.json.*;
/** Kuwo's AUDIO_LYRIC payload contains millisecond starts and durations, not queue entries. */
public final class KuwoLyrics {
 public static final class Row {public final long start,end;public final String text;Row(long s,long e,String t){start=s;end=e;text=t;}}
 private String track="",raw="",baseline="";private List<Row> rows=Collections.emptyList();
 private final LinkedHashSet<String> retired=new LinkedHashSet<>();

 public boolean track(String next){return track(next,null);}
 public boolean track(String next,String cached){if(track.equals(next))return false;if(!raw.isEmpty()){retired.add(raw);while(retired.size()>8)retired.remove(retired.iterator().next());}track=next;baseline=cached==null?"":cached;raw="";rows=Collections.emptyList();return true;}
 /** Original extras have no song ID: accept new callback content, never retained/known prior-track data. */
 public boolean original(String expected,String value){value=value==null?"":value;if(value.equals(baseline)||retired.contains(value))return false;return update(expected,value);}
 /** Only source-tagged extras matching the current song may supply lyrics. */
 public boolean update(String expected,String value){if(!track.equals(expected)||expected.isEmpty())return false;value=value==null?"":value;if(raw.equals(value))return false;raw=value;rows=parse(value);return true;}
 /** One bootstrap snapshot per newly attached token; this is the original session contract, not proof of lyric ownership. */
 public boolean snapshot(String expected,String value){return update(expected,value);}
 public List<Row> rows(){return rows;}
 public static List<Row> parse(String raw){if(raw==null||raw.length()>524288||raw.isEmpty())return Collections.emptyList();try{
  JSONObject root=new JSONObject(raw);if(root.optInt("resultCode",-1)!=20000)return Collections.emptyList();JSONArray lines=root.getJSONArray("AUDIO_LYRIC");if(lines.length()>4096)return Collections.emptyList();
  List<Row> result=new ArrayList<>();long previous=-1;
  for(int n=0;n<lines.length();n++){JSONObject line=lines.getJSONObject(n);Object s=line.get("startTime"),d=line.get("time"),t=line.get("text");if(!(s instanceof Number)||!(d instanceof Number)||!(t instanceof String))return Collections.emptyList();long start=((Number)s).longValue(),duration=((Number)d).longValue();String text=(String)t;
   if(((Number)s).doubleValue()!=start||((Number)d).doubleValue()!=duration||start<0||duration<0||start<previous||start>Integer.MAX_VALUE||duration>Integer.MAX_VALUE||start+duration>Integer.MAX_VALUE||text.length()>16384)return Collections.emptyList();
   result.add(new Row(start,start+duration,text));previous=start;
  }return Collections.unmodifiableList(result);
 }catch(Exception invalid){return Collections.emptyList();}}
 public static List<Object> model(List<Row> rows)throws Exception{Class<?> type=Class.forName("com.dudu.autoui.common.y0.c.a");List<Object> result=new ArrayList<>();for(Row row:rows){Object item=type.getConstructor().newInstance();type.getMethod("b",long.class).invoke(item,row.start);type.getMethod("a",long.class).invoke(item,row.end);type.getMethod("a",String.class).invoke(item,row.text);result.add(item);}return result;}
 public static int lineAt(List<Row> rows,long position){int found=-1;for(int n=0;n<rows.size();n++){Row row=rows.get(n);if(row.start>position)break;if(position<row.end)found=n;}return found;}
}
