"""Build Nimbo's shared world-map data: land dot grid + country / timezone anchor points."""
import json, sys

src, out = sys.argv[1], sys.argv[2]
topo = json.load(open(src, encoding="utf-8"))
sx, sy = topo["transform"]["scale"]
tx, ty = topo["transform"]["translate"]

arcs = []
for arc in topo["arcs"]:
    x = y = 0
    pts = []
    for dx, dy in arc:
        x += dx; y += dy
        pts.append((x * sx + tx, y * sy + ty))
    arcs.append(pts)

def ring(idx):
    pts = []
    for i in idx:
        a = arcs[i] if i >= 0 else list(reversed(arcs[~i]))
        pts.extend(a if not pts else a[1:])
    # Unwrap longitudes so rings crossing the antimeridian stay continuous
    # (otherwise the +-180 jump flips the even-odd fill and paints false land bands).
    out, shift = [pts[0]], 0.0
    for (xa, _), (xb, yb) in zip(pts, pts[1:]):
        d = xb - xa
        if d > 180: shift -= 360
        elif d < -180: shift += 360
        out.append((xb + shift, yb))
    # Keep the ring's west edge in [-180, 180) so holes (e.g. the Caspian) line up with their outer ring.
    k = 0.0
    m = min(p[0] for p in out)
    while m + k < -180: k += 360
    while m + k >= 180: k -= 360
    return [(x + k, y) for x, y in out]

polys = []  # list of rings (outer + holes, even-odd fill)
for g in topo["objects"]["land"]["geometries"]:
    parts = g["arcs"] if g["type"] == "MultiPolygon" else [g["arcs"]]
    for poly in parts:
        rings = [ring(r) for r in poly]
        xs = [p[0] for r in rings for p in r]; ys = [p[1] for r in rings for p in r]
        polys.append((min(xs), max(xs), min(ys), max(ys), rings))

def on_land(lon, lat):
    return any(_on_land(lon + k, lat) for k in (0.0, 360.0, -360.0))

def _on_land(lon, lat):
    for x0, x1, y0, y1, rings in polys:
        if not (x0 <= lon <= x1 and y0 <= lat <= y1):
            continue
        inside = False
        for r in rings:
            n = len(r)
            for i in range(n):
                xa, ya = r[i]; xb, yb = r[i - 1]
                if (ya > lat) != (yb > lat) and lon < (xb - xa) * (lat - ya) / (yb - ya) + xa:
                    inside = not inside
        if inside:
            return True
    return False

STEP, LON0, LAT_TOP, LAT_BOTTOM = 1.0, -180.0, 80.0, -56.0
cols = int(360 / STEP)
rows = int((LAT_TOP - LAT_BOTTOM) / STEP)
hex_rows, count = [], 0
for r in range(rows):
    lat = LAT_TOP - (r + 0.5) * STEP
    bits = []
    for c in range(cols):
        lon = LON0 + (c + 0.5) * STEP
        b = on_land(lon, lat); bits.append(b); count += b
    s = ""
    for i in range(0, cols, 4):
        v = 0
        for k in range(4):
            v = (v << 1) | int(bits[i + k])
        s += "0123456789abcdef"[v]
    hex_rows.append(s)

# Representative point per country = the usual VPN datacenter city (lat, lon).
countries = {
 "AD":(42.51,1.52),"AE":(25.20,55.27),"AL":(41.33,19.82),"AM":(40.18,44.51),"AR":(-34.60,-58.38),"AT":(48.21,16.37),
 "AU":(-33.87,151.21),"AZ":(40.41,49.87),"BA":(43.86,18.41),"BD":(23.81,90.41),"BE":(50.85,4.35),"BG":(42.70,23.32),
 "BH":(26.23,50.59),"BR":(-23.55,-46.63),"BY":(53.90,27.56),"CA":(43.65,-79.38),"CH":(47.38,8.54),"CL":(-33.45,-70.67),
 "CN":(31.23,121.47),"CO":(4.71,-74.07),"CR":(9.93,-84.08),"CY":(35.17,33.36),"CZ":(50.08,14.44),"DE":(50.11,8.68),
 "DK":(55.68,12.57),"DZ":(36.75,3.06),"EE":(59.44,24.75),"EG":(30.04,31.24),"ES":(40.42,-3.70),"FI":(60.17,24.94),
 "FR":(48.86,2.35),"GB":(51.51,-0.13),"GE":(41.72,44.79),"GR":(37.98,23.73),"HK":(22.32,114.17),"HR":(45.81,15.98),
 "HU":(47.50,19.04),"ID":(-6.21,106.85),"IE":(53.35,-6.26),"IL":(32.09,34.78),"IN":(19.08,72.88),"IQ":(33.31,44.36),
 "IR":(35.69,51.39),"IS":(64.15,-21.94),"IT":(45.46,9.19),"JO":(31.95,35.93),"JP":(35.68,139.69),"KE":(-1.29,36.82),
 "KG":(42.87,74.59),"KR":(37.57,126.98),"KW":(29.38,47.99),"KZ":(43.24,76.95),"LB":(33.89,35.50),"LT":(54.69,25.28),
 "LU":(49.61,6.13),"LV":(56.95,24.11),"MA":(33.57,-7.59),"MD":(47.01,28.86),"ME":(42.44,19.26),"MK":(42.00,21.43),
 "MN":(47.89,106.91),"MT":(35.90,14.51),"MX":(19.43,-99.13),"MY":(3.14,101.69),"NG":(6.52,3.38),"NL":(52.37,4.90),
 "NO":(59.91,10.75),"NZ":(-36.85,174.76),"OM":(23.59,58.41),"PA":(8.98,-79.52),"PE":(-12.05,-77.04),"PH":(14.60,120.98),
 "PK":(24.86,67.01),"PL":(52.23,21.01),"PT":(38.72,-9.14),"QA":(25.29,51.53),"RO":(44.43,26.10),"RS":(44.79,20.45),
 "RU":(55.76,37.62),"SA":(24.71,46.68),"SE":(59.33,18.07),"SG":(1.35,103.82),"SI":(46.06,14.51),"SK":(48.15,17.11),
 "TH":(13.76,100.50),"TJ":(38.56,68.79),"TM":(37.96,58.33),"TR":(41.01,28.98),"TW":(25.03,121.57),"UA":(50.45,30.52),
 "US":(40.71,-74.01),"UY":(-34.90,-56.16),"UZ":(41.30,69.24),"VE":(10.48,-66.90),"VN":(10.82,106.63),"ZA":(-26.20,28.05),
}

# Device timezone -> approximate user position (route start). Fallback: region/locale country above.
timezones = {
 "Europe/Moscow":(55.76,37.62),"Europe/Kaliningrad":(54.71,20.51),"Europe/Samara":(53.20,50.15),"Europe/Volgograd":(48.71,44.51),
 "Europe/Saratov":(51.53,46.03),"Europe/Ulyanovsk":(54.32,48.40),"Europe/Astrakhan":(46.35,48.04),"Europe/Kirov":(58.60,49.66),
 "Asia/Yekaterinburg":(56.84,60.61),"Asia/Omsk":(54.99,73.37),"Asia/Novosibirsk":(55.03,82.92),"Asia/Barnaul":(53.35,83.78),
 "Asia/Tomsk":(56.50,84.97),"Asia/Novokuznetsk":(53.76,87.11),"Asia/Krasnoyarsk":(56.01,92.89),"Asia/Irkutsk":(52.29,104.28),
 "Asia/Chita":(52.03,113.50),"Asia/Yakutsk":(62.03,129.73),"Asia/Vladivostok":(43.12,131.89),"Asia/Sakhalin":(46.96,142.73),
 "Asia/Magadan":(59.56,150.80),"Asia/Kamchatka":(53.02,158.65),"Europe/Minsk":(53.90,27.56),"Europe/Kiev":(50.45,30.52),
 "Europe/Kyiv":(50.45,30.52),"Europe/Chisinau":(47.01,28.86),"Europe/Riga":(56.95,24.11),"Europe/Vilnius":(54.69,25.28),
 "Europe/Tallinn":(59.44,24.75),"Europe/Helsinki":(60.17,24.94),"Europe/Stockholm":(59.33,18.07),"Europe/Oslo":(59.91,10.75),
 "Europe/Copenhagen":(55.68,12.57),"Europe/Berlin":(52.52,13.40),"Europe/Warsaw":(52.23,21.01),"Europe/Prague":(50.08,14.44),
 "Europe/Vienna":(48.21,16.37),"Europe/Budapest":(47.50,19.04),"Europe/Bratislava":(48.15,17.11),"Europe/Zurich":(47.38,8.54),
 "Europe/Amsterdam":(52.37,4.90),"Europe/Brussels":(50.85,4.35),"Europe/Paris":(48.86,2.35),"Europe/London":(51.51,-0.13),
 "Europe/Dublin":(53.35,-6.26),"Europe/Madrid":(40.42,-3.70),"Europe/Lisbon":(38.72,-9.14),"Europe/Rome":(41.90,12.50),
 "Europe/Athens":(37.98,23.73),"Europe/Sofia":(42.70,23.32),"Europe/Bucharest":(44.43,26.10),"Europe/Belgrade":(44.79,20.45),
 "Europe/Istanbul":(41.01,28.98),"Asia/Tbilisi":(41.72,44.79),"Asia/Yerevan":(40.18,44.51),"Asia/Baku":(40.41,49.87),
 "Asia/Almaty":(43.24,76.95),"Asia/Qostanay":(53.21,63.62),"Asia/Aqtobe":(50.28,57.17),"Asia/Aqtau":(43.65,51.17),
 "Asia/Oral":(51.23,51.37),"Asia/Atyrau":(47.11,51.92),"Asia/Qyzylorda":(44.85,65.51),"Asia/Bishkek":(42.87,74.59),
 "Asia/Tashkent":(41.30,69.24),"Asia/Samarkand":(39.65,66.96),"Asia/Dushanbe":(38.56,68.79),"Asia/Ashgabat":(37.96,58.33),
 "Asia/Dubai":(25.20,55.27),"Asia/Tehran":(35.69,51.39),"Asia/Jerusalem":(31.77,35.21),"Asia/Tel_Aviv":(32.09,34.78),
 "Asia/Shanghai":(31.23,121.47),"Asia/Hong_Kong":(22.32,114.17),"Asia/Tokyo":(35.68,139.69),"Asia/Seoul":(37.57,126.98),
 "Asia/Singapore":(1.35,103.82),"Asia/Bangkok":(13.76,100.50),"Asia/Kolkata":(19.08,72.88),"Asia/Ho_Chi_Minh":(10.82,106.63),
 "America/New_York":(40.71,-74.01),"America/Chicago":(41.88,-87.63),"America/Denver":(39.74,-104.99),
 "America/Los_Angeles":(34.05,-118.24),"America/Toronto":(43.65,-79.38),"America/Sao_Paulo":(-23.55,-46.63),
 "Australia/Sydney":(-33.87,151.21),
}

bad = [k for k, (la, lo) in countries.items() if not any(on_land(lo + dx, la + dy) for dx in (-1, 0, 1) for dy in (-1, 0, 1))]
print("dots", count, "countries", len(countries), "timezones", len(timezones), "off-land:", bad)

data = {
  "version": 1,
  "source": "Natural Earth 1:110m land (public domain) via world-atlas@2.0.2",
  "grid": {"step": STEP, "lonWest": LON0, "latNorth": LAT_TOP, "cols": cols, "rows": rows,
           "encoding": "rows top->bottom; each row is hex, MSB-first, bit=1 means land at cell centre (lonWest+(c+0.5)*step, latNorth-(r+0.5)*step)"},
  "land": hex_rows,
  "countries": {k: [v[0], v[1]] for k, v in sorted(countries.items())},
  "timezones": {k: [v[0], v[1]] for k, v in sorted(timezones.items())},
}
json.dump(data, open(out, "w", encoding="utf-8"), separators=(",", ":"), ensure_ascii=False)
