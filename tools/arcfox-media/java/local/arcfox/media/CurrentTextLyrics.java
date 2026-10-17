package local.arcfox.media;

import java.util.*;

/** The text actually received in the CarPlay title field, never an invented full-song timeline. */
public final class CurrentTextLyrics {
    private String current;
    public void reset(){current=null;}
    public List<NativeLyrics.Row> snapshot(){return current==null||current.isEmpty()?Collections.emptyList():Collections.singletonList(new NativeLyrics.Row(0,Long.MAX_VALUE,current));}
    public List<NativeLyrics.Row> update(String title){
        String next=title==null?"":title.trim();
        if(next.length()>NativeLyrics.MAX_TEXT)next=next.substring(0,NativeLyrics.MAX_TEXT);
        if(next.equals(current))return null;
        current=next;
        return next.isEmpty()?Collections.emptyList():Collections.singletonList(new NativeLyrics.Row(0,Long.MAX_VALUE,next));
    }
}
