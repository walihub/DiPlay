package local.arcfox.media;

import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.*;

/** Worker-confined native lyric snapshots. Timestamps use the documented LRC string, never a guessed raw unit. */
public final class NativeLyrics {
    public static final int MAX_ROWS=2000, MAX_TEXT=2048;
    private static final Pattern TIME=Pattern.compile("^\\[?(\\d{1,4}):(\\d{2})(?:[.:](\\d{1,3}))?\\]?$");
    public static final class Row {
        public final long start,end;
        public final String text;
        public Row(long s,long e,String t){start=s;end=e;text=t;}
        @Override public boolean equals(Object other){if(!(other instanceof Row))return false;Row r=(Row)other;return start==r.start&&end==r.end&&text.equals(r.text);}
        @Override public int hashCode(){return Objects.hash(start,end,text);}
    }
    private static final class Track {
        final String id,title,artist,album;
        Track(Object info){id=string(info,"getId");title=string(info,"getName");artist=string(info,"getArtist");album=string(info,"getAlbum");}
        boolean identified(){return !id.isEmpty()||!title.isEmpty();}
        boolean matches(Track other){
            if(!identified()||!other.identified())return false;
            if(!id.isEmpty()&&!other.id.isEmpty()&&!id.equals(other.id))return false;
            return same(title,other.title)&&same(artist,other.artist)&&same(album,other.album);
        }
        private boolean same(String a,String b){return a.isEmpty()||b.isEmpty()||a.equals(b);}
    }
    private Track track;
    private List<Row> published;
    private boolean timeline;
    private String single="";
    private Integer anonymousRequest;
    private int snapshotRequests;

    public List<Row> snapshot(){return published==null?Collections.emptyList():published;}

    public void reset(){track=null;published=null;timeline=false;single="";anonymousRequest=null;snapshotRequests=0;}

    /** Metadata snapshots without lyrics retain the same song's existing lyric snapshot. */
    public List<Row> metadata(Object info){return metadata(info,false);}
    public List<Row> metadata(Object info,boolean authoritativeLyrics){
        Track next=new Track(info);
        if(!next.identified())return null;
        boolean changed=track==null||!track.matches(next);
        track=next;
        if(changed){published=null;timeline=false;single="";anonymousRequest=null;snapshotRequests=0;}
        List<?> raw=list(info,"getAllLyric");
        if(raw!=null&&!raw.isEmpty())return replace(convert(raw));
        return changed||authoritativeLyrics?replace(Collections.emptyList()):null;
    }

    public static boolean identified(Object info){return new Track(info).identified();}

    /** Anonymous callbacks trigger a bounded authoritative metadata reread, never direct binding. */
    public boolean requestSnapshot(Object info){
        if(track==null||!track.identified()||snapshotRequests>=2)return false;
        List<?> raw=list(info,"getAllLyric");
        int signature=raw==null?0:convert(raw).hashCode();
        if(anonymousRequest!=null&&anonymousRequest==signature)return false;
        anonymousRequest=signature;snapshotRequests++;return true;
    }

    /** A lyric callback must identify the current song; late previous-song replies are discarded. */
    public List<Row> lyrics(Object info){
        if(track==null||!track.matches(new Track(info)))return null;
        List<?> raw=list(info,"getAllLyric");
        return replace(raw==null?Collections.emptyList():convert(raw));
    }

    /** Full timelines scroll from the normal millisecond progress event; do not resend every line. */
    public List<Row> currentLine(String value){
        if(track==null||!track.identified()||timeline)return null;
        String next=text(value);
        if(next.equals(single))return null;
        single=next;
        List<Row> rows=next.isEmpty()?Collections.emptyList():Collections.singletonList(new Row(0,Long.MAX_VALUE,next));
        return deduplicate(rows);
    }

    private List<Row> replace(List<Row> rows){timeline=!rows.isEmpty();single="";return deduplicate(rows);}
    private List<Row> deduplicate(List<Row> rows){if(rows.equals(published))return null;published=Collections.unmodifiableList(new ArrayList<>(rows));return published;}

    public static long parseTime(String value){
        if(value==null)return -1;
        Matcher match=TIME.matcher(value.trim());if(!match.matches())return -1;
        try{
            long minutes=Long.parseLong(match.group(1)),seconds=Long.parseLong(match.group(2));
            if(minutes>1440||seconds>=60)return -1;
            String fraction=match.group(3);
            long milliseconds=fraction==null?0:Long.parseLong(fraction)*(fraction.length()==1?100:fraction.length()==2?10:1);
            return minutes*60000+seconds*1000+milliseconds;
        }catch(NumberFormatException invalid){return -1;}
    }

    public static List<Row> convert(List<?> raw){
        if(raw.size()>MAX_ROWS)return Collections.emptyList();
        TreeMap<Long,String> lines=new TreeMap<>();
        for(Object item:raw){
            long time=parseTime(string(item,"getStartTimeString"));String content=text(string(item,"getContent"));
            if(time<0||content.isEmpty())continue;
            String previous=lines.get(time);
            if(previous==null)lines.put(time,content);
            else if(!previous.equals(content))lines.put(time,text(previous+"\n"+content));
        }
        List<Row> rows=new ArrayList<>();List<Long> times=new ArrayList<>(lines.keySet());
        for(int i=0;i<times.size();i++){
            long start=times.get(i),end=i+1<times.size()?times.get(i+1):start+10000;
            rows.add(new Row(start,end,lines.get(start)));
        }
        return rows;
    }
    private static String text(String value){if(value==null)return "";String s=value.trim();return s.length()>MAX_TEXT?s.substring(0,MAX_TEXT):s;}
    private static String string(Object item,String name){Object value=invoke(item,name);return value instanceof String?((String)value).trim():"";}
    private static List<?> list(Object item,String name){Object value=invoke(item,name);return value instanceof List?(List<?>)value:null;}
    private static Object invoke(Object item,String name){if(item==null)return null;try{Method m=item.getClass().getMethod(name);m.setAccessible(true);return m.invoke(item);}catch(Exception unavailable){return null;}}
}
