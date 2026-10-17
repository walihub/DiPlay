#!/usr/bin/env python3
"""Patch the Kuwo car plugin on top of the accepted Dudu vehicle/media v3 APK."""
from pathlib import Path
from zipfile import ZipFile,ZIP_STORED
import tempfile,hashlib,re
from build_kuwo import run,method,ROOT,HERE,JAVA,SDK,ANDROID,APKTOOL,signature_entry
SOURCE=ROOT/'build/arcfox-release/Dudu_fix51_ArcfoxVehicleData_v3_MediaConsistency_platform_signed.apk'
OUTPUT=ROOT/'build/arcfox-release/Dudu_fix51_ArcfoxVehicleData_v5_KuwoAutoSelect_platform_signed.apk'

def patch(base):
 p=base/'smali/com/dudu/autoui/manage/music/plugin/n1.smali';s=p.read_text()
 def insert(body,call):
  match=re.search(r'    \.locals (\d+)\n',body);assert match
  count=max(1,int(match[1]));body=body[:match.start()]+f'    .locals {count}\n'+body[match.end():]
  return body.replace(f'    .locals {count}\n',f'''    .locals {count}
{call}
    move-result v0
    if-eqz v0, :arcfox_original
    return-void
    :arcfox_original
''',1)
 def initialize(body):
  anchor='    invoke-super {p0, p1, p2}, Lcom/dudu/autoui/manage/music/l;->a(Landroid/content/Context;Lcom/dudu/autoui/manage/music/r;)V'
  assert body.count(anchor)==1
  body=body.replace('    .locals 0','    .locals 1',1)
  return body.replace(anchor,anchor+"\n    invoke-static {p1, p0, p2}, Llocal/arcfox/kuwo/KuwoCardBridge;->start(Landroid/content/Context;Ljava/lang/Object;Ljava/lang/Object;)Z\n    move-result v0\n    if-eqz v0, :arcfox_original\n    return-void\n    :arcfox_original")
 s=method(s,'public a(Landroid/content/Context;Lcom/dudu/autoui/manage/music/r;)V',initialize)
 # The Arcfox branch uses the provided callback directly. Other vehicles run the original initializer.
 for sig,call in [('public c()V','stop'),('public d()V','refresh')]:
  s=method(s,sig,lambda body,call=call:insert(body,f'    invoke-static {{p0}}, Llocal/arcfox/kuwo/KuwoCardBridge;->{call}(Ljava/lang/Object;)Z'))
 for name,event in [('g',6),('h',2),('i',1),('j',5)]:
  s=method(s,f'public {name}()V',lambda body,event=event:insert(body,f'    const/4 v0, {event}\n    invoke-static {{p0, v0}}, Llocal/arcfox/kuwo/KuwoCardBridge;->command(Ljava/lang/Object;I)Z'))
 p.write_text(s)
 p=base/'smali/com/dudu/autoui/manage/music/plugin/ArcfoxMediaPlugin.smali';s=p.read_text()
 anchor='    invoke-static {p1, p0}, Llocal/arcfox/media/MediaBridge;->start(Landroid/content/Context;Ljava/lang/Object;)V'
 assert s.count(anchor)==1
 s=s.replace(anchor,anchor+'\n    invoke-static {p1}, Llocal/arcfox/kuwo/KuwoAutoSelect;->watch(Landroid/content/Context;)V')
 p.write_text(s)

def main():
 # Keep the historical patch() ABI; all production builds use the composed current fixes.
 from build_dudu_fixes import main as build_current
 build_current()
if __name__=='__main__':main()
