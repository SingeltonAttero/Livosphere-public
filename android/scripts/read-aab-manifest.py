#!/usr/bin/env python3
"""Read package/version fields from the base manifest protobuf in an AAB."""
import sys
import zipfile


def varint(data, offset):
    value = 0
    shift = 0
    while offset < len(data):
        byte = data[offset]
        offset += 1
        value |= (byte & 0x7F) << shift
        if byte < 0x80:
            return value, offset
        shift += 7
    raise ValueError("truncated protobuf varint")


def fields(data):
    offset = 0
    while offset < len(data):
        tag, offset = varint(data, offset)
        number, wire = tag >> 3, tag & 7
        if wire == 0:
            value, offset = varint(data, offset)
        elif wire == 2:
            length, offset = varint(data, offset)
            end = offset + length
            if end > len(data):
                raise ValueError("truncated protobuf message")
            value, offset = data[offset:end], end
        elif wire == 1:
            value, offset = data[offset:offset + 8], offset + 8
        elif wire == 5:
            value, offset = data[offset:offset + 4], offset + 4
        else:
            raise ValueError(f"unsupported protobuf wire type {wire}")
        yield number, wire, value


def text(value):
    if not isinstance(value, bytes):
        return None
    try:
        decoded = value.decode("utf-8")
    except UnicodeDecodeError:
        return None
    return decoded if decoded and all(c.isprintable() or c in "\r\n\t" for c in decoded) else None


def attrs(element):
    result = {}
    for number, wire, value in fields(element):
        if number == 4 and wire == 2:
            parts = {n: text(v) for n, w, v in fields(value) if w == 2}
            if parts.get(2) and parts.get(3) is not None:
                result[parts[2]] = parts[3]
    return result


def walk_element(node):
    # XmlElement: field 3 is the element name, field 4 attributes, field 5 child XmlNodes.
    name = None
    attributes = {}
    children = []
    for number, wire, value in fields(node):
        if number == 3 and wire == 2:
            name = text(value)
        elif number == 4 and wire == 2:
            attributes.update(attrs(node))
            break
    for number, wire, value in fields(node):
        if number == 5 and wire == 2:
            for child_number, child_wire, child_value in fields(value):
                if child_number == 1 and child_wire == 2:
                    children.append(walk_element(child_value))
    return name, attributes, children


def main(path):
    with zipfile.ZipFile(path) as archive:
        if archive.testzip() is not None:
            raise ValueError("AAB archive is corrupt")
        names = set(archive.namelist())
        if "base/manifest/AndroidManifest.xml" not in names:
            raise ValueError("AAB has no base manifest")
        payload = archive.read("base/manifest/AndroidManifest.xml")
    root = None
    for number, wire, value in fields(payload):
        if number == 1 and wire == 2:
            root = walk_element(value)
            break
    if not root or root[0] != "manifest":
        raise ValueError("AAB base manifest root is not manifest")
    package = root[1].get("package")
    version_code = root[1].get("versionCode")
    version_name = root[1].get("versionName")
    if not package or not version_code or not version_name:
        raise ValueError("AAB manifest lacks package/version metadata")
    print(f"{package}|{version_code}|{version_name}")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: read-aab-manifest.py <aab>")
    try:
        main(sys.argv[1])
    except (OSError, ValueError, zipfile.BadZipFile) as error:
        print(f"AAB manifest: {error}", file=sys.stderr)
        raise SystemExit(1)
