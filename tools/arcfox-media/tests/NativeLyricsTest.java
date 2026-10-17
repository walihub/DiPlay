import local.arcfox.media.NativeLyrics;
import java.util.*;
public class NativeLyricsTest {
 public static class LyricRow {final String time,text;public LyricRow(String t,String s){time=t;text=s;}public String getStartTimeString(){return time;}public String getContent(){return text;}public long getStartTime(){throw new AssertionError("raw unit must not be guessed");}}
 public static class Track {final String id;final List<?> rows;public Track(String i,List<?> r){id=i;rows=r;}public String getId(){return id;}public String getName(){return id;}public String getArtist(){return "Artist";}public String getAlbum(){return "Album";}public String getCover(){return "";}public List<?> getAllLyric(){return rows;}}
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){
  check(NativeLyrics.parseTime("01:02.3")==62300);check(NativeLyrics.parseTime("01:02.34")==62340);check(NativeLyrics.parseTime("[01:02.345]")==62345);
  check(NativeLyrics.parseTime("01:60.1")==-1);check(NativeLyrics.parseTime("garbage")==-1);
  NativeLyrics state=new NativeLyrics();Track one=new Track("one",Arrays.asList(new LyricRow("00:02.500","second"),new LyricRow("00:01.2","first")));
  List<NativeLyrics.Row> rows=state.metadata(one);check(rows.size()==2);check(rows.get(0).start==1200);check(rows.get(0).end==2500);check(rows.get(1).end==12500);
  check(state.lyrics(one)==null);check(state.requestSnapshot(new Track("",one.rows)));check(!state.requestSnapshot(new Track("",one.rows)));check(state.requestSnapshot(new Track("",null)));check(!state.requestSnapshot(new Track("",Collections.emptyList())));check(state.currentLine("current line ignored for full timeline")==null);
  check(state.metadata(new Track("two",null)).isEmpty());check(state.lyrics(one)==null);
  check(state.currentLine("line").get(0).end==Long.MAX_VALUE);check(state.currentLine("line")==null);check(state.currentLine("").isEmpty());
  check(state.metadata(new Track("two",null))==null);state.reset();check(state.currentLine("old")==null);
  check(NativeLyrics.convert(Arrays.asList(new LyricRow(null,"unknown raw"),new LyricRow("99:99","invalid"))).isEmpty());
  System.out.println("NativeLyricsTest passed (LRC milliseconds, sorting/end times, dedup, stale track, current-line fallback, invalid timestamps)");
 }
}
