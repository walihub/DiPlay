"""Real Kuwo queue and Dudu list hooks; call after existing patch functions."""
from pathlib import Path
import re

def patch_kuwo(base):
 p=base/'smali/cn/kuwo/mod/mediaSession/KwMediaSessionService.smali';s=p.read_text()
 anchor='.method public A()V\n    .locals 6'
 match=re.search(r'\.method public A\(\)V\n    \.locals (\d+)',s);assert match
 replacement=match[0]+'''
    iget-object v0, p0, Lcn/kuwo/mod/mediaSession/KwMediaSessionService;->e:Landroid/support/v4/media/session/MediaSessionCompat;
    invoke-static {v0}, Llocal/arcfox/kuwo/KuwoQueueBridge;->publish(Ljava/lang/Object;)Z
    move-result v0
    if-eqz v0, :arcfox_original_queue
    return-void
    :arcfox_original_queue
'''
 s=s[:match.start()]+replacement+s[match.end():];p.write_text(s)
 p=base/'smali/cn/kuwo/mod/mediaSession/KwMediaSessionService$d.smali';s=p.read_text();assert '.method public A(J)V' not in s
 s+='''
.method public A(J)V
    .locals 0
    invoke-static {p1, p2}, Llocal/arcfox/kuwo/KuwoQueueBridge;->select(J)V
    return-void
.end method
''';p.write_text(s)

def patch_dudu(base):
 p=base/'smali/com/dudu/autoui/manage/music/plugin/n1.smali';s=p.read_text()
 assert '.method public e()Ljava/util/List;' not in s
 s+='''
.method public e()Ljava/util/List;
    .locals 1
    invoke-static {p0}, Llocal/arcfox/kuwo/KuwoCardBridge;->playlist(Ljava/lang/Object;)Ljava/util/List;
    move-result-object v0
    return-object v0
.end method
.method public a()Z
    .locals 1
    invoke-static {p0}, Llocal/arcfox/kuwo/KuwoCardBridge;->supportsPlaylist(Ljava/lang/Object;)Z
    move-result v0
    return v0
.end method
.method public a(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;)V
    .locals 0
    invoke-static {p0, p1, p4}, Llocal/arcfox/kuwo/KuwoCardBridge;->selectPlaylist(Ljava/lang/Object;ILjava/lang/String;)V
    return-void
.end method
''';p.write_text(s)
