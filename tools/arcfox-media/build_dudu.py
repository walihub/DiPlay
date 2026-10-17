#!/usr/bin/env python3
"""Patch only Arcfox music callbacks and commands over the vehicle-data v2 APK."""
from pathlib import Path
from zipfile import ZipFile,ZIP_STORED
import subprocess,tempfile,os,hashlib,re
from manifest import patch_manifest
from lyrics_patch import patch_lyrics
ROOT=Path(__file__).resolve().parents[2]; HERE=Path(__file__).parent
JAVA=Path('/Users/h/Documents/arcfox/.android-tools/jdk21/Contents/Home')
SDK=Path('/Users/h/Documents/arcfox/.android-sdk'); ANDROID=SDK/'platforms/android-30/android.jar'
APKTOOL=Path('/Users/h/Documents/arcfox/.android-tools/apktool/apktool.jar')
SOURCE=ROOT/'build/arcfox-release/Dudu_fix51_ArcfoxVehicleData_v2_TyreCard_Offset40_platform_signed.apk'
OUTPUT=ROOT/'build/arcfox-release/Dudu_fix51_ArcfoxVehicleData_v3_MediaConsistency_platform_signed.apk'
def run(*cmd):subprocess.run([str(x) for x in cmd],check=True,env={**os.environ,'JAVA_HOME':str(JAVA)})
def replace(text,signature,body):
 pattern=r'\.method '+re.escape(signature)+r'\n.*?\.end method'
 text,count=re.subn(pattern,'.method '+signature+'\n'+body+'\n.end method',text,flags=re.S)
 assert count==1,(signature,count)
 return text

def patch(base):
 directory=base/'smali/com/dudu/autoui/manage/music/plugin'
 p=directory/'ArcfoxMediaPlugin.smali'; s=p.read_text()
 s=replace(s,'private sendEvent(I)V','    .locals 0\n    invoke-static {p1}, Llocal/arcfox/media/MediaBridge;->command(I)V\n    return-void')
 anchor='    invoke-super {p0, p1, p2}, Lcom/dudu/autoui/manage/music/l;->a(Landroid/content/Context;Lcom/dudu/autoui/manage/music/r;)V'
 assert s.count(anchor)==1
 s=s.replace(anchor,anchor+'\n\n    invoke-static {p1, p0}, Llocal/arcfox/media/MediaBridge;->start(Landroid/content/Context;Ljava/lang/Object;)V')
 # SDK owns binding/rebinding; remove redundant QQ/raw binder registration to prevent a second writer.
 start=s.index('    new-instance v0, Landroid/content/Intent;',s.index('.method public a(Landroid/content/Context;'))
 end=s.index('    :try_start_0',start)
 s=s[:start]+s[end:]
 s=s.replace('.method public c()V\n    .locals 2','.method public c()V\n    .locals 2\n\n    invoke-static {}, Llocal/arcfox/media/MediaBridge;->stop()V')
 # Avoid optimistic button state when a command cannot be delivered.
 for method in ('h','i'):
  s=replace(s,'public '+method+'()V','    .locals 1\n    const/4 v0, 0x3\n    invoke-direct {p0, v0}, Lcom/dudu/autoui/manage/music/plugin/ArcfoxMediaPlugin;->sendEvent(I)V\n    return-void')
 p.write_text(s)
 p=directory/'DuDuMediaCallback.smali';s=p.read_text()
 for sig,body in {
 'public onMediaInfoChanged(ILcom/arcfox/media/center/bean/MediaInfo;)V':'.locals 0\n invoke-static {p0, p1, p2}, Llocal/arcfox/media/MediaBridge;->info(Ljava/lang/Object;ILjava/lang/Object;)V',
 'public onMediaPlayProgressChanged(IJJLjava/lang/String;)V':'.locals 0\n invoke-static/range {p0 .. p5}, Llocal/arcfox/media/MediaBridge;->progress(Ljava/lang/Object;IJJ)V',
 'public onMediaPlayStateChanged(II)V':'.locals 0\n invoke-static {p0, p1, p2}, Llocal/arcfox/media/MediaBridge;->state(Ljava/lang/Object;II)V',
 'public onServiceConnectStatus(Z)V':'.locals 0\n sput-boolean p1, Lcom/dudu/autoui/manage/music/plugin/DuDuMediaCallback;->sdkConnected:Z\n invoke-static {p0, p1}, Llocal/arcfox/media/MediaBridge;->connected(Ljava/lang/Object;Z)V'
 }.items():s=replace(s,sig,body+'\n return-void')
 p.write_text(s)

 # p.s() already maintains an Arcfox background monitor for other selected players.
 # Recreate it on each non-Arcfox controller initialization so restoration has a live callback owner.
 p=base/'smali/com/dudu/autoui/manage/music/p.smali';s=p.read_text()
 anchor='    sget-object v1, Lcom/dudu/autoui/manage/music/p;->qqMonitor:Lcom/dudu/autoui/manage/music/plugin/ArcfoxMediaPlugin;\n\n    if-nez v1, :cond_7'
 assert s.count(anchor)==1
 s=s.replace(anchor,'    # Refresh background monitor after restoring a different controller.')
 p.write_text(s)
 patch_lyrics(base)

def main():
 with tempfile.TemporaryDirectory(prefix='dudu-media-') as td:
  temp=Path(td); decoded=temp/'decoded';classes=temp/'classes';dex=temp/'dex';classes.mkdir();dex.mkdir()
  run(JAVA/'bin/java','-jar',APKTOOL,'d','-r','-f',SOURCE,'-o',decoded)
  patch(decoded)
  run(JAVA/'bin/javac','-source','8','-target','8','-cp',ANDROID,'-d',classes,*HERE.glob('java/local/arcfox/media/*.java'))
  run(SDK/'build-tools/36.0.0/d8','--min-api','25','--lib',ANDROID,'--output',dex,*classes.rglob('*.class'))
  run(JAVA/'bin/java','-jar',APKTOOL,'b',decoded,'-o',temp/'rebuilt.apk')
  with ZipFile(SOURCE) as old,ZipFile(temp/'rebuilt.apk') as changed,ZipFile(temp/'unsigned.apk','w') as out:
   for item in old.infolist():
    if item.filename.upper().startswith('META-INF/'):continue
    payload=changed.read('classes.dex') if item.filename=='classes.dex' else old.read(item.filename)
    if item.filename=='AndroidManifest.xml':payload=patch_manifest(payload)
    out.writestr(item,payload)
   assert 'classes5.dex' not in old.namelist()
   out.write(dex/'classes.dex','classes5.dex',compress_type=ZIP_STORED)
  tools=SDK/'build-tools/30.0.3'
  run(tools/'zipalign','-f','4',temp/'unsigned.apk',temp/'aligned.apk')
  run(tools/'apksigner','sign','--v4-signing-enabled','false','--key','/Users/h/Documents/arcfox/sign/platform.pk8','--cert','/Users/h/Documents/arcfox/sign/platform.x509.pem','--out',OUTPUT,temp/'aligned.apk')
  run(tools/'apksigner','verify','--verbose','--print-certs',OUTPUT)
  with ZipFile(SOURCE) as old,ZipFile(OUTPUT) as new:
   for name in old.namelist():
    if name in ('classes.dex','AndroidManifest.xml') or name.upper().startswith('META-INF/'):continue
    assert old.read(name)==new.read(name),name
 print(hashlib.sha256(OUTPUT.read_bytes()).hexdigest(),OUTPUT)
if __name__=='__main__':main()
