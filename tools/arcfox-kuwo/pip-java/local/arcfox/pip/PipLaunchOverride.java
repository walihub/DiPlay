package local.arcfox.pip;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
/** Only the navigation-shaped PIP card's launch target. Never changes the car's navigation app. */
public final class PipLaunchOverride {
 private static final String PREFS="dudu_pip_launch_override",NAV="nav_launch_package";
 private static boolean navigationCard(View view){try{return ((Integer)view.getClass().getMethod("getMinimalismItemType").invoke(view))==106;}catch(Exception e){return false;}}
 private static SharedPreferences prefs(Context context){return context.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
 public static String resolve(View view,String fallback){
  if(view==null||!navigationCard(view))return fallback;
  try{String selected=prefs(view.getContext()).getString(NAV,"");
   return selected==null||selected.isEmpty()?fallback:selected;
  }catch(Exception unavailable){return fallback;}
 }
 public static boolean select(View view,Object app){
  if(view==null||app==null||!navigationCard(view))return false;
  try{
   Object raw=app.getClass().getField("b").get(app);
   if(!(raw instanceof String)||((String)raw).isEmpty())return false;
   String selected=(String)raw;
   if(!prefs(view.getContext()).edit().putString(NAV,selected).commit())return false;
   view.getClass().getMethod("setAppClazz",String.class).invoke(view,selected);
   return true;
  }catch(Exception unavailable){return false;}
 }
 private PipLaunchOverride(){}
}
