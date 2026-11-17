"""Builds android/app/src/main/assets/map/world50m.bin for the Pages layout Map screen.

Source: Natural Earth 1:50m (public domain, https://www.naturalearthdata.com), GeoJSON from
github.com/nvkelso/natural-earth-vector: ne_50m_admin_0_countries, ne_50m_lakes,
ne_50m_admin_1_states_provinces_lines.

Usage: python -I make_map_asset.py <folder with the three .geojson files> <output .bin>

File format (little endian): "EJM1", int32 layer count; per layer: int32 line count; per line:
int32 point count, then point count pairs of int16 (longitude * 150, latitude * 150).
Layers: 0 countries and coastlines, 1 lakes, 2 state and province lines.
"""
import json, os, struct, sys

SCALE = 150
LAYERS = ["ne_50m_admin_0_countries", "ne_50m_lakes", "ne_50m_admin_1_states_provinces_lines"]


def lines(path):
    g = json.load(open(path, encoding="utf-8"))
    out = []
    for f in g["features"]:
        geo = f.get("geometry")
        if not geo:
            continue
        t, c = geo["type"], geo["coordinates"]
        if t == "Polygon":
            out += c
        elif t == "MultiPolygon":
            out += [r for p in c for r in p]
        elif t == "LineString":
            out.append(c)
        elif t == "MultiLineString":
            out += c
    return out


def main():
    src, dst = sys.argv[1], sys.argv[2]
    data = bytearray(b"EJM1") + struct.pack("<i", len(LAYERS))
    for name in LAYERS:
        packed = []
        for line in lines(os.path.join(src, name + ".geojson")):
            pts = []
            for p in line:
                q = (round(max(-180.0, min(180.0, p[0])) * SCALE), round(max(-90.0, min(90.0, p[1])) * SCALE))
                if not pts or pts[-1] != q:
                    pts.append(q)
            if len(pts) >= 2:
                packed.append(pts)
        data += struct.pack("<i", len(packed))
        for pts in packed:
            data += struct.pack("<i", len(pts))
            for x, y in pts:
                data += struct.pack("<hh", x, y)
        print(name, len(packed), "lines", sum(len(p) for p in packed), "points")
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    open(dst, "wb").write(bytes(data))
    print(dst, len(data), "bytes")


main()
