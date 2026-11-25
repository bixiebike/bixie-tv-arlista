# Bixie TV árlista

A bemutatóterem tévéjén (TCL 50P755, Google TV) folyamatosan forgó árlista, márkánként egy képernyővel.

## Hogyan működik

1. **`generator/general.py`**: negyedóránként (GitHub Actions) lekéri a Shoprenterből az aktív kerékpárokat, a színváltozatok közül a legolcsóbbat, és az aktuális akciós árat. Kimenet: `prices.json` + körbevágott termékképek (`img/`).
2. **`site/index.html`**: a tévén futó oldal (B stílus, menütábla). 15 percenként újratölti a `prices.json`-t, és a következő márkaváltáskor cseréli az árakat. Ha nincs net, az utoljára letöltött árakat mutatja. Hajnali 4-kor teljesen újratölt.
3. **GitHub Pages**: innen tölti le a tévé az oldalt.
4. **`android/`**: saját Android TV alkalmazás (WebView). Bekapcsoláskor és készenlétből ébredéskor előtérbe hozza az árlistát.

## Napi teendők

- **Új modell a Shoprenterben**: magától megjelenik. Szép nevet és rövid leírást a `generator/modellek.json`-ban kap (SKU szerint). Ha hiányzik, a futás naplója kiírja: „ÚJ MODELL…”.
- **Modell elrejtése**: `"rejt": true` a `modellek.json`-ban.
- **Kifutott / letiltott termék**: a Shoprenter státusza alapján magától lekerül.

## Extra képernyők (akció, kép, videó)

A `site/extra.json` sorolja fel őket. Körönként egyszer jönnek, az utolsó márka után. A `tol`/`ig` dátum (első és utolsó nap) szerint maguktól megjelennek és eltűnnek, levenni nem kell őket.

- `"tipus": "blackfriday"`: animált BF-képernyő visszaszámlálóval; a modellszám élőben jön az árakból, az „akár” számok és az alsó két sor a fájlban állítható.
- `"tipus": "kep"` vagy `"video"`: kész 1920×1080-as kép vagy hang nélküli mp4 a `site/extra/` mappában, `"src": "extra/fajlnev.jpg"`.
- `"mp"`: hány másodpercig látszik.

A tévé negyedóránként újraolvassa a fájlt, az APK-hoz nem kell nyúlni.

## URL-paraméterek (tesztelés böngészőben)

`?tv=1` vezérlősáv nélkül · `?stil=a` kártyás stílus · `?marka=2` adott márkával indul · `?mp=20` másodperc/képernyő · `?allo=1` animáció nélkül · `?kezd=extra` az extra képernyővel indul · `?extra=0` extra képernyők nélkül

## GitHub Secrets

| Név | Mire |
|---|---|
| `SHOPRENTER_USER`, `SHOPRENTER_PASS` | Shoprenter API |
| `APK_KEYSTORE_B64`, `APK_KEYSTORE_PASS` | opcionális: állandó aláírás az APK-hoz (nélküle frissítéskor az appot előbb törölni kell) |

## APK telepítése a tévére

1. A tévén: Beállítások > Rendszer > Névjegy > „Android TV operációs rendszer buildje” hétszer megnyomva → fejlesztői mód.
2. Play Áruház: **Downloader** (AFTVnews) telepítése, és engedélyezni neki az ismeretlen forrásból való telepítést.
3. Downloaderben: `https://github.com/bixiebike/bixie-tv-arlista/releases/download/apk/bixie-arlista.apk`
4. Első indításkor megnyílik a „Megjelenítés más alkalmazások felett” engedély: kapcsold be a Bixie árlistánál. Ez kell az automatikus induláshoz.
5. Tartalék, ha a tévé mégsem hozza elő magától: ADB-vel `adb shell appops set hu.bixie.arlista SYSTEM_ALERT_WINDOW allow`.
