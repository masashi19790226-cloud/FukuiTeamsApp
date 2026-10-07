"""
3チームの選手の誕生日を集める。build_previews.py から1日1回だけ呼び、data/standings.json の "BIRTHDAYS" に入れる。

- ブローウィンズ: 公式サイトの選手一覧 → 各選手の詳細ページの「生年月日」
- 丸岡RUCK    : 女子Fリーグ公式サイトのクラブページの選手名簿(背番号・氏名・生年月日が1つの表にある)
- ユナイテッド : 公式サイトのチームページ → 各選手の詳細ページ(detail.php?id=)の「生年月日」

選手の詳細ページは一度読んだら30日間は読み直さない(誕生日は変わらないため)。
覚え書きは "cache" に入れる(アプリは使わない)。
"""

import html as htmllib
import re
from datetime import datetime, timedelta, timezone

JST = timezone(timedelta(hours=9))

BLOWINDS_SITE = "https://www.fukuiblowinds.com"
BLOWINDS_PLAYERS = BLOWINDS_SITE + "/team/players/"
UNITED_SITE = "https://fukuiunited.co.jp"
UNITED_TEAM = UNITED_SITE + "/team/"
WFL_SITE = "https://w-fleague.jp"
WFL_RUCK_CLUB = WFL_SITE + "/club/maruoka/"

CACHE_DAYS = 30


def _text(fragment: str) -> str:
    t = re.sub(r"<(script|style)[^>]*>.*?</\1>", " ", fragment, flags=re.S | re.I)
    t = re.sub(r"<br\s*/?>|</p>|</li>|</div>|</h\d>|</tr>|</dt>|</dd>|</td>|</th>", "\n", t, flags=re.I)
    t = re.sub(r"<[^>]+>", " ", t)
    t = htmllib.unescape(t)
    t = re.sub(r"[ \t　]+", " ", t)
    return re.sub(r"\n\s*\n+", "\n", t).strip()


def _birthday_in(text: str):
    """「生年月日 2000年3月14日」「生年月日 2000.03.14」などから 2000-03-14。無ければ None"""
    m = re.search(r"生年月日\s*[:：]?\s*(\d{4})\s*[年./-]\s*(\d{1,2})\s*[月./-]\s*(\d{1,2})", text)
    if not m:
        return None
    return f"{int(m.group(1)):04d}-{int(m.group(2)):02d}-{int(m.group(3)):02d}"


def _fresh(entry, today):
    """覚え書きが30日以内のものか"""
    try:
        d = datetime.strptime(entry.get("checked", ""), "%Y-%m-%d").date()
        return (today - d).days < CACHE_DAYS
    except ValueError:
        return False


def blowinds(fetch, cache, today):
    page = fetch(BLOWINDS_PLAYERS)
    links = []
    for m in re.finditer(r"team/players/detail/id=(\d+)(?:\?|&amp;|&)PlayerID=(\d+)", page):
        if (m.group(1), m.group(2)) not in links:
            links.append((m.group(1), m.group(2)))
    out, fetched = [], 0
    for detail_id, pid in links:
        key = f"blowinds:{pid}"
        hit = cache.get(key)
        if not (hit and _fresh(hit, today)):
            url = f"{BLOWINDS_SITE}/team/players/detail/id={detail_id}?PlayerID={pid}"
            try:
                html = fetch(url)
                fetched += 1
            except Exception as e:
                print(f"[WARN] 誕生日: ブローウィンズの選手ページを読めませんでした {url} {e!r}")
                if hit:
                    out.append((key, hit))
                continue
            name, number, photo = "", "", ""
            # 選手の写真の代替文字が選手名。写真のファイル名の先頭が背番号(例 13_Kawashima_2026-27_HP.jpg)
            for tag in re.findall(r"<img\b[^>]*>", html, flags=re.I):
                src = re.search(r"\bsrc\s*=\s*[\"']([^\"']+)[\"']", tag)
                if src and "/files/user/common/" in src.group(1) and "/img/logo/" not in src.group(1):
                    alt = re.search(r"\balt\s*=\s*[\"']([^\"']*)[\"']", tag)
                    name = htmllib.unescape(alt.group(1)).strip() if alt else ""
                    photo = src.group(1) if src.group(1).startswith("http") else BLOWINDS_SITE + src.group(1)
                    num = re.search(r"/(\d+)_[^/]*$", src.group(1))
                    number = num.group(1) if num else ""
                    break
            hit = {"team": "BLOWINDS", "name": name, "number": number, "photo": photo,
                   "birthday": _birthday_in(_text(html)), "checked": today.isoformat()}
        out.append((key, hit))
    print(f"[誕生日] ブローウィンズ: {len(out)}人(詳細ページ{fetched}件を読み込み)")
    return out


def united(fetch, cache, today):
    page = fetch(UNITED_TEAM)
    links = []
    for m in re.finditer(r"<a\b[^>]*href=[\"']([^\"']*detail\.php\?id=(\d+))[\"'][^>]*>(.*?)</a>", page, flags=re.S | re.I):
        pid = m.group(2)
        if any(x[0] == pid for x in links):
            continue
        num = re.match(r"\s*(\d+)", _text(m.group(3)))
        links.append((pid, num.group(1) if num else ""))
    photos = {}
    for tag in re.findall(r"<img[^>]+teammate__photo[^>]*>", page):
        src = re.search(r'src="([^"]+)"', tag)
        alt = re.search(r'alt="([^"]*)"', tag)
        if src and alt and alt.group(1).strip():
            photos[re.sub(r"[\s　]+", "", htmllib.unescape(alt.group(1)))] = src.group(1)
    out, fetched = [], 0
    for pid, number in links:
        key = f"united:{pid}"
        hit = cache.get(key)
        if not (hit and _fresh(hit, today)):
            url = f"{UNITED_SITE}/team/detail.php?id={pid}"
            try:
                html = fetch(url)
                fetched += 1
            except Exception as e:
                print(f"[WARN] 誕生日: ユナイテッドの選手ページを読めませんでした {url} {e!r}")
                if hit:
                    out.append((key, hit))
                continue
            title = re.search(r"<title>(.*?)</title>", html, flags=re.S | re.I)
            name = htmllib.unescape(title.group(1)).split(" - ")[0].strip() if title else ""
            hit = {"team": "UNITED", "name": name, "number": number,
                   "photo": photos.get(re.sub(r"[\s　]+", "", name), ""),
                   "birthday": _birthday_in(_text(html)), "checked": today.isoformat()}
        out.append((key, hit))
    print(f"[誕生日] ユナイテッド: {len(out)}人(詳細ページ{fetched}件を読み込み)")
    return out


def _ruck_photo(info):
    """丸岡RUCK公式の選手紹介(standings.ruck_members)の1人分から写真のURL"""
    if isinstance(info, dict):
        return info.get("photo", "")
    return info or ""


def ruck(fetch, ruck_photos):
    """女子Fリーグ公式のクラブページから、今季の選手名簿(背番号・氏名・生年月日)。毎回1〜2ページだけ読む"""
    club = fetch(WFL_RUCK_CLUB)
    m = re.search(r"var\s+team\s*=\s*(\d+)", club)
    if not m:
        print("[WARN] 誕生日: 女子Fリーグのクラブページで丸岡RUCKの番号が見つかりません")
        return []
    body = fetch(f"{WFL_SITE}/modules/php/FlPlayer.php?team={m.group(1)}", {"Referer": WFL_RUCK_CLUB})
    out = []
    for tr in re.findall(r"<tr\b[^>]*>(.*?)</tr>", body, flags=re.S | re.I):
        tds = [_text(x) for x in re.findall(r"<td\b[^>]*>(.*?)</td>", tr, flags=re.S | re.I)]
        if len(tds) < 5:
            continue
        # 列は ポジション・背番号・氏名・ローマ字・生年月日・出身 の順
        bd = re.match(r"(\d{4})\.(\d{1,2})\.(\d{1,2})", tds[4])
        name = re.sub(r"[\s　]+", " ", tds[2]).strip()
        if not bd or not name:
            continue
        key = re.sub(r"\s+", "", name)
        out.append((f"ruck:{key}", {
            "team": "RAC", "name": name, "number": tds[1].strip(),
            "photo": _ruck_photo((ruck_photos or {}).get(key)),
            "birthday": f"{int(bd.group(1)):04d}-{int(bd.group(2)):02d}-{int(bd.group(3)):02d}",
        }))
    print(f"[誕生日] 丸岡RUCK: {len(out)}人")
    return out


def build(fetch, old, ruck_photos=None):
    """誕生日のデータを作る。old は前回の分(無ければ {})。
    今日すでに作っていれば None(書き直さない)。チームごとに失敗しても、前回の分を残す。"""
    today = datetime.now(JST).date()
    if old.get("date") == today.isoformat():
        return None
    cache = old.get("cache") or {}
    prev = {p.get("_key"): p for p in old.get("players") or [] if p.get("_key")}
    entries = []
    for label, team, func in (("ブローウィンズ", "BLOWINDS", lambda: blowinds(fetch, cache, today)),
                              ("丸岡RUCK", "RAC", lambda: ruck(fetch, ruck_photos)),
                              ("ユナイテッド", "UNITED", lambda: united(fetch, cache, today))):
        try:
            got = func()
            if not got:
                raise ValueError("選手が見つかりません")
            entries += got
        except Exception as e:
            print(f"[WARN] 誕生日: {label}の取得に失敗。前回の分を使います {e!r}")
            entries += [(k, p) for k, p in prev.items() if p.get("team") == team]
    new_cache = {k: v for k, v in entries if not k.startswith("ruck:")}
    players = []
    for k, v in entries:
        if not v.get("birthday") or not v.get("name"):
            continue
        p = {kk: vv for kk, vv in v.items() if kk != "checked"}
        p["_key"] = k
        players.append(p)
    players.sort(key=lambda p: (p["birthday"][5:], p["team"], p["name"]))
    return {"date": today.isoformat(), "updated_at": datetime.now(timezone.utc).isoformat(),
            "players": players, "cache": new_cache}
