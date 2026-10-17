"""Reproducible HUD-only patches on captured NaviService and the user's v24 coexist APK."""
from pathlib import Path
import subprocess, shutil, zipfile, copy, hashlib, json, sys
ROOT=Path(__file__).resolve().parents[2];WORK=ROOT/'build/hud-arbitration';OUT=ROOT/'build/arcfox-release'
JAVA=Path('/Users/h/Documents/arcfox/.android-tools/jdk21/Contents/Home/bin');SDK=Path('/Users/h/Documents/arcfox/.android-sdk');APKTOOL=Path('/Users/h/Documents/arcfox/.android-tools/apktool/apktool.jar')
GAODE=Path('/Users/h/Documents/arcfox/gaode');BASE=GAODE/'build/比亚迪3.15.872_极狐适配_v24_气泡缩放1.2_共存版_platform_signed.apk';SOURCE=GAODE/'work/byd872-hud-keepalive-v23'
CLIENT=ROOT/'shared/src/main/java/com/shilapi/xcertplay/vehicle/arcfox/HudSourceClient.java';API=SDK/'platforms/android-30/android.jar'
def run(args):subprocess.run(list(map(str,args)),check=True)
def compile_sources(sources,target,classpath=API):
 shutil.rmtree(target,ignore_errors=True);target.mkdir(parents=True,exist_ok=True);run([JAVA/'javac','--release','8','-g:none','-cp',classpath,'-d',target,*sources])
def dex(classes,target):run([JAVA/'java','-cp',SDK/'build-tools/30.0.3/lib/dx.jar','com.android.dx.command.Main','--dex','--min-sdk-version=28','--output='+str(target),classes])
def inject(path,method,code):
 s=path.read_text();a=s.index(method);b=s.index('.end method',a);body=s[a:b];pos=body.index('\n',body.index('.locals'))+1;body=body[:pos]+code+body[pos:];path.write_text(s[:a]+body+s[b:])
def sign(source,target):
 stripped=target.with_suffix('.unsigned.apk');aligned=target.with_suffix('.aligned.apk')
 with zipfile.ZipFile(source) as src,zipfile.ZipFile(stripped,'w') as dst:
  for entry in src.infolist():
   if entry.filename.startswith('META-INF/'):continue
   item=copy.copy(entry);item.extra=b'';item.compress_type=zipfile.ZIP_STORED if item.filename=='resources.arsc' else zipfile.ZIP_DEFLATED
   dst.writestr(item,src.read(entry),compresslevel=6)
 run([SDK/'build-tools/36.0.0/zipalign','-f','-p','4',stripped,aligned]);run([JAVA/'java','-jar',SDK/'build-tools/36.0.0/lib/apksigner.jar','sign','--key','/Users/h/Documents/arcfox/sign/platform.pk8','--cert','/Users/h/Documents/arcfox/sign/platform.x509.pem','--out',target,aligned]);run([JAVA/'java','-jar',SDK/'build-tools/36.0.0/lib/apksigner.jar','verify','--print-certs',target]);stripped.unlink();aligned.unlink()
 OUT.mkdir(parents=True,exist_ok=True)
 target.with_suffix('.apk.sha256').write_text(hashlib.sha256(target.read_bytes()).hexdigest()+'  '+target.name+'\n')
def native():
 decoded=WORK/'naviservice-base';tree=WORK/'naviservice-hud';shutil.rmtree(tree,ignore_errors=True);shutil.copytree(decoded,tree)
 ns='Llocal/arcfox/hud/HudArbiter;'
 app=tree/'smali/com/adayo/service/navi/ServiceApplication.smali'
 inject(app,'.method public onCreate()V','\n    invoke-static {p0}, '+ns+'->attach(Landroid/content/Context;)V\n')
 app_code=app.read_text();assert ns not in app_code.split('.method public constructor')[1].split('.end method')[0], 'Arbiter must not start before Context attachment'
 assert ns in app_code.split('.method public onCreate()V')[1].split('.end method')[0]
 queue=tree/'smali/com/adayo/service/navi/can/VehicleSendQueue9.smali'
 for method in ['.method public sendValue(IIIF)V','.method public sendValueNow(IIIF)V']:
  inject(queue,method,'\n    invoke-static {p1, p3}, '+ns+'->nativeInt(II)Z\n    move-result v0\n    if-eqz v0, :arbiter_original\n    return-void\n    :arbiter_original\n')
 inject(queue,'.method public sendByteValue([B)V','\n    invoke-static {p1}, '+ns+'->nativeRoad([B)Z\n    move-result v0\n    if-eqz v0, :arbiter_original\n    return-void\n    :arbiter_original\n')
 base=tree/'smali/com/adayo/service/navi/can/BaseCarManager.smali'
 inject(base,'.method public sendVehicleValue(Lcom/adayo/service/navi/can/bean/VehicleBean;)V','\n    iget v0, p1, Lcom/adayo/service/navi/can/bean/VehicleBean;->vehicleID:I\n    iget v1, p1, Lcom/adayo/service/navi/can/bean/VehicleBean;->valI:I\n    invoke-static {v0, v1}, '+ns+'->nativeInt(II)Z\n    move-result v0\n    if-eqz v0, :arbiter_original\n    return-void\n    :arbiter_original\n')
 inject(base,'.method public sendVehicleByteValue([B)V','\n    invoke-static {p1}, '+ns+'->nativeRoad([B)Z\n    move-result v0\n    if-eqz v0, :arbiter_original\n    return-void\n    :arbiter_original\n')
 manifest=tree/'AndroidManifest.xml';s=manifest.read_text().replace('    <application ', '    <uses-permission android:name="android.car.permission.CAR_VENDOR_EXTENSION"/>\n    <uses-permission android:name="android.permission.REAL_GET_TASKS"/>\n    <queries><package android:name="com.shihab.diplay"/><package android:name="com.autonavi.amaparcfox"/><package android:name="com.baidu.naviauto"/></queries>\n    <application ',1).replace('    </application>', '        <service android:name="local.arcfox.hud.HudArbiterService" android:exported="true" android:permission="android.car.permission.CAR_VENDOR_EXTENSION"/>\n    </application>');manifest.write_text(s)
 yml=tree/'apktool.yml';yml.write_text(yml.read_text().replace('versionCode: 2','versionCode: 5').replace('versionName: V01.01.05','versionName: V01.01.05-HudArbiter3-HudOnly'))
 # Fail the build if a future HUD patch changes OEM navigation lifecycle, keys or maps.
 allowed={Path('com/adayo/service/navi/ServiceApplication.smali'),Path('com/adayo/service/navi/can/VehicleSendQueue9.smali'),Path('com/adayo/service/navi/can/BaseCarManager.smali')}
 originals={p.relative_to(decoded/'smali') for p in (decoded/'smali').rglob('*.smali')}
 assert originals=={p.relative_to(tree/'smali') for p in (tree/'smali').rglob('*.smali')}
 changed={p for p in originals if (decoded/'smali'/p).read_bytes()!=(tree/'smali'/p).read_bytes()}
 assert changed==allowed, 'HUD patch changed unrelated OEM classes: '+str(changed^allowed)
 assert 'RecoveryReceiver' not in manifest.read_text()
 assert 'NativeNaviLifecycle' not in app.read_text()
 compile_sources(list((ROOT/'tools/arcfox-hud/service-src').rglob('*.java')),WORK/'service-classes');dex(WORK/'service-classes',WORK/'service.dex')
 (tree/'unknown').mkdir(exist_ok=True);shutil.copy2(WORK/'service.dex',tree/'unknown/classes2.dex')
 # apktool copies unknown entries verbatim; include the injected service dex as a stored entry.
 run([JAVA/'java','-jar',APKTOOL,'b','-p',WORK/'framework','-o',WORK/'native-unsigned.apk',tree])
 with zipfile.ZipFile(WORK/'native-unsigned.apk') as z:assert 'classes2.dex' in z.namelist()
 target=OUT/'NaviService-V01.01.05-HudArbiter3-HudOnly-system.apk';sign(WORK/'native-unsigned.apk',target);return target
def replace_method(text,name,body):
 a=text.index('    private static void '+name);start=text.index('{',a);depth=1;i=start+1
 while depth:
  depth+= (text[i]=='{')-(text[i]=='}');i+=1
 return text[:start+1]+'\n'+body+'\n    }'+text[i:]
def gaode():
 with zipfile.ZipFile(BASE) as z:assert z.read('classes26.dex')==(SOURCE/'classes26.dex').read_bytes(),'Base HUD differs from verified source'
 src=WORK/'gaode-src';src.mkdir(exist_ok=True)
 for p in (SOURCE/'src').glob('Arcfox*.java'):shutil.copy2(p,src/p.name)
 p=src/'ArcfoxVehicle.java';s=p.read_text().replace('    private static Context context;', '    private static com.shilapi.xcertplay.vehicle.arcfox.HudSourceClient hudSource;\n    private static Context context;',1).replace('        if(worker!=null)return;', '        if(worker!=null)return;\n        if(isArcfox())hudSource=new com.shilapi.xcertplay.vehicle.arcfox.HudSourceClient(context);',1)
 s=s.replace('            clearHud();try{if(car!=null)', '            clearHud();if(hudSource!=null){hudSource.close();hudSource=null;}try{if(car!=null)',1)
 s=replace_method(s,'clearHud()', '        if(hudSource!=null)hudSource.update(false,0,0,0,null);\n        ownsHud=false;committed=null;hudReason="arbiter_stop";')
 s=replace_method(s,'updateHud(boolean navigating)', '        if(navigating&&pending!=null)committed=pending;\n        if(!navigating)committed=null;\n        Hud h=committed;\n        if(hudSource!=null)hudSource.update(navigating,h==null?0:h.icon,h==null?0:h.distance,0,h==null?null:h.road);\n        ownsHud=navigating;hudReason="arbiter_source";')
 p.write_text(s)
 compile_sources(list((SOURCE/'stubs').rglob('*.java')),WORK/'gaode-stubs')
 compile_sources(list(src.glob('*.java'))+[CLIENT],WORK/'gaode-classes',str(API)+':'+str(WORK/'gaode-stubs'))
 dex(WORK/'gaode-classes',WORK/'gaode-hud.dex')
 unsigned=WORK/'gaode-unsigned.apk'
 with zipfile.ZipFile(BASE) as a,zipfile.ZipFile(unsigned,'w') as b:
  for entry in a.infolist():
   if not entry.filename.startswith('META-INF/'):b.writestr(entry,(WORK/'gaode-hud.dex').read_bytes() if entry.filename=='classes26.dex' else a.read(entry))
 target=OUT/'高德-比亚迪3.15.872-v24-气泡1.2-共存-HudArbiter1-system.apk';sign(unsigned,target)
 with zipfile.ZipFile(BASE) as a,zipfile.ZipFile(target) as b:
  names={n for n in a.namelist() if not n.startswith('META-INF/')};assert names=={n for n in b.namelist() if not n.startswith('META-INF/')}
  assert [n for n in names if a.read(n)!=b.read(n)]==['classes26.dex']
 return target
if __name__=='__main__':
 WORK.mkdir(parents=True,exist_ok=True);OUT.mkdir(parents=True,exist_ok=True)
 compile_sources([ROOT/'tools/arcfox-hud/service-src/local/arcfox/hud/HudSelection.java',ROOT/'tools/arcfox-hud/SelectionTest.java'],WORK/'selection-tests')
 run([JAVA/'java','-cp',WORK/'selection-tests','SelectionTest'])
 outputs=[native()] if "--native-only" in sys.argv else [native(),gaode()];(OUT/'HudArbiter-build-report.json').write_text(json.dumps({'outputs':[str(p) for p in outputs],'base_gaode':str(BASE),'installed':False,'physical_hud_verified':False},ensure_ascii=False,indent=2));print('\n'.join(map(str,outputs)))
