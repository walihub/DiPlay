#!/usr/bin/env python3
"""Add the Arcfox adapter without rewriting Kuwo resources, login, decoder or SDK."""
from pathlib import Path
from zipfile import ZipFile, ZIP_STORED
import os, subprocess, tempfile, hashlib, re, json
from manifest import patch_manifest
ROOT=Path(__file__).resolve().parents[2];HERE=Path(__file__).parent
JAVA=Path('/Users/h/Documents/arcfox/.android-tools/jdk21/Contents/Home')
SDK=Path('/Users/h/Documents/arcfox/.android-sdk');ANDROID=SDK/'platforms/android-30/android.jar'
APKTOOL=Path('/Users/h/Documents/arcfox/.android-tools/apktool/apktool.jar')
SOURCE=Path('/Users/h/Documents/arcfox/app/酷我音乐车机版 v7.1.8.21.apk')
OUTPUT=ROOT/'build/arcfox-release/Kuwo-7.1.8.21-original-signed.apk'
def signature_entry(name):
 parts=name.upper().split('/')
 return len(parts)==2 and parts[0]=='META-INF' and (parts[1]=='MANIFEST.MF' or parts[1].endswith(('.SF','.RSA','.DSA','.EC')) or parts[1].startswith('SIG-'))
def run(*cmd):subprocess.run([str(c) for c in cmd],check=True,env={**os.environ,'JAVA_HOME':str(JAVA)})
def method(text,signature,transform):
 pattern=r'(\.method '+re.escape(signature)+r'\n)(.*?)(\.end method)'
 def apply(m):return m[1]+transform(m[2])+m[3]
 text,count=re.subn(pattern,apply,text,flags=re.S);assert count==1,(signature,count);return text

def patch(base):
 p=base/'smali/cn/kuwo/mod/mediaSession/KwMediaSessionService.smali';s=p.read_text()
 def attach(body):
  assert body.count('    return-void')==1
  return body.replace('    return-void','''    iget-object v0, p0, Lcn/kuwo/mod/mediaSession/KwMediaSessionService;->e:Landroid/support/v4/media/session/MediaSessionCompat;
    invoke-static {p0, v0}, Llocal/arcfox/kuwo/KuwoBridge;->attach(Landroid/content/Context;Ljava/lang/Object;)V
    return-void''')
 def detach(body):
  return body.replace('    .locals 5','''    .locals 5
    iget-object v0, p0, Lcn/kuwo/mod/mediaSession/KwMediaSessionService;->e:Landroid/support/v4/media/session/MediaSessionCompat;
    invoke-static {v0}, Llocal/arcfox/kuwo/KuwoBridge;->detach(Ljava/lang/Object;)V''',1)
 s=method(s,'public q()V',attach);s=method(s,'public x()V',detach);p.write_text(s)
 p=base/'smali/cn/kuwo/kwmusiccar/KwCarPlay/KwCarPlay$q.smali';s=p.read_text()
 def gate(body):
  assert re.search(r'    \.locals [1-9]',body)
  return re.sub(r'(    \.locals \d+\n)',r'''\1
    invoke-static {p2}, Llocal/arcfox/kuwo/KuwoBridge;->allow(Landroid/content/Intent;)Z
    move-result v0
    if-nez v0, :arcfox_deliver
    return-void
    :arcfox_deliver
''',body,count=1)
 s=method(s,'public onReceive(Landroid/content/Context;Landroid/content/Intent;)V',gate);p.write_text(s)


def main():
 from prepare_original import main as prepare
 prepare()
if __name__=='__main__':main()
