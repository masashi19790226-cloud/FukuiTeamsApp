"""
3チームの公式サイトから試合日程を読み取り、data/games.json を自動で更新する。

- ブローウィンズ : 公式サイトの試合日程一覧(月ごと)
- 丸岡RUCK       : 公式サイトの「対戦スケジュール / 試合結果」の表
- ユナイテッド   : 公式サイトの月間スケジュール(「◯◯KO vs.相手@会場」の行)

動き:
- まだ games.json に無い試合 → 新しく追加
- 既にある試合(同じチーム・同じ日付) → 「時間未定」だった開始時刻や
  「調整中」だった会場が決まっていれば更新。RUCKの試合番号(gid)が
  分かれば追記。それ以外(チーム名の表記など)は手で直した内容を尊重して触らない
- サイトから消えた試合は削除しない(延期などで日付が変わった場合は、
  古い方が残るので手で消す)

サイトの構造が変わると読めなくなるので、GitHub Actionsのログを見ながら調整する。
"""

import html as htmllib
import json
import os
import re
import urllib.request
from datetime import datetime, timedelta

BASE_DIR = os.path.join(os.path.dirname(__file__), "..", "data")
GAMES_PATH = os.path.join(BASE_DIR, "games.json")

BLOWINDS_LIST_URL = "https://www.fukuiblowinds.com/schedule/list/?year={year}&month={month}&scheduledate=upcoming"
RUCK_URL = "https://ruck-fukui.com/schedules-results"
UNITED_URLS = ["https://fukuiunited.co.jp/team/schedule.php", "https://fukuiunited.co.jp/"]

WEEKDAY_JP = ["月", "火", "水", "木", "金", "土", "日"]
EN_WEEKDAYS = "Mon|Tue|Wed|Thu|Fri|Sat|Sun"
ID_PREFIX = {"blowinds": "bw", "rac": "rc", "united": "un"}

# ホーム判定に使う会場名のキーワード(ユナイテッド用。RUCKはセーレン・ドリームアリーナのみホーム)
FUKUI_VENUE_WORDS = ["福井", "セーレン", "テクノポート", "9.98", "９．９８", "丸岡", "坂井", "敦賀", "鯖江", "越前", "武生", "三国", "大野", "勝山", "小浜"]

NOW = datetime.utcnow() + timedelta(hours=9)  # 日本時間


# ---------- 共通 ----------

def fetch_html(url: str) -> str:
    req = urllib.request.Request(
        url,
        headers={
            "User-Agent": (
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                "AppleWebKit/537.36 (KHTML, like Gecko) "
                "Chrome/120.0 Safari/537.36 FukuiTeamsAppBot/1.0"
            )
        },
    )
    with urllib.request.urlopen(req, timeout=20) as res:
        return res.read().decode("utf-8", errors="ignore")


def safe_fetch(name, url):
    try:
        page = fetch_html(url)
        print(f"[INFO] {name}: {url} を取得 ({len(page)} 文字)")
        return page
    except Exception as e:
        print(f"[WARN] {name}: {url} の取得に失敗 {e}")
        return None


def html_to_lines(page: str):
    page = re.sub(r"(?is)<(script|style).*?</\1>", "\n", page)
    text = re.sub(r"<[^>]+>", "\n", page)
    text = htmllib.unescape(text).replace("\u3000", " ")
    return [re.sub(r"\s+", " ", ln).strip() for ln in text.splitlines() if ln.strip()]


def clean(text: str) -> str:
    text = htmllib.unescape(re.sub(r"<[^>]+>", " ", text))
    return re.sub(r"\s+", " ", text).strip()


def is_fukui_venue(venue: str) -> bool:
    return any(w in venue for w in FUKUI_VENUE_WORDS)


def valid_time(t: str) -> bool:
    return bool(re.fullmatch(r"\d{1,2}:\d{2}", t or ""))


def make_game(team, y, m, d, time, opponent, venue, is_home, **extra):
    time = time.zfill(5) if valid_time(time) else (time or "時間未定")
    g = {
        "team": team,
        "date": f"{y:04d}-{m:02d}-{d:02d}",
        "time": time,
        "day_of_week": WEEKDAY_JP[datetime(y, m, d).weekday()],
        "opponent": opponent,
        "venue": venue or "調整中",
        "is_home": is_home,
    }
    g.update({k: v for k, v in extra.items() if v})
    return g


def season_start_year(start_month: int) -> int:
    """シーズンが start_month 月に始まる競技の、今シーズンの開始年。"""
    return NOW.year if NOW.month >= start_month - 2 else NOW.year - 1


# ---------- ブローウィンズ ----------

BW_PATTERN = re.compile(
    r"(HOME|AWAY)\s+(\S+)\s+福井\s+福井\s+(\d{1,2})/(\d{1,2})\s*\(([月火水木金土日])\)\s*"
    r"(\S+)?\s*location_on\s+(.+?)\s+(\S+)\s+\8(?=\s|$)"
)


def scrape_blowinds():
    games = {}
    start = season_start_year(9)  # B.LEAGUEは9月開幕・翌5月ごろ終了
    y, m = NOW.year, NOW.month
    for _ in range(9):  # 今月から9か月先まで
        page = safe_fetch("BW", BLOWINDS_LIST_URL.format(year=y, month=m))
        if page:
            joined = " ".join(html_to_lines(page))
            for mt in BW_PATTERN.finditer(joined):
                home_away, kind, mo, d, _dow, time, venue, opp = mt.groups()
                if "プレシーズン" in kind:
                    continue
                mo, d = int(mo), int(d)
                year = start if mo >= 7 else start + 1
                g = make_game("blowinds", year, mo, d, time or "時間未定", opp, venue.strip(), home_away == "HOME")
                games[g["date"]] = g
        m += 1
        if m > 12:
            y, m = y + 1, 1
    print(f"[INFO] BW: {len(games)} 試合を読み取り")
    return list(games.values())


# ---------- 丸岡RUCK ----------

def scrape_ruck():
    page = safe_fetch("RUCK", RUCK_URL)
    if not page:
        return []
    ym = re.search(r"リーグ\s*(\d{4})-\d{2}", clean(page))
    start = int(ym.group(1)) if ym else season_start_year(6)

    games = []
    for row in re.findall(r"(?is)<tr[^>]*>(.*?)</tr>", page):
        cells = re.findall(r"(?is)<td[^>]*>(.*?)</td>", row)
        if len(cells) < 5:
            continue  # 見出し行など
        date_text = clean(cells[1])
        dm = re.search(r"(\d{1,2})月(\d{1,2})日", date_text)
        if not dm:
            continue
        mo, d = int(dm.group(1)), int(dm.group(2))
        year = start if mo >= 5 else start + 1
        time = clean(cells[2])
        opp = clean(cells[3])
        venue = clean(cells[4])
        gid_m = re.search(r"gid=(\d+)", cells[5]) if len(cells) > 5 else None
        if not opp:
            continue
        # RUCKはセーレン・ドリームアリーナ(福井県営体育館)開催だけがホーム。
        # 福井県内の別会場でも、それ以外はすべてアウェイ扱い
        is_home = "セーレン" in venue or "福井県営体育館" in venue
        games.append(make_game("rac", year, mo, d, time, opp, venue, is_home,
                               gid=gid_m.group(1) if gid_m else None))
    print(f"[INFO] RUCK: {len(games)} 試合を読み取り")
    return games


# ---------- 手動の日程(scripts/manual_schedule.json) ----------

MANUAL_PATH = os.path.join(os.path.dirname(__file__), "manual_schedule.json")


def scrape_manual():
    """ポスターなどから手で入れた日程。公式サイトの情報を優先し、足りない試合・時刻・会場だけを補う。"""
    if not os.path.exists(MANUAL_PATH):
        return []
    with open(MANUAL_PATH, "r", encoding="utf-8") as f:
        rows = json.load(f).get("games", [])
    games = []
    for r in rows:
        y, m, d = (int(x) for x in r["date"].split("-"))
        extra = {"placeholder_until": r.get("placeholder_until"), "placeholder_from": r.get("placeholder_from")}
        games.append(make_game(r["team"], y, m, d, r.get("time", ""), r["opponent"], r.get("venue", ""),
                               bool(r.get("is_home")), **extra))
    print(f"[INFO] 手動の日程: {len(games)} 件を確認")
    return games


def remove_settled_placeholders(games):
    """仮の行(placeholder_until 付き)は、その期間に本当の試合が入ったら消す。"""
    real = [g for g in games if not g.get("placeholder_until")]
    kept, removed = [], 0
    for g in games:
        until = g.get("placeholder_until")
        start = g.get("placeholder_from") or g["date"]
        if until and any(r["team"] == g["team"] and start <= r["date"] <= until for r in real):
            removed += 1
            print(f"[INFO] 仮の行を削除: {g['id']} {g['date']} {g['opponent']}")
            continue
        kept.append(g)
    games[:] = kept
    return removed


# ---------- 福井ユナイテッド ----------

def parse_united_page(page: str):
    """
    月間スケジュール:  「2026.9」→「6 (Sun)」→「…13:00KO vs.相手@会場」
    トップページ    :  「09.27 (Sun)」→「…11:00KO vs.相手@会場」
    の2種類の並びに対応する。
    """
    lines = html_to_lines(page)
    games = []
    year, month, day = NOW.year, None, None
    i = 0
    while i < len(lines):
        ln = lines[i]
        mh = re.fullmatch(r"(\d{4})\.(\d{1,2})", ln)
        md = re.fullmatch(rf"(\d{{1,2}})\s*\(({EN_WEEKDAYS})\)", ln)
        mmd = re.fullmatch(rf"(\d{{2}})\.(\d{{2}})\s*\(({EN_WEEKDAYS})\)", ln)
        if mh:
            year, month = int(mh.group(1)), int(mh.group(2))
        elif md and month:
            day = int(md.group(1))
        elif mmd:
            month, day = int(mmd.group(1)), int(mmd.group(2))
            # 年をまたぐ表示(12月に1月の予定など)への対応
            year = NOW.year + 1 if month < NOW.month - 6 else NOW.year
        elif "KO" in ln and "vs" in ln and month and day:
            buf = ln
            j = i + 1
            # 「vs.」と相手名、「@会場」がタグで分割されている場合があるのでつなげる
            while j < len(lines) and j <= i + 3 and ("@" not in buf or buf.rstrip().endswith("@")):
                buf += " " + lines[j]
                j += 1
            ev = re.search(r"(\d{1,2}:\d{2})\s*KO\s*vs\.?\s*(.+?)\s*[@＠]\s*(.+)", buf)
            if ev:
                time, opp, venue = ev.group(1), ev.group(2).strip(), ev.group(3).strip()
                games.append(make_game("united", year, month, day, time, opp, venue, is_fukui_venue(venue)))
        i += 1
    return games


UNITED_ASYNC = "https://fukuiunited.co.jp/system/async/async.php"


def united_api_matches():
    """ユナイテッド公式サイトの試合一覧(試合情報ページが読み込んでいるデータ)を全部取る。
    1件ごとに kickoff(「2026/04/05 12:00」)・opponent.name・place(home_away・name)・score などが入っている。"""
    def post(body):
        req = urllib.request.Request(
            UNITED_ASYNC, data=json.dumps(body).encode("utf-8"), method="POST",
            headers={"User-Agent": "Mozilla/5.0 FukuiTeamsAppBot/1.0", "Content-Type": "application/json"},
        )
        with urllib.request.urlopen(req, timeout=20) as res:
            return json.loads(res.read().decode("utf-8", errors="ignore"))

    pt = post({"className": "PostTypes", "method": "get", "slug": ["match"], "post_type_options": True})
    ptid = pt["data"][0]["post_type_id"]
    res = post({"className": "MatchInfo", "method": "get", "post_type_id": [ptid], "category": "",
                "orderby": [{"column": "kickoff", "order": "asc"}], "offset": 0, "limit": 300})
    return res.get("data", []) or []


def scrape_united_api():
    """公式サイトの試合一覧から、今年(ユナイテッドのシーズンは1月〜12月)の全試合を作る。
    すでに終わった試合(過去の試合)も含む。県選手権・天皇杯などのリーグ戦以外も入れる。"""
    games = {}
    for m in united_api_matches():
        ko = m.get("kickoff") or ""
        mk = re.match(r"(\d{4})/(\d{1,2})/(\d{1,2})\s+(\d{1,2}:\d{2})", ko)
        if not mk or int(mk.group(1)) != NOW.year:
            continue
        y, mo, d, time = int(mk.group(1)), int(mk.group(2)), int(mk.group(3)), mk.group(4)
        opp = ((m.get("opponent") or {}).get("name") or "").strip()
        place = m.get("place") or {}
        venue = (place.get("name") or "").strip()
        ha = (place.get("home_away") or "").upper()
        # HOME/AWAY が書かれていない試合(県選手権・天皇杯など)は、会場が福井県内ならホーム扱い
        is_home = True if ha == "HOME" else False if ha == "AWAY" else is_fukui_venue(venue)
        if not opp:
            continue
        g = make_game("united", y, mo, d, time, opp, venue, is_home)
        games[g["date"]] = g
    print(f"[INFO] UNITED: 公式サイトの試合一覧から {len(games)} 試合を読み取り")
    return games


def scrape_united():
    games = {}
    # まず公式サイトの試合一覧(過去の試合も含む今年の全試合)。読めなければ日程ページだけで続ける
    try:
        games.update(scrape_united_api())
    except Exception as e:
        print(f"[WARN] UNITED: 公式サイトの試合一覧の取得に失敗 {e!r}")
    pages = []
    first = safe_fetch("UNITED", UNITED_URLS[0])
    if first:
        pages.append(first)
        # 翌月分へのリンクがあればたどる(月送りのリンク先を最大2つ)
        links = []
        for href in re.findall(r'href="([^"]*schedule\.php\?[^"]+)"', first):
            url = htmllib.unescape(href)
            if url.startswith("/"):
                url = "https://fukuiunited.co.jp" + url
            elif not url.startswith("http"):
                url = "https://fukuiunited.co.jp/team/" + url
            if url not in links:
                links.append(url)
        for url in links[:2]:
            p = safe_fetch("UNITED", url)
            if p:
                pages.append(p)
    top = safe_fetch("UNITED", UNITED_URLS[1])
    if top:
        pages.append(top)

    for p in pages:
        for g in parse_united_page(p):
            # 試合一覧(API)で読めた日は、そちらを優先する
            games.setdefault(g["date"], g)
    print(f"[INFO] UNITED: {len(games)} 試合を読み取り")
    return list(games.values())


# ---------- games.json への反映 ----------

def sort_key_of(g):
    t = g["time"].replace(":", "") if valid_time(g["time"]) else "0000"
    return g["date"].replace("-", "") + "-" + t.zfill(4)


def next_id(games, team):
    prefix = ID_PREFIX[team]
    nums = [int(g["id"][len(prefix):]) for g in games
            if g["id"].startswith(prefix) and g["id"][len(prefix):].isdigit()]
    return f"{prefix}{(max(nums) + 1 if nums else 1):02d}"


def merge(games, scraped):
    added, updated = 0, 0
    for s in scraped:
        until = s.get("placeholder_until")
        # 仮の行は、その期間にもう本当の試合があれば入れない
        start = s.get("placeholder_from") or s["date"]
        if until and any(g.get("team") == s["team"] and not g.get("placeholder_until")
                         and start <= g.get("date", "") <= until for g in games):
            continue
        same = [g for g in games if g.get("team") == s["team"] and g.get("date") == s["date"]]
        if not same:
            new = {"id": next_id(games, s["team"]), **s,
                   "ticket_status": "情報なし", "ticket_sale_start": "-"}
            new["sort_key"] = sort_key_of(new)
            games.append(new)
            added += 1
            print(f"[INFO] 追加: {new['id']} {new['date']} {new['time']} vs {new['opponent']} @{new['venue']}")
            continue

        g = same[0]
        changes = []
        # 仮の行と同じ日に本当の試合が見つかったら、仮の行を本当の試合の内容に置き換える
        if g.get("placeholder_until") and not s.get("placeholder_until"):
            for key in ["time", "day_of_week", "opponent", "venue", "is_home", "gid"]:
                if s.get(key) is not None:
                    g[key] = s[key]
            g.pop("placeholder_until", None)
            g.pop("placeholder_from", None)
            g["sort_key"] = sort_key_of(g)
            updated += 1
            print(f"[INFO] 仮の行を確定: {g['id']} {g['date']} vs {g['opponent']}")
            continue
        if not valid_time(g.get("time", "")) and valid_time(s["time"]):
            g["time"] = s["time"]
            g["sort_key"] = sort_key_of(g)
            changes.append(f"開始時刻 {s['time']}")
        if any(w in g.get("venue", "") for w in ["調整中", "未定"]) and s["venue"] and "調整中" not in s["venue"]:
            g["venue"] = s["venue"]
            changes.append(f"会場 {s['venue']}")
        if s.get("gid") and not g.get("gid"):
            g["gid"] = s["gid"]
            changes.append(f"gid {s['gid']}")
        if changes:
            updated += 1
            print(f"[INFO] 更新: {g['id']} {g['date']} ({', '.join(changes)})")
    return added, updated


def main():
    with open(GAMES_PATH, "r", encoding="utf-8") as f:
        games = json.load(f)

    total_added = total_updated = 0
    # 公式サイトを先に読み、最後に手動の日程で足りない分を補う
    for name, scraper in [("BW", scrape_blowinds), ("RUCK", scrape_ruck), ("UNITED", scrape_united), ("MANUAL", scrape_manual)]:
        try:
            scraped = scraper()
        except Exception as e:
            # 1チームで失敗しても他のチームの更新は続ける
            print(f"[WARN] {name}: 読み取り中にエラー {e}")
            continue
        a, u = merge(games, scraped)
        total_added += a
        total_updated += u

    removed = remove_settled_placeholders(games)
    if total_added or total_updated or removed:
        games.sort(key=lambda g: (list(ID_PREFIX).index(g.get("team", "blowinds")), g.get("sort_key", "")))
        with open(GAMES_PATH, "w", encoding="utf-8") as f:
            f.write("[\n" + ",\n".join("  " + json.dumps(g, ensure_ascii=False) for g in games) + "\n]\n")
    print(f"[INFO] 追加 {total_added} 件 / 更新 {total_updated} 件")


if __name__ == "__main__":
    main()
