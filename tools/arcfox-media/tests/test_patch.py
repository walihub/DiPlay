#!/usr/bin/env python3
"""Check exact original ABI and generated smali invocation register widths."""
from pathlib import Path
import tempfile,shutil,sys,re
HERE=Path(__file__).resolve().parents[1];sys.path.insert(0,str(HERE))
from build_dudu import patch
ORIGINAL=Path('/Users/h/Documents/arcfox/tmp/dudu_fix51')
NAMES=['smali/com/dudu/autoui/manage/music/plugin/ArcfoxMediaPlugin.smali','smali/com/dudu/autoui/manage/music/plugin/DuDuMediaCallback.smali','smali/com/dudu/autoui/manage/music/p.smali']
with tempfile.TemporaryDirectory(prefix='media-smali-test-') as folder:
 out=Path(folder)
 for name in NAMES:
  p=out/name;p.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(ORIGINAL/name,p)
 patch(out)
 text='\n'.join((out/name).read_text() for name in NAMES)
 expected={
  'info(Ljava/lang/Object;ILjava/lang/Object;)V':3,
  'state(Ljava/lang/Object;II)V':3,
  'progress(Ljava/lang/Object;IJJLjava/lang/String;)V':7,
  'lyric(Ljava/lang/Object;ILjava/lang/Object;)V':3,
  'connected(Ljava/lang/Object;Z)V':2,
  'start(Landroid/content/Context;Ljava/lang/Object;)V':2,
  'command(I)V':1,'stop()V':0,
 }
 for signature,width in expected.items():
  matches=re.findall(r'invoke-static(?:/range)? \{([^}]*)\}, Llocal/arcfox/media/MediaBridge;->'+re.escape(signature),text)
  assert matches,signature
  for registers in matches:
   if '..' in registers:
    lo,hi=map(lambda v:int(v.strip()[1:]),registers.split('..'));count=hi-lo+1
   else:count=len([v for v in registers.split(',') if v.strip()])
   assert count==width,(signature,registers,width)
 assert 'com.arcfox.music.qq.service.MusicQqService' not in (out/NAMES[0]).read_text()
 assert '->sendMediaEvent(II)V' not in (out/NAMES[0]).read_text()
 manager=(ORIGINAL/NAMES[2]).read_text()
 for signature in ['a(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V','b(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V','a(Ljava/lang/String;Landroid/graphics/Bitmap;Z)V','a(II)V','a(ZZ)V','a(Ljava/util/List;I)V','s()V']:
  assert '.method public '+signature in manager,signature
 assert '.method public static k()I' in (ORIGINAL/'smali/com/dudu/autoui/manage/music/s.smali').read_text()
 assert '.method public static b(Ljava/lang/String;I)V' in (ORIGINAL/'smali/com/dudu/autoui/common/e1/p0.smali').read_text()
 sdk=(ORIGINAL/'smali_classes2/com/arcfox/media/center/IMediaCenterSdk.smali').read_text()
 for signature in ['getLastMediaType()I','getMediaInfo(ILcom/arcfox/media/center/callback/IMediaSysThirdCallback;)V','getPlayStatus(ILcom/arcfox/media/center/callback/IMediaSysThirdCallback;)V','sendMediaEvent(II)V']:
  assert '.method public abstract '+signature in sdk,signature
row=(ORIGINAL/'smali/com/dudu/autoui/common/y0/c/a.smali').read_text()
for field in ['.field public a:Ljava/lang/String;','.field public b:J','.field public c:J','.field public d:Ljava/lang/String;']:
 assert field in row,field
media=(ORIGINAL/'smali_classes2/com/arcfox/media/center/bean/MediaInfo.smali').read_text()
assert '.method public final getAllLyric()Ljava/util/List;' in media
lyric=(ORIGINAL/'smali_classes2/com/arcfox/media/center/bean/SysLyricRow.smali').read_text()
assert '.method public final getStartTimeString()Ljava/lang/String;' in lyric
assert '.method public final getContent()Ljava/lang/String;' in lyric
print('Smali patch ABI test passed (register widths, Dudu manager methods, exact SDK binder signatures)')
