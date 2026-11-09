#!/usr/bin/env python3
"""Generates app/.../ui/LandmarkPaths.kt: one landmark silhouette per country, plus the national
symbols that drift across the screen. Landmarks are tourist sights only: no place built for worship,
active or ancient. Everything is built from simple geometry, so these are
original simplified drawings and the app ships no images.

    pip install shapely pillow
    python tools/brand/make_landmarks.py [preview.png]

Landmark coordinates: x runs 0..100 around a centre of 50, y runs upwards from the ground at 0.
Symbol coordinates: about -0.5..0.5 around the origin, y upwards.
"""
import math
import os
import sys

from shapely import affinity
from shapely.geometry import MultiPolygon, Point, Polygon, box
from shapely.ops import unary_union

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "app", "src", "main",
                   "java", "com", "voltavpn", "app", "ui", "LandmarkPaths.kt")


# ---------- drawing vocabulary ----------
def rect(x0, y0, x1, y1): return box(x0, y0, x1, y1)
def bar(cx, y0, w, h): return box(cx - w / 2, y0, cx + w / 2, y0 + h)
def poly(*pts): return Polygon(pts).buffer(0)
def circle(cx, cy, r): return Point(cx, cy).buffer(r, 24)
def ellipse(cx, cy, rx, ry): return affinity.scale(Point(cx, cy).buffer(1, 32), rx, ry, origin=(cx, cy))
def union(*g): return unary_union(g)
def tri(cx, y0, w, h): return poly((cx - w / 2, y0), (cx + w / 2, y0), (cx, y0 + h))
def mirror(g, cx=50): return union(g, affinity.scale(g, -1, 1, origin=(cx, 0)))
def rot(g, deg, origin): return affinity.rotate(g, deg, origin=origin)
def trapezoid(cx, y0, w0, w1, h): return poly((cx - w0 / 2, y0), (cx + w0 / 2, y0), (cx + w1 / 2, y0 + h), (cx - w1 / 2, y0 + h))
def dome(cx, y0, w, h): return ellipse(cx, y0, w / 2, h).intersection(rect(cx - w, y0, cx + w, y0 + h + 1))


def onion(cx, y0, w, h):
    """An onion dome with a pointed tip."""
    r = w / 2
    pts = [(cx - r * 0.72, y0)]
    for t in [i / 16 for i in range(17)]:
        a = t * math.pi
        bulge = math.sin(a) ** 0.72 * (1 - t * 0.28)
        pts.append((cx - r * bulge, y0 + h * 0.72 * t))
    pts.append((cx, y0 + h))
    left = pts
    right = [(2 * cx - x, y) for x, y in reversed(left)]
    return poly(*left, *right)


def arch(cx, y0, w, h):
    """The opening of an arched door or window."""
    return union(bar(cx, y0, w, h - w / 2), circle(cx, y0 + h - w / 2, w / 2))


def tier_roof(cx, y0, w, h, flare=4):
    """A curved East Asian roof with lifted eaves."""
    return poly((cx - w / 2 - flare, y0 + h * 0.34), (cx - w / 2, y0), (cx + w / 2, y0), (cx + w / 2 + flare, y0 + h * 0.34),
                (cx + w * 0.30, y0 + h * 0.48), (cx + w * 0.10, y0 + h), (cx - w * 0.10, y0 + h), (cx - w * 0.30, y0 + h * 0.48))


def battlements(x0, x1, y, h, n):
    w = (x1 - x0) / (n * 2 - 1)
    return union(*[bar(x0 + w / 2 + i * 2 * w, y, w, h) for i in range(n)])


def arcade(x0, x1, y, w, h, n):
    step = (x1 - x0) / n
    return union(*[arch(x0 + step * (i + .5), y, w, h) for i in range(n)])


def columns(x0, x1, y, w, h, n):
    step = (x1 - x0 - w) / (n - 1)
    return union(*[bar(x0 + w / 2 + i * step, y, w, h) for i in range(n)])


def mountain(*pts):
    return poly((pts[0][0], 0), *pts, (pts[-1][0], 0))


def palm(cx, y0, h, s=1.0):
    trunk = poly((cx - 1.2 * s, y0), (cx + 1.2 * s, y0), (cx + 2.2 * s, y0 + h), (cx + 1.2 * s, y0 + h))
    head = (cx + 1.8 * s, y0 + h)
    fronds = [rot(ellipse(head[0] + 7 * s, head[1], 7.5 * s, 1.5 * s), a, head) for a in (-35, 5, 40, 80, 125, 165, 215)]
    return union(trunk, *fronds)


def cedar(cx, y0, h, w):
    layers = [poly((cx - w * s / 2, y0 + h * b), (cx + w * s / 2, y0 + h * b), (cx + w * s * .22, y0 + h * (b + .16)), (cx - w * s * .22, y0 + h * (b + .16)))
              for s, b in ((1, .16), (.86, .34), (.66, .52), (.42, .70), (.2, .84))]
    return union(bar(cx, y0, h * .07, h * .9), *layers)


# ---------- landmarks ----------
def eiffel():
    prof = [(0, 26), (8, 21), (18, 15), (30, 10), (45, 7), (65, 4), (95, 2), (120, 1), (134, .15)]
    body = poly(*[(50 - w, y) for y, w in prof], *[(50 + w, y) for y, w in reversed(prof)])
    return union(body, bar(50, 19, 34, 2.4), bar(50, 44, 19, 2), bar(50, 96, 7, 1.6)).difference(
        union(ellipse(50, 0, 13.5, 16), trapezoid(50, 25, 11, 7.4, 17), trapezoid(50, 49, 6, 3.6, 40)))


def cn_tower():
    return union(trapezoid(50, 0, 13, 4.2, 92), mirror(poly((50, 0), (60, 0), (53.5, 60))), ellipse(50, 96, 11, 5.5),
                 bar(50, 100, 7, 7), bar(50, 107, 3, 7), tri(50, 114, 3, 46))


def liberty():
    base = union(trapezoid(50, 0, 40, 28, 16), trapezoid(50, 16, 18, 13, 26), bar(50, 42, 17, 3))
    robe = poly((42, 45), (58, 45), (56.5, 62), (54.5, 78), (53, 85), (46.8, 85), (45, 76), (43.2, 60))
    arm = poly((52, 80), (55, 83), (60.5, 104), (57.8, 105))
    torch = union(trapezoid(59.6, 104, 2.2, 5.5, 3), ellipse(59.8, 110, 1.9, 3.4))
    book = rot(rect(38.5, 66, 44, 74), 12, (41, 70))
    rays = [rot(tri(49.8, 93.5, 1.5, 6), a, (49.8, 91)) for a in (-54, -27, 0, 27, 54)]
    return union(base, robe, arm, torch, book, circle(49.8, 89.5, 4), *rays)


def big_ben():
    body = union(bar(50, 0, 20, 82), bar(50, 82, 24, 16), trapezoid(50, 98, 21, 15, 6), bar(50, 104, 13, 7), tri(50, 111, 13, 34),
                 bar(39, 98, 2, 8), bar(61, 98, 2, 8), tri(39, 106, 2, 6), tri(61, 106, 2, 6))
    wing = union(rect(4, 0, 40, 24), rect(60, 0, 96, 24), battlements(4, 40, 24, 3, 8), battlements(60, 96, 24, 3, 8))
    return union(body, wing).difference(union(circle(50, 90, 8.6).difference(union(circle(50, 90, 7.2), bar(50, 89.5, 1, 7), rect(50, 89.5, 55, 90.5))),
                                               arcade(6, 38, 5, 3, 12, 5), arcade(62, 94, 5, 3, 12, 5)))


def brandenburg():
    deck = union(rect(6, 42, 94, 50), rect(10, 50, 90, 55), trapezoid(50, 55, 34, 24, 6))
    quad = union(bar(50, 61, 20, 2), *[poly((x, 63), (x + 2.6, 63), (x + 4, 71), (x + 1, 71)) for x in (40, 45, 50, 55)], poly((49, 71), (51, 71), (50.6, 83), (49.4, 83)))
    return union(deck, quad, columns(8, 92, 3, 5.5, 39, 6), bar(50, 0, 92, 3))


def windmill():
    tower = union(trapezoid(50, 0, 34, 20, 58), bar(50, 22, 42, 2.4), dome(50, 58, 22, 10))
    sail = union(bar(50, 62, 1.8, 54), rect(50.9, 76, 60, 114).difference(union(*[rect(52.2, y, 58.8, y + 4) for y in range(78, 112, 7)], )))
    sails = union(*[rot(sail, a, (50, 62)) for a in (35, 125, 215, 305)])
    return union(tower, sails).difference(union(arch(50, 0, 10, 15), arch(50, 34, 6, 10)))




def namdaemun():
    base = trapezoid(50, 0, 74, 66, 24).difference(arch(50, 0, 14, 19))
    return union(base, bar(50, 24, 44, 8), tier_roof(50, 32, 54, 9, 8), bar(50, 41, 28, 6), tier_roof(50, 47, 40, 12, 8))


def great_wall():
    top = [(0, 18), (12, 22), (24, 32), (36, 42), (50, 44), (64, 40), (76, 30), (88, 26), (100, 28)]
    wall = poly((0, 0), *top, (100, 0))
    cren = union(*[poly((x, y), (x + 2, y), (x + 2, y + 3.5), (x, y + 3.5)) for x, y in [(4, 19), (10, 21), (17, 25), (23, 30.5), (30, 36), (64, 39), (70, 34), (77, 28.5), (84, 26), (91, 25.8)]])
    tower = union(bar(46, 40, 20, 18), battlements(36, 56, 58, 3.5, 5)).difference(union(arch(41, 44, 3.4, 8), arch(46, 44, 3.4, 8), arch(51, 44, 3.4, 8)))
    tower2 = union(bar(16, 20, 11, 14), battlements(10.5, 21.5, 34, 2.6, 3)).difference(arch(16, 24, 3, 6))
    return union(wall, cren, tower, tower2)


def taipei_101():
    parts = [trapezoid(50, 0, 26, 19, 26), bar(50, 26, 19, 2)]
    for i in range(8):
        parts.append(trapezoid(50, 28 + i * 9.4, 15, 21, 9.4))
    parts += [trapezoid(50, 103, 15, 9, 7), bar(50, 110, 6, 5), tri(50, 115, 3.5, 24)]
    return union(*parts)


def hk_skyline():
    boc = union(poly((42, 0), (58, 0), (58, 78), (50, 112), (42, 96)), bar(50, 112, .8, 14)).difference(union(poly((43, 26), (57, 12), (57, 15), (43, 29)), poly((43, 52), (57, 38), (57, 41), (43, 55)), poly((43, 78), (57, 64), (57, 67), (43, 81))))
    return union(mountain((0, 26), (14, 46), (30, 38), (46, 60), (66, 46), (82, 56), (100, 28)), boc, rect(62, 0, 72, 86), rect(28, 0, 38, 72), rect(16, 0, 25, 52), rect(76, 0, 86, 64), rect(6, 0, 13, 36), rect(90, 0, 98, 42), tri(67, 86, 10, 8))


def marina_bay():
    towers = [poly((x, 0), (x + 16, 0), (x + 14, 58), (x + 2, 58)) for x in (8, 42, 76)]
    boat = poly((0, 62), (98, 62), (106, 66), (104, 68), (50, 69), (2, 67))
    return union(*towers, boat, bar(16, 58, 8, 4), bar(50, 58, 8, 4), bar(84, 58, 8, 4)).difference(union(rect(15.5, 22, 16.5, 58), rect(49.5, 22, 50.5, 58), rect(83.5, 22, 84.5, 58)))


def petronas():
    def tower(cx):
        return union(trapezoid(cx, 0, 21, 19, 54), trapezoid(cx, 54, 19, 16, 22), trapezoid(cx, 76, 16, 12, 14), trapezoid(cx, 90, 12, 8, 10), trapezoid(cx, 100, 8, 4.5, 8), tri(cx, 108, 4.5, 28))
    bridge = union(rect(35, 52, 65, 56), poly((50, 52), (47, 48), (39, 30), (41, 30), (50, 50)), poly((50, 52), (53, 48), (61, 30), (59, 30), (50, 50)))
    return union(tower(25), tower(75), bridge)








def mayon():
    return union(mountain((-6, 2), (14, 16), (32, 40), (44, 64), (50, 72), (56, 64), (68, 40), (86, 16), (106, 2)),
                 ellipse(58, 79, 8, 3.4), ellipse(68, 85, 8, 3), palm(10, 0, 30), palm(92, 0, 25, .9))


def taj_mahal():
    plinth = bar(50, 0, 100, 6)
    hall = bar(50, 6, 62, 30).difference(union(arch(50, 6, 15, 25), arch(30, 8, 6, 10), arch(30, 21, 6, 10), arch(70, 8, 6, 10), arch(70, 21, 6, 10)))
    main = union(bar(50, 36, 22, 5), onion(50, 41, 31, 37), bar(50, 78, .8, 8), circle(50, 83, 1))
    side = [union(bar(x, 36, 9, 7), dome(x, 43, 9, 6), bar(x, 49, .6, 3)) for x in (28, 72)]
    minaret = [union(trapezoid(x, 6, 5.4, 4.4, 50), bar(x, 56, 6.6, 1.6), bar(x, 57.6, 5, 5), dome(x, 62.6, 6, 4)) for x in (6, 94)]
    return union(plinth, hall, main, *side, *minaret)


def minar_pakistan():
    petals = [rot(ellipse(50, 8, 5, 11), a, (50, 0)) for a in (-75, -50, -25, 0, 25, 50, 75)]
    return union(*petals, bar(50, 0, 70, 3), bar(50, 3, 54, 4), trapezoid(50, 7, 20, 11, 34), bar(50, 41, 14, 2), trapezoid(50, 43, 10, 6.6, 30), bar(50, 73, 10, 1.6), trapezoid(50, 75, 6, 4, 16), bar(50, 91, 7, 1.4), dome(50, 92.4, 5, 7), tri(50, 99, 1, 9))


def smriti_soudho():
    parts = []
    for i, (w, h) in enumerate(((94, 40), (78, 58), (62, 74), (46, 88), (30, 99), (17, 108), (7, 114))):
        outer = tri(50, 0, w, h)
        parts.append(outer.difference(tri(50, 0, w - 8, h - 7)) if i < 6 else outer)
    return union(*parts, bar(50, 0, 100, 2))


def bayterek():
    trunk = union(poly((48, 0), (52, 0), (51, 92), (49, 92)), *[poly((50 - s * 2, 18 + i), (50 - s * 1, 18 + i), (50 + s * (15 - i * .07), 88), (50 + s * (14 - i * .07), 88)) for s in (-1, 1) for i in (0,)],
                   *[poly((50 + s * 15, 88), (50 + s * 13.5, 88), (50 + s * 2, 52), (50 + s * 3, 52)) for s in (-1, 1)])
    ball = circle(50, 101, 12)
    return union(trunk, ball, bar(50, 0, 26, 3), bar(50, 3, 14, 3), poly((50, 88), (38, 92), (62, 92)))


def opera_house():
    def bez(a, c, b, n=14):
        return [((1 - t) ** 2 * a[0] + 2 * (1 - t) * t * c[0] + t ** 2 * b[0], (1 - t) ** 2 * a[1] + 2 * (1 - t) * t * c[1] + t ** 2 * b[1]) for t in [i / n for i in range(n + 1)]]

    def shell(x, w, h):
        tip = (x + w * .2, h)
        return poly(*bez((x, 0), (x - w * .1, h * .62), tip), *bez(tip, (x + w * .86, h * .8), (x + w, 0))[1:])
    group = [shell(4, 26, 34), shell(15, 26, 48), shell(28, 26, 58), shell(55, 22, 30), shell(64, 22, 42), shell(74, 22, 52)]
    cut = [g.difference(group[i + 1].buffer(.9)) if i not in (2, 5) else g for i, g in enumerate(group)]
    lift = [affinity.translate(g, 0, 11) for g in cut]
    return union(trapezoid(50, 0, 108, 98, 8), bar(50, 8, 90, 3), *lift)


def sky_tower():
    return union(trapezoid(50, 0, 18, 8, 12), trapezoid(50, 12, 9, 6, 66), mirror(poly((50, 0), (58, 0), (53, 32))), bar(50, 78, 11, 3), trapezoid(50, 81, 15, 17, 7), trapezoid(50, 88, 17, 13, 6), trapezoid(50, 94, 8, 4, 5), tri(50, 99, 3.4, 42))








def obelisco():
    return union(bar(50, 0, 54, 2), bar(50, 2, 26, 4), trapezoid(50, 6, 13, 7.4, 92), tri(50, 98, 7.4, 8))


def machu_picchu():
    peak = mountain((36, 20), (46, 44), (54, 76), (60, 88), (67, 88), (74, 70), (82, 50), (92, 32), (106, 18))
    terr = union(*[rect(-6 + i * 4.6, 0, 62 - i * .8, 7 + i * 4.4) for i in range(7)])
    huts = union(*[union(rect(x, y, x + 5, y + 3), tri(x + 2.5, y + 3, 7, 3.5)) for x, y in ((6, 34), (14, 34), (34, 36), (24, 36))])
    return union(peak, terr, huts)


def angel_falls():
    tepui = poly((-6, 0), (6, 26), (10, 72), (14, 82), (50, 86), (88, 82), (92, 70), (96, 28), (106, 0))
    fall = poly((44.5, 84), (47.5, 84), (49, 22), (43, 22))
    mist = union(ellipse(46, 18, 12, 5))
    return tepui.difference(union(fall, mist))




def moai():
    profile = [(0, 0), (.82, 0), (.86, .30), (.72, .36), (.86, .44), (.82, .52), (.98, .62), (.82, .70), (.84, .86), (.76, 1), (.2, 1), (.06, .8), (0, .4)]

    def head(x, h, w):
        return poly(*[(x + px * w, 5 + py * h) for px, py in profile])
    return union(bar(50, 0, 106, 5), head(0, 40, 26), head(36, 58, 32), head(76, 46, 28))


def clock_tower():
    gate = union(rect(14, 0, 86, 38), battlements(14, 86, 38, 3, 9))
    tower = union(bar(50, 38, 24, 22), bar(50, 60, 20, 12), dome(50, 72, 18, 12), bar(50, 84, 5, 6), dome(50, 90, 5, 4), bar(50, 94, .9, 5))
    return union(gate, tower).difference(union(arch(50, 0, 16, 28), arch(30, 0, 10, 22), arch(70, 0, 10, 22), circle(50, 50, 6), arch(50, 62, 6, 8)))




def colosseum():
    shell = poly((-2, 0), (102, 0), (103, 24), (100, 32), (92, 36), (72, 44), (60, 58), (30, 60), (6, 60), (-2, 56))
    holes = []
    for y, top in ((3, 58), (19, 58), (35, 54)):
        for i in range(12):
            x = 3 + i * 8.2
            if y == 35 and x > 56: continue
            if y == 19 and x > 80: continue
            holes.append(arch(x, y, 4.8, 12))
    return union(shell.difference(union(*holes)), bar(50, 0, 106, 2))


def palace_culture():
    tiers = [bar(50, 0, 96, 22), bar(50, 22, 66, 14), bar(50, 36, 46, 18), bar(50, 54, 34, 20), bar(50, 74, 26, 18), bar(50, 92, 18, 12), trapezoid(50, 104, 13, 6, 12), tri(50, 116, 6, 36)]
    finials = [tri(x, y, 4, 9) for x, y in ((3, 22), (97, 22), (19, 36), (81, 36), (29, 54), (71, 54), (35, 74), (65, 74))]
    return union(*tiers, *finials).difference(union(arcade(6, 94, 3, 3.4, 12, 11), arcade(22, 78, 58, 2.4, 10, 7)))


def bran_castle():
    rock = mountain((-4, 0), (6, 16), (22, 26), (44, 30), (70, 28), (88, 18), (104, 0))
    def t(cx, y, w, h, roof): return union(bar(cx, y, w, h), tri(cx, y + h, w + 3, roof))
    return union(rock, rect(26, 26, 78, 56), t(24, 24, 14, 50, 20), t(52, 40, 11, 34, 16), t(76, 22, 16, 42, 24), t(40, 50, 9, 18, 13), tri(58, 56, 30, 12)).difference(union(arch(24, 56, 3.4, 8), arch(76, 42, 3.8, 8), arch(52, 56, 3, 7), arch(40, 42, 3, 6)))


def budapest_parliament():
    body = rect(0, 0, 100, 30)
    centre = union(bar(50, 30, 26, 14), onion(50, 44, 25, 28), tri(50, 72, 3.4, 34))
    spires = [union(bar(x, 28, 6, 19), tri(x, 47, 7, 24)) for x in (36, 64)]
    wings = [tri(x, 30, 10, 13) for x in (8, 20, 80, 92)] + [union(bar(x, 28, 3, 12), tri(x, 40, 3.6, 11)) for x in (2, 14, 26, 74, 86, 98)]
    return union(body, centre, *spires, *wings).difference(arcade(4, 96, 4, 3.4, 15, 13))


def riesenrad():
    wheel = circle(50, 56, 47).difference(circle(50, 56, 44))
    spokes = [rot(bar(50, 12, 1, 88), a, (50, 56)) for a in range(0, 180, 30)]
    rim = circle(50, 56, 33).difference(circle(50, 56, 31.5))
    cabins = [affinity.translate(rect(46, 7, 54, 15), 47 * math.sin(math.radians(a)), 47 - 47 * math.cos(math.radians(a))) for a in range(0, 360, 30)]
    legs = mirror(poly((50, 58), (47, 58), (20, 0), (26, 0)))
    return union(wheel, *spokes, rim, *cabins, legs, circle(50, 56, 3.4), bar(50, 0, 62, 2.4))


def matterhorn():
    peak = mountain((-8, 6), (10, 16), (24, 28), (32, 44), (37, 50), (42, 70), (47, 84), (52, 96), (55, 88), (60, 76), (66, 54), (74, 46), (84, 26), (108, 8))
    return peak.union(bar(50, 0, 116, 2))


def stockholm_hall():
    body = rect(0, 0, 100, 34)
    tower = union(bar(66, 0, 18, 82), trapezoid(66, 82, 17, 12, 8), bar(66, 90, 8, 5), dome(66, 95, 8, 5), bar(66, 100, .8, 6))
    crowns = [union(circle(x, y, 1.5), tri(x, y + 1, 2.2, 3)) for x, y in ((64, 108), (68, 108), (66, 111))]
    return union(body, tower, *crowns, battlements(0, 54, 34, 2, 11), tri(32, 34, 30, 7)).difference(union(arcade(4, 52, 3, 4, 16, 6), arcade(78, 98, 3, 4, 16, 2), arch(66, 3, 6, 15), arch(66, 60, 3, 13)))






def nyhavn():
    def house(x, w, h, kind):
        roof = tri(x + w / 2, h, w, w * .7) if kind == 0 else union(*[bar(x + w / 2, h + i * 2.6, w - i * 3.2, 2.6) for i in range(int(w / 3.3))])
        windows = union(*[bar(x + w * c, h - f, 1.9, 3.2) for c in (.3, .7) for f in (7, 15, 23) if h - f > 2])
        return union(rect(x, 0, x + w, h), roof).difference(windows)
    row = union(house(0, 15, 34, 1), house(15, 13, 42, 0), house(28, 16, 30, 1), house(44, 13, 46, 0), house(57, 15, 36, 1), house(72, 13, 28, 0), house(85, 15, 40, 1))
    mast = union(bar(30, 10, 1.1, 62), poly((31, 68), (41, 58), (31, 58)), poly((29, 60), (19, 50), (29, 50)), bar(30, 30, 18, .7))
    return union(row, mast, bar(50, 0, 104, 3))




def atomium():
    nodes = [(50, 8), (22, 34), (50, 34), (78, 34), (50, 58), (22, 80), (50, 80), (78, 80), (50, 108)]
    links = [(0, 1), (0, 2), (0, 3), (1, 4), (2, 4), (3, 4), (1, 5), (3, 7), (4, 5), (4, 6), (4, 7), (5, 8), (6, 8), (7, 8), (2, 6)]
    tubes = []
    for a, b in links:
        (x0, y0), (x1, y1) = nodes[a], nodes[b]
        n = math.hypot(x1 - x0, y1 - y0)
        nx, ny = -(y1 - y0) / n * 1.2, (x1 - x0) / n * 1.2
        tubes.append(poly((x0 + nx, y0 + ny), (x1 + nx, y1 + ny), (x1 - nx, y1 - ny), (x0 - nx, y0 - ny)))
    return union(*tubes, *[circle(x, y, 8.4) for x, y in nodes], trapezoid(50, 0, 44, 30, 3), poly((42, 3), (58, 3), (54, 10), (46, 10)))


def charles_bridge():
    bridge = rect(-4, 15, 104, 22).union(rect(-4, 0, 104, 15).difference(arcade(-4, 72, 0, 11, 13, 5)))
    tower = union(bar(82, 0, 22, 66), poly((71, 66), (93, 66), (82, 96)), *[tri(x, 66, 3, 8) for x in (71, 93)], *[tri(x, 72, 2, 6) for x in (76, 88)])
    lamps = [union(bar(x, 22, .8, 6), circle(x, 28.5, 1)) for x in (4, 16, 28, 40, 52, 64)]
    return union(bridge, tower, *lamps).difference(union(arch(82, 0, 11, 20), arch(82, 40, 5, 13)))


def dubrovnik():
    wall = rect(-4, 0, 104, 24)
    fort = union(trapezoid(26, 0, 36, 28, 44), battlements(12, 40, 44, 5, 7), bar(26, 44, 16, 14), battlements(18, 34, 58, 4, 4))
    bell = union(bar(72, 24, 9, 30), trapezoid(72, 54, 8, 2, 12), dome(72, 65, 2.4, 2))
    houses = [union(rect(x, 24, x + w, 24 + h), tri(x + w / 2, 24 + h, w + 2, 6)) for x, w, h in ((46, 11, 12), (58, 8, 16), (82, 11, 14), (94, 9, 9))]
    return union(wall, battlements(-4, 104, 24, 3, 22), fort, bell, *houses).difference(union(arch(26, 8, 6, 12), arch(72, 40, 3.4, 9)))


def galata():
    return union(trapezoid(50, 0, 34, 28, 58), bar(50, 58, 30, 3), bar(50, 61, 26, 12), bar(50, 73, 31, 2.5), tri(50, 75.5, 30, 38), rect(4, 0, 96, 12), tri(14, 12, 18, 7), tri(86, 12, 18, 7)).difference(union(arcade(38, 62, 62, 3, 9, 5), arch(50, 4, 7, 14), arch(44, 34, 2.4, 6), arch(56, 34, 2.4, 6)))


def burj_khalifa():
    prof = [(0, 13), (20, 13), (20, 11), (40, 11), (40, 9), (60, 9), (60, 7.2), (78, 7.2), (78, 5.6), (96, 5.6), (96, 4.2), (112, 4.2), (112, 3), (126, 3), (126, 1.8), (140, 1.8), (140, .9), (152, .9), (172, .05)]
    return union(poly(*[(50 - x, y) for y, x in prof], *[(50 + x, y) for y, x in reversed(prof)]), rect(16, 0, 84, 7), mirror(poly((50, 0), (70, 0), (58, 16))))


def kingdom_centre():
    tower = trapezoid(50, 0, 44, 42, 106)
    cut = poly((50 - 19, 106.1), (50 + 19, 106.1), (50 + 16, 88), (50 + 8, 72), (50, 66), (50 - 8, 72), (50 - 16, 88))
    bridge = rect(29, 84, 71, 88)
    base = rect(0, 0, 100, 10)
    return union(tower.difference(cut), bridge, base)


def pyramids():
    return union(tri(38, 0, 74, 62), tri(72, 0, 46, 38), tri(96, 0, 20, 17), bar(50, 0, 128, 1.5),
                 poly((-14, 1), (-1, 1), (-1, 8), (-5, 8), (-6, 13), (-9, 13), (-11, 9), (-14, 8)))


def table_mountain():
    return union(mountain((-10, 10), (6, 24), (16, 44), (22, 50), (24, 64), (30, 66), (72, 66), (78, 62), (84, 42), (94, 34), (100, 42), (106, 30), (116, 14)),
                 poly((-12, 0), (120, 0), (120, 7), (-12, 7)))


def aqueduct():
    tiers = rect(-6, 0, 106, 34).difference(arcade(-6, 106, 0, 11, 26, 7))
    return union(tiers, bar(50, 34, 112, 4), bar(50, 0, 118, 2), poly((-6, 38), (8, 38), (2, 46)), poly((68, 38), (106, 38), (92, 44), (80, 42)))


def belem():
    base = union(rect(-4, 0, 74, 26), battlements(-4, 74, 26, 3, 13), *[union(bar(x, 26, 5, 7), dome(x, 33, 5, 4)) for x in (0, 26, 50, 70)])
    tower = union(bar(58, 0, 34, 66), battlements(41, 75, 66, 4, 7), bar(58, 66, 24, 18), battlements(46, 70, 84, 3, 5), *[union(bar(x, 56, 4.6, 12), dome(x, 68, 4.6, 5)) for x in (41, 75)], *[union(bar(x, 84, 3.6, 5), dome(x, 89, 3.6, 4)) for x in (46, 70)])
    return union(base, tower, bar(50, 0, 108, 2)).difference(union(arch(58, 34, 6, 14), arch(58, 66, 7, 12), arcade(0, 34, 6, 4, 13, 4)))








def gediminas():
    hill = mountain((-10, 3), (10, 14), (30, 34), (46, 42), (70, 42), (84, 30), (108, 5))
    tower = union(bar(58, 42, 24, 22), bar(58, 64, 20, 20), battlements(48, 68, 84, 3, 5), bar(58, 84, .9, 18), poly((58.4, 102), (70, 99), (58.4, 95)))
    trees = [ellipse(x, y, 7, 7) for x, y in ((14, 16), (24, 26), (86, 26), (96, 15), (34, 34))]
    return union(hill, tower, *trees).difference(union(arch(58, 44, 5, 11), arch(52, 68, 2.6, 7), arch(64, 68, 2.6, 7)))


def freedom_monument():
    base = union(bar(50, 0, 56, 3), bar(50, 3, 44, 3), bar(50, 6, 30, 22))
    shaft = trapezoid(50, 28, 15, 10, 66)
    figure = union(poly((44, 94), (56, 94), (54, 112), (46, 112)), circle(50, 115, 3), poly((46, 110), (49, 113), (39, 136), (37, 134)), poly((54, 110), (51, 113), (61, 136), (63, 134)))
    stars = [circle(x, y, 2.1) for x, y in ((37, 138), (50, 141), (63, 138))]
    return union(base, shaft, figure, *stars, poly((50, 124), (51, 138), (49, 138))).difference(arch(50, 8, 6, 15))




def chisinau_arch():
    base = union(bar(50, 0, 76, 3), columns(16, 84, 3, 5.4, 38, 6), bar(50, 41, 76, 5))
    upper = union(bar(50, 46, 44, 13), bar(50, 59, 34, 2), dome(50, 61, 26, 12), bar(50, 73, .9, 7))
    return union(base, upper)






def mia_doha():
    tier = union(rect(16, 0, 84, 26), rect(28, 26, 72, 44), rect(36, 44, 64, 58), poly((36, 58), (64, 58), (58, 66), (42, 66)), dome(50, 66, 14, 9))
    water = bar(50, 0, 110, 2)
    return union(water, tier, rect(-4, 0, 14, 7), rect(86, 0, 104, 7)).difference(union(arch(50, 0, 13, 22), arch(50, 58, 2.6, 6), arcade(30, 70, 30, 3, 9, 5), arcade(18, 34, 6, 3, 12, 2), arcade(66, 82, 6, 3, 12, 2)))


def kuwait_towers():
    def t(cx, h, ball, by):
        return union(trapezoid(cx, 0, 7, 2, h), circle(cx, by, ball))
    big = union(t(36, 118, 12, 76), circle(36, 100, 7.2))
    mid = t(68, 96, 11, 62)
    small = tri(88, 0, 3.8, 60)
    return union(big, mid, small, bar(50, 0, 104, 2))


def bahrain_wtc():
    def sail(sign):
        pts = [(50 + sign * 8, 0), (50 + sign * 40, 0), (50 + sign * 34, 40), (50 + sign * 22, 84), (50 + sign * 6, 116), (50 + sign * 14, 70)]
        return poly(*pts)
    bridges = [rect(40, y, 60, y + 3) for y in (28, 52, 76)]
    rotors = [circle(50, y + 1.5, 3.4).union(bar(50, y - 10, 1, 23)) for y in (28, 52, 76)]
    return union(sail(-1), sail(1), *bridges, *rotors, bar(50, 0, 96, 3))


def nizwa_fort():
    drum = union(trapezoid(46, 0, 66, 56, 52), battlements(18, 74, 52, 4, 9))
    wall = union(rect(-4, 0, 104, 20), battlements(70, 104, 20, 3, 6))
    return union(drum, wall, palm(92, 18, 30, 1), palm(4, 18, 24, .9)).difference(union(arch(46, 2, 9, 14), arch(30, 30, 2.8, 8), arch(46, 32, 2.8, 8), arch(62, 30, 2.8, 8)))


def petra():
    cliff = rect(-4, 0, 104, 106)
    facade_cut = union(
        arch(50, 0, 13, 22),
        *[bar(x, 2, 3, 40) for x in (12, 28, 72, 88)],
        rect(5, 46, 95, 47.6),
        poly((10, 52), (90, 52), (50, 62)).difference(poly((15, 52.8), (85, 52.8), (50, 60.6))),
        bar(30, 66, 2, 28), bar(70, 66, 2, 28), bar(40, 66, 2, 26), bar(60, 66, 2, 26),
        union(poly((24, 94), (36, 94), (30, 101)), poly((64, 94), (76, 94), (70, 101))).difference(union(poly((26.5, 94.8), (33.5, 94.8), (30, 98.5)), poly((66.5, 94.8), (73.5, 94.8), (70, 98.5)))),
        tri(50, 92, 14, 8),
    )
    return cliff.difference(facade_cut)






def olive_tree():
    trunk = poly((36, 0), (64, 0), (58, 10), (60, 22), (56, 36), (62, 48), (54, 44), (50, 54), (45, 44), (36, 50), (43, 34), (38, 20), (42, 9))
    knot = ellipse(50, 16, 3.4, 5)
    crown = union(*[ellipse(x, y, rx, ry) for x, y, rx, ry in ((50, 66, 30, 16), (24, 56, 19, 12), (76, 56, 19, 12), (36, 76, 17, 10), (66, 76, 17, 10), (50, 84, 14, 8), (10, 48, 9, 6), (90, 48, 9, 6))])
    hills = union(mountain((-10, 3), (14, 10), (40, 4)), mountain((60, 4), (88, 11), (110, 3)))
    return union(trunk.difference(knot), crown, hills, bar(50, 0, 118, 2))


def shibam():
    data = [(-2, 11, 38), (9, 12, 62), (21, 10, 46), (31, 13, 76), (44, 11, 58), (55, 13, 84), (68, 11, 52), (79, 12, 68), (91, 11, 42)]
    towers = [trapezoid(x + w / 2, 0, w, w - 1.4, h) for x, w, h in data]
    holes = [bar(x + w * c, y, 1.3, 1.9) for x, w, h in data for c in (.28, .72) for y in range(7, h - 3, 7)]
    return union(*towers, bar(50, 0, 110, 2)).difference(union(*holes))


def el_jem():
    shell = poly((-2, 0), (102, 0), (102, 46), (64, 52), (54, 24), (46, 22), (36, 52), (-2, 56))
    holes = []
    for y in (3, 19, 34):
        for i in range(13):
            x = 2 + i * 7.9
            if 28 < x < 72 and y > 5: continue
            holes.append(arch(x, y, 4.5, 12))
    return union(shell.difference(union(*holes)), bar(50, 0, 106, 2))


def maqam():
    def leaf(a):
        return rot(poly((50, 0), (56, 0), (54.5, 62), (50, 104), (45.5, 62)), a, (50, 0))
    blades = union(leaf(-13), leaf(13), bar(50, 0, 7, 44), poly((39, 0), (61, 0), (55, 28), (45, 28)))
    joint = bar(50, 64, 36, 3)
    return union(blades, joint, ellipse(50, 112, 3, 7), bar(50, 0, 76, 3))


def severus_arch():
    body = union(bar(50, 0, 72, 54), bar(50, 54, 78, 4), bar(50, 58, 54, 12), poly((10, 58), (32, 58), (21, 70)), poly((68, 58), (90, 58), (79, 70)), tri(50, 70, 44, 11))
    return union(body, bar(50, 0, 88, 2), *[bar(x, 2, 3.4, 52) for x in (11, 89)]).difference(union(arch(50, 2, 28, 42), arch(21, 8, 5, 24), arch(79, 8, 5, 24)))


def palmyra():
    arch_ = union(bar(50, 0, 44, 48), bar(50, 48, 48, 4), tri(50, 52, 42, 9), poly((26, 52), (34, 52), (30, 58)), poly((66, 52), (74, 52), (70, 58))).difference(union(arch(50, 0, 16, 36), arch(35, 0, 6, 26), arch(65, 0, 6, 26)))
    return union(arch_, columns(-4, 24, 2, 2.6, 34, 4), rect(-4, 36, 24, 38.5), columns(76, 104, 2, 2.6, 34, 4), rect(76, 36, 104, 38.5), bar(50, 0, 112, 2))




def minsk_library():
    r, k = 36, .42
    gem = poly(*[(50 + r * x, 56 + r * y) for x, y in ((-k, -1), (k, -1), (1, -k), (1, k), (k, 1), (-k, 1), (-1, k), (-1, -k))])
    bands = union(rect(10, 40.6, 90, 41.8), rect(10, 70.2, 90, 71.4), rect(34.3, 18, 35.5, 94), rect(64.5, 18, 65.7, 94),
                  poly((34.3, 92), (35.5, 92), (15.5, 72), (14.3, 72)), poly((64.5, 92), (65.7, 92), (85.7, 72), (84.5, 72)),
                  poly((34.3, 20), (35.5, 20), (15.5, 40), (14.3, 40)), poly((64.5, 20), (65.7, 20), (85.7, 40), (84.5, 40)))
    return union(gem.difference(bands), trapezoid(50, 0, 82, 62, 22).difference(arcade(22, 78, 2, 5, 15, 6)), bar(50, 0, 104, 2))


def star(cx, cy, r):
    pts = []
    for i in range(10):
        a = math.pi / 2 + i * math.pi / 5
        q = r if i % 2 == 0 else r * .42
        pts.append((cx + q * math.cos(a), cy + q * math.sin(a)))
    return poly(*pts)


def fuji_himeji():
    """Mount Fuji with a castle keep in the style of Himeji in front of it."""
    fuji = mountain((0, 8), (18, 22), (38, 54), (46, 67), (56, 67), (64, 54), (84, 22), (108, 6))
    snow = poly((40, 58), (44, 54), (47, 58), (51, 53), (55, 58), (58, 54), (62, 58), (62.4, 57), (58, 52), (55, 56), (51, 51), (47, 56), (44, 52), (39.5, 57))
    keep = [trapezoid(20, 0, 34, 27, 10)]
    for w, y in ((27, 10), (22, 20), (17, 29), (12, 37)):
        keep += [bar(20, y, w * .68, 8), tier_roof(20, y + 7, w, 5.5, 3)]
    return union(fuji.difference(snow), *keep)


def phang_nga():
    """Khao Phing Kan, the limestone needle in Phang Nga Bay, with a longtail boat."""
    needle = poly((60, 2), (66, 2), (71, 32), (80, 56), (73, 70), (56, 72), (47, 60), (54, 30))
    karst = poly((-8, 2), (-4, 42), (8, 60), (24, 56), (36, 26), (42, 2))
    boat = union(poly((80, 3), (104, 3), (112, 13), (107, 8), (78, 8)), rect(84, 8, 96, 13), bar(90, 13, 15, 1.2))
    return union(needle, karst, boat, palm(22, 54, 12, .6), bar(50, 0, 118, 3))


def ha_long():
    """Ha Long Bay: limestone karsts rising from the water, with a junk under battened sails."""
    karsts = union(poly((-10, 2), (-8, 30), (-2, 46), (8, 52), (18, 44), (22, 22), (26, 2)),
                   poly((20, 2), (24, 18), (30, 30), (38, 34), (44, 26), (46, 2)),
                   poly((66, 2), (68, 24), (74, 44), (84, 60), (94, 54), (100, 34), (108, 2)),
                   poly((50, 2), (54, 14), (60, 18), (66, 12), (70, 2)))

    def sail(cx, w, h):
        cloth = poly((cx - w * .36, 13), (cx + w * .36, 13), (cx + w * .56, 13 + h * .55), (cx + w * .36, 13 + h), (cx - w * .2, 13 + h * .9), (cx - w * .46, 13 + h * .5))
        battens = union(*[rect(cx - w, 13 + h * f, cx + w, 13 + h * f + .7) for f in (.22, .44, .66)])
        return cloth.difference(battens)
    hull = poly((28, 5), (72, 5), (82, 15), (70, 11), (30, 11), (20, 17))
    boat = union(hull, sail(37, 15, 30), sail(50, 19, 42), sail(63, 13, 26), rect(29, 11, 71, 13))
    return union(karsts.difference(boat.buffer(1.5)), boat, bar(50, 0, 122, 3))


def monas():
    """Monas, the National Monument in Jakarta."""
    cup = poly((6, 0), (94, 0), (94, 6), (70, 13), (61, 25), (39, 25), (30, 13), (6, 6))
    return union(cup, trapezoid(50, 25, 13, 7.5, 76), bar(50, 101, 18, 3), trapezoid(50, 104, 6, 8, 3), ellipse(50, 112, 4, 6), tri(50, 113, 6.4, 12))


def angel_independence():
    """The Angel of Independence in Mexico City."""
    fig = union(poly((46, 98), (54, 98), (51.8, 114), (48.2, 114)), circle(50, 116.4, 2.1))
    wings = mirror(poly((51, 110), (72, 128), (62, 108)))
    arm = union(poly((51, 111), (59, 130), (57, 131), (49, 114)), circle(58.5, 133, 2.6).difference(circle(58.5, 133, 1.4)))
    return union(bar(50, 0, 62, 3), bar(50, 3, 44, 4), bar(50, 7, 30, 9), trapezoid(50, 16, 10, 7, 79), bar(50, 95, 11, 3), fig, wings, arm)


def santorini():
    """Santorini's cliff-top white houses and a windmill above the caldera."""
    cliff = poly((-8, 2), (-8, 34), (8, 44), (30, 50), (56, 50), (78, 42), (98, 28), (108, 2))
    houses = []
    for x, y, w, h in ((6, 40, 15, 12), (19, 44, 13, 18), (32, 46, 14, 10), (45, 46, 12, 22), (58, 44, 15, 12), (72, 38, 13, 12), (84, 30, 12, 11)):
        houses.append(rect(x, y, x + w, y + h).difference(union(arch(x + w * .32, y + 2, 2, 5), arch(x + w * .7, y + 2, 2, 5))))
    mill = union(trapezoid(70, 50, 11, 8, 22), dome(70, 72, 8, 3))
    sails = union(*[rot(bar(70, 73, .9, 15), a, (70, 73)) for a in (30, 120, 210, 300)])
    return union(cliff, *houses, mill, sails, bar(50, 0, 120, 3))


def taq_kasra():
    """Taq Kasra at Ctesiphon, the great arch of a Sasanian palace."""
    vault = ellipse(50, 0, 37, 84).difference(ellipse(50, 0, 26, 72)).intersection(rect(-20, 0, 120, 100))
    wall = rect(-8, 0, 18, 50).difference(union(arcade(-8, 18, 4, 4, 15, 3), arcade(-8, 18, 26, 4, 15, 3)))
    return union(vault, wall, mountain((86, 2), (94, 16), (102, 11), (110, 2)), bar(50, 0, 122, 2))


def ait_benhaddou():
    """Ait Benhaddou, the earthen ksar on a hill in southern Morocco."""
    hill = mountain((-8, 2), (6, 14), (24, 30), (46, 42), (64, 36), (84, 18), (108, 2))
    def tower(cx, y, w, h):
        t = union(trapezoid(cx, y, w, w * .84, h), *[bar(cx + o * w * .36, y + h - 1, w * .18, 5) for o in (-1, 1)], bar(cx, y + h - 1, w * .22, 3))
        return t.difference(union(*[bar(cx + o * w * .18, y + h * f, 1.3, 3) for o in (-1, 1) for f in (.34, .66)]))
    return union(hill, tower(26, 26, 13, 20), tower(40, 34, 15, 26), tower(55, 34, 16, 34), tower(70, 26, 13, 24), tower(14, 12, 12, 16), palm(92, 6, 22, .9), palm(-2, 2, 20, .8))


def pigeon_rocks():
    """The Pigeons' Rock sea arch at Raouche in Beirut, with a Lebanese cedar."""
    rock = poly((6, 2), (0, 28), (8, 56), (24, 66), (44, 62), (55, 40), (58, 2)).difference(arch(30, 2, 15, 28))
    islet = poly((66, 2), (68, 18), (78, 26), (92, 20), (98, 2))
    return union(rock, islet, cedar(82, 24, 38, 26), bar(50, 0, 118, 3))


def sugarloaf():
    """Sugarloaf Mountain in Rio de Janeiro, with its cable car."""
    big = mountain((30, 2), (38, 32), (47, 62), (57, 76), (68, 78), (78, 66), (88, 34), (100, 6))
    urca = mountain((-10, 2), (-2, 18), (8, 31), (20, 33), (30, 20), (38, 6))
    wire = poly((14, 34), (58, 78), (58.6, 77.2), (14.6, 33.2))
    car = union(bar(34, 48, 6, 4), bar(34, 52, .6, 3))
    return union(big, urca, wire, car, bar(50, 0, 122, 3))


def spasskaya():
    """The Spasskaya clock tower on the Moscow Kremlin wall."""
    wall = union(rect(-6, 0, 106, 26), battlements(-6, 106, 26, 5, 20))
    tower = union(bar(50, 0, 26, 64), bar(50, 64, 29, 3), bar(50, 67, 21, 17), bar(50, 84, 16, 3), bar(50, 87, 13, 10), trapezoid(50, 97, 13, 2.4, 28), star(50, 129, 4.6),
                  *[tri(x, 67, 3.4, 9) for x in (39, 61)], *[tri(x, 87, 2.4, 7) for x in (43, 57)])
    return union(wall, tower).difference(union(arch(50, 0, 10, 20), circle(50, 52, 7.4).difference(union(circle(50, 52, 6), bar(50, 52, 1, 5), rect(50, 51.5, 54, 52.5))), arch(50, 69, 5, 11)))


def alhambra():
    """The Alhambra on its hill in Granada, with cypresses."""
    hill = mountain((-10, 2), (2, 22), (30, 28), (72, 30), (98, 22), (112, 2))
    walls = union(rect(4, 24, 96, 40), battlements(4, 96, 40, 3, 20))
    keep = union(bar(66, 24, 22, 42), battlements(55, 77, 66, 3.6, 6))
    towers = [union(bar(x, 24, w, h), battlements(x - w / 2, x + w / 2, 24 + h, 3, 3)) for x, w, h in ((16, 11, 28), (36, 10, 22), (90, 10, 24))]
    trees = [ellipse(x, y + 10, 2.8, 11) for x, y in ((6, 20), (26, 26), (46, 28), (102, 12))]
    return union(hill, walls, keep, *towers, *trees).difference(union(arcade(55, 77, 46, 3.4, 12, 3), arcade(40, 54, 28, 2.8, 9, 3), arch(16, 34, 2.6, 8), arch(90, 34, 2.6, 8)))


def cliffs_moher():
    """The Cliffs of Moher with O'Brien's Tower on top."""
    cliff = poly((-8, 2), (-8, 58), (12, 62), (34, 58), (46, 50), (52, 40), (66, 38), (78, 26), (90, 24), (108, 12), (108, 2))
    strata = union(*[poly((-8, y), (108, y - 18), (108, y - 17), (-8, y + 1)) for y in (20, 32, 44, 56)])
    tower = union(bar(16, 60, 11, 15), battlements(10.5, 21.5, 75, 3, 3)).difference(arch(16, 62, 3, 7))
    return union(cliff.difference(strata), tower, bar(50, 0, 118, 2))


def svan_towers():
    """The medieval Svan defensive towers of Ushguli below the Caucasus."""
    peak = mountain((16, 8), (42, 44), (62, 80), (72, 92), (84, 70), (98, 44), (118, 14))
    snow = poly((62, 80), (72, 92), (84, 70), (78, 66), (73, 76), (68, 66))
    def svan(cx, h):
        return union(trapezoid(cx, 0, 10, 8.4, h), bar(cx, h, 10, 4), battlements(cx - 5, cx + 5, h + 4, 2, 3), tri(cx, h + 6, 10, 4)).difference(union(arch(cx, h - 9, 2, 5), arch(cx, h * .4, 2, 5)))
    house = union(rect(36, 0, 56, 12), tri(46, 12, 24, 6))
    return union(peak.difference(snow), svan(6, 44), svan(20, 54), svan(32, 38), house, bar(50, 0, 128, 2))


def helsinki_station():
    """Helsinki Central Station, with its clock tower."""
    hall = union(rect(-2, 0, 72, 24), dome(30, 24, 40, 16), bar(30, 24, 14, 3))
    tower = union(trapezoid(84, 0, 13, 11, 86), bar(84, 86, 13, 3), trapezoid(84, 89, 9, 5, 8), dome(84, 97, 5, 4))
    return union(hall, tower, bar(50, 0, 108, 2)).difference(union(arch(30, 2, 22, 32), arcade(50, 70, 3, 3, 13, 3), arcade(0, 16, 3, 3, 13, 2), circle(84, 78, 4.2)))


def tsarevets():
    """Tsarevets Fortress in Veliko Tarnovo, with Baldwin's Tower."""
    hill = mountain((-10, 2), (4, 20), (22, 26), (70, 34), (88, 22), (112, 2))
    wall = poly((6, 22), (94, 28), (94, 34), (6, 28))
    cren = union(*[bar(x, 28 + (x - 6) * .068, 3, 4) for x in range(8, 94, 7)])
    baldwin = union(bar(30, 26, 19, 36), battlements(20.5, 39.5, 62, 4, 5)).difference(union(arch(30, 26, 6, 13), arch(30, 46, 3, 8)))
    gate = union(bar(72, 30, 15, 22), battlements(64.5, 79.5, 52, 3, 4)).difference(arch(72, 30, 5, 11))
    return union(hill, wall, cren, baldwin, gate)


def kalemegdan():
    """Belgrade Fortress at Kalemegdan, with the Victor monument."""
    wall = union(rect(-6, 0, 106, 22), battlements(-6, 106, 22, 4, 18), rect(10, 22, 36, 34), battlements(10, 36, 34, 3, 5)).difference(union(arch(23, 3, 9, 15), arcade(44, 104, 3, 4, 12, 6)))
    column = union(bar(68, 22, 13, 8), bar(68, 30, 5, 62), bar(68, 92, 7, 2))
    victor = union(poly((65.8, 94), (70.2, 94), (69.6, 110), (66.4, 110)), circle(68, 112, 1.9), poly((69, 107), (75, 104), (75, 102.6), (68.6, 105)), bar(75, 94, .6, 14))
    return union(wall, column, victor, ellipse(90, 32, 10, 9), ellipse(46, 30, 7, 7))


def pulpit_rock():
    """Preikestolen, the Pulpit Rock above Lysefjord."""
    cliff = poly((-10, 2), (-10, 70), (22, 76), (50, 76), (47, 54), (36, 42), (28, 2))
    crack = poly((30, 76), (32, 76), (29, 44), (27.5, 44))
    far = mountain((44, 2), (66, 34), (84, 26), (112, 6))
    return union(cliff.difference(crack), far, bar(50, 0, 126, 3))


def kirkjufell():
    """Kirkjufell, Iceland's arrowhead mountain, with the falls in front of it."""
    peak = mountain((6, 12), (28, 36), (40, 60), (46, 84), (58, 92), (64, 78), (74, 50), (92, 24), (112, 10))
    bands = union(*[poly((20, y), (100, y + 5), (100, y + 6), (20, y + 1)) for y in (36, 52, 68)])
    ledge = poly((-12, 2), (-12, 16), (20, 20), (42, 14), (66, 16), (66, 2)).difference(union(bar(18, 2, 7, 16), bar(38, 2, 5, 12)))
    return union(peak.difference(bands), ledge, bar(50, 0, 128, 2))


def tallinn():
    """Tallinn's medieval walls, with Tall Hermann and Fat Margaret."""
    wall = union(rect(-4, 0, 104, 22), battlements(-4, 104, 22, 3, 18))
    def t(cx, w, h, roof): return union(bar(cx, 0, w, h), tri(cx, h, w + 3, roof))
    hermann = union(bar(68, 0, 15, 76), battlements(60.5, 75.5, 76, 3.4, 4), bar(68, 79, .8, 16), poly((68.4, 95), (79, 92), (68.4, 88)))
    margaret = union(trapezoid(14, 0, 26, 24, 30), battlements(2, 26, 30, 3.4, 6))
    return union(wall, hermann, margaret, t(38, 10, 36, 18), t(92, 12, 38, 20), t(52, 8, 28, 14)).difference(union(arch(14, 8, 4, 10), arch(68, 52, 3.4, 10), arch(92, 22, 3, 7), arcade(26, 100, 3, 4, 12, 7)))


def kamianets():
    """Kamianets-Podilskyi Castle on its rock, with the stone bridge."""
    rock = mountain((-8, 2), (6, 18), (26, 28), (70, 30), (84, 20), (108, 2))
    def t(cx, w, h, roof): return union(bar(cx, 18, w, h), tri(cx, 18 + h, w + 3, roof))
    walls = union(rect(14, 26, 84, 46), battlements(14, 84, 46, 3, 13))
    return union(rock, walls, t(16, 13, 38, 18), t(38, 10, 42, 16), t(62, 11, 46, 18), t(84, 12, 34, 16)).difference(union(arch(50, 26, 7, 14), arch(16, 42, 3, 7), arch(62, 48, 3, 7), arch(84, 38, 3, 7), arcade(-8, 12, 2, 5, 11, 2)))


LANDMARKS = {
    "BY": minsk_library,
    "FR": eiffel, "CA": cn_tower, "US": liberty, "GB": big_ben, "DE": brandenburg, "NL": windmill, "JP": fuji_himeji,
    "KR": namdaemun, "CN": great_wall, "TW": taipei_101, "HK": hk_skyline, "SG": marina_bay, "MY": petronas, "ID": monas,
    "TH": phang_nga, "VN": ha_long, "PH": mayon, "IN": taj_mahal, "PK": minar_pakistan, "BD": smriti_soudho, "KZ": bayterek,
    "AU": opera_house, "NZ": sky_tower, "RU": spasskaya, "UA": kamianets, "BR": sugarloaf, "AR": obelisco, "PE": machu_picchu,
    "VE": angel_falls, "MX": angel_independence, "CL": moai, "CO": clock_tower, "ES": alhambra, "IT": colosseum, "PL": palace_culture,
    "RO": bran_castle, "HU": budapest_parliament, "AT": riesenrad, "CH": matterhorn, "SE": stockholm_hall, "NO": pulpit_rock,
    "FI": helsinki_station, "DK": nyhavn, "IE": cliffs_moher, "BE": atomium, "CZ": charles_bridge, "HR": dubrovnik, "TR": galata,
    "AE": burj_khalifa, "SA": kingdom_centre, "EG": pyramids, "ZA": table_mountain, "IL": aqueduct, "PT": belem, "GR": santorini,
    "BG": tsarevets, "RS": kalemegdan, "LT": gediminas, "LV": freedom_monument, "EE": tallinn, "MD": chisinau_arch, "GE": svan_towers,
    "IS": kirkjufell, "QA": mia_doha, "KW": kuwait_towers, "BH": bahrain_wtc, "OM": nizwa_fort, "JO": petra, "MA": ait_benhaddou,
    "IQ": taq_kasra, "PS": olive_tree, "YE": shibam, "TN": el_jem, "DZ": maqam, "LY": severus_arch, "SY": palmyra, "LB": pigeon_rocks,
}


# ---------- national symbols ----------
def petal_ring(n, length, width, start=0.0, r0=0.0):
    return union(*[rot(ellipse(0, r0 + length / 2, width / 2, length / 2), start + i * 360 / n, (0, 0)) for i in range(n)])


def m_tulip():
    cup = poly((-.26, -.02), (-.28, .28), (-.14, .14), (0, .32), (.14, .14), (.28, .28), (.26, -.02), (.16, -.12), (-.16, -.12))
    stem = poly((-.025, -.12), (.025, -.12), (.03, -.5), (-.03, -.5))
    return union(cup, stem, rot(ellipse(.13, -.34, .05, .16), -35, (.03, -.46)))


def m_lotus():
    petals = [rot(ellipse(0, .22, .085, .24), a, (0, -.1)) for a in (-72, -40, 0, 40, 72)]
    return union(*petals, ellipse(0, -.16, .32, .05))


def m_shamrock():
    leaf = union(circle(-.075, .22, .115), circle(.075, .22, .115), poly((-.17, .17), (.17, .17), (0, 0)))
    return union(leaf, rot(leaf, 120, (0, 0)), rot(leaf, -120, (0, 0)), poly((-.018, 0), (.018, 0), (.06, -.46), (.02, -.46)))


def m_fern():
    leaves = []
    for i in range(9):
        y, l = -.34 + i * .085, .3 - i * .029
        for s in (-1, 1):
            leaves.append(poly((0, y), (s * l, y + .10), (s * l * .65, y + .03)))
    return union(*leaves, poly((-.012, -.5), (.012, -.5), (.004, .5), (-.004, .5)))


def m_rose():
    outer = petal_ring(5, .36, .36, 0, .1)
    inner = circle(0, 0, .13)
    return outer.difference(union(circle(0, 0, .2).difference(circle(0, 0, .17)), rot(rect(-.015, .17, .015, .44), 36, (0, 0)), rot(rect(-.015, .17, .015, .44), 108, (0, 0)), rot(rect(-.015, .17, .015, .44), 180, (0, 0)), rot(rect(-.015, .17, .015, .44), 252, (0, 0)), rot(rect(-.015, .17, .015, .44), 324, (0, 0)))).union(inner.difference(circle(.02, .01, .05)))


def m_hibiscus():
    return union(petal_ring(5, .42, .3, 0, .06), rot(poly((-.018, 0), (.018, 0), (.03, .5), (-.03, .5)), 60, (0, 0))).difference(union(circle(0, 0, .055)))


def m_edelweiss():
    return petal_ring(9, .44, .13, 0, .07).union(union(*[circle(.05 * math.cos(a), .05 * math.sin(a), .036) for a in (0, 2.1, 4.2)]))


def m_olive():
    stem = poly((-.012, -.5), (.012, -.5), (.012, .5), (-.012, .5))
    leaves = [rot(ellipse(s * .17, y, .17, .05), s * 22, (0, y)) for s, y in ((-1, -.26), (1, -.1), (-1, .06), (1, .22))]
    return union(stem, *leaves, circle(.13, -.28, .065), circle(-.13, -.04, .065), ellipse(0, .38, .05, .12))


def m_daisy():
    return petal_ring(13, .34, .11, 0, .13).union(circle(0, 0, .12))


def m_poppy():
    return union(petal_ring(4, .44, .46, 45, .02)).difference(circle(0, 0, .06)).union(circle(0, 0, .03))


def m_sunflower():
    return petal_ring(16, .24, .09, 0, .24).union(circle(0, 0, .22).difference(circle(0, 0, .19))).union(circle(0, 0, .13))


def m_jasmine():
    return union(*[rot(ellipse(0, .27, .11, .23), i * 72, (0, 0)) for i in range(5)]).difference(circle(0, 0, .035))


def m_wattle():
    return union(circle(-.2, .18, .13), circle(.13, .24, .15), circle(.26, -.06, .12), circle(-.08, -.08, .14), circle(-.3, -.16, .09), poly((-.2, -.42), (-.16, -.42), (.12, .2), (.08, .2)))


def m_orchid():
    side = [rot(ellipse(0, .25, .13, .24), a, (0, 0)) for a in (-65, 65)]
    top = [rot(ellipse(0, .25, .07, .23), a, (0, 0)) for a in (0, -130, 130)]
    lip = union(poly((-.09, -.02), (.09, -.02), (.14, -.32), (0, -.26), (-.14, -.32)))
    return union(*side, *top, lip).difference(circle(0, .03, .03))


def m_fleur():
    centre = poly((0, .5), (.09, .28), (.1, .1), (.05, -.08), (-.05, -.08), (-.1, .1), (-.09, .28))
    petal = rot(ellipse(.25, .16, .085, .24), -28, (.25, .16)).difference(circle(.11, .36, .1))
    tail = poly((.04, -.2), (.2, -.24), (.26, -.42), (.12, -.34), (.03, -.34))
    return union(centre, petal, affinity.scale(petal, -1, 1, origin=(0, 0)), tail, affinity.scale(tail, -1, 1, origin=(0, 0)), bar(0, -.17, .4, .07))


def m_grape():
    berries = [circle(x, y, .1) for x, y in ((-.2, .14), (0, .14), (.2, .14), (-.1, -.04), (.1, -.04), (0, -.22), (-.3, .3) if False else (0, .3))]
    return union(*berries, rot(ellipse(.15, .36, .15, .07), 25, (.05, .3)), poly((-.015, .28), (.015, .28), (.03, .48), (-.01, .48)))


SYMBOLS = {
    "TULIP": m_tulip, "LOTUS": m_lotus, "SHAMROCK": m_shamrock, "FERN": m_fern, "ROSE": m_rose, "HIBISCUS": m_hibiscus,
    "EDELWEISS": m_edelweiss, "OLIVE": m_olive, "DAISY": m_daisy, "POPPY": m_poppy, "SUNFLOWER": m_sunflower,
    "JASMINE": m_jasmine, "WATTLE": m_wattle, "ORCHID": m_orchid, "FLEUR": m_fleur, "GRAPE": m_grape,
}


# ---------- output ----------
def polygons(g):
    if isinstance(g, Polygon): return [g]
    if isinstance(g, MultiPolygon): return list(g.geoms)
    return [p for p in getattr(g, "geoms", []) if isinstance(p, Polygon)]


def fmt(v, digits):
    s = f"{v:.{digits}f}".rstrip("0").rstrip(".")
    return s if s not in ("-0", "") else "0"


def to_path(g, flip_height, digits, tolerance):
    g = g.simplify(tolerance).buffer(0)
    parts = []
    for p in polygons(g):
        for ring in [p.exterior, *p.interiors]:
            pts = list(ring.coords)[:-1]
            parts.append("M" + "L".join(f"{fmt(x, digits)} {fmt(flip_height - y, digits)}" for x, y in pts) + "Z")
    return "".join(parts)


def build():
    marks = {}
    for cc, fn in LANDMARKS.items():
        g = fn().intersection(box(-200, 0, 300, 400)).buffer(0)  # nothing below the ground
        x0, y0, x1, y1 = g.bounds
        g = affinity.translate(g, -x0, -y0)
        marks[cc] = (g, x1 - x0, y1 - y0)
    return marks, {k: fn().buffer(0) for k, fn in SYMBOLS.items()}


def write_kotlin(marks, symbols):
    lines = ["package com.voltavpn.app.ui", "", "// Generated by tools/brand/make_landmarks.py; edit that file instead.", "",
             "/** A country's landmark as an even-odd SVG path, with its ground line at y = [height]. */",
             "class LandmarkShape(val width: Float, val height: Float, val path: String)", "",
             "val LANDMARK_SHAPES: Map<String, LandmarkShape> by lazy {", "    mapOf("]
    for cc in sorted(marks):
        g, w, h = marks[cc]
        lines.append(f'        "{cc}" to LandmarkShape({fmt(w, 1)}f, {fmt(h, 1)}f, "{to_path(g, h, 1, .2)}"),')
    lines += ["    )", "}", "", "/** National symbols as even-odd SVG paths, about 1 x 1 and centred on 0,0. */",
              "val SYMBOL_PATHS: Map<Motif, String> by lazy {", "    mapOf("]
    for name in sorted(symbols):
        lines.append(f'        Motif.{name} to "{to_path(symbols[name], 0, 3, .002)}",')
    lines += ["    )", "}", ""]
    with open(OUT, "w", encoding="utf-8", newline="\n") as fh:
        fh.write("\n".join(lines))
    print(f"{len(marks)} landmarks, {len(symbols)} symbols, {os.path.getsize(OUT) // 1024} KB source")


def preview(marks, symbols, path):
    from PIL import Image, ImageDraw
    cell, cols = 240, 11
    keys = sorted(marks)
    rows = math.ceil(len(keys) / cols) + 2
    img = Image.new("RGB", (cols * cell, rows * cell), (11, 16, 32))
    d = ImageDraw.Draw(img)

    def draw(g, ox, oy, s, flip):
        for p in polygons(g):
            d.polygon([(ox + x * s, oy - y * s) for x, y in p.exterior.coords], fill=(120, 170, 230))
            for r in p.interiors:
                d.polygon([(ox + x * s, oy - y * s) for x, y in r.coords], fill=(11, 16, 32))

    for i, cc in enumerate(keys):
        g, w, h = marks[cc]
        s = min((cell - 30) / w, (cell - 40) / h)
        cx, cy = (i % cols) * cell, (i // cols) * cell
        draw(g, cx + (cell - w * s) / 2, cy + cell - 10, s, True)
        d.text((cx + 6, cy + 5), cc, fill=(255, 200, 60))
    base = math.ceil(len(keys) / cols) * cell
    for i, k in enumerate(sorted(symbols)):
        cx, cy = (i % cols) * cell, base + (i // cols) * cell
        draw(symbols[k], cx + cell / 2, cy + cell / 2, 190, True)
        d.text((cx + 6, cy + 5), k, fill=(255, 200, 60))
    img.save(path)
    print("preview:", path)


if __name__ == "__main__":
    marks, symbols = build()
    write_kotlin(marks, symbols)
    if len(sys.argv) > 1:
        preview(marks, symbols, sys.argv[1])
