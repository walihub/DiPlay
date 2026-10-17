"""Append read-only vehicle provider declarations to Dudu AXML without recompiling its resources."""
import struct

U32 = lambda data, offset: struct.unpack_from('<I', data, offset)[0]
NO = 0xffffffff
ANDROID = 'http://schemas.android.com/apk/res/android'


def patch_manifest(data):
    assert struct.unpack_from('<HH', data) == (3, 8)
    assert U32(data, 4) == len(data)
    chunks = []
    pos = 8
    while pos < len(data):
        size = U32(data, pos + 4)
        assert size >= 8 and pos + size <= len(data)
        chunks.append(data[pos:pos + size])
        pos += size
    pool_index = next(i for i, c in enumerate(chunks) if struct.unpack_from('<H', c)[0] == 1)
    pool = chunks[pool_index]
    _, header, _ = struct.unpack_from('<HHI', pool)
    count, styles, flags, start, style_start = struct.unpack_from('<5I', pool, 8)
    assert styles == 0 and style_start == 0 and header == 28
    utf8 = bool(flags & 0x100)

    def read_length(offset):
        if utf8:
            value = pool[offset]
            return (((value & 127) << 8) | pool[offset + 1], offset + 2) if value & 128 else (value, offset + 1)
        value = struct.unpack_from('<H', pool, offset)[0]
        return (((value & 32767) << 16) | struct.unpack_from('<H', pool, offset + 2)[0], offset + 4) if value & 32768 else (value, offset + 2)

    strings = []
    for i in range(count):
        length, offset = read_length(start + U32(pool, header + i * 4))
        if utf8:
            length, offset = read_length(offset)
        strings.append(pool[offset:offset + length * (1 if utf8 else 2)].decode('utf-8' if utf8 else 'utf-16le'))
    assert 'local.arcfox.vehicle.VehicleProvider' not in strings, 'already patched'

    def idx(value):
        if value not in strings:
            strings.append(value)
        return strings.index(value)

    uri = idx(ANDROID)
    attr_ids = {'name': 0x01010003, 'permission': 0x01010006,
                'protectionLevel': 0x01010009, 'exported': 0x01010010,
                'required': 0x0101028e, 'authorities': 0x01010018}

    def node(tag, attrs=(), children=b''):
        tag_id = idx(tag)
        encoded = b''
        for key, value in sorted(attrs, key=lambda pair: attr_ids[pair[0]]):
            if isinstance(value, bool):
                raw, kind, typed = NO, 0x12, NO if value else 0
            elif isinstance(value, int):
                raw, kind, typed = NO, 0x11, value
            else:
                raw = typed = idx(value)
                kind = 3
            encoded += struct.pack('<IIIHBBI', uri, idx(key), raw, 8, 0, kind, typed)
        begin = struct.pack('<HHIII', 0x102, 16, 36 + len(encoded), 0, NO)
        begin += struct.pack('<II6H', NO, tag_id, 20, 20, len(attrs), 0, 0, 0) + encoded
        end = struct.pack('<HHIIIII', 0x103, 16, 24, 0, NO, NO, tag_id)
        return begin + children + end

    top = b''.join(node('uses-permission', [('name', permission)]) for permission in (
        'android.car.permission.CAR_SPEED', 'android.car.permission.CAR_POWERTRAIN',
        'android.permission.GRANT_RUNTIME_PERMISSIONS',
        'android.car.permission.CAR_VENDOR_EXTENSION'))
    app = node('uses-library', [('name', 'android.car'), ('required', False)])
    app += node('provider', [('name', 'local.arcfox.vehicle.VehicleProvider'),
                            ('authorities', 'com.dudu.autoui.arcfox.vehicle.init'),
                            ('exported', False)])

    offsets = [U32(pool, header + i * 4) for i in range(count)]
    blob = pool[start:]
    for string in strings[count:]:
        offsets.append(len(blob))
        raw = string.encode('utf-8' if utf8 else 'utf-16le')
        units = len(string.encode('utf-16le')) // 2
        assert units < 128 and (not utf8 or len(raw) < 128)
        blob += (bytes([units, len(raw)]) if utf8 else struct.pack('<H', units))
        blob += raw + (b'\0' if utf8 else b'\0\0')
    blob += b'\0' * (-len(blob) % 4)
    new_start = header + len(strings) * 4
    chunks[pool_index] = struct.pack('<HHI5I', 1, header, new_start + len(blob),
        len(strings), 0, flags & ~1, new_start, 0) + b''.join(struct.pack('<I', o) for o in offsets) + blob
    resources = next(i for i, c in enumerate(chunks) if struct.unpack_from('<H', c)[0] == 0x180)
    ids = list(struct.unpack('<' + 'I' * ((len(chunks[resources]) - 8) // 4), chunks[resources][8:]))
    ids.extend([0] * (len(strings) - len(ids)))
    for key, value in attr_ids.items():
        index = strings.index(key)
        assert ids[index] in (0, value)
        ids[index] = value
    chunks[resources] = struct.pack('<HHI', 0x180, 8, 8 + 4 * len(ids)) + struct.pack('<' + 'I' * len(ids), *ids)
    output = []
    for chunk in chunks:
        kind = struct.unpack_from('<H', chunk)[0]
        if kind == 0x102 and strings[U32(chunk, 20)] == 'application':
            output.append(top)
        if kind == 0x103 and strings[U32(chunk, 20)] == 'application':
            output.append(app)
        output.append(chunk)
    body = b''.join(output)
    return struct.pack('<HHI', 3, 8, 8 + len(body)) + body
