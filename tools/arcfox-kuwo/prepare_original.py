#!/usr/bin/env python3
"""Distribute the original Kuwo bytes; vehicle integration lives in Dudu.

Do not re-sign the player: a signature-only control fails resource URL creation.
"""
from pathlib import Path
import hashlib,os,shutil,subprocess
ROOT=Path(__file__).resolve().parents[2]
SOURCE=Path('/Users/h/Documents/arcfox/app/酷我音乐车机版 v7.1.8.21.apk')
OUTPUT=ROOT/'build/arcfox-release/Kuwo-7.1.8.21-original-signed.apk'
EXPECTED='92a115fec3631945607e1d4b5d52d1b311844c59dd91ff6f68f883d0a0707742'
CERT='44bc3100f89f3a2fd5b25203fe84ef72a8bd51e4'
def main():
 assert hashlib.sha256(SOURCE.read_bytes()).hexdigest()==EXPECTED,'Original source changed; verify playback before replacing baseline'
 # This source has valid v2/v3, no v1. The actual Android 11 target uses v2/v3.
 result=subprocess.run(['/Users/h/Documents/arcfox/.android-sdk/build-tools/30.0.3/apksigner','verify','--min-sdk-version','24','--verbose','--print-certs',str(SOURCE)],env={**os.environ,'JAVA_HOME':'/Users/h/Documents/arcfox/.android-tools/jdk21/Contents/Home'},check=True,capture_output=True,text=True)
 assert CERT in result.stdout
 assert 'Verified using v2 scheme (APK Signature Scheme v2): true' in result.stdout
 OUTPUT.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(SOURCE,OUTPUT)
 assert hashlib.sha256(OUTPUT.read_bytes()).hexdigest()==EXPECTED
 print('Original v2/v3 signature retained; player bytes unchanged. No platform re-signing.')
 print(EXPECTED,OUTPUT)
if __name__=='__main__':main()
