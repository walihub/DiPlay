#!/usr/bin/env python3
"""Check real APK hooks/ABI rather than assuming the old Kuwo SDK is compatible."""
from pathlib import Path
import sys,tempfile,re,shutil
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import build_kuwo as kuwo
import build_dudu_kuwo as dudu
ROOT=kuwo.ROOT
KW=ROOT/'build/kuwo-analysis/decoded'
DU=ROOT/'build/kuwo-analysis/dudu'

def transactions(file,interface):
 s=file.read_text();table=re.search(r'\.packed-switch 0x1\n(.*?)\.end packed-switch',s,re.S)
 result={}
 for code,label in enumerate(re.findall(r':pswitch_\w+',table[1]),1):
  block=re.search(r'^    '+label+r'\n(.*?)(?=^    :pswitch_|^    :cond_|\Z)',s,re.M|re.S)
  result[code]=re.findall(r'invoke-interface .*?L'+interface+r';->([^\n]+)',block[1])[0]
 return result

new=transactions(KW/'smali/cn/kuwo/autosdk/b$a.smali','cn/kuwo/autosdk/b')
old=transactions(DU/'smali/cn/kuwo/autosdk/a$a.smali','cn/kuwo/autosdk/a')
assert new[4].endswith('Ljava/util/List;') and old[4].endswith('()I')
assert new[5]=='getCurrentPos()I' and new[3].endswith('Lcn/kuwo/base/bean/Music;')
# Existing platform token accessor ABI.
assert '.method public c()Landroid/support/v4/media/session/MediaSessionCompat$Token;' in (KW/'smali/android/support/v4/media/session/MediaSessionCompat.smali').read_text()
assert '.method public e()Ljava/lang/Object;' in (KW/'smali/android/support/v4/media/session/MediaSessionCompat$Token.smali').read_text()
files=['smali/cn/kuwo/mod/mediaSession/KwMediaSessionService.smali','smali/cn/kuwo/kwmusiccar/KwCarPlay/KwCarPlay$q.smali']
with tempfile.TemporaryDirectory() as directory:
 t=Path(directory)
 for file in files:
  p=t/file;p.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(KW/file,p)
 kuwo.patch(t)
 s=(t/files[0]).read_text()
 assert s.count('KuwoBridge;->attach')==1 and s.count('KuwoBridge;->detach')==1
 assert s.index('KuwoBridge;->detach')<s.index('->f()V',s.index('.method public x()V'))
 assert (t/files[1]).read_text().count('KuwoBridge;->allow')==1


with tempfile.TemporaryDirectory() as directory:
 t=Path(directory);file='smali/com/dudu/autoui/manage/music/plugin/n1.smali';p=t/file;p.parent.mkdir(parents=True);shutil.copyfile(DU/file,p)
 companion='smali/com/dudu/autoui/manage/music/plugin/ArcfoxMediaPlugin.smali'
 shutil.copyfile(DU/companion,t/companion)
 dudu.patch(t);s=p.read_text();assert s.count('KuwoCardBridge;->')==7
 monitor=(t/companion).read_text()
 injected='\n    invoke-static {p1}, Llocal/arcfox/kuwo/KuwoAutoSelect;->watch(Landroid/content/Context;)V'
 assert monitor.count(injected)==1
 assert monitor.replace(injected,'')==(DU/companion).read_text()
 init=re.search(r'\.method public a\(Landroid/content/Context;Lcom/dudu/autoui/manage/music/r;\)V.*?\.end method',s,re.S)[0]
 assert init.index('invoke-super')<init.index('KuwoCardBridge;->start')<init.index('return-void')<init.index('ZDATA_KUWOCAR_MODE_TEMP')
 manager=(DU/'smali/com/dudu/autoui/manage/music/p.smali').read_text()
 assert '.field private c:Lcom/dudu/autoui/manage/music/l;' in manager
 assert '.method public static w()Lcom/dudu/autoui/manage/music/p;' in manager
 callback=(DU/'smali/com/dudu/autoui/manage/music/r.smali').read_text()
 for sig in ['a(II)V','a(ZZ)V','a(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V','a(Ljava/lang/String;Landroid/graphics/Bitmap;Z)V','b(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V']:
  assert '.method public abstract '+sig in callback,sig
print('APK ABI verified: old/new Kuwo transaction 4 incompatible; token accessors, 2 Kuwo classes, 7 guarded Dudu hooks and callback signatures checked')

assert kuwo.signature_entry("META-INF/CERT.RSA")
assert kuwo.signature_entry("META-INF/MANIFEST.MF")
assert not kuwo.signature_entry("META-INF/services/a3.b")
assert not kuwo.signature_entry("META-INF/kotlinx_coroutines_core.version")
print("Signing filter preserves ServiceLoader descriptors and Kotlin/library metadata")

# Controller IDs must come from the original switch tables, not the package-name hash index.
manager=(DU/'smali/com/dudu/autoui/manage/music/p.smali').read_text()
table=re.search(r'\.packed-switch 0x2\n(.*?)\.end packed-switch',manager,re.S)[1]
labels=re.findall(r':pswitch_\w+',table)
label=labels[6-2]
block=re.search(r'^    '+label+r'\n(.*?)(?=^    :pswitch_|^    :cond_|\Z)',manager,re.M|re.S)[1]
assert 'new-instance v1, Lcom/dudu/autoui/manage/music/plugin/n1;' in block
print('Auto-selection ID 6 verified against original plugin switch; monitor hook uses existing Arcfox plugin startup')
