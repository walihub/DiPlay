package local.arcfox.kuwo;
/** Bounded retries to ask the original Kuwo loader to materialize URI-only metadata. */
public final class ArtworkRefreshPolicy {
 private String key="";
 private int attempts;
 private long last;
 public boolean request(String identity,String uri,boolean hasBitmap,long now){
  String next=String.valueOf(identity)+"\n"+String.valueOf(uri);
  if(!next.equals(key)){key=next;attempts=0;last=0;}
  // A URI can initially accompany a default bitmap; request the true image once as well.
  boolean uriAvailable=uri!=null&&!uri.isEmpty();
  if(hasBitmap&&(!uriAvailable||attempts>0))return false;
  if(attempts>=3||(attempts>0&&now-last<5000))return false;
  attempts++;last=now;return true;
 }
}
