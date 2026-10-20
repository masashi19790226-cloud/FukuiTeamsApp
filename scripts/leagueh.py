"""
福井永平寺ブルーサンダー(男子ハンドボール・リーグH)のデータを、リーグH公式サイト(leagueh.jp)と
クラブ公式サイト(fukuieiheijihand.co.jp)から読み取る部品。

- 試合結果 : リーグH公式の星取表(/rankings/matrix/)の福井永平寺の行 → 試合ごとのページ(/schedule/511M11/ など)
- 順位表   : リーグH公式の順位表(/rankings/)。男子の表(勝ち点=勝ち2・引き分け1)
- 選手の得点: 試合ごとのページの「スコア」の表(福井永平寺の選手の分)を足し合わせる
- 日程     : クラブ公式サイトの試合スケジュール(/schedule/)の表

check_results.py(試合結果)・build_previews.py(順位表・得点)・update_schedule.py(日程)から使う。
読み込み(fetch)は呼ぶ側の関数を渡してもらい、ここでは文字の読み取りだけをする。
"""

import html as htmllib
import re
from datetime import datetime

LEAGUEH = "https://leagueh.jp"
MATRIX_URL = LEAGUEH + "/rankings/matrix/"
RANKINGS_URL = LEAGUEH + "/rankings/"
CLUB_SITE = "https://www.fukuieiheijihand.co.jp"
CLUB_SCHEDULE_URL = CLUB_SITE + "/schedule/"
# リーグH公式のURLでの福井永平寺の呼び名(/team/bluethunder/)
TEAM_SLUG = "bluethunder"


def _text(fragment: str) -> str:
    """タグを外して空白をまとめた文字"""
    t = re.sub(r"<!--.*?-->", " ", fragment or "", flags=re.S)
    t = re.sub(r"<[^>]+>", " ", t)
    t = htmllib.unescape(t).replace("　", " ")
    return re.sub(r"\s+", " ", t).strip()


def _int(s):
    s = (s or "").strip().replace("−", "-").replace("－", "-")
    return int(s) if re.fullmatch(r"-?\d+", s) else None


# ---------- 星取表(福井永平寺の行の試合) ----------

def matrix_games(page: str):
    """星取表の福井永平寺の行から、終わった試合の一覧。
    [{"code": "511M11", "url": "https://leagueh.jp/schedule/511M11/", "my": 26, "opp": 28}, ...]
    (1つのマスに同じ相手との2試合が入ることがある)"""
    body = page.split("<tbody", 1)[1] if "<tbody" in page else page
    for tr in re.findall(r"<tr\b[^>]*>(.*?)</tr>", body, flags=re.S | re.I):
        head = re.search(r"<th\b[^>]*>(.*?)</th>", tr, flags=re.S | re.I)
        if not head or f"/team/{TEAM_SLUG}/" not in head.group(1):
            continue
        out = []
        for m in re.finditer(
            r"<a\b[^>]*href=[\"'](?:https?://leagueh\.jp)?/schedule/(\w+)/?[\"'][^>]*>\s*<b>\s*(\d+)\s*</b>\s*<span>[^<]*</span>\s*<b>\s*(\d+)\s*</b>",
            tr, flags=re.S | re.I,
        ):
            code = m.group(1)
            if any(x["code"] == code for x in out):
                continue
            out.append({"code": code, "url": f"{LEAGUEH}/schedule/{code}/", "my": int(m.group(2)), "opp": int(m.group(3))})
        return out
    return []


# ---------- 試合ごとのページ ----------

def parse_game(page: str):
    """試合ごとのページから、日付・開始時刻・会場・両チーム・点数・前後半・福井永平寺の選手の成績。
    読み取れなければ None"""
    m = re.search(r"class=[\"']date[^\"']*[\"'][^>]*>(.*?)</p>", page, flags=re.S | re.I)
    if not m:
        return None
    nums = re.findall(r"<em>\s*([\d:]+)\s*</em>", m.group(1))
    if len(nums) < 3:
        return None
    try:
        date = datetime(int(nums[0]), int(nums[1]), int(nums[2])).strftime("%Y-%m-%d")
    except ValueError:
        return None
    time = nums[3] if len(nums) >= 4 and ":" in nums[3] else ""
    venue = re.search(r"class=[\"']venue[^\"']*[\"'][^>]*>(.*?)</p>", page, flags=re.S | re.I)
    # 両チーム(左がホーム)
    teams = re.findall(r"<div class=[\"']col team[\"']>\s*<a\b[^>]*href=[\"']/team/(\w+)/?[\"'][^>]*>(.*?)</a>", page, flags=re.S | re.I)
    # 点数は日付の後の <strong> の中の <b> 2つ
    score_part = page[m.end():]
    sm = re.search(r"<strong>\s*<b[^>]*>\s*(\d+)\s*</b>.*?<b[^>]*>\s*(\d+)\s*</b>\s*</strong>", score_part, flags=re.S | re.I)
    halves = re.findall(r"<li>\s*<b>\s*(\d+)\s*</b>\s*<span>\s*([^<]+?)\s*</span>\s*<b>\s*(\d+)\s*</b>\s*</li>",
                        score_part[:3000], flags=re.S | re.I)
    if len(teams) < 2 or not sm:
        return None
    home_slug, away_slug = teams[0][0], teams[1][0]
    home_score, away_score = int(sm.group(1)), int(sm.group(2))
    fukui_home = home_slug == TEAM_SLUG
    if not fukui_home and away_slug != TEAM_SLUG:
        return None
    return {
        "date": date,
        "time": time,
        "venue": _text(venue.group(1)) if venue else "",
        "is_home": fukui_home,
        "opponent": _text(teams[1][1] if fukui_home else teams[0][1]),
        "my": home_score if fukui_home else away_score,
        "opp": away_score if fukui_home else home_score,
        # 前半・後半(延長があればそれも)。福井永平寺側の点数を先に
        "periods": [
            {"label": lab, "my": int(a) if fukui_home else int(b), "opp": int(b) if fukui_home else int(a)}
            for a, lab, b in halves
        ],
        "players": game_players(page),
    }


def game_players(page: str):
    """試合ごとのページの「スコア」の表のうち、福井永平寺の選手の分。
    [{"name","number","position","goals","shots","saves","save_total"}, ...]
    列は ポジション・背番号・選手・フィールドシュート(得点・シュート・阻止・総数)・7mスロー(得点・シュート・阻止・総数)・警告・退場・失格"""
    for sec in re.findall(r"<section>(.*?)</section>", page.split("class=\"score\"", 1)[-1], flags=re.S | re.I):
        h1 = re.search(r"<h1\b[^>]*>(.*?)</h1>", sec, flags=re.S | re.I)
        if not h1 or f"/team/{TEAM_SLUG}/" not in h1.group(0):
            continue
        body = re.search(r"<tbody>(.*?)</tbody>", sec, flags=re.S | re.I)
        if not body:
            return []
        out = []
        for tr in re.findall(r"<tr\b[^>]*>(.*?)</tr>", body.group(1), flags=re.S | re.I):
            tds = re.findall(r"<td\b[^>]*>(.*?)</td>", tr, flags=re.S | re.I)
            if len(tds) < 11:
                continue
            cells = [_text(x) for x in tds]
            name = re.sub(r"\s+", " ", cells[2]).strip()
            if not name:
                continue
            v = [(_int(c) or 0) for c in cells[3:11]]
            out.append({
                "name": name, "number": cells[1], "position": cells[0],
                "goals": v[0] + v[4], "shots": v[1] + v[5],
                "saves": v[2] + v[6], "save_total": v[3] + v[7],
            })
        return out
    return []


# ---------- 順位表 ----------

def rankings(page: str):
    """リーグH公式の順位表(男子)。チーム・勝ち点・試合数・勝・分・負・得点・失点・得失点。
    福井永平寺が入っていなければ(女子の表などを読んだとき)空"""
    m = re.search(r"<table\b[^>]*class=[\"'][^\"']*ranking-table[^\"']*[\"'][^>]*>(.*?)</table>", page, flags=re.S | re.I)
    if not m:
        return []
    body = re.search(r"<tbody>(.*?)</tbody>", m.group(1), flags=re.S | re.I)
    rows = []
    for tr in re.findall(r"<tr\b[^>]*>(.*?)</tr>", body.group(1) if body else "", flags=re.S | re.I):
        rank = re.search(r"<th\b[^>]*>(.*?)</th>", tr, flags=re.S | re.I)
        team_td = re.search(r"<td\b[^>]*class=[\"']team[\"'][^>]*>(.*?)</td>", tr, flags=re.S | re.I)
        if not rank or not team_td:
            continue
        rest = [_text(x) for x in re.findall(r"<td\b[^>]*>(.*?)</td>", tr[team_td.end():], flags=re.S | re.I)]
        if len(rest) < 8:
            continue
        nums = [_int(x) for x in rest[:8]]
        if None in nums:
            continue
        slug = re.search(r"/team/(\w+)/", team_td.group(1))
        rows.append({
            "rank": _int(_text(rank.group(1))) or len(rows) + 1,
            "team": _text(team_td.group(1)),
            "slug": slug.group(1) if slug else "",
            "points": nums[0], "played": nums[1], "win": nums[2], "draw": nums[3], "lose": nums[4],
            "gf": nums[5], "ga": nums[6], "gd": nums[7],
        })
    if not any(r["slug"] == TEAM_SLUG for r in rows):
        return []
    return rows


def standings_from(rank_page: str, form_by_slug=None):
    """standings.json の "THUNDER" に入れる形(丸岡RUCKの順位表と同じ形)。読めなければ None"""
    rows = rankings(rank_page)
    if not rows:
        return None
    table = []
    for r in rows:
        row = {k: r[k] for k in ("rank", "team", "points", "played", "win", "draw", "lose", "gf", "ga", "gd")}
        row["record"] = f"{r['win']}勝{r['lose']}敗" + (f"{r['draw']}分" if r["draw"] else "")
        form = (form_by_slug or {}).get(r["slug"])
        if form:
            row["form"] = form
        table.append(row)
    return {
        "league": "リーグH(男子)",
        "source_url": RANKINGS_URL,
        "note": "リーグH公式の順位表(勝ち点は勝ち2・引き分け1)",
        "regular": {"label": "レギュラーシーズン", "table": table},
    }


# ---------- 選手の得点(試合ごとのページを足し合わせる) ----------

def scorers_from(cache: dict):
    """試合ごとの選手の成績(cache: 試合番号 → parse_game の結果)を足して、得点の表を作る。
    standings.json の "THUNDER" の "scorers" に入れる形(丸岡RUCKと同じ形)。試合が無ければ None"""
    total = {}
    games = [g for g in cache.values() if isinstance(g, dict) and g.get("players")]
    for g in games:
        for p in g["players"]:
            key = re.sub(r"[\s　]+", "", p["name"])
            t = total.setdefault(key, {"name": p["name"], "team": "福井永平寺ブルーサンダー", "goals": 0, "shots": 0,
                                       "games": 0, "number": p.get("number", ""), "position": p.get("position", "")})
            t["goals"] += p.get("goals", 0)
            t["shots"] += p.get("shots", 0)
            t["games"] += 1
            # 背番号・ポジションは一番新しい試合のものにする
            if g.get("date", "") >= t.get("_date", ""):
                t["_date"] = g.get("date", "")
                t["number"] = p.get("number", "") or t["number"]
                t["position"] = p.get("position", "") or t["position"]
    if not total:
        return None
    rows = sorted(total.values(), key=lambda r: (-r["goals"], -r["shots"], _int(r["number"]) or 999))
    rank, prev = 0, None
    for i, r in enumerate(rows):
        r.pop("_date", None)
        if r["goals"] != prev:
            rank, prev = i + 1, r["goals"]
        r["rank"] = rank
    return {
        "label": "今季の得点",
        "note": f"リーグH公式の試合ごとの記録({len(games)}試合)から数えた数字です。得点・シュートは7mスローを含みます。",
        "source_url": MATRIX_URL,
        "rows": rows,
    }


def form_of(cache: dict, n: int = 5):
    """福井永平寺の直近 n 試合の勝敗(古い順。例「●○○」)"""
    games = sorted((g for g in cache.values() if isinstance(g, dict) and "my" in g), key=lambda g: g.get("date", ""))
    marks = ["○" if g["my"] > g["opp"] else ("●" if g["my"] < g["opp"] else "△") for g in games]
    return "".join(marks[-n:])


# ---------- クラブ公式サイトの試合スケジュール ----------

def club_schedule(page: str):
    """クラブ公式サイトの試合スケジュールの表。
    [{"month","day","time","opponent","venue","is_home","start_year"}, ...]
    年は表の上の見出し「LEAGUE H 2026-27」の最初の年から決める(7月以降はその年、6月以前は次の年)"""
    out = []
    # 見出し(大会名)ごとに表がある。見出しの年が読めない表は使わない
    parts = re.split(r"(<h3\b[^>]*class=[\"'][^\"']*scheduleTtl[^\"']*[\"'][^>]*>.*?</h3>)", page, flags=re.S | re.I)
    start_year = None
    for part in parts:
        h = re.match(r"<h3\b", part, flags=re.I)
        if h:
            title = _text(part)
            # 全角の数字・ハイフンも読む
            title = title.translate(str.maketrans("０１２３４５６７８９－", "0123456789-"))
            ym = re.search(r"(20\d{2})\s*[-/]\s*(\d{2})", title)
            start_year = int(ym.group(1)) if ym else None
            continue
        if start_year is None:
            continue
        for table in re.findall(r"<table\b[^>]*>(.*?)</table>", part, flags=re.S | re.I):
            for tr in re.findall(r"<tr\b[^>]*>(.*?)</tr>", table, flags=re.S | re.I):
                tds = re.findall(r"<td\b[^>]*>(.*?)</td>", tr, flags=re.S | re.I)
                if len(tds) < 4:
                    continue
                dm = re.search(r"(\d{1,2})\s*/\s*(\d{1,2})", _text(tds[0]))
                if not dm:
                    continue
                place = tds[3]
                tag = re.search(r"<span\b[^>]*class=[\"'][^\"']*tag[^\"']*[\"'][^>]*>(.*?)</span>", place, flags=re.S | re.I)
                ha = _text(tag.group(1)).upper() if tag else ""
                venue = _text(place[tag.end():] if tag else place)
                tm = re.search(r"\d{1,2}:\d{2}", _text(tds[1]))
                out.append({
                    "month": int(dm.group(1)), "day": int(dm.group(2)),
                    "time": tm.group(0) if tm else "",
                    "opponent": _text(tds[2]),
                    "venue": venue,
                    "is_home": ha == "HOME",
                    "start_year": start_year,
                })
    return out


def season_year(start_year: int, month: int) -> int:
    """「2026-27」のシーズンの month 月の年(7月以降は2026年、6月以前は2027年)"""
    return start_year if month >= 7 else start_year + 1
