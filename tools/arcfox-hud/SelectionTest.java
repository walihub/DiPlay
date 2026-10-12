import local.arcfox.hud.HudSelection;
public class SelectionTest {
 static void check(boolean ok){if(!ok)throw new AssertionError();}
 public static void main(String[] args){
  HudSelection state=new HudSelection();HudSelection.Source nativeMap=state.get("native"),phone=state.get("phone"),clone=state.get("clone");
  state.active(nativeMap,true);check(state.choose("native")==nativeMap);
  state.active(phone,true);check(state.choose("music")==nativeMap);
  check(state.choose("phone")==phone);state.active(nativeMap,false);check(state.choose("music")==phone);
  state.active(clone,true);check(state.choose("music")==phone);check(state.choose("clone")==clone);
  state.active(clone,false);check(state.choose("music")==phone);
  state.active(phone,false);check(state.choose("music")==null);
  state.active(nativeMap,true);check(state.choose("phone")==nativeMap); // Browsing an inactive map does not own HUD.
  System.out.println("HUD foreground/source end/fallback selection tests passed");
 }
}
