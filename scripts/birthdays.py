"""
3チームの選手の誕生日を集める。build_previews.py から1日1回だけ呼び、data/standings.json の "BIRTHDAYS" に入れる。

- ブローウィンズ: 公式サイトの選手一覧 → 各選手の詳細ページの「生年月日」
- 丸岡RUCK    : 女子Fリーグ公式サイトのクラブページの選手名簿(背番号・氏名・生年月日が1つの表にある)
- ユナイテッド : 公式サイトのチームページ → 各選手の詳細ページ(detail.php?id=)の「生年月日」

あわせて、選手の詳細ページ・選手紹介にある身長・体重・出身地・出身校も入れる(アプリの選手タブと特集で使う)。
- ブローウィンズ: 身長/体重・出身地・出身校(大学など)。出身高校はBリーグ公式の選手ページ(roster_detail)から
- 丸岡RUCK    : 身長・出身地(公式サイトの選手紹介。体重は載っていない)
- ユナイテッド : 身長/体重・出身地。出身高校は「所属/経歴」の最初の高校・ユース

選手の詳細ページは一度読んだら30日間は読み直さない(誕生日は変わらないため)。
覚え書きは "cache" に入れる(アプリは使わない)。
"""

import hashlib
import html as htmllib
import json
import os
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

BLEAGUE_ROSTER = "https://www.bleague.jp/roster_detail/?PlayerID="

# 手で直すプロフィール(公式サイトに載っていない出身高校など)
MANUAL_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "manual_profiles.json")
MANUAL_KEYS = ("height", "weight", "hometown", "high_school", "school", "position")


def _load_manual():
    """manual_profiles.json の中身と、その覚え書き(中身が変わったら、その日のうちに作り直すため)"""
    try:
        with open(MANUAL_PATH, encoding="utf-8") as f:
            text = f.read()
        return json.loads(text), hashlib.sha1(text.encode("utf-8")).hexdigest()[:12]
    except FileNotFoundError:
        return {}, ""
    except Exception as e:
        print(f"[WARN] 誕生日: manual_profiles.json を読めませんでした {e!r}")
        return {}, ""

# データの形の版。身長などを足したときに上げると、その日のうちに作り直す
VERSION = 4


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


def _profile(text: str) -> dict:
    """選手ページの文字から 身長・体重・出身地・出身校。
    例「身長 / 体重 175cm / 75kg」「身長／体重: 179cm／75kg」「出身地 兵庫県」「出身校 東海大学」。
    読み取れないもの・ありえない数字は空にする"""
    out = {"height": "", "weight": "", "hometown": "", "school": "", "position": ""}
    m = re.search(r"ポジション\s*[:：]?\s*\n?\s*(GK|FP|DF|MF|FW|PG|SG|SF|PF|C|G|F)(?:\s*/\s*(?:PG|SG|SF|PF|C|G|F))?\b", text)
    if m:
        # 「C/PF」のような2つのポジションもそのまま(空白は取る)
        out["position"] = re.sub(r"\s+", "", text[m.start(1):m.end()])
    m = re.search(r"身長[^\d\n]{0,20}\n?[^\d\n]{0,10}(\d{3}(?:\.\d)?)\s*cm", text)
    if m and 140 <= float(m.group(1)) <= 235:
        out["height"] = m.group(1)
    m = re.search(r"体重[^\d]{0,30}?(?:\d{3}(?:\.\d)?\s*cm\s*[/／]\s*)?(\d{2,3}(?:\.\d)?)\s*kg", text)
    if m and 35 <= float(m.group(1)) <= 160:
        out["weight"] = m.group(1)
    for key, label in (("hometown", "出身地"), ("school", "出身校")):
        m = re.search(label + r"\s*[:：]?\s*\n?\s*([^\n]{1,30})", text)
        if m:
            v = m.group(1).strip()
            # 次の項目名や数字を拾ってしまったときは使わない
            if v and not re.search(r"\d|生年月日|身長|体重|血液型|出身|国籍|未記入", v):
                out[key] = v
    return out


def _high_school_bleague(text: str) -> str:
    """Bリーグ公式の選手ページの「出身校（高）北陸高等学校」から高校名。無ければ空"""
    m = re.search(r"出身校\s*[（(]\s*高\s*[）)]\s*[:：]?\s*\n?\s*([^\n]{2,30})", text)
    if not m:
        return ""
    v = m.group(1).strip()
    # 「-」(分からない)や、次の項目名を拾ったときは使わない
    return "" if re.search(r"出身校|\d|未記入", v) or re.fullmatch(r"[-−ー―－\s]+", v) else v


def _high_school_career(text: str) -> str:
    """ユナイテッドの「所属／経歴: 藤枝明誠高校→日本大学→…」から、最初の高校・ユース。無ければ空"""
    m = re.search(r"経歴\s*[:：]?\s*\n?\s*([^\n]{2,200})", text)
    if not m:
        return ""
    for part in re.split(r"[→⇒>＞/／、,]", m.group(1)):
        part = part.strip()
        if re.search(r"高校|高等学校|ユース|U-?18|U１８", part) and len(part) <= 30:
            return part
    return ""


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
        # 身長・ポジションなどを入れる前に覚えた分("position" が無い)は読み直す
        if not (hit and _fresh(hit, today) and "high_school" in hit):
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
            body = _text(html)
            # 出身高校はBリーグ公式の選手ページから(公式サイトのPlayerIDとBリーグのPlayerIDは同じ)
            high_school = ""
            try:
                high_school = _high_school_bleague(_text(fetch(BLEAGUE_ROSTER + pid)))
                fetched += 1
            except Exception as e:
                print(f"[WARN] 誕生日: Bリーグ公式の選手ページを読めませんでした PlayerID={pid} {e!r}")
            hit = {"team": "BLOWINDS", "name": name, "number": number, "photo": photo,
                   "birthday": _birthday_in(body), **_profile(body), "high_school": high_school,
                   "checked": today.isoformat()}
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
        if not (hit and _fresh(hit, today) and "high_school" in hit):
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
            body = _text(html)
            profile = _profile(body)
            # ユナイテッドの選手ページには出身校の欄が無い(「出身校」の文字が別の場所にあっても使わない)
            profile["school"] = ""
            hit = {"team": "UNITED", "name": name, "number": number,
                   "photo": photos.get(re.sub(r"[\s　]+", "", name), ""),
                   "birthday": _birthday_in(body), **profile, "high_school": _high_school_career(body),
                   "checked": today.isoformat()}
        out.append((key, hit))
    print(f"[誕生日] ユナイテッド: {len(out)}人(詳細ページ{fetched}件を読み込み)")
    return out


def _ruck_photo(info):
    """丸岡RUCK公式の選手紹介(standings.ruck_members)の1人分から写真のURL"""
    if isinstance(info, dict):
        return info.get("photo", "")
    return info or ""


def _ruck_info(info, key):
    """丸岡RUCK公式の選手紹介の1人分から、身長・出身地などの1項目"""
    return info.get(key, "") if isinstance(info, dict) else ""


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
        info = (ruck_photos or {}).get(key)
        out.append((f"ruck:{key}", {
            "team": "RAC", "name": name, "number": tds[1].strip(),
            "position": tds[0].strip() or _ruck_info(info, "position"),
            "photo": _ruck_photo(info),
            # 身長・出身地は丸岡RUCK公式の選手紹介から(体重・出身校は載っていない)
            "height": _ruck_info(info, "height"), "weight": "",
            "hometown": _ruck_info(info, "hometown"), "school": "", "high_school": "",
            "birthday": f"{int(bd.group(1)):04d}-{int(bd.group(2)):02d}-{int(bd.group(3)):02d}",
        }))
    print(f"[誕生日] 丸岡RUCK: {len(out)}人")
    return out


def build(fetch, old, ruck_photos=None):
    """誕生日のデータを作る。old は前回の分(無ければ {})。
    今日すでに作っていれば None(書き直さない)。チームごとに失敗しても、前回の分を残す。"""
    today = datetime.now(JST).date()
    manual, manual_sig = _load_manual()
    if old.get("date") == today.isoformat() and old.get("v") == VERSION and old.get("manual", "") == manual_sig:
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
    # 丸岡RUCKの選手紹介(写真・身長・出身地)を読めなかったときは、前回の分の写真などを引き継ぐ
    if not ruck_photos:
        for k, v in entries:
            old = prev.get(k)
            if k.startswith("ruck:") and old:
                for kk in ("photo", "height", "hometown"):
                    if not v.get(kk) and old.get(kk):
                        v[kk] = old[kk]
    new_cache = {k: v for k, v in entries if not k.startswith("ruck:")}
    players = []
    for k, v in entries:
        if not v.get("birthday") or not v.get("name"):
            continue
        p = {kk: vv for kk, vv in v.items() if kk != "checked"}
        p["_key"] = k
        # 手で直した項目で上書きする
        fix = (manual.get(p.get("team", "")) or {}).get(re.sub(r"[\s　]+", "", p["name"]))
        if isinstance(fix, dict):
            for kk in MANUAL_KEYS:
                if fix.get(kk):
                    p[kk] = str(fix[kk])
        players.append(p)
    players.sort(key=lambda p: (p["birthday"][5:], p["team"], p["name"]))
    return {"date": today.isoformat(), "v": VERSION, "manual": manual_sig, "updated_at": datetime.now(timezone.utc).isoformat(),
            "players": players, "cache": new_cache}
