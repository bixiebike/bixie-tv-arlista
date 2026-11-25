"""Bixie TV árlista: Shoprenter -> public/prices.json + public/img/*.webp

Futtatás: python generator/general.py public
Környezeti változók: SHOPRENTER_USER, SHOPRENTER_PASS
A képeket csak akkor tölti le és vágja újra, ha még nincsenek meg (a fájlnév a képútvonal hash-e).
"""
import datetime, hashlib, html, io, json, os, pathlib, re, sys, time
from zoneinfo import ZoneInfo

import requests
from PIL import Image, ImageChops, ImageDraw

API = "https://bixie.api.myshoprenter.hu"
IMG_BASE = "https://www.bixie.hu/custom/bixie/image/cache/w900h900m00q100/"
AUTH = (os.environ["SHOPRENTER_USER"], os.environ["SHOPRENTER_PASS"])
HDR = {"Accept": "application/json"}
HU_LANG = "bGFuZ3VhZ2UtbGFuZ3VhZ2VfaWQ9MQ=="
BRANDS_OK = None  # a modellek.json marka_sorrend-je tölti

HERE = pathlib.Path(__file__).parent
OUT = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "public")
IMG_DIR = OUT / "img"
IMG_DIR.mkdir(parents=True, exist_ok=True)


def get(path):
    for _ in range(20):
        r = requests.get(API + path, auth=AUTH, headers=HDR, timeout=60)
        if r.status_code == 429:
            time.sleep(5)
            continue
        r.raise_for_status()
        return r.json()
    raise RuntimeError("Shoprenter 429 tartósan: " + path)


def get_all(path):
    out, page = [], 0
    while True:
        sep = "&" if "?" in path else "?"
        d = get(f"{path}{sep}full=1&limit=200&page={page}")
        out += d.get("items", [])
        if page >= int(d.get("pageCount", 1)) - 1:
            return out
        page += 1


def last_id(ref):
    return ((ref or {}).get("href") or "").rstrip("/").split("/")[-1]


def auto_name(full, brand):
    """Új, még fel nem vett modellnél a termék nevéből vág le egy rövid nevet."""
    part = re.split(r"\s[-–]\s", full)[-1]
    part = re.sub(rf"^{re.escape(brand)}\s+", "", part, flags=re.I)
    part = re.sub(r'\s\d+(\.\d+)?"?\s.*$', "", part)
    return part.strip()[:28]


def trim_image(im, yellow_cleanup=False):
    im = im.convert("RGB")
    if yellow_cleanup:
        w, h = im.size
        ImageDraw.Draw(im).rectangle([0, 0, int(w * .6), int(h * .6)], fill="white")
        px = im.load()
        for y in range(h):
            for x in range(w):
                r, g, b = px[x, y]
                if r > 200 and g > 180 and b < 120:
                    px[x, y] = (255, 255, 255)
        im = im.crop((0, int(h * .22), w, h))
    w, h = im.size
    px = im.load()
    corners = [px[2, 2], px[w - 3, 2], px[2, h - 3], px[w - 3, h - 3]]
    bg = tuple(max(200, sorted(c[i] for c in corners)[2]) for i in range(3))
    lut = []
    for i in range(3):
        lut += [min(255, int(v * 255 / bg[i])) for v in range(256)]
    im = im.point(lut)
    mask = ImageChops.difference(im, Image.new("RGB", im.size, "white")).convert("L").point(lambda v: 255 if v > 28 else 0)
    bb = mask.getbbox()
    if bb:
        pad = int(max(w, h) * .02)
        im = im.crop((max(0, bb[0] - pad), max(0, bb[1] - pad), min(w, bb[2] + pad), min(h, bb[3] + pad)))
    im.thumbnail((640, 400))
    return im


def image_file(path, yellow_cleanup):
    key = hashlib.sha1((path + ("|y" if yellow_cleanup else "") + "|v1").encode()).hexdigest()[:16]
    fn = IMG_DIR / f"{key}.webp"
    if not fn.exists():
        r = requests.get(IMG_BASE + path, timeout=60)
        r.raise_for_status()
        trim_image(Image.open(io.BytesIO(r.content)), yellow_cleanup).save(fn, "WEBP", quality=78)
    return f"img/{key}.webp"


def main():
    cfg = json.loads((HERE / "modellek.json").read_text(encoding="utf-8"))
    order, min_price, models = cfg["marka_sorrend"], cfg["min_ar"], cfg["modellek"]

    manu = {m["id"]: m["name"] for m in get("/manufacturers?full=1&limit=200")["items"]}
    prods = get_all("/products")
    names = {}
    for d in get_all("/productDescriptions"):
        if (d.get("language") or {}).get("href", "").endswith(HU_LANG):
            names[last_id(d.get("product"))] = html.unescape(d.get("name") or "")

    now_s = datetime.datetime.now(ZoneInfo("Europe/Budapest")).strftime("%Y-%m-%d %H:%M:%S")
    specials = {}
    for s in get_all("/productSpecials"):
        if s.get("customerGroup"):
            continue
        if int(s.get("minQuantity") or 0) > 1:
            continue
        df, dt = s.get("dateFrom") or "0000", s.get("dateTo") or "0000"
        if df[:4] != "0000" and df > now_s:
            continue
        if dt[:4] != "0000" and dt < now_s:
            continue
        pid = last_id(s.get("product"))
        gross = float(s["price"]) * 1.27
        specials[pid] = min(specials.get(pid, 1e12), gross)

    def parent_of(p):
        return last_id(p.get("parentProduct"))

    children = {}
    for p in prods:
        par = parent_of(p)
        if par and par != p["id"]:
            children.setdefault(par, []).append(p)

    items, unknown = [], []
    for p in prods:
        par = parent_of(p)
        if par and par != p["id"]:
            continue
        if str(p.get("status")) != "1":
            continue
        brand = manu.get(last_id(p.get("manufacturer")), "")
        if brand not in order:
            continue
        sku = p.get("sku") or ""
        conf = models.get(sku, {})
        if conf.get("rejt"):
            continue
        best = None
        for g in [p] + [c for c in children.get(p["id"], []) if str(c.get("status")) == "1"]:
            price = float(g["price"]) * 1.27
            if price < 1000:
                continue
            sp = specials.get(g["id"]) or (specials.get(p["id"]) if g is not p else None)
            sale = sp if sp and sp < price else None
            eff = sale or price
            if best is None or eff < best[0]:
                best = (eff, price, sale)
        if not best or best[1] < min_price or "AKKU" in sku.upper():
            continue
        if not conf:
            unknown.append(sku)
        img_path = conf.get("kep") or p.get("mainPicture") or ""
        try:
            img = image_file(img_path, conf.get("sarga_grafika_torles", False)) if img_path else ""
        except Exception as e:  # egy rossz kép miatt ne álljon le az egész
            print("KÉPHIBA", sku, e)
            img = ""
        items.append({
            "brand": brand,
            "sku": sku,
            "name": conf.get("nev") or auto_name(names.get(p["id"], sku), brand),
            "tag": conf.get("leiras", ""),
            "price": int(round(best[1], -3)),
            "sale": int(round(best[2], -3)) if best[2] else None,
            "img": img,
        })

    if len(items) < 10:
        raise SystemExit(f"Gyanúsan kevés modell ({len(items)}), nem írom felül az árlistát.")

    data = {"updated": now_s, "order": order, "items": items}
    (OUT / "prices.json").write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")

    used = {i["img"].split("/")[-1] for i in items if i["img"]}
    for f in IMG_DIR.glob("*.webp"):
        if f.name not in used:
            f.unlink()

    counts = {b: sum(1 for i in items if i["brand"] == b) for b in order}
    print("Kész:", now_s, counts, "akciós:", sum(1 for i in items if i["sale"]))
    if unknown:
        print("ÚJ MODELL, vedd fel a modellek.json-ba (név + leírás):", ", ".join(unknown))


if __name__ == "__main__":
    main()
