#!/usr/bin/env python3
"""Compose verified tyre/artwork fixes without dropping existing vehicle/media integration.

Playlist proposals are intentionally excluded from both smali hooks and Java compilation.
"""
from pathlib import Path
from zipfile import ZipFile, ZIP_STORED
import tempfile, hashlib, importlib.util
from build_kuwo import run, ROOT, HERE, JAVA, SDK, ANDROID, APKTOOL, signature_entry
from build_dudu_kuwo import patch

SOURCE=ROOT/'build/arcfox-release/Dudu_fix51_ArcfoxVehicleData_v3_MediaConsistency_platform_signed.apk'
OUTPUT=ROOT/'build/arcfox-release/Dudu_fix51_ArcfoxVehicleData_v9_PipAppsHiddenSync_platform_signed.apk'

def compile_dex(temp, label, sources, stubs=False):
 classes=temp/(label+'-classes');dex=temp/(label+'-dex');classes.mkdir();dex.mkdir()
 run(JAVA/'bin/javac','-source','8','-target','8','-cp',ANDROID,'-d',classes,*sources)
 compiled=[p for p in classes.rglob('*.class') if not(stubs and '/com/dudu/' in str(p))]
 assert compiled
 run(SDK/'build-tools/36.0.0/d8','--min-api','25','--lib',ANDROID,'--classpath',classes,'--output',dex,*compiled)
 return dex/'classes.dex'

def main():
 # Accepted original vehicle/media v3 is kept as an immutable baseline.
 assert hashlib.sha256(SOURCE.read_bytes()).hexdigest()=='4a28165964fc431f50015852d66fbd172ec4e600e58cf330b7426eff00eadea0'
 vehicle=ROOT/'tools/arcfox-vehicle';media=ROOT/'tools/arcfox-media'
 with tempfile.TemporaryDirectory(prefix='dudu-media-fixes-') as td:
  temp=Path(td);decoded=temp/'decoded'
  run(JAVA/'bin/java','-jar',APKTOOL,'d','-r','-f',SOURCE,'-o',decoded)
  patch(decoded) # Kuwo compatibility and passive auto-selection; no playlist patch.
  lyric_spec=importlib.util.spec_from_file_location('native_lyrics_patch',media/'lyrics_patch.py')
  lyric_patch=importlib.util.module_from_spec(lyric_spec);lyric_spec.loader.exec_module(lyric_patch)
  lyric_patch.patch_lyrics(decoded)
  pip_spec=importlib.util.spec_from_file_location('pip_launch_patch',HERE/'pip_patch.py')
  pip_patch=importlib.util.module_from_spec(pip_spec);pip_spec.loader.exec_module(pip_patch)
  pip_patch.patch(decoded)
  dex4=compile_dex(temp,'vehicle',[
   ROOT/'shared/src/main/java/com/shilapi/xcertplay/vehicle/arcfox/data/ArcfoxCarReader.java',
   *sorted((vehicle/'stubs').rglob('*.java')),
   *sorted((vehicle/'java/local/arcfox/vehicle').glob('*.java'))],stubs=True)
  dex5=compile_dex(temp,'media',sorted((media/'java/local/arcfox/media').glob('*.java')))
  dex6=compile_dex(temp,'kuwo',[
   HERE/'java/local/arcfox/kuwo/MediaPolicy.java',
   HERE/'java/local/arcfox/kuwo/Instrument.java',
   HERE/'java/local/arcfox/kuwo/KeyPolicy.java',
   *sorted(HERE.glob('dudu-java/local/arcfox/kuwo/*.java')),
   *sorted((HERE/'pip-java').rglob('*.java'))])
  run(JAVA/'bin/java','-jar',APKTOOL,'b',decoded,'-o',temp/'rebuilt.apk')
  with ZipFile(SOURCE) as old,ZipFile(temp/'rebuilt.apk') as rebuilt,ZipFile(temp/'unsigned.apk','w') as out:
   assert 'classes6.dex' not in old.namelist()
   for item in old.infolist():
    if signature_entry(item.filename):continue
    if item.filename=='classes4.dex':out.write(dex4,item.filename,compress_type=ZIP_STORED)
    elif item.filename=='classes5.dex':out.write(dex5,item.filename,compress_type=ZIP_STORED)
    else:out.writestr(item,rebuilt.read(item.filename) if item.filename=='classes.dex' else old.read(item.filename))
   out.write(dex6,'classes6.dex',compress_type=ZIP_STORED)
  tools=SDK/'build-tools/30.0.3';run(tools/'zipalign','-f','4',temp/'unsigned.apk',temp/'aligned.apk')
  run(tools/'apksigner','sign','--v4-signing-enabled','false','--key','/Users/h/Documents/arcfox/sign/platform.pk8','--cert','/Users/h/Documents/arcfox/sign/platform.x509.pem','--out',OUTPUT,temp/'aligned.apk')
  run(tools/'apksigner','verify','--verbose','--print-certs',OUTPUT)
  with ZipFile(SOURCE) as old,ZipFile(OUTPUT) as new:
   for name in old.namelist():
    if name in ('classes.dex','classes4.dex','classes5.dex') or signature_entry(name):continue
    assert old.read(name)==new.read(name),name
   for name in ('classes4.dex','classes5.dex','classes6.dex'):
    assert b'KuwoQueue' not in new.read(name),'unapproved playlist proposal bundled'
 print('Manifest/resources/assets/classes2/classes3 preserved; classes4 tyres + classes5 artwork/native lyrics + classes6 Kuwo lyrics rebuilt; no playlist proposal')
 print(hashlib.sha256(OUTPUT.read_bytes()).hexdigest(),OUTPUT)
if __name__=='__main__':main()
