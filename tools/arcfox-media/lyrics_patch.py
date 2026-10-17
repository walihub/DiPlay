"""Idempotent lyric callback patch for root's existing v3/fix baseline."""
from pathlib import Path
import re

def patch_lyrics(base):
    path=Path(base)/'smali/com/dudu/autoui/manage/music/plugin/DuDuMediaCallback.smali'
    text=path.read_text()
    methods={
        'public onMediaLyricChanged(ILcom/arcfox/media/center/bean/MediaInfo;)V':
            '    .locals 0\n    invoke-static {p0, p1, p2}, Llocal/arcfox/media/MediaBridge;->lyric(Ljava/lang/Object;ILjava/lang/Object;)V\n    return-void',
        'public onMediaPlayProgressChanged(IJJLjava/lang/String;)V':
            '    .locals 0\n    invoke-static/range {p0 .. p6}, Llocal/arcfox/media/MediaBridge;->progress(Ljava/lang/Object;IJJLjava/lang/String;)V\n    return-void',
    }
    for signature,body in methods.items():
        pattern=r'\.method '+re.escape(signature)+r'\n.*?\.end method'
        text,count=re.subn(pattern,'.method '+signature+'\n'+body+'\n.end method',text,flags=re.S)
        assert count==1,(signature,count)
    path.write_text(text)
