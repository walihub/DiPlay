package local.arcfox.kuwo;
/** Only cross-path duplicates are suppressed; two real presses on the same path remain valid. */
public final class KeyPolicy {
 private String command,origin;
 private long at;
 public synchronized boolean accept(String next,String path,long now){
  if(next==null)return true;
  if(next.equals(command)&&!path.equals(origin)&&now>=at&&now-at<=350)return false;
  command=next;origin=path;at=now;return true;
 }
 public static String raw(String action,int key){
  if(!"adayo.keyEvent.onKeyDown.N6".equals(action))return null;
  return key==16?"MEDIA_NEXT":key==15?"MEDIA_PRE":null;
 }
}
