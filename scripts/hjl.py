"""
ヴェルコスタ福井(男子ホッケー・ホッケー日本リーグ サムライリーグD1)のデータを、
ホッケージャパンリーグ公式サイト(hjl-hockey.tv)から読み取る部品。

- 日程・結果 : 日程・結果のページ(/schedule/)の表のうち、福井のロゴ(teamlogo/<年>/4.png)がある行
- 試合の記録 : 試合詳細のページ(/match_detail/?matchID=…)。クォーターごとの点数・SO・得点者・メンバー表・観客数
- 順位表     : 順位表・成績のページ(/ranking/)の表のうち、福井が入っている表
- 選手       : チーム・選手のページ(/teams?teamID=4&teamYear=<年>)
- ニュース   : ニュースのページ(/news/)のうち、題名に「福井」「ヴェルコスタ」がある記事

ホーム・アウェイ・中立は、公式サイトの表には書かれていないので、会場で決める(home_away)。
  越前町営(福井県の会場)= ホーム / 相手の本拠地の県の会場 = アウェイ / それ以外 = 中立(セントラル開催)
例外は scripts/manual_schedule.json の "home_away_overrides" で上書きする(update_schedule.py)。

引き分けはSO(シュートアウト)で決着する。点数(my・opp)は第4クォーターまでの点数、SOは so_my・so_opp に入れる。

update_schedule.py(日程)・check_results.py(試合結果)・build_previews.py(順位表・得点)・history.py(過去の記録)・
check_alerts.py(ニュース)から使う。読み込み(fetch)は呼ぶ側の関数を渡してもらい、ここでは文字の読み取りだけをする
(leagueh.py と同じ形)。
"""

import html as htmllib
import re
from datetime import datetime

HJL = "https://hjl-hockey.tv"
SCHEDULE_URL = HJL + "/schedule/"
RANKING_URL = HJL + "/ranking/"
NEWS_URL = HJL + "/news/"
# 公式サイトでのヴェルコスタ福井の番号(ロゴの画像の名前・チームのページの teamID)
TEAM_ID = "4"
TEAM_NAME = "ヴェルコスタ福井"
HOME_VENUE = "越前町営朝日総合運動場"
# 日程・結果のページは、スマホから見たとき(User-Agent が Android など)は表の作りが違い、ステージ名(「7位決定戦」など)が
# 載らない。パソコンから見たときの表で読むので、読み込むときはこの見出し(User-Agent)を付ける
HEADERS = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                         "Chrome/120.0 Safari/537.36 FukuiTeamsAppBot/1.0"}
HOME_PREF = "福井"

# チームの番号(ロゴの画像の名前)→ (正式な名前, 本拠地の県)。2026年のサムライリーグD1・D2
TEAMS = {
    "4": (TEAM_NAME, "福井"),
    "6": ("GIFU ASAHI BLUE DEVILS", "岐阜"),
    "187": ("アルカディア奈良", "奈良"),
    "11": ("立命館ホリーズ", "大阪"),
    "8": ("LIEBE栃木", "栃木"),
    "12": ("Selrio島根", "島根"),
    "16": ("山梨学院OCTOBER EAGLES", "山梨"),
    "13": ("BlueSticks SHIGA", "滋賀"),
    "2": ("ALDER飯能", "埼玉"),
    "10": ("小矢部RED OX", "富山"),
    "9": ("東京農業大学", "東京"),
    "28": ("フリークス東京", "東京"),
    "14": ("駿河台大学", "埼玉"),
    # 2022〜2025年の対戦相手(名前は公式の試合詳細のページのもの)。表示灯フラーテルは本拠地の県が公式サイトに無いので空
    "3": ("表示灯フラーテルホッケーチーム", ""),
    "5": ("福井工業大学", "福井"),
    "7": ("法政大学", "東京"),
}

# 過去の記録に入れる年(この年から去年まで。日程・結果のページは /schedule2025/ など)。
# 2021年以前は表の作りが違う(会場に県が無い・試合詳細のURLが違う)ので入れていない
PAST_FROM_YEAR = 2022

# ホーム・アウェイ・中立を会場から決められない試合(試合の番号 → "H"・"A"・"N")
#  1759:2022/5/22 三成公園(島根)の表示灯フラーテル戦。相手の本拠地が分からないので中立にする(作者が決めた)
HA_FIXED = {"1759": "N"}

# 日程の表の短い会場名 → 正式な会場名(公式サイトの「スタジアム」のページから)
VENUES = {
    "越前町営": HOME_VENUE,
    "大井": "大井ホッケー競技場",
    "山梨学院": "山梨学院ホッケースタジアム",
    "小矢部": "小矢部ホッケーフィールド",
    "川崎重工": "川崎重工ホッケースタジアム",
    "OSP": "OSPホッケースタジアム",
    "親里": "親里ホッケー場",
    "OIC": "立命館OICフィールド",
    "三成公園": "三成公園ホッケー場",
    "広島広域": "広島広域公園第二球技場",
}

# タブの名前(日程のページの <div id="…">)→ リーグの名前
DIVISIONS = {"women": "さくらリーグ", "h1": "サムライリーグD1", "h2": "サムライリーグD2"}


def schedule_url(year: int) -> str:
    """その年の日程・結果のページ(今年は /schedule/、去年までは /schedule2025/ など)"""
    from datetime import date
    return SCHEDULE_URL if year >= date.today().year else f"{HJL}/schedule{year}/"


def team_url(year: int) -> str:
    return f"{HJL}/teams?teamID={TEAM_ID}&teamYear={year}"


def match_url(mid) -> str:
    return f"{HJL}/match_detail/?matchID={mid}"


def _text(fragment: str) -> str:
    """タグを外して空白をまとめた文字"""
    t = re.sub(r"<!--.*?-->", " ", fragment or "", flags=re.S)
    t = re.sub(r"<[^>]+>", " ", t)
    t = htmllib.unescape(t).replace("　", " ").replace("\xa0", " ")
    return re.sub(r"\s+", " ", t).strip()


def _int(s):
    s = (s or "").strip().replace("−", "-").replace("－", "-")
    return int(s) if re.fullmatch(r"-?\d+", s) else None


def _td(row: str, cls: str):
    """行の中の <td class="cls …"> の中身(最初の1つ)。無ければ空"""
    m = re.search(rf"<td\b[^>]*class=[\"']{cls}(?:\s[^\"']*)?[\"'][^>]*>(.*?)</td>", row, flags=re.S | re.I)
    return m.group(1) if m else ""


# ---------- ホーム・アウェイ・中立 ----------

def home_away(venue_short: str, pref: str, opp_id: str):
    """会場から (ホームか, 中立か) を決める。
    越前町営・福井県の会場 = ホーム / 相手の本拠地の県の会場 = アウェイ / それ以外 = 中立。
    相手の本拠地が分からないとき(TEAMS に無いチーム)は、福井県の外ならアウェイにしておく"""
    if "越前町営" in (venue_short or "") or pref == HOME_PREF:
        return True, False
    opp_pref = TEAMS.get(str(opp_id), ("", ""))[1]
    if not opp_pref or not pref:
        return False, False
    return False, pref != opp_pref


def venue_name(venue_short: str, pref: str) -> str:
    """games.json に入れる会場名(分かる会場は正式な名前。それ以外は「阿須(埼玉)」の形)"""
    if venue_short in VENUES:
        return VENUES[venue_short]
    if venue_short and pref:
        return f"{venue_short}({pref})"
    return venue_short or ""


# ---------- 日程・結果のページ ----------

def schedule(page: str, team_id: str = TEAM_ID):
    """日程・結果のページから、福井(team_id)の試合の一覧(日付の順)。
    [{"mid","date","time","venue","venue_short","pref","division","section","stage","no","opponent","opp_id",
      "is_home","neutral","my","opp","so_my","so_opp","url","ticket_url"}, ...]
    まだ終わっていない試合は my・opp が None。相手がまだ決まっていない行(「D1 7位」など。ロゴが無い)は入らない"""
    out = []
    # タブ(さくら・サムライD1・サムライD2)と、その中の区切り(レギュラー・ファイナルステージ・入替戦)の位置
    marks = [(m.start(), "div", m.group(1)) for m in re.finditer(r"<div id=[\"'](\w+)[\"'][^>]*>\s*<dl", page)]
    marks += [(m.start(), "dt", _text(m.group(1))) for m in re.finditer(r"<dt>(.*?)</dt>", page, flags=re.S)]
    marks.sort()
    for m in re.finditer(r"<tr\b[^>]*>(.*?)</tr>", page, flags=re.S | re.I):
        row = m.group(1)
        logos = re.findall(r"teamlogo/(\d{4})/([^./\"']+)\.png", row)
        if len(logos) < 2 or team_id not in (logos[0][1], logos[1][1]):
            continue
        date_td = _td(row, "date")
        dm = re.search(r"(\d{1,2})\.(\d{1,2})", date_td)
        if not dm:
            continue
        try:
            date = datetime(int(logos[0][0]), int(dm.group(1)), int(dm.group(2))).strftime("%Y-%m-%d")
        except ValueError:
            continue
        tm = re.search(r"(\d{1,2}:\d{2})", _td(row, "time"))
        # 「00:00」は時刻がまだ決まっていない行
        time = tm.group(1).zfill(5) if tm and tm.group(1) not in ("0:00", "00:00") else ""
        venue_td = _td(row, "venue")
        vm = re.match(r"\s*(.*?)\s*(?:<br\s*/?>)?\s*[（(]\s*(.*?)\s*[）)]\s*$", venue_td, flags=re.S | re.I)
        venue_short = _text(vm.group(1)) if vm else _text(venue_td)
        pref = _text(vm.group(2)) if vm else ""
        fukui_left = logos[0][1] == team_id
        opp_id = logos[1][1] if fukui_left else logos[0][1]
        # チーム名のマス(左・右)。短い名前の後に「RS 7位」などが付くことがある
        cells = re.findall(r"<td\b[^>]*class=[\"']logo[\"'][^>]*>(.*?)</td>", row, flags=re.S | re.I)
        labels = []
        for c in cells[:2]:
            spans = re.findall(r"<span\b[^>]*>(.*?)</span>", c, flags=re.S | re.I)
            labels.append(_text(spans[0]) if spans else _text(c))
        opp_label = (labels[1] if fukui_left else labels[0]) if len(labels) == 2 else ""
        opponent = TEAMS.get(opp_id, (opp_label, ""))[0] or opp_label
        # 点数「1 - 4」。SOは「1 - 1<br><span …>(3 SO 2)</span>」
        result_td = _td(row, "result")
        sm = re.match(r"\s*(\d+)\s*-\s*(\d+)", _text(result_td))
        so = re.search(r"\(\s*(\d+)\s*SO\s*(\d+)\s*\)", _text(result_td))
        left, right = (int(sm.group(1)), int(sm.group(2))) if sm else (None, None)
        so_l, so_r = (int(so.group(1)), int(so.group(2))) if so and sm else (None, None)
        mid = re.search(r"match_detail/\?matchID=(\d+)", row)
        if not mid:
            # スマホ向けの表では、試合詳細へのリンクがすぐ下の行(class="details …")にある
            nxt = re.match(r"\s*<tr\b[^>]*>\s*<td\b[^>]*class=[\"'][^\"']*details[^\"']*[\"'][^>]*>(.*?)</tr>",
                           page[m.end():m.end() + 3000], flags=re.S | re.I)
            mid = re.search(r"match_detail/\?matchID=(\d+)", nxt.group(1)) if nxt else None
        ticket = re.search(r"<a\b[^>]*href=[\"']([^\"']+)[\"'][^>]*>\s*<span\b[^>]*>\s*チケット", row, flags=re.S | re.I)
        division, section = "", ""
        for pos, kind, val in marks:
            if pos > m.start():
                break
            if kind == "div":
                division, section = DIVISIONS.get(val, ""), ""
            else:
                section = val
        is_home, neutral = home_away(venue_short, pref, opp_id)
        fixed = HA_FIXED.get(mid.group(1) if mid else "")
        if fixed:
            is_home, neutral = fixed == "H", fixed == "N"
        out.append({
            "mid": mid.group(1) if mid else "",
            "date": date,
            "time": time,
            "venue": venue_name(venue_short, pref),
            "venue_short": venue_short,
            "pref": pref,
            "division": division,
            "section": section,
            "stage": _text(_td(row, "stage")),
            "no": _text(_td(row, "nbr")),
            "opponent": opponent,
            "opp_id": opp_id,
            "is_home": is_home,
            "neutral": neutral,
            "my": left if fukui_left else right,
            "opp": right if fukui_left else left,
            "so_my": so_l if fukui_left else so_r,
            "so_opp": so_r if fukui_left else so_l,
            "url": match_url(mid.group(1)) if mid else SCHEDULE_URL,
            "ticket_url": htmllib.unescape(ticket.group(1)) if ticket else "",
        })
    out.sort(key=lambda g: (g["date"], g["time"]))
    return out


def stage_label(row: dict) -> str:
    """games.json の "stage" に入れる言葉(レギュラーステージは空。「7位決定戦」「入替戦」など)"""
    if row.get("stage"):
        return row["stage"]
    sec = row.get("section") or ""
    if "入替" in sec:
        return sec
    return ""


def mark(my, opp, so_my=None, so_opp=None) -> str:
    """勝敗の記号(SOで決まった試合は、SOの勝ち負け)"""
    if my is None or opp is None:
        return ""
    if my != opp:
        return "○" if my > opp else "●"
    if so_my is not None and so_opp is not None and so_my != so_opp:
        return "○" if so_my > so_opp else "●"
    return "△"


# ---------- 試合詳細のページ ----------

def _lineup(table: str):
    """メンバー表。[{"name","number","position","started","played","minute","pid","captain"}, ...]
    1列目は「X」= 先発、数字 = 途中から出た時間(分)、空 = 出ていない"""
    out = []
    for tr in re.findall(r"<tr\b[^>]*>(.*?)</tr>", table, flags=re.S | re.I):
        tds = re.findall(r"<td\b[^>]*>(.*?)</td>", tr, flags=re.S | re.I)
        if len(tds) < 4:
            continue
        first, position, number = _text(tds[0]), _text(tds[1]), _text(tds[2])
        pid = re.search(r"playerID=(\d+)", tds[3])
        link = re.search(r"<a\b[^>]*>(.*?)</a>", tds[3], flags=re.S | re.I)
        name = _text(link.group(1) if link else tds[3])
        if not name or not pid:
            continue  # 監督の行・空の行
        started = first.upper() == "X"
        out.append({
            "name": name, "number": number, "position": position,
            "started": started, "played": started or first.isdigit(),
            "minute": int(first) if first.isdigit() else None,
            "pid": pid.group(1), "captain": "(C)" in _text(tds[3]),
        })
    return out


def _goals(cell: str):
    """得点の欄(片方のチームの分)。[{"minute","kind","name","pid"}, ...](kind は FG・PC・PS)"""
    out = []
    for p in re.findall(r"<p\b[^>]*>(.*?)</p>", cell, flags=re.S | re.I):
        mn = re.search(r"class=[\"']minute[\"'][^>]*>(.*?)</span>", p, flags=re.S | re.I)
        nm = re.search(r"class=[\"']player[\"'][^>]*>(.*?)</span>", p, flags=re.S | re.I)
        if not nm:
            continue
        mt = _text(mn.group(1)) if mn else ""
        mm = re.match(r"(\d+)[^A-Za-z]*([A-Za-z]+)?", mt)
        pid = re.search(r"playerID=(\d+)", p)
        out.append({
            "minute": int(mm.group(1)) if mm else None,
            "kind": (mm.group(2) or "") if mm else "",
            "name": _text(nm.group(1)),
            "pid": pid.group(1) if pid else "",
        })
    return out


def parse_match(page: str, team_id: str = TEAM_ID):
    """試合詳細のページから、日付・開始時刻・会場・相手・点数・クォーターごとの点数・SO・得点者・メンバー表・観客数。
    福井の試合でなければ None。まだ終わっていない試合は my・opp が None"""
    sc = re.search(r"<table\b[^>]*id=[\"']score[\"'][^>]*>(.*?)</table>", page, flags=re.S | re.I)
    if not sc:
        return None
    score = sc.group(1)
    logos = re.findall(r"teamlogo/\d{4}/([^./\"']+)\.png", score)
    if len(logos) < 2 or team_id not in logos[:2]:
        return None
    fukui_left = logos[0] == team_id
    names = [_text(x) for x in re.findall(r"<td\b[^>]*class=[\"']teamname[\"'][^>]*>(.*?)</td>", score, flags=re.S | re.I)]
    totals = [_int(_text(x)) for x in re.findall(r"<td\b[^>]*class=[\"']total[^\"']*[\"'][^>]*>(.*?)</td>", score, flags=re.S | re.I)]
    periods, so = [], None
    for a, lab, b in re.findall(
        r"<td\b[^>]*class=[\"']quarter[\"'][^>]*>(.*?)</td>\s*<td\b[^>]*class=[\"']index[\"'][^>]*>(.*?)</td>\s*"
        r"<td\b[^>]*class=[\"']quarter[\"'][^>]*>(.*?)</td>", score, flags=re.S | re.I,
    ):
        l, r, lab = _int(_text(a)), _int(_text(b)), _text(lab)
        if l is None or r is None:
            continue
        if lab.upper() == "SO":
            so = (l, r)
        else:
            periods.append({"label": lab, "my": l if fukui_left else r, "opp": r if fukui_left else l})
    left, right = (totals + [None, None])[:2]
    # 大会名・ステージ・日時・会場(<br> で区切ってある)
    info = re.search(r"<table\b[^>]*id=[\"']matchinfo[\"'][^>]*>(.*?)</table>", page, flags=re.S | re.I)
    lines = [_text(x) for x in re.split(r"<br\s*/?>", info.group(1), flags=re.I)] if info else []
    lines = [x for x in lines if x]
    date, time, venue, stage = "", "", "", ""
    for i, ln in enumerate(lines):
        dm = re.match(r"(\d{4})/(\d{1,2})/(\d{1,2})(?:\s+(\d{1,2}:\d{2}))?", ln)
        if dm:
            date = f"{int(dm.group(1)):04d}-{int(dm.group(2)):02d}-{int(dm.group(3)):02d}"
            time = (dm.group(4) or "").zfill(5) if dm.group(4) else ""
            venue = lines[i + 1] if i + 1 < len(lines) else ""
            # 日時の前の行:大会名 → ステージ(レギュラーステージ・ファイナルステージ)→(あれば)7位決定戦など
            stage = lines[i - 1] if i >= 3 else ""
            break
    if not date:
        return None
    detail = re.search(r"<table\b[^>]*class=[\"']table detail[\"'][^>]*>(.*?)</table>", page, flags=re.S | re.I)
    my_goals, opp_goals = [], []
    if detail:
        for tr in re.findall(r"<tr\b[^>]*>(.*?)</tr>", detail.group(1), flags=re.S | re.I):
            if "得" not in _text(re.sub(r"<td\b[^>]*class=[\"'](?:left|right)[\"'][^>]*>.*?</td>", " ", tr, flags=re.S | re.I)):
                continue
            lc = re.search(r"<td\b[^>]*class=[\"']left[\"'][^>]*>(.*?)</td>", tr, flags=re.S | re.I)
            rc = re.search(r"<td\b[^>]*class=[\"']right[\"'][^>]*>(.*?)</td>", tr, flags=re.S | re.I)
            lg, rg = _goals(lc.group(1) if lc else ""), _goals(rc.group(1) if rc else "")
            my_goals, opp_goals = (lg, rg) if fukui_left else (rg, lg)
            break
    lt = re.search(r"<table\b[^>]*class=[\"']lineup[\"'][^>]*>(.*?)</table>", page, flags=re.S | re.I)
    rt = re.search(r"<table\b[^>]*class=[\"']lineup right[\"'][^>]*>(.*?)</table>", page, flags=re.S | re.I)
    mine = lt if fukui_left else rt
    vis = re.search(r"観客数\s*</th>\s*<td\b[^>]*>(.*?)</td>", page, flags=re.S | re.I)
    vm = re.search(r"([\d,]+)\s*人", _text(vis.group(1))) if vis else None
    full = re.search(r"スタジアム\s*</th>\s*<td\b[^>]*>(.*?)</td>", page, flags=re.S | re.I)
    opp_id = logos[1] if fukui_left else logos[0]
    opp_name = (names[1] if fukui_left else names[0]) if len(names) >= 2 else TEAMS.get(opp_id, ("", ""))[0]
    return {
        "date": date,
        "time": time,
        "venue": _text(full.group(1)) if full and _text(full.group(1)) else venue,
        "stage": stage,
        "opponent": opp_name,
        "opp_id": opp_id,
        "fukui_left": fukui_left,
        "my": left if fukui_left else right,
        "opp": right if fukui_left else left,
        "so_my": (so[0] if fukui_left else so[1]) if so else None,
        "so_opp": (so[1] if fukui_left else so[0]) if so else None,
        "periods": periods,
        "goals": my_goals,
        "opp_goals": opp_goals,
        "lineup": _lineup(mine.group(1)) if mine else [],
        "visitors": int(vm.group(1).replace(",", "")) if vm else None,
    }


def match_players(g: dict):
    """試合の記録(parse_match の結果)から、福井の選手ごとの成績。
    [{"name","number","position","pid","played","started","goals"}, ...](メンバー表に載った選手)"""
    goals = {}
    for x in g.get("goals") or []:
        key = x.get("pid") or re.sub(r"\s+", "", x.get("name", ""))
        goals[key] = goals.get(key, 0) + 1
    out = []
    for p in g.get("lineup") or []:
        n = goals.get(p.get("pid")) or goals.get(re.sub(r"\s+", "", p["name"])) or 0
        out.append({"name": p["name"], "number": p["number"], "position": p["position"], "pid": p.get("pid", ""),
                    "played": bool(p.get("played")), "started": bool(p.get("started")), "goals": n})
    return out


# ---------- 順位表・得点ランキング ----------

def rankings(page: str, team_id: str = TEAM_ID):
    """順位表・成績のページから、福井が入っている順位表。
    列は 順位・チーム名・勝点・試合数・勝・SO勝・SO負・負・得点・失点・得失点。福井が見つからなければ空"""
    for t in re.findall(r"<table\b[^>]*class=[\"']standings[\"'][^>]*>(.*?)</table>", page, flags=re.S | re.I):
        if not re.search(rf"teamlogo/\d{{4}}/{team_id}\.png", t):
            continue
        body = t.split("</thead>", 1)[-1]
        rows = []
        for tr in re.findall(r"<tr\b[^>]*>(.*?)</tr>", body, flags=re.S | re.I):
            tds = re.findall(r"<td\b[^>]*>(.*?)</td>", tr, flags=re.S | re.I)
            if len(tds) < 11:
                continue
            nums = [_int(_text(x)) for x in tds[2:11]]
            if None in nums:
                continue
            tid = re.search(r"teamlogo/\d{4}/([^./\"']+)\.png", tds[1])
            rows.append({
                "rank": _int(_text(tds[0])) or len(rows) + 1,
                "team": _text(tds[1]),
                "tid": tid.group(1) if tid else "",
                "points": nums[0], "played": nums[1], "win": nums[2], "so_win": nums[3], "so_lose": nums[4],
                "lose": nums[5], "gf": nums[6], "ga": nums[7], "gd": nums[8],
            })
        return rows
    return []


def standings_date(page: str, team_id: str = TEAM_ID) -> str:
    """福井が入っている順位表の「2026年10月25日時点」。読めなければ空"""
    for m in re.finditer(r"<table\b[^>]*class=[\"']standings[\"'][^>]*>(.*?)</table>", page, flags=re.S | re.I):
        if re.search(rf"teamlogo/\d{{4}}/{team_id}\.png", m.group(1)):
            d = re.findall(r"class=[\"']standings-date[\"'][^>]*>(.*?)</p>", page[:m.start()], flags=re.S | re.I)
            return _text(d[-1]) if d else ""
    return ""


def record_text(r: dict) -> str:
    """「2勝5敗」。SOがあれば「3勝2敗・SO勝1・SO負1」"""
    t = f"{r['win']}勝{r['lose']}敗"
    if r.get("so_win"):
        t += f"・SO勝{r['so_win']}"
    if r.get("so_lose"):
        t += f"・SO負{r['so_lose']}"
    return t


def standings_from(rank_page: str, form: str = ""):
    """standings.json の "VERCOSTA" に入れる形(ほかのチームの順位表と同じ形に、SO勝・SO負を足したもの)。
    draw は SO までもつれた試合の数(SO勝 + SO負)。読めなければ None"""
    rows = rankings(rank_page)
    if not rows:
        return None
    table = []
    for r in rows:
        row = {k: r[k] for k in ("rank", "team", "tid", "points", "played", "win", "lose", "so_win", "so_lose", "gf", "ga", "gd")}
        row["draw"] = r["so_win"] + r["so_lose"]
        row["record"] = record_text(r)
        if r["tid"] == TEAM_ID and form:
            row["form"] = form
        table.append(row)
    date = standings_date(rank_page)
    return {
        "league": "ホッケー日本リーグ サムライリーグD1",
        "source_url": RANKING_URL,
        "note": "ホッケージャパンリーグ公式の順位表" + (f"({date})" if date else "")
                + "。引き分けはSO(シュートアウト)で勝ち負けを決めます。",
        "regular": {"label": "レギュラーステージ", "table": table},
    }


def leaders(page: str, team_id: str = TEAM_ID):
    """順位表・成績のページの得点ランキングのうち、福井の選手が入っている表(福井のいるリーグの表)。
    [{"rank","name","team","tid","goals","photo","pid"}, ...]。福井の選手が1人も入っていなければ空"""
    for t in re.findall(r"<table\b[^>]*class=[\"']standings scoring[\"'][^>]*>(.*?)</table>", page, flags=re.S | re.I):
        if not re.search(rf"teamlogo/\d{{4}}/{team_id}\.png", t):
            continue
        out = []
        for tr in re.findall(r"<tr\b[^>]*>(.*?)</tr>", t.split("</thead>", 1)[-1], flags=re.S | re.I):
            tds = re.findall(r"<td\b[^>]*>(.*?)</td>", tr, flags=re.S | re.I)
            if len(tds) < 4 or _int(_text(tds[3])) is None:
                continue
            photo = re.search(r"<img\b[^>]*src=[\"']([^\"']+/players/[^\"']+)[\"']", tds[1])
            pid = re.search(r"playerID=(\d+)", tds[1])
            tid = re.search(r"teamlogo/\d{4}/([^./\"']+)\.png", tds[2])
            out.append({
                "rank": _int(_text(tds[0])) or len(out) + 1,
                "name": _text(tds[1]), "team": _text(tds[2]), "tid": tid.group(1) if tid else "",
                "goals": _int(_text(tds[3])),
                "photo": photo.group(1) if photo else "", "pid": pid.group(1) if pid else "",
            })
        return out
    return []


# ---------- チーム・選手のページ ----------

def roster(page: str):
    """チーム・選手のページの選手の一覧。
    [{"pid","name","reading","number","position","photo"}, ...](reading はローマ字)"""
    out = []
    for m in re.finditer(r"<li\b[^>]*id=[\"']player(\d+)[\"'][^>]*>(.*?)</li>", page, flags=re.S | re.I):
        li = m.group(2)
        name = re.search(r"class=[\"']jp[\"'][^>]*>(.*?)</span>", li, flags=re.S | re.I)
        if not name or not _text(name.group(1)):
            continue
        en = re.search(r"class=[\"']en[\"'][^>]*>(.*?)</span>", li, flags=re.S | re.I)
        num = re.search(r"class=[\"']number[\"'][^>]*>(.*?)</span>", li, flags=re.S | re.I)
        pos = re.search(r"class=[\"']position[\"'][^>]*>(.*?)<", li, flags=re.S | re.I)
        photo = re.search(r"<img\b[^>]*src=[\"']([^\"']+)[\"']", li)
        out.append({
            "pid": m.group(1),
            "name": _text(name.group(1)),
            "reading": _text(en.group(1)) if en else "",
            "number": _text(num.group(1)) if num else "",
            "position": _text(pos.group(1)) if pos else "",
            "photo": photo.group(1) if photo else "",
        })
    return out


# ---------- 選手の得点・出場(試合ごとの記録を足し合わせる) ----------

def scorers_from(cache: dict, roster_rows=None):
    """試合ごとの記録(cache: 試合の番号 → parse_match の結果)を足して、選手の表を作る。
    standings.json の "VERCOSTA" の "scorers" に入れる形(丸岡RUCK・ブルーサンダーと同じ形に、先発の数を足したもの)。
    roster_rows(チーム・選手のページの一覧)を渡すと、写真・ローマ字を付け、まだ試合に出ていない選手も0で入れる。
    試合が無ければ None"""
    games = [g for g in cache.values() if isinstance(g, dict) and g.get("lineup") and g.get("my") is not None]
    total = {}
    for g in sorted(games, key=lambda x: x.get("date", "")):
        for p in match_players(g):
            key = p["pid"] or re.sub(r"\s+", "", p["name"])
            t = total.setdefault(key, {"name": p["name"], "team": TEAM_NAME, "goals": 0, "games": 0, "starts": 0,
                                       "number": "", "position": "", "pid": p["pid"]})
            t["goals"] += p["goals"]
            t["games"] += 1 if p["played"] else 0
            t["starts"] += 1 if p["started"] else 0
            # 背番号・ポジションは一番新しい試合のものにする(古い試合から順に足している)
            t["number"] = p["number"] or t["number"]
            t["position"] = p["position"] or t["position"]
    if not games:
        return None
    for r in roster_rows or []:
        key = r.get("pid") or re.sub(r"\s+", "", r.get("name", ""))
        t = total.setdefault(key, {"name": r["name"], "team": TEAM_NAME, "goals": 0, "games": 0, "starts": 0,
                                   "number": r.get("number", ""), "position": r.get("position", ""), "pid": r.get("pid", "")})
        # いまの背番号・ポジションはチームのページのものを使う
        t["number"] = r.get("number") or t["number"]
        t["position"] = r.get("position") or t["position"]
        if r.get("photo"):
            t["photo"] = r["photo"]
        if r.get("reading"):
            t["reading"] = r["reading"]
    rows = sorted(total.values(), key=lambda r: (-r["goals"], -r["games"], -r["starts"], _int(r["number"]) or 999))
    rank, prev = 0, None
    for i, r in enumerate(rows):
        if r["goals"] != prev:
            rank, prev = i + 1, r["goals"]
        r["rank"] = rank
    return {
        "label": "今季の得点",
        "note": f"ホッケージャパンリーグ公式の試合ごとの記録({len(games)}試合)から数えた数字です。"
                "出場=試合に出た試合数(先発と途中出場)。SO(シュートアウト)の得点は含みません。",
        "source_url": SCHEDULE_URL,
        "rows": rows,
    }


def form_of(cache: dict, n: int = 5) -> str:
    """福井の直近 n 試合の勝敗(古い順。例「●○●」。SOで決まった試合は、SOの勝ち負け)"""
    games = sorted((g for g in cache.values() if isinstance(g, dict) and g.get("my") is not None and g.get("opp") is not None),
                   key=lambda g: (g.get("date", ""), g.get("time", "")))
    return "".join(mark(g["my"], g["opp"], g.get("so_my"), g.get("so_opp")) for g in games)[-n:]


# ---------- ニュース ----------

NEWS_WORDS = re.compile(r"ヴェルコスタ|福井")


def news(page: str):
    """ニュースのページから、題名に「ヴェルコスタ」「福井」がある記事。
    [{"id","title","link","date"(2026-11-11)}, ...]"""
    out = []
    for m in re.finditer(r"<a\b[^>]*href=[\"'](https://hjl-hockey\.tv/news/(\d{4})/(\d{2})/(\d{2})/(\d+)/?)[\"'][^>]*>(.*?)</a>",
                         page, flags=re.S | re.I):
        tm = re.search(r"class=[\"']newstitle[^\"']*[\"'][^>]*>(.*?)</span>", m.group(6), flags=re.S | re.I)
        title = _text(tm.group(1)) if tm else ""
        if not title or not NEWS_WORDS.search(title):
            continue
        if any(x["id"] == "hjl:" + m.group(5) for x in out):
            continue
        out.append({"id": "hjl:" + m.group(5), "title": title, "link": m.group(1),
                    "date": f"{m.group(2)}-{m.group(3)}-{m.group(4)}"})
    return out
