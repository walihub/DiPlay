#!/usr/bin/env python3
"""Targeted offline regression checks; no APK build or Gradle required."""
from pathlib import Path
import subprocess, tempfile
root=Path(__file__).resolve().parents[3]
java=Path('/Users/h/Documents/arcfox/.android-tools/jdk21/Contents/Home/bin')
source=root/'tools/arcfox-kuwo/java/local/arcfox/kuwo'
bridge=(source/'KuwoBridge.java').read_text()
assert 'getTransportControls().pause()' not in bridge, 'observation must not pause playback'
assert 'PACKAGE.equals(candidate.getPackageName())' in bridge, 'same-package sessions must not compete'
kw=root/'build/kuwo-analysis/decoded'
service=(kw/'smali/cn/kuwo/mod/mediaSession/KwMediaSessionService.smali').read_text()
assert '.method public F(Z)V' in service
assert '->G(ZZ)V' in service
assert 'android.media.metadata.ALBUM_ART_URI' in service
assert 'Lv6/d;->f(Ljava/lang/String;Llc/l;)V' in service
assert 'invoke-virtual {p0, v0}, Lcn/kuwo/mod/mediaSession/KwMediaSessionService;->F(Z)V' in service
assert 'getMethod("F",boolean.class)' in bridge
with tempfile.TemporaryDirectory(prefix='kuwo-art-policy-') as directory:
 path=Path(directory)
 test=path/'PolicyTest.java'
 test.write_text('''import local.arcfox.kuwo.ArtworkRefreshPolicy;
public final class PolicyTest {
 static void ok(boolean value){if(!value)throw new AssertionError();}
 public static void main(String[] args){
  ArtworkRefreshPolicy p=new ArtworkRefreshPolicy();
  ok(p.request("track","https://image",false,0));
  ok(!p.request("track","https://image",false,100));
  ok(p.request("track","https://image",false,5000));
  ok(p.request("track","https://image",false,10000));
  ok(!p.request("track","https://image",false,15000));
  ok(p.request("next","https://image2",true,15001));
  ok(!p.request("next","https://image2",true,20001));
  ok(!p.request("bitmap",null,true,20002));
 }
}''')
 subprocess.run([str(java/'javac'),'-d',str(path),str(source/'ArtworkRefreshPolicy.java'),str(test)],check=True)
 subprocess.run([str(java/'java'),'-cp',str(path),'PolicyTest'],check=True)
print('PASS: no observer pause; same-package isolation; original artwork loader ABI/callback; bounded original-loader refresh policy')
