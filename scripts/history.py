"""
過去の試合の記録(アプリの「過去の記録」・一面の「○年前の今日」・次の相手との通算の対戦成績で使う)。
build_previews.py から1日1回だけ呼び、data/history.json に書き出す。

- ユナイテッド  : 公式サイトの試合一覧(試合情報ページが読み込んでいるデータ。2019年〜今季の全公式戦)。
                  日付・大会・相手・会場・HOME/AWAY・スコア・PK戦・観客数・得点者。毎回まとめて読み直す(1回の読み込み)
- ブルーサンダー: リーグH公式の星取表(シーズンごと。2024-25〜今季)の福井永平寺の行 → 試合ごとのページ。
                  終わった試合のページは変わらないので、一度読んだ試合は前回の分(history.json)を使う。
                  1回に読む試合のページは THUNDER_MAX_PAGES まで(残りは次の実行で読む)
- 丸岡RUCK      : 女子Fリーグ公式の「過去の記録」(/archive/年/score.html の日程・結果。2017-18〜)と、
                  今季の分はトップページの試合一覧(standings.wfleague_matches)。
                  過去のシーズンは変わらないので、一度読んだシーズンは前回の分(history.json)を使う
- ブローウィンズは、まだ入れていない(Bリーグ公式のメンテナンス明けに加える)

読めなかったチームは前回の分を残す。
"""

import json
import re
from datetime import datetime, timedelta, timezone

import html as htmllib

import leagueh
import name_changes
import standings

JST = timezone(timedelta(hours=9))

# データの形の版。変えたときに上げると、その日のうちに作り直す
VERSION = 5

UNITED_SITE = "https://fukuiunited.co.jp"
UNITED_ASYNC = UNITED_SITE + "/system/async/async.php"

THUNDER_MAX_PAGES = 30

WFL_SITE = "https://w-fleague.jp"
RUCK_WORD = "丸岡"
# 丸岡RUCKのホームとみなす会場(セーレン・ドリームアリーナ=福井県営体育館。過去の記録では「福井」と書かれていることがある)
RUCK_HOME_ARENAS = ("セーレン", "県営")


def _int(v):
    try:
        return int(str(v).strip())
    except (TypeError, ValueError):
        return None


# ---------- 選手(過去の選手の一覧) ----------

def name_key(name: str) -> str:
    """選手の名前を比べるための形(空白を取り、異体字をそろえる)"""
    t = re.sub(r"[\s\u3000・]+", "", name or "")
    for a, b in (("﨑", "崎"), ("髙", "高"), ("濵", "浜"), ("邉", "邊"), ("𠮷", "吉")):
        t = t.replace(a, b)
    return t


class PlayerBook:
    """試合ごとの出場・得点を足し合わせて、選手ごとの通算の記録にする"""

    def __init__(self, team=None):
        self.p = {}
        # チーム(結婚などで名字が変わった選手を今の名前にまとめるのに使う。name_changes.py)
        self.team = team

    def add(self, name, number, position, season, date, played, started, goals, log=None):
        """log を渡すと、試合ごとの記録 [日付, 相手, H/A, 福井の点, 相手の点] + [先発か, 得点] を選手の "log" に足す
        (アプリの「出た試合」・相手別の成績・1試合の最多得点に使う)"""
        name = (name or "").strip()
        if not name or name in ("???",):
            return
        if self.team:
            name = name_changes.current_name(self.team, name)
        k = name_key(name)
        r = self.p.setdefault(k, {"name": name, "number": "", "position": "", "seasons": [],
                                  "apps": 0, "starts": 0, "goals": 0, "first": date, "last": date})
        if self.team and "former" not in r:
            r["former"] = name_changes.former_names(self.team, name)
        # 背番号・ポジションは新しい試合のものを残す(古い試合から順に足す)
        if number:
            r["number"] = str(number)
        if position:
            r["position"] = position
        if season and season not in r["seasons"]:
            r["seasons"].append(season)
        r["apps"] += 1 if played else 0
        r["starts"] += 1 if started else 0
        r["goals"] += goals or 0
        r["first"] = min(r["first"], date)
        r["last"] = max(r["last"], date)
        if log is not None and played:
            r.setdefault("log", []).append(list(log) + [bool(started), goals or 0])

    def result(self, current_keys=None):
        out = []
        for k, r in self.p.items():
            # いまもチームにいるか(今のチームの選手名簿と比べる。名簿が無ければ分からない = None)
            r["now"] = (k in current_keys) if current_keys else None
            out.append(r)
        out.sort(key=lambda r: (-r["apps"], -r["goals"], r["name"]))
        return out


# ---------- ユナイテッド ----------

def _items(v):
    """公式サイトのデータの一覧(リストのときと {"0": {...}, "1": {...}} のときがある)を、リストにする"""
    if isinstance(v, dict):
        return [x for x in v.values() if isinstance(x, dict)]
    return [x for x in (v or []) if isinstance(x, dict)]


def united_matches(post_json, book=None):
    """ユナイテッド公式サイトの試合一覧から、終わった試合をすべて(古い順)。
    book(PlayerBook)を渡すと、試合ごとの先発・途中出場・得点を選手ごとに足していく(エキシビジョンは数えない)"""
    pt = post_json(UNITED_ASYNC, {"className": "PostTypes", "method": "get", "slug": ["match"], "post_type_options": True})
    ptid = pt["data"][0]["post_type_id"]
    res = post_json(UNITED_ASYNC, {"className": "MatchInfo", "method": "get", "post_type_id": [ptid], "category": "",
                                   "orderby": [{"column": "kickoff", "order": "asc"}], "offset": 0, "limit": 500})
    out = []
    data = sorted(res.get("data", []) or [], key=lambda m: m.get("kickoff") or "")
    for m in data:
        if ((m.get("match_status") or {}).get("slug") or "") != "over":
            continue
        mk = re.match(r"(\d{4})/(\d{1,2})/(\d{1,2})\s+(\d{1,2}:\d{2})", m.get("kickoff") or "")
        score = m.get("score") or {}
        my, opp = _int(score.get("proponent")), _int(score.get("opponent"))
        opponent = ((m.get("opponent") or {}).get("name") or "").strip()
        if not mk or my is None or opp is None or not opponent:
            continue
        place = m.get("place") or {}
        ha = (place.get("home_away") or "").upper()
        pk = ""
        if str((m.get("pk") or {}).get("is_pk")) == "1":
            p = (score.get("times") or {}).get("pk") or {}
            pm, po = _int(p.get("proponent")), _int(p.get("opponent"))
            if pm is not None and po is not None:
                pk = f"{pm}-{po}"
        # 得点者(同じ選手の得点はまとめる。得点の順)
        goals = {}
        for g in _items(m.get("goal_proponent")):
            name = (g.get("player") or "").strip()
            if name:
                goals[name] = goals.get(name, 0) + 1
        season = re.match(r"(\d{4})", ((m.get("season") or {}).get("name") or ""))
        comp = ((m.get("competition") or {}).get("name") or "").strip()
        if book is not None and "エキシビジョン" not in comp:
            date = f"{int(mk.group(1)):04d}-{int(mk.group(2)):02d}-{int(mk.group(3)):02d}"
            sea = season.group(1) if season else mk.group(1)
            # 先発・控え(控えは途中で出たときだけ出場に数える)
            came_in = {name_key((c.get("in") or {}).get("player")) for c in _items(m.get("member_change_proponent"))}
            counted = set()
            lg = [date, opponent, "H" if ha == "HOME" else "A" if ha == "AWAY" else "N", my, opp]
            for p in _items(m.get("starting_member_proponent")):
                n = (p.get("player") or "").strip()
                book.add(n, p.get("number"), p.get("position"), sea, date, True, True, goals.get(n, 0), lg)
                counted.add(n)
            for p in _items(m.get("substitute_proponent")):
                n = (p.get("player") or "").strip()
                if name_key(n) in came_in and n not in counted:
                    book.add(n, p.get("number"), p.get("position"), sea, date, True, False, goals.get(n, 0), lg)
                    counted.add(n)
            # 先発・交代の記録に無い得点者(記録が抜けている試合)も、出場1・得点として数える
            for n, g in goals.items():
                if n not in counted:
                    book.add(n, "", "", sea, date, True, False, g, lg)
        out.append({
            "date": f"{int(mk.group(1)):04d}-{int(mk.group(2)):02d}-{int(mk.group(3)):02d}",
            "time": mk.group(4),
            "season": season.group(1) if season else mk.group(1),
            "comp": ((m.get("competition") or {}).get("name") or "").strip(),
            "title": (m.get("title") or "").strip(),
            "opp": opponent,
            "venue": (place.get("name") or "").strip(),
            # H = ホーム・A = アウェイ・N = どちらでもない(県選手権・天皇杯などの会場)
            "ha": "H" if ha == "HOME" else "A" if ha == "AWAY" else "N",
            "my": my,
            "their": opp,
            "pk": pk,
            "visitors": _int(m.get("visitors")),
            "scorers": [{"name": k, "goals": v} for k, v in goals.items()],
            "url": f"{UNITED_SITE}/match/result.php?id={m.get('id')}",
        })
    out.sort(key=lambda x: (x["date"], x["time"]))
    print(f"[過去の記録] ユナイテッド: {len(out)}試合")
    return out


# ---------- ブルーサンダー ----------

def thunder_matches(fetch, old_matches):
    """リーグH公式の星取表(全シーズン)から福井永平寺の終わった試合。返り値は (試合のリスト, 読み残しがあるか)"""
    by_code = {m.get("code"): m for m in old_matches or [] if m.get("code")}
    top = fetch(leagueh.MATRIX_URL)
    seasons = sorted({int(s) for s in re.findall(r"<option\b[^>]*value=[\"']\./\?s=(\d{4})[\"']", top)})
    if not seasons:
        raise ValueError("星取表のシーズンの一覧が読めません")
    out, pending, read = [], False, 0
    for s in seasons:
        page = fetch(f"{leagueh.MATRIX_URL}?s={s}")
        links = leagueh.matrix_games(page)
        label = f"{s}-{(s + 1) % 100:02d}"
        for link in links:
            code = link["code"]
            old_m = by_code.get(code)
            # 前回の分の選手の一覧に選手ページの番号(5つ目)が無ければ、試合ページを読み直す(読める数の範囲で)
            if old_m and old_m.get("roster") and len(old_m["roster"][0]) < 5 and read < THUNDER_MAX_PAGES:
                by_code.pop(code)
            if code in by_code:
                m = dict(by_code[code])
                # 星取表の点数が直っていれば合わせる
                m["my"], m["their"] = link["my"], link["opp"]
                out.append(m)
                continue
            if read >= THUNDER_MAX_PAGES:
                pending = True
                continue
            read += 1
            try:
                g = leagueh.parse_game(fetch(link["url"]))
            except Exception as e:
                print(f"[WARN] 過去の記録: ブルーサンダー {code} の取得に失敗 {e!r}")
                pending = True
                continue
            if not g:
                print(f"[WARN] 過去の記録: ブルーサンダー {code} の試合ページを読み取れませんでした")
                continue
            top3 = sorted([p for p in g.get("players") or [] if p.get("goals")], key=lambda p: -p["goals"])[:3]
            # 試合のスコア表に載った選手(過去の選手の一覧に使う。[名前, 背番号, ポジション, 得点])
            roster = [[p["name"], p.get("number", ""), p.get("position", ""), p.get("goals", 0), p.get("pid", "")]
                      for p in g.get("players") or []]
            out.append({
                "code": code,
                "date": g["date"],
                "time": g.get("time", ""),
                "season": label,
                "comp": "リーグH",
                "title": f"リーグH {label}",
                "opp": g["opponent"],
                "venue": g.get("venue", ""),
                "ha": "H" if g["is_home"] else "A",
                "my": link["my"],
                "their": link["opp"],
                "pk": "",
                "visitors": None,
                "scorers": [{"name": p["name"], "goals": p["goals"]} for p in top3],
                "url": link["url"],
                "roster": roster,
            })
    out.sort(key=lambda x: (x["date"], x.get("time", "")))
    print(f"[過去の記録] ブルーサンダー: {len(seasons)}シーズン・{len(out)}試合(今回読んだ試合ページ{read}件"
          + ("・残りは次回" if pending else "") + ")")
    return out, pending


# ---------- 丸岡RUCK ----------

def _wfl_text(fragment: str) -> str:
    t = re.sub(r"<[^>]+>", " ", fragment or "")
    return re.sub(r"\s+", " ", htmllib.unescape(t)).strip()


def wfl_archive_rows(page: str):
    """女子Fリーグ公式の過去の記録の日程・結果(読み込まれる部品の中身)から、試合の一覧。
    年によって作りが少し違う(日付が「2017.06.18」「2023-10-21」、チーム名が alt= か title=、列の数)ので、
    「節」の欄(class に season を含む td)ごとに区切って読む。
    [{"gid","date","time","round","home","away","hs","as","arena"}, ...](スコアの無い試合は入れない)"""
    out = []
    parts = re.split(r"<td\b[^>]*class=[\"'][^\"']*\bseason\b[^\"']*[\"'][^>]*>", page)
    for part in parts[1:]:
        rnd = _wfl_text(part.split("</td>", 1)[0])
        d = re.search(r"class=[\"']days[^\"']*[\"'][^>]*>\s*(\d{4})[.\-/](\d{1,2})[.\-/](\d{1,2})", part)
        tm = re.search(r"class=[\"']time[\"'][^>]*>\s*(\d{1,2}:\d{2})?", part)
        left = re.search(r"class=[\"']leftTeam[\"'].*?(?:alt|title)=[\"']([^\"']+)[\"']", part, flags=re.S)
        right = re.search(r"class=[\"']rightTeam[\"'].*?(?:alt|title)=[\"']([^\"']+)[\"']", part, flags=re.S)
        score = re.search(r"class=[\"']score[\"'][^>]*>(.*?)</td>", part, flags=re.S)
        gid = re.search(r"result\.html\?gid=(\d+)", part)
        arena = re.search(r"class=[\"']arena[\"'][^>]*>(.*?)</td>", part, flags=re.S)
        if not (d and left and right and score):
            continue
        sm = re.match(r"\s*(\d+)\s*-\s*(\d+)\s*$", _wfl_text(score.group(1)))
        if not sm:
            continue
        out.append({
            "gid": gid.group(1) if gid else "",
            "date": f"{int(d.group(1)):04d}-{int(d.group(2)):02d}-{int(d.group(3)):02d}",
            "time": (tm.group(1) or "") if tm else "",
            "round": rnd,
            "home": htmllib.unescape(left.group(1)).strip(),
            "away": htmllib.unescape(right.group(1)).strip(),
            "hs": int(sm.group(1)),
            "as": int(sm.group(2)),
            "arena": _wfl_text(arena.group(1)) if arena else "",
        })
    return out


def _ruck_match(m, season, stage):
    """女子Fリーグの1試合(どちらかが丸岡RUCK)を、過去の記録の形に"""
    ruck_home_side = RUCK_WORD in m["home"]
    opp = m["away"] if ruck_home_side else m["home"]
    my, their = (m["hs"], m["as"]) if ruck_home_side else (m["as"], m["hs"])
    arena = m.get("arena") or m.get("venue") or ""
    is_home = any(w in arena for w in RUCK_HOME_ARENAS) or arena.strip() == "福井"
    rnd = m.get("round", "")
    # 「999節」のような番号は節として出さない
    rnd = "" if not rnd or rnd.startswith("999") else rnd
    title = " ".join(x for x in (f"女子Fリーグ {season}", stage, rnd) if x)
    return {
        "date": m["date"],
        "time": m.get("time", ""),
        "season": season,
        "comp": "女子Fリーグ",
        "title": title,
        "opp": opp,
        "venue": arena,
        "ha": "H" if is_home else "A",
        "my": my,
        "their": their,
        "pk": "",
        "visitors": None,
        "scorers": [],
        "url": f"{WFL_SITE}/score/result.html?gid={m['gid']}" if m.get("gid") else f"{WFL_SITE}/archive/",
        "y": m.get("y"),
    }


def _wfl_season(y: int) -> str:
    """女子Fリーグの過去の記録の年 → シーズンの呼び方(2019年までは「2019」、2020年からは「2020-21」)"""
    return str(y) if y <= 2019 else f"{y}-{(y + 1) % 100:02d}"


def ruck_archive_extras(fetch, old=None):
    """女子Fリーグ公式の過去の記録から、丸岡RUCKの選手の得点(ゴールランキング)と、リーグの表彰。
    - 得点:/archive/<年>/score.html が読み込む部品の大会番号(tid)ごとに /modules/php/FlArchiveGoalRanking.php?tid=&rn=500
      (1点以上の選手。出場はその選手の出場試合数)。年のページの部品の番号はシーズンの区切り(レギュラー・上位/下位)ごと。
      次の年のページに前の年の部品が載っていることがあるので、同じ番号は最初の年だけで数える
    - 表彰:/archive/<年>/ の「最優秀選手賞」「得点王」「ベストファイブ」などのうち、丸岡の選手
    終わった年は前回の分を使い、いちばん新しい年だけ読み直す。返り値 {"years": {年: {"season","scorers":[…],"awards":[…]}}}"""
    old_years = {int(k): v for k, v in ((old or {}).get("years") or {}).items()}
    top = fetch(f"{WFL_SITE}/archive/")
    years = sorted({int(y) for y in re.findall(r"href=[\"'](?:https?://w-fleague\.jp)?/archive/(\d{4})/", top)})
    out, used_tids = {}, set()
    for y in years:
        season = _wfl_season(y)
        if y in old_years and y != years[-1]:
            out[y] = old_years[y]
            used_tids.update(old_years[y].get("tids") or [])
            continue
        ref = {"Referer": f"{WFL_SITE}/archive/{y}/goalrank.html", "X-Requested-With": "XMLHttpRequest"}
        entry = {"season": season, "scorers": [], "awards": [], "tids": []}
        try:
            page = fetch(f"{WFL_SITE}/archive/{y}/score.html?t=0")
            tids = []
            for t in re.findall(r"url:\s*[\"']/modules/php/[\w.]+\.php[\"'][^;]*?data:\s*[\"'][^\"']*?tid=(\d+)", page, flags=re.S):
                if t not in tids and t not in used_tids:
                    tids.append(t)
            by_name = {}
            for tid in tids:
                html = fetch(f"{WFL_SITE}/modules/php/FlArchiveGoalRanking.php?tid={tid}&rn=500", ref)
                for tr in re.findall(r"<tr>(.*?)</tr>", html, flags=re.S):
                    tds = re.findall(r"<td\b[^>]*>(.*?)</td>", tr, flags=re.S)
                    if len(tds) < 8 or RUCK_WORD not in tds[2]:
                        continue
                    name = re.sub(r"[\s\u3000]+", " ", _wfl_text(tds[1])).strip()
                    nums = [_int(_wfl_text(x)) or 0 for x in tds[3:8]]
                    r = by_name.setdefault(name_key(name), {"name": name, "goals": 0, "apps": 0, "shots": 0})
                    r["goals"] += nums[0]
                    r["shots"] += nums[3]
                    r["apps"] += nums[4]
            entry["scorers"] = sorted(by_name.values(), key=lambda r: (-r["goals"], -r["apps"]))
            entry["tids"] = tids
            used_tids.update(tids)
        except Exception as e:
            print(f"[WARN] 過去の記録: 丸岡RUCK {season} のゴールランキングの取得に失敗 {e!r}")
            if y in old_years:
                entry = old_years[y]
        try:
            prize_page = fetch(f"{WFL_SITE}/archive/{y}/")
            for sec in prize_page.split('id="Prize"')[1:]:
                h3 = re.search(r"<h3[^>]*>(.*?)</h3>", sec, flags=re.S)
                if not h3:
                    continue
                award = _wfl_text(h3.group(1))
                for dl in re.findall(r"<dl>(.*?)</dl>", sec, flags=re.S):
                    team = re.search(r"<dd>(.*?)</dd>", dl, flags=re.S)
                    who = re.search(r"<dt>\s*<p>(.*?)</p>", dl, flags=re.S)
                    note = re.findall(r"<p>([^<]*)</p>", dl)
                    if team and who and RUCK_WORD in _wfl_text(team.group(1)):
                        entry["awards"].append({"award": award,
                                                "name": re.sub(r"[\s\u3000]+", " ", _wfl_text(who.group(1))).strip(),
                                                "note": note[-1].strip() if len(note) >= 2 and re.search(r"受賞|回目", note[-1]) else ""})
        except Exception as e:
            print(f"[WARN] 過去の記録: 丸岡RUCK {season} の表彰の取得に失敗 {e!r}")
            if y in old_years:
                entry["awards"] = old_years[y].get("awards") or []
        out[y] = entry
    n_sc = sum(len(v.get("scorers") or []) for v in out.values())
    n_aw = sum(len(v.get("awards") or []) for v in out.values())
    print(f"[過去の記録] 丸岡RUCK: ゴールランキング {n_sc}人分・表彰 {n_aw}件({len(out)}シーズン)")
    return {"years": {str(k): v for k, v in out.items()}}


# 丸岡RUCKの試合ごとの記録(出場選手・シュート・得点・観客数)を1回の自動更新で読む試合の数(残りは次の更新で)
RUCK_LINEUP_PER_RUN = 60


def _cells(tr: str):
    return [_wfl_text(x) for x in re.findall(r"<td\b[^>]*>(.*?)</td>", tr, flags=re.S)]


def wfl_game_lineup(main_html: str, word: str = RUCK_WORD):
    """女子Fリーグ公式の試合ごとの記録(/modules/php/FlRecordMain.php?gid=)から、そのチームの出場選手。
    [[名前, 背番号, ポジション, 出場("S"=先発 ◯ / "B"=途中出場 △ / ""=出場なし), シュート数, 得点], …]
    メンバー表は「チーム名 メンバー」の見出しのあとにあり、左のチームは表が2つ(列の並びが逆のものと Pos から始まるもの)あるので、
    Pos から始まる表を使う。得点は「得点」の欄(左のチームは Lname、右のチームは Rname)の名前を数える"""
    strongs = [(m.start(), m.group(1)) for m in re.finditer(r"<strong>([^<]*?)\s*メンバー\s*</strong>", main_html)]
    mine = [i for i, (_, t) in enumerate(strongs) if word in t]
    if not mine:
        return []
    k = mine[0]
    start = strongs[k][0]
    end = strongs[k + 1][0] if k + 1 < len(strongs) else main_html.find('<div id="Result"')
    if end < 0:
        end = len(main_html)
    left_side = k == 0
    rows = []
    for table in re.findall(r"<table\b.*?</table>", main_html[start:end], flags=re.S):
        trs = re.findall(r"<tr\b[^>]*>(.*?)</tr>", table, flags=re.S)
        if not trs or not _cells(trs[0]) or _cells(trs[0])[0] != "Pos":
            continue
        for tr in trs[2:]:
            c = _cells(tr)
            if len(c) < 6 or c[0] not in ("FP", "GK"):
                continue
            name = re.sub(r"\(C\)|（C）", "", c[3])
            name = re.sub(r"[\s\u3000]+", " ", name).strip()
            app = "S" if "◯" in c[2] or "○" in c[2] else ("B" if "△" in c[2] else "")
            shots = (_int(c[4]) or 0) + (_int(c[5]) or 0)
            rows.append([name, c[1], c[0], app, shots, 0])
        break
    # 得点(結果の表の「得点」の行だけ。「警告/退場」などの行は入れない。表は行ごとに見出し(th)が変わる)
    res = main_html[main_html.find('<div id="Result"'):] if '<div id="Result"' in main_html else ""
    first_table = re.search(r"<table\b.*?</table>", res, flags=re.S)
    cls = "Lname" if left_side else "Rname"
    by_key = {name_key(r[0]): r for r in rows}
    goal_names = []
    current = ""
    for tr in re.findall(r"<tr\b[^>]*>(.*?)</tr>", first_table.group(0) if first_table else "", flags=re.S):
        th = re.search(r"<th\b[^>]*>(.*?)</th>", tr, flags=re.S)
        if th:
            current = _wfl_text(th.group(1))
        if current != "得点":
            continue
        goal_names += re.findall(rf'<td class="{cls}">(.*?)</td>', tr, flags=re.S)
    for nm in goal_names:
        t = _wfl_text(nm)
        if not t or "OG" in t.upper() or "オウン" in t:
            continue
        t = re.sub(r"[（(].*?[）)]", "", t)
        r = by_key.get(name_key(t))
        if r:
            r[5] += 1
    return rows


def ruck_lineups(fetch, matches, prev_matches):
    """丸岡RUCKの各試合に、出場選手(lineup)・観客数(visitors)・得点者(scorers)を付ける。
    一度読んだ試合は前回の分を使い、新しく読むのは RUCK_LINEUP_PER_RUN 試合まで。読み残しがあれば True"""
    prev = {m.get("url"): m for m in prev_matches or [] if m.get("lineup") is not None}
    fetched, left = 0, 0
    for m in matches:
        url = m.get("url") or ""
        gm = re.search(r"gid=(\d+)", url)
        if not gm:
            continue
        p = prev.get(url)
        if p:
            for k in ("lineup", "visitors", "scorers"):
                if p.get(k) is not None:
                    m[k] = p[k]
            continue
        if fetched >= RUCK_LINEUP_PER_RUN:
            left += 1
            continue
        gid = gm.group(1)
        ref = {"Referer": url, "X-Requested-With": "XMLHttpRequest"}
        try:
            main_html = fetch(f"{WFL_SITE}/modules/php/FlRecordMain.php?gid={gid}", ref)
            fetched += 1
        except Exception as e:
            print(f"[WARN] 過去の記録: 丸岡RUCK {m.get('date')} の試合の記録を読めませんでした {e!r}")
            continue
        lineup = wfl_game_lineup(main_html)
        m["lineup"] = lineup
        m["scorers"] = [{"name": r[0], "goals": r[5]} for r in sorted(lineup, key=lambda r: -r[5]) if r[5] > 0]
        try:
            detail = fetch(f"{WFL_SITE}/modules/php/FlRecordDetail2022.php?gid={gid}", ref)
            va = re.search(r"観客数\s*([\d,]+)\s*人", _wfl_text(detail))
            if va:
                m["visitors"] = int(va.group(1).replace(",", ""))
        except Exception as e:
            print(f"[WARN] 過去の記録: 丸岡RUCK {m.get('date')} の観客数を読めませんでした {e!r}")
    print(f"[過去の記録] 丸岡RUCK: 試合の記録を{fetched}試合分読みました(残り{left}試合)")
    return left > 0


def ruck_players_from_lineups(matches, current_keys):
    """試合ごとの出場選手から、丸岡RUCKのこれまでの選手(出場・先発・得点・シュート)。
    結婚などで名字が変わった選手は今の名前にまとめる。出場選手が分かる試合が無ければ []"""
    book = PlayerBook("RAC")
    shots = {}
    logs = {}
    for m in sorted(matches, key=lambda x: (x["date"], x.get("time", ""))):
        for name, number, pos, app, sh, goals in m.get("lineup") or []:
            if not app:
                continue
            book.add(name, number, pos, m.get("season"), m["date"], True, app == "S", goals)
            k = name_key(name_changes.current_name("RAC", name))
            shots[k] = shots.get(k, 0) + (sh or 0)
            # 試合ごとの記録(アプリの「出た試合」と相手別の成績に使う)
            # [日付, 相手, H/A, 福井の点, 相手の点, 先発か, 得点, シュート]
            logs.setdefault(k, []).append([m["date"], m.get("opp", ""), m.get("ha", ""), m.get("my"), m.get("their"),
                                           app == "S", goals, sh or 0])
    players = book.result(current_keys)
    for p in players:
        p["shots"] = shots.get(name_key(p["name"]), 0)
        p["log"] = logs.get(name_key(p["name"]), [])
    return players


def ruck_former_profiles(fetch, players):
    """丸岡RUCKの元所属の選手(now が False)を、女子Fリーグのほかのクラブの選手名簿(alumni.wfl_rosters)と名前で合わせ、
    生年月日(birthday)・いまのクラブ(now_team)を入れる。名簿にいなければ now_checked だけ付ける"""
    import alumni
    rosters = alumni.wfl_rosters(fetch)
    if not rosters:
        return
    found = 0
    for p in players:
        if p.get("now") is not False:
            continue
        r = rosters.get(alumni._key(p["name"]))
        if r is None:
            # 結婚などで名前が変わる前の名前でも探す
            for f in p.get("former") or []:
                r = rosters.get(alumni._key(f))
                if r:
                    break
        p["now_checked"] = datetime.now(JST).strftime("%Y-%m-%d")
        if r:
            p["now_team"] = r.get("club", "")
            if r.get("birthday"):
                p["birthday"] = r["birthday"]
            found += 1
    print(f"[過去の記録] 丸岡RUCK: 元所属の選手のうち、いま女子Fリーグのほかのクラブにいる選手 {found}人")


def ruck_players(extras, current_keys):
    """ゴールランキング(1点以上の選手)から、丸岡RUCKのこれまでの選手(得点の多い順)。出場は得点したシーズンの分だけ"""
    book = {}
    for y in sorted(int(k) for k in (extras.get("years") or {})):
        v = extras["years"][str(y)]
        for r in v.get("scorers") or []:
            # 結婚などで名字が変わった選手は、今の名前にまとめる(前の名前は former に残す)
            name = name_changes.current_name("RAC", r["name"])
            k = name_key(name)
            p = book.setdefault(k, {"name": name, "number": "", "position": "", "seasons": [], "apps": 0,
                                    "starts": 0, "goals": 0, "first": "", "last": "",
                                    "former": name_changes.former_names("RAC", name)})
            if v["season"] not in p["seasons"]:
                p["seasons"].append(v["season"])
            p["apps"] += r.get("apps") or 0
            p["goals"] += r.get("goals") or 0
    players = list(book.values())
    for p in players:
        p["now"] = (name_key(p["name"]) in current_keys) if current_keys else None
    players.sort(key=lambda p: (-p["goals"], -p["apps"], p["name"]))
    return players


def ruck_matches(fetch, old_matches):
    """丸岡RUCKの試合(過去のシーズン+今季)。返り値は試合のリスト(古い順)"""
    old_by_year = {}
    for m in old_matches or []:
        if m.get("y"):
            old_by_year.setdefault(m["y"], []).append(m)
    top = fetch(f"{WFL_SITE}/archive/")
    years = sorted({int(y) for y in re.findall(r"href=[\"'](?:https?://w-fleague\.jp)?/archive/(\d{4})/", top)})
    if not years:
        raise ValueError("過去の記録の年の一覧が読めません")
    out, seen = [], set()
    for y in years:
        # 2019年までは1年の中で終わるシーズン(「2019」)、2020年からは年をまたぐシーズン(「2020-21」)。公式の過去の記録の呼び方に合わせる
        season = str(y) if y <= 2019 else f"{y}-{(y + 1) % 100:02d}"
        # 一度読んだシーズン(前回の分にある年)は読み直さない
        if y in old_by_year:
            for m in old_by_year[y]:
                key = m.get("url")
                if key not in seen:
                    seen.add(key)
                    out.append(m)
            continue
        try:
            page = fetch(f"{WFL_SITE}/archive/{y}/score.html?t=0")
        except Exception as e:
            print(f"[WARN] 過去の記録: 丸岡RUCK {season} の日程・結果のページの取得に失敗 {e!r}")
            continue
        # ページが読み込む部品(url: "/modules/php/....php", data: "tid=1234&t=" + t)。順位表の部品は使わない
        mods = re.findall(r"url:\s*[\"'](/modules/php/[\w.]+\.php)[\"'][^;]*?data:\s*[\"']([^\"']*)[\"']", page, flags=re.S)
        mods = [(p, d) for p, d in mods if "Ranking" not in p]
        for i, (path, data) in enumerate(mods):
            q = data if data.endswith("t=0") else (data + "0" if data.endswith("t=") else data)
            try:
                # この部品は、元のページ(Referer)が無いと「情報が入るまでしばらくお待ちください」しか返さない
                rows = wfl_archive_rows(fetch(f"{WFL_SITE}{path}?{q}",
                                              {"Referer": f"{WFL_SITE}/archive/{y}/score.html?t=0",
                                               "X-Requested-With": "XMLHttpRequest"}))
            except Exception as e:
                print(f"[WARN] 過去の記録: 丸岡RUCK {season} {path} の取得に失敗 {e!r}")
                continue
            # 2つ目からの部品は、レギュラーシーズンの後の「ファイナルシーズン」(上位・下位リーグ)
            stage = "ファイナルシーズン" if i > 0 else ""
            for r in rows:
                if RUCK_WORD not in r["home"] and RUCK_WORD not in r["away"]:
                    continue
                r["y"] = y
                m = _ruck_match(r, season, stage)
                # 同じ部品が次の年のページにも載っていることがあるので、試合の番号で1回だけ入れる
                if m["url"] in seen:
                    continue
                seen.add(m["url"])
                out.append(m)
    # 今季(トップページの試合一覧。終わった試合だけ)
    try:
        now = datetime.now(JST)
        cur = now.year if now.month >= 5 else now.year - 1
        season = f"{cur}-{(cur + 1) % 100:02d}"
        for r in standings.wfleague_matches(fetch(f"{WFL_SITE}/")):
            if r["hs"] is None or (RUCK_WORD not in r["home"] and RUCK_WORD not in r["away"]):
                continue
            if r["date"] < f"{cur}-05-01":
                continue
            m = _ruck_match(dict(r, round=""), season, "")
            if not r.get("gid"):
                m["url"] = f"{WFL_SITE}/score/"
            m["y"] = None
            key = ("cur", m["date"], m["time"], m["opp"])
            if key in seen:
                continue
            seen.add(key)
            out.append(m)
    except Exception as e:
        print(f"[WARN] 過去の記録: 丸岡RUCK 今季の試合の取得に失敗 {e!r}")
    out.sort(key=lambda x: (x["date"], x.get("time", "")))
    print(f"[過去の記録] 丸岡RUCK: {len(years)}シーズン分の記録から {len(out)}試合")
    return out


def thunder_players(matches, current_keys):
    book = PlayerBook()
    pids = {}
    for m in sorted(matches, key=lambda x: (x["date"], x.get("time", ""))):
        for row in m.get("roster") or []:
            name, number, position, goals = row[:4]
            book.add(name, number, position, m.get("season"), m["date"], True, False, goals,
                     [m["date"], m.get("opp", ""), m.get("ha", ""), m.get("my"), m.get("their")])
            if len(row) >= 5 and row[4]:
                pids[name_key(name)] = row[4]
    players = book.result(current_keys)
    for p in players:
        p["pid"] = pids.get(name_key(p["name"]), "")
    return players


def _add_birthday_height(p, page: str):
    """選手ページ(Bリーグ公式・リーグH公式)の「生年月日」「身長」を、元選手の記録に入れる(読めなければ入れない)。
    読み方は誕生日のデータ(birthdays.py)と同じ"""
    import birthdays
    text = birthdays._text(page)
    bd = birthdays._birthday_in(text)
    if bd:
        p["birthday"] = bd
    h = birthdays._profile(text).get("height")
    if h:
        p["height"] = h
    p["bd_checked"] = True


def thunder_players_now(fetch, players, old_players=None, recheck_days=7):
    """ブルーサンダーの元選手の「あの選手は今」と写真:リーグH公式の選手ページ(/player/番号/)。
    見出し「高光 凌（大崎オーソル埼玉） - …」のかっこの中が今のチーム、写真は /files/<年>/player/<チーム>/p<番号>.jpg。
    一度調べた選手は recheck_days 日たつまで前回の分を使う"""
    old = {p.get("pid"): p for p in old_players or [] if p.get("pid")}
    today = datetime.now(JST).date()
    fetched = 0
    for p in players:
        pid = p.get("pid")
        if not pid or p.get("now") is not False:
            continue
        o = old.get(pid) or {}
        try:
            fresh = (today - datetime.fromisoformat(o.get("now_checked", "")).date()).days < recheck_days
        except ValueError:
            fresh = False
        # 生年月日・身長をまだ調べていない(2026年10月より前に調べた)選手は読み直す
        fresh = fresh and o.get("bd_checked")
        if fresh:
            for k in ("now_team", "now_checked", "photo", "birthday", "height", "bd_checked"):
                if k in o:
                    p[k] = o[k]
            continue
        try:
            page = fetch(f"{leagueh.LEAGUEH}/player/{pid}/")
            fetched += 1
        except Exception as e:
            print(f"[WARN] 過去の記録: ブルーサンダーの元選手の選手ページを読めませんでした {p.get('name')} {e!r}")
            continue
        t = re.search(r"<title>([^<]*)</title>", page)
        team = re.search(r"[（(]([^（）()]+)[）)]\s*-", t.group(1)) if t else None
        p["now_team"] = htmllib.unescape(team.group(1)).strip() if team else ""
        p["now_checked"] = today.isoformat()
        _add_birthday_height(p, page)
        ph = re.search(rf"src=[\"'](/files/[^\"']*/player/[^\"']*p{pid}\.(?:jpe?g|png))[\"']", page)
        if ph:
            p["photo"] = leagueh.LEAGUEH + ph.group(1)
    print(f"[過去の記録] ブルーサンダー: 元選手のいまのチームを{fetched}人分調べました")
    return players


# ---------- 日本代表の選出(丸岡RUCK) ----------

RUCK_SITE = "https://ruck-fukui.com"
# 「代表」の言葉が入っていても、代表選出のお知らせではない記事
NATIONAL_EXCLUDE = re.compile(r"決定戦|北信越大会|選手権大会|記者会見|地域女子|県大会|代表取締役")
NAME_BAD = re.compile(r"代表|日本|選出|候補|女子|大学|高校|株式|会社|福井|丸岡")
# 「No.10 #池内天紀 選手」「No.9 FP 北川夏奈(イワタグルーブ)」「No.3 横山凜花 福井県立大学2年」
NATIONAL_NO_NAME = re.compile(r"No\.?\s*\d{1,2}\s*(?:FP|GK)?\s*#?\s*([一-龥々]{2,6}?)(?=\s|選手|[（(【、,。]|$)")
# 「北川夏奈選手、池内天紀選手」(名前の前が漢字でないこと)
NATIONAL_NAME = re.compile(r"(?<![一-龥々])([一-龥々]{3,6})\s*選手")


def post_eyecatch(fetch, link: str) -> str:
    """お知らせのページの一番上の画像(アイキャッチ。<img class="… wp-post-image" srcset="…768w …">)の、横768pxほどのURL。
    RSS の本文には入っていないので、ページを読む。選手の写真が並んだ画像のことが多い。無ければ空の文字"""
    page = fetch(link)
    m = re.search(r"<img\b[^>]*\bwp-post-image\b[^>]*>", page)
    if not m:
        return ""
    tag = m.group(0)
    widths = {}
    ss = re.search(r"\bsrcset=[\"']([^\"']+)[\"']", tag)
    for part in (ss.group(1).split(",") if ss else []):
        bits = part.strip().split()
        if len(bits) == 2 and bits[1].endswith("w") and bits[1][:-1].isdigit():
            widths[int(bits[1][:-1])] = htmllib.unescape(bits[0])
    src = re.search(r"\bsrc=[\"']([^\"']+)[\"']", tag)
    return widths.get(768) or widths.get(1024) or (htmllib.unescape(src.group(1)) if src else "")


JFA_SITE = "https://www.jfa.jp"
# JFA のメンバー発表の行「ALA 平田 梨豊 ﾋﾗﾀ ﾘﾎ（福井丸岡ラック）」から、丸岡の選手の名前(漢字・かな)を取り出す
JFA_RUCK_MEMBER = re.compile(
    r"([一-龥々ぁ-んァ-ヶー]{1,6}[ 　][一-龥々ぁ-んァ-ヶー]{1,6})[ 　]+[ｦ-ﾟ ･・]+[ 　]*[（(]\s*福井丸岡(?:ラック|RUCK)")


def jfa_national_selections(fetch, old=None, years=None):
    """JFA公式サイトのフットサル日本女子代表のニュース(メンバー発表)から、福井丸岡ラックの選手が選ばれたもの。
    丸岡RUCK公式サイトにお知らせが出ないこともあるので、こちらでも探す。national_selections と同じ形で返す(新しい順)。
    old: 前回の分(一度読んだ記事は読み直さない)"""
    now = datetime.now(JST)
    years = years or [now.year - 1, now.year]
    done = {o.get("link"): o for o in old or [] if o.get("link", "").startswith(JFA_SITE)}
    out = []
    for y in years:
        try:
            listing = fetch(f"{JFA_SITE}/national_team/futsalw_{y}/news/")
        except Exception as e:
            print(f"[WARN] 過去の記録: JFAのフットサル女子代表 {y}年のニュース一覧を読めませんでした {e!r}")
            continue
        ids = []
        for nid in re.findall(rf"/national_team/futsalw_{y}/news/(\d{{5,}})/", listing):
            if nid not in ids:
                ids.append(nid)
        for nid in ids:
            link = f"{JFA_SITE}/national_team/futsalw_{y}/news/{nid}/"
            if link in done:
                if done[link].get("players"):
                    out.append(done[link])
                continue
            try:
                page = fetch(link)
            except Exception as e:
                print(f"[WARN] 過去の記録: JFAのニュースを読めませんでした {link} {e!r}")
                continue
            text = _wfl_text(page)
            title_m = re.search(r"<title>(.*?)</title>", page, flags=re.S)
            title = _wfl_text(title_m.group(1)).split("|")[0].strip() if title_m else ""
            if "メンバー" not in title:
                continue
            names = []
            for n in JFA_RUCK_MEMBER.findall(text):
                n = re.sub(r"[ 　]+", "", n)
                if n not in names:
                    names.append(n)
            if not names:
                continue
            dm = re.search(r"(\d{4})年\s*(\d{1,2})月\s*(\d{1,2})日", text)
            date = "%04d-%02d-%02d" % tuple(int(x) for x in dm.groups()) if dm else ""
            kind = "U-18日本代表" if "U-18" in title else "日本代表候補" if "候補" in title else "日本代表"
            out.append({"date": date, "title": title, "kind": kind, "players": names, "link": link,
                        "photos": {}, "image": ""})
    print(f"[過去の記録] 丸岡RUCK: JFAのメンバー発表から代表選出 {len(out)}件")
    return out


def merge_national(ruck, jfa):
    """丸岡RUCK公式のお知らせと、JFAのメンバー発表を合わせる(新しい順)。
    同じ選出(日付が14日以内で、選手が重なる)は、丸岡RUCK公式のお知らせ(写真がある)だけにする"""
    out = [dict(r) for r in ruck or []]

    def day(d):
        try:
            return datetime.strptime(d, "%Y-%m-%d")
        except (TypeError, ValueError):
            return None
    for j in jfa or []:
        dj = day(j.get("date"))
        dup = False
        for r in out:
            dr = day(r.get("date"))
            same_players = set(j.get("players") or []) & {re.sub(r"[ 　]+", "", n) for n in r.get("players") or []}
            if dj and dr and abs((dj - dr).days) <= 14 and same_players:
                # 丸岡RUCK公式のお知らせに名前が無い選手(JFAの発表にだけいる選手)は足す
                for n in j.get("players") or []:
                    if n not in {re.sub(r"[ 　]+", "", x) for x in r.get("players") or []}:
                        r["players"] = list(r.get("players") or []) + [n]
                dup = True
                break
        if not dup:
            out.append(j)
    out.sort(key=lambda x: x.get("date") or "", reverse=True)
    return out


def national_selections(fetch, max_pages=10, old=None):
    """丸岡RUCK公式サイトの「代表」のお知らせ(サイト内検索のRSS。古い記事まで)から、日本代表・代表候補に選ばれた選手。
    [{"date","title","kind","players":[名前...],"link","photos":{名前:写真},"image":アイキャッチ}, ...](新しい順)
    old: 前回の分(アイキャッチは一度読んだお知らせは前回の分を使う)"""
    old_image = {o.get("link"): o.get("image") for o in (old or []) if o.get("image")}
    import xml.etree.ElementTree as ET
    from email.utils import parsedate_to_datetime
    from urllib.parse import quote
    ns = "{http://purl.org/rss/1.0/modules/content/}encoded"
    out, seen = [], set()
    for page in range(1, max_pages + 1):
        try:
            root = ET.fromstring(fetch(f"{RUCK_SITE}/?s={quote('代表')}&feed=rss2&paged={page}").encode("utf-8"))
        except Exception:
            break  # ページが無くなった(404)ら終わり
        items = list(root.iter("item"))
        if not items:
            break
        for it in items:
            title = (it.findtext("title") or "").strip()
            link = (it.findtext("link") or "").strip()
            if "代表" not in title or NATIONAL_EXCLUDE.search(title) or link in seen:
                continue
            seen.add(link)
            raw = it.findtext(ns) or it.findtext("description") or ""
            body = _wfl_text(raw)
            names = []
            for rx, text in ((NATIONAL_NO_NAME, body), (NATIONAL_NAME, title + " " + body[:300])):
                for n in rx.findall(text):
                    if not NAME_BAD.search(n) and n not in names:
                        names.append(n)
            if not names:
                continue  # 選手の名前が無いお知らせ(代表チームとの試合の結果など)は選出ではないので入れない
            try:
                date = parsedate_to_datetime(it.findtext("pubDate") or "").astimezone(JST).strftime("%Y-%m-%d")
            except (TypeError, ValueError):
                date = ""
            head = title + body[:120]
            kind = ("U-18日本代表" if "U-18" in head else
                    "日本代表候補" if "候補" in head else "日本代表")
            # 選手の写真:お知らせの写真(絵文字を除く)の数と選手の数が同じなら、順に対応させる
            imgs = []
            for tag in re.findall(r"<img\b[^>]*>", raw):
                if "wp-smiley" in tag or "s.w.org" in tag:
                    continue
                src = re.search(r"\bsrc=[\"']([^\"']+)[\"']", tag)
                if not src:
                    continue
                url = htmllib.unescape(src.group(1))
                ss = re.search(r"\bsrcset=[\"']([^\"']+)[\"']", tag)
                for part in (ss.group(1).split(",") if ss else []):
                    bits = part.strip().split()
                    if len(bits) == 2 and bits[1] == "300w":
                        url = htmllib.unescape(bits[0])
                        break
                if url not in imgs:
                    imgs.append(url)
            photos = dict(zip(names, imgs)) if names and len(imgs) == len(names) else {}
            # お知らせの一番上の画像(アイキャッチ)。本文に写真が無いお知らせでも、ここに選手の写真があることが多い
            image = old_image.get(link, "")
            if not image and link:
                try:
                    image = post_eyecatch(fetch, link)
                except Exception as e:
                    print(f"[WARN] 過去の記録: 代表選出のお知らせの画像を読めませんでした {link} {e!r}")
            out.append({"date": date, "title": title, "kind": kind, "players": names, "link": link, "photos": photos,
                        "image": image})
    out.sort(key=lambda x: x["date"], reverse=True)
    print(f"[過去の記録] 丸岡RUCK: 代表選出のお知らせ {len(out)}件")
    return out


# ---------- ブローウィンズ(Bリーグ公式のクラブページの、シーズンごとの成績) ----------

BLEAGUE = "https://www.bleague.jp"
BLOWINDS_TEAM_ID = "2891"
# 大会の略号 → アプリに出す名前
BLEAGUE_COMP = {"BO": "B.ONE リーグ戦", "B1": "B1 リーグ戦", "B2": "B2 リーグ戦", "B3": "B3 リーグ戦",
                "B3RS": "B3 レギュラーシーズン", "B3PO": "B3 プレーオフ", "PO": "プレーオフ", "CS": "チャンピオンシップ"}


def blowinds_seasons(fetch):
    """Bリーグ公式のクラブページの「シーズン・大会・勝・負・平均得点・平均失点…」の表から、シーズンごとの成績(新しい順)。
    [{"season": "2023-24", "comp": "B3 レギュラーシーズン", "code": "B3RS", "win": 46, "lose": 4, "ppg": "93.8", "oppg": "79.2"}, ...]"""
    page = fetch(f"{BLEAGUE}/club_detail/?TeamID={BLOWINDS_TEAM_ID}")
    for table in re.findall(r"<table\b.*?</table>", page, flags=re.S | re.I):
        out = []
        for tr in re.findall(r"<tr\b[^>]*>(.*?)</tr>", table, flags=re.S | re.I):
            tds = [_wfl_text(x) for x in re.findall(r"<td\b[^>]*>(.*?)</td>", tr, flags=re.S | re.I)]
            # 1列目がシーズン(2024-25)、2列目が大会の略号、3・4列目が勝・負、5・6列目が平均得点・平均失点(小数)の表だけを使う
            if (len(tds) >= 6 and re.fullmatch(r"\d{4}-\d{2}", tds[0]) and re.fullmatch(r"[A-Z0-9]{2,6}", tds[1])
                    and tds[2].isdigit() and tds[3].isdigit() and re.fullmatch(r"\d+\.\d", tds[4])):
                out.append({"season": tds[0], "code": tds[1], "comp": BLEAGUE_COMP.get(tds[1], tds[1]),
                            "win": int(tds[2]), "lose": int(tds[3]), "ppg": tds[4], "oppg": tds[5]})
        if out:
            print(f"[過去の記録] ブローウィンズ: シーズンごとの成績 {len(out)}行")
            return out
    raise ValueError("クラブページにシーズンごとの成績の表が見つかりません")


# Bリーグ公式の日程・結果のページ(シーズン → [(リーグのタブ, 大会の番号, アプリに出す大会名)])。
# タブは 1=B1(B.PREMIER) 2=B2(2026-27からB.ONE) 3=B3(2026-27からB.NEXT)。大会の番号はページの「大会」の選択肢の値
BLOWINDS_SCHEDULE = [
    ("2026-27", "2026", [("2", "7", "B.ONE リーグ戦")]),
    ("2025-26", "2025", [("2", "7", "B2 リーグ戦"), ("2", "8", "B2 プレーオフ")]),
    ("2024-25", "2024", [("2", "7", "B2 リーグ戦"), ("2", "8", "B2 プレーオフ")]),
    ("2023-24", "2023", [("3", "15", "B3 リーグ戦"), ("3", "32", "B3 プレーオフ")]),
]
BL_ITEM = re.compile(r'<span class="title">([^<]*)</span>|<li class="list-item" id="(\d+)"[^>]*>(.*?)</li>', re.S)


def bleague_schedule_items(html: str):
    """日程・結果のページ(または追加読み込みのJSONの部品)から、試合ごとの (日付, 試合の番号, 中身のHTML)。
    日付は「2024.10.05(土)」の見出しで、そのあとの試合に付く。
    プレーオフは見出しが大会名(「りそなグループ B2 PLAYOFFS 2024-25」)で日付が無いので ""(試合の中の「05/04 (日)」を使う)"""
    date = ""
    for m in BL_ITEM.finditer(html):
        if m.group(2) is None:
            d = re.search(r"(\d{4})\.(\d{2})\.(\d{2})", m.group(1))
            date = f"{d.group(1)}-{d.group(2)}-{d.group(3)}" if d else ""
        else:
            yield date, m.group(2), m.group(3)


def bleague_game(date: str, key: str, item: str, start_year: int, my_name: str = "福井"):
    """試合1つ分のHTMLから、アプリの過去の試合の形。終わっていない試合・福井の試合でないもの・日付が分からないものは None。
    start_year: シーズンが始まった年(試合の中の「05/04」の年を決めるのに使う。7月より前なら次の年)"""
    if "FINAL" not in item:
        return None
    names = [_wfl_text(x) for x in re.findall(r'<span class="team-name">(.*?)</span>', item, flags=re.S)]
    home = re.search(r'home-score[^"]*"[^>]*>\s*<span>\s*(\d+)', item)
    away = re.search(r'away-score[^"]*"[^>]*>\s*<span>\s*(\d+)', item)
    if len(names) < 2 or not home or not away or my_name not in names[:2]:
        return None
    is_home = names[0] == my_name
    info = re.findall(r'<div class="info-arena">(.*?)</div>', item, flags=re.S)
    spans = [_wfl_text(x) for x in re.findall(r"<span[^>]*>(.*?)</span>", info[0], flags=re.S)] if info else []
    rnd = next((x for x in spans if re.fullmatch(r"第\d+節|.*(QF|SF|FINAL|ファイナル|セミ|クォーター).*", x)), "")
    venue = next((x for x in spans if "|" in x), "")
    venue = venue.split("|")[-1].strip() if venue else ""
    tm = next((x for x in spans if re.fullmatch(r"\d{1,2}:\d{2}", x)), "")
    md = next((re.match(r"(\d{1,2})/(\d{1,2})", x) for x in spans if re.match(r"\d{1,2}/\d{1,2}\b", x)), None)
    if md:
        mon, day = int(md.group(1)), int(md.group(2))
        date = f"{start_year if mon >= 7 else start_year + 1}-{mon:02d}-{day:02d}"
    if not date:
        return None
    hs, as_ = int(home.group(1)), int(away.group(1))
    return {"date": date, "time": tm, "opp": names[1] if is_home else names[0], "venue": venue,
            "ha": "H" if is_home else "A", "my": hs if is_home else as_, "their": as_ if is_home else hs, "pk": "",
            "visitors": None, "scorers": [], "round": rnd,
            "url": f"{BLEAGUE}/game_detail/?ScheduleKey={key}&tab=2"}


def blowinds_matches(fetch, prev_matches=None):
    """Bリーグ公式の日程・結果のページから、ブローウィンズの過去の試合(古い順)。
    1ページ20試合で、続きは同じページに data_format=json&index=20, 40… を付けて読む(ページの「もっと見る」と同じ)。
    終わったシーズンは前回の分があればそれを使い、今のシーズン(一番上)だけ毎回読み直す"""
    prev_by_season = {}
    for m in prev_matches or []:
        prev_by_season.setdefault(m.get("season"), []).append(m)
    out = []
    for i, (season, year, events) in enumerate(BLOWINDS_SCHEDULE):
        if i > 0 and prev_by_season.get(season):
            out += prev_by_season[season]
            continue
        got = {}
        # プレーオフを先に読む(リーグ戦の一覧にもプレーオフの試合が混ざるので、先に読んだ大会名を使う)
        for tab, event, comp in reversed(events):
            q = f"year={year}&mon=all&day=&event={event}&club={BLOWINDS_TEAM_ID}&tab={tab}&ha=&fb="
            pages = [fetch(f"{BLEAGUE}/schedule/?{q}")]
            index = "20"
            for _ in range(15):  # 1シーズン最大300試合分まで
                try:
                    j = json.loads(fetch(f"{BLEAGUE}/schedule/?data_format=json&{q}&index={index}"))
                except Exception:
                    break
                pages.append("".join(j.get("topics") or []))
                index = j.get("index")
                if not index:
                    break
            for page in pages:
                for date, key, item in bleague_schedule_items(page):
                    g = bleague_game(date, key, item, int(year))
                    if g and key not in got:
                        g["season"] = season
                        g["comp"] = comp
                        g["title"] = f"{comp} {season}" + (f" {g['round']}" if g["round"] else "")
                        got[key] = g
        print(f"[過去の記録] ブローウィンズ {season}: {len(got)}試合")
        out += list(got.values())
    out.sort(key=lambda m: (m["date"], m.get("time") or ""))
    return out


# 試合ごとの選手の成績(ボックススコア)を1回の自動更新で読む試合の数(1ページ約1MBと大きいので分けて読む。
# 残りは pending にして同じ日のうちに続きを読む。終わった試合は一度読めば前回の分を使う)
BOX_PER_RUN = 60
# 試合のページには選手の成績が JSON で入っている("HomeBoxscores":[…], "AwayBoxscores":[…])。
# PeriodCategory 18 が試合全体の合計(1〜4 が各クォーター、15・16 が前半・後半)
BOX_TOTAL = 18


def _json_array_after(page: str, key: str):
    """ページの中の "key":[ … ] の配列を読む(無ければ [])"""
    i = page.find(f'"{key}":[')
    if i < 0:
        return []
    arr, _ = json.JSONDecoder().raw_decode(page, page.index("[", i))
    return arr if isinstance(arr, list) else []


def _json_object_after(page: str, key: str):
    """ページの中の "key":{ … } のオブジェクトを読む(無ければ {})"""
    i = page.find(f'"{key}":{{')
    if i < 0:
        return {}
    obj, _ = json.JSONDecoder().raw_decode(page, page.index("{", i))
    return obj if isinstance(obj, dict) else {}


def bleague_box(fetch, game_url: str, team_id: str = BLOWINDS_TEAM_ID):
    """試合のページから、(そのチームの選手ごとの成績 [[PlayerID, 名前, 背番号, 得点, 先発か, 出場したか], …],
    試合の情報 {"opp": 相手の正式名, "venue": 会場, "visitors": 観客数})。
    試合の情報はページの "Game":{…}(HomeTeamNameJ・AwayTeamNameJ・StadiumNameJ・Attendance)から"""
    page = fetch(game_url.replace("&tab=2", "&tab=4"))
    info = {}
    g = _json_object_after(page, "Game")
    if g:
        is_home = str(g.get("HomeTeamID")) == str(team_id)
        opp = (g.get("AwayTeamNameJ") if is_home else g.get("HomeTeamNameJ")) or ""
        if opp.strip():
            info["opp"] = opp.strip()
        if (g.get("StadiumNameJ") or "").strip():
            info["venue"] = g["StadiumNameJ"].strip()
        if isinstance(g.get("Attendance"), int) and g["Attendance"] > 0:
            info["visitors"] = g["Attendance"]
    rows = []
    for r in _json_array_after(page, "HomeBoxscores") + _json_array_after(page, "AwayBoxscores"):
        if str(r.get("TeamID")) != str(team_id) or r.get("PeriodCategory") != BOX_TOTAL:
            continue
        pt = str(r.get("PlayTime") or "")
        played = bool(re.search(r"[1-9]", pt)) or (r.get("Point") or 0) > 0
        mm = re.match(r"(\d+):(\d+)", pt)
        secs = int(mm.group(1)) * 60 + int(mm.group(2)) if mm else 0
        n = lambda k: int(r.get(k) or 0)
        # [PlayerID, 名前, 背番号, 得点, 先発か, 出場したか, 出場秒数, 2P成功, 2P試投, 3P成功, 3P試投, FT成功, FT試投,
        #  リバウンド, アシスト, スティール, ブロック, ターンオーバー, 貢献度(EFF)]
        rows.append([str(r.get("PlayerID") or ""), (r.get("PlayerNameJ") or "").strip(), str(r.get("PlayerNo") or ""),
                     n("Point"), bool(r.get("StartingFlg")), played, secs,
                     n("PT2M"), n("PT2A"), n("PT3M"), n("PT3A"), n("FTM"), n("FTA"),
                     n("RB_TOT"), n("AS"), n("ST"), n("BS"), n("TO"), n("EFF")])
    return rows, info


# 試合ごとの選手の成績の1行の長さ(これより短い前回の分は、細かい成績が無いので読み直す)
BOX_ROW_LEN = 19


# 日程の一覧の略称 → 正式名(試合のページをまだ読めていない相手でも、同じ相手が2つの名前で並ばないように)
BLEAGUE_SHORT_NAMES = {
    "信州": "信州ブレイブウォリアーズ", "奈良": "バンビシャス奈良", "青森": "青森ワッツ",
    "愛媛": "愛媛オレンジバイキングス", "山形": "山形ワイヴァンズ", "福岡": "ライジングゼファー福岡",
    "A千葉": "アルティーリ千葉", "静岡": "ベルテックス静岡", "富山": "富山グラウジーズ",
    "福島": "福島ファイヤーボンズ", "神戸": "神戸ストークス", "熊本": "熊本ヴォルターズ",
    "鹿児島": "鹿児島レブナイズ", "岐阜": "岐阜スゥープス", "金沢": "金沢武士団",
    "横浜EX": "横浜エクセレンス", "岩手": "岩手ビッグブルズ", "新潟": "新潟アルビレックスBB",
    "東京Z": "アースフレンズ東京Z", "八王子": "東京八王子ビートレインズ", "埼玉": "さいたまブロンコス",
    "岡山": "トライフープ岡山", "東京U": "東京ユナイテッドバスケットボールクラブ", "立川": "立川ダイス",
    "越谷": "越谷アルファーズ", "FE名古屋": "ファイティングイーグルス名古屋", "香川": "香川ファイブアローズ",
    "徳島": "徳島ガンバロウズ", "三重": "ヴィアティン三重", "湘南": "湘南ユナイテッドBC",
    "山口": "山口パッツファイブ", "しながわ": "しながわシティ バスケットボールクラブ",
}


def blowinds_boxes(fetch, matches, prev_matches):
    """各試合に選手の成績(box)を付ける。前回読んだ試合はその分を使い、新しく読むのは BOX_PER_RUN 試合まで。
    まだ読めていない試合が残れば True(続きは次の更新で)"""
    # 前回読んだ試合の分(選手の成績と、相手の正式名・会場・観客数)
    prev_box = {m.get("url"): m for m in prev_matches or []
                if m.get("box") and all(len(r) >= BOX_ROW_LEN for r in m["box"])}
    fetched, left = 0, 0
    for m in matches:
        if m.get("box") and all(len(r) >= BOX_ROW_LEN for r in m["box"]):
            continue
        prev = prev_box.get(m.get("url"))
        if prev:
            for k in ("box", "opp", "opp_short", "venue", "visitors"):
                if prev.get(k) is not None:
                    m[k] = prev[k]
            continue
        if fetched >= BOX_PER_RUN:
            left += 1
            continue
        try:
            box, info = bleague_box(fetch, m["url"])
            fetched += 1
        except Exception as e:
            print(f"[WARN] 過去の記録: ブローウィンズの試合の選手の成績を読めませんでした {m.get('date')} {e!r}")
            continue
        if box:
            m["box"] = box
            # 相手は日程の一覧では「信州」のような略称なので、試合のページの正式名にする(略称は opp_short に残す)
            if info.get("opp"):
                m["opp_short"] = m.get("opp_short") or m.get("opp", "")
                m["opp"] = info["opp"]
            if info.get("venue"):
                m["venue"] = info["venue"]
            if info.get("visitors"):
                m["visitors"] = info["visitors"]
    # 試合のページをまだ読めていない試合の相手は、日程の一覧の略称(「信州」)のまま。
    # 読めた試合でわかった「略称 → 正式名」で置き換えて、同じ相手が2つの名前で並ばないようにする
    full_of = dict(BLEAGUE_SHORT_NAMES)
    full_of.update({m["opp_short"]: m["opp"] for m in matches
                    if m.get("opp_short") and m.get("opp") and m["opp_short"] != m["opp"]})
    for m in matches:
        if not m.get("opp_short") and m.get("opp") in full_of:
            m["opp_short"] = m["opp"]
            m["opp"] = full_of[m["opp_short"]]
    print(f"[過去の記録] ブローウィンズ: 選手の成績を{fetched}試合分読みました(残り{left}試合)")
    return left > 0


def _url_exists(url: str) -> bool:
    """画像が置いてあるか(HEAD で確かめる)"""
    import urllib.request
    try:
        req = urllib.request.Request(url, method="HEAD", headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req, timeout=15) as res:
            return 200 <= res.status < 300
    except Exception:
        return False


def blowinds_players(matches, current_keys, old_players=None):
    """試合ごとの選手の成績から、これまでの選手(出場の多い順)。得点は goals に入れる。
    写真はBリーグ公式の選手の画像(files/user/roster/2891/<シーズン>/<PlayerID>_03.png)。
    福井にいた新しいシーズンから順に、画像が置いてあるものを使う(前回見つけた写真はそのまま使う)"""
    # 前回見つけた福井在籍時の写真(今のクラブの写真で補ったもの photo_now は、移籍もあるので毎回調べ直す)
    old_photo = {p.get("pid"): p.get("photo") for p in old_players or []
                 if p.get("pid") and p.get("photo") and not p.get("photo_now")}
    book = PlayerBook()
    pids = {}
    # 細かい成績の合計(選手 → シーズン → 合計)。行が短い(細かい成績が無い)試合は数えない
    keys = ("sec", "p2m", "p2a", "p3m", "p3a", "ftm", "fta", "reb", "ast", "stl", "blk", "to", "eff")
    detail = {}
    logs = {}
    for m in sorted(matches, key=lambda x: (x["date"], x.get("time", ""))):
        for row in m.get("box") or []:
            pid, name, number, points, started, played = row[:6]
            if not played:
                continue
            book.add(name, number, "", m.get("season"), m["date"], True, started, points)
            if pid:
                pids[name_key(name)] = pid
            # 試合ごとの記録(アプリの「出た試合」・相手別の成績・1試合の最高記録に使う)
            # [日付, 相手, H/A, 福井の点, 相手の点, 先発か, 得点, 出場秒数, リバウンド, アシスト]
            if len(row) >= BOX_ROW_LEN:
                logs.setdefault(name_key(name), []).append([m["date"], m.get("opp", ""), m.get("ha", ""), m.get("my"), m.get("their"),
                                                            bool(started), points, row[6], row[13], row[14]])
            if len(row) >= BOX_ROW_LEN:
                d = detail.setdefault(name_key(name), {}).setdefault(m.get("season"), dict.fromkeys(("g", "pts") + keys, 0))
                d["g"] += 1
                d["pts"] += points
                for k, v in zip(keys, row[6:BOX_ROW_LEN]):
                    d[k] += v
    players = book.result(current_keys)
    for p in players:
        p["log"] = logs.get(name_key(p["name"]), [])
        by = detail.get(name_key(p["name"])) or {}
        if by:
            total = dict.fromkeys(("g", "pts") + keys, 0)
            for v in by.values():
                for k in total:
                    total[k] += v[k]
            # シーズンごと(古い順)と通算の合計。アプリで平均・成功率にする
            p["stats"] = {"total": total, "seasons": [dict(v, season=s) for s, v in sorted(by.items())]}
    checked = 0
    for p in players:
        pid = pids.get(name_key(p["name"]), "")
        p["pid"] = pid
        if not pid:
            continue
        if pid in old_photo:
            p["photo"] = old_photo[pid]
            continue
        p["photo"] = ""
        for season in reversed(p["seasons"]):
            url = f"https://bleague.bl.kuroco-img.app/files/user/roster/{BLOWINDS_TEAM_ID}/{season}/{pid}_03.png"
            checked += 1
            if _url_exists(url):
                p["photo"] = url
                break
    print(f"[過去の記録] ブローウィンズ: 選手{len(players)}人(写真の有無を{checked}件確かめました)")
    return players


def _current_season_label(today=None) -> str:
    """いまのBリーグのシーズン(7月から次のシーズン)「2026-27」"""
    d = today or datetime.now(JST).date()
    y = d.year if d.month >= 7 else d.year - 1
    return f"{y}-{(y + 1) % 100:02d}"


def blowinds_players_now(fetch, players, old_players=None, recheck_days=7):
    """元選手の「あの選手は今」:Bリーグ公式の選手ページの道順(パンくず)にある今のクラブ。
    「クラブ > 横浜エクセレンス > 名前」なら横浜エクセレンス、「選手 > 名前」ならBリーグのクラブにはいない。
    一度調べた選手は recheck_days 日たつまで前回の分を使う。
    福井にいたときの写真が無い選手は、今のクラブの写真(files/user/roster/<TeamID>/<今のシーズン>/<PlayerID>_03.png)を使う"""
    old = {p.get("pid"): p for p in old_players or [] if p.get("pid")}
    today = datetime.now(JST).date()
    season_now = _current_season_label(today)
    fetched = 0
    for p in players:
        pid = p.get("pid")
        if not pid or p.get("now") is not False:
            continue
        o = old.get(pid) or {}
        try:
            fresh = (today - datetime.fromisoformat(o.get("now_checked", "")).date()).days < recheck_days
        except ValueError:
            fresh = False
        # 生年月日・身長をまだ調べていない(2026年10月より前に調べた)選手は読み直す
        fresh = fresh and o.get("bd_checked")
        if fresh:
            for k in ("now_team", "now_team_id", "now_checked", "birthday", "height", "bd_checked"):
                if k in o:
                    p[k] = o[k]
        else:
            try:
                page = fetch(f"{BLEAGUE}/roster_detail/?PlayerID={pid}")
                fetched += 1
            except Exception as e:
                print(f"[WARN] 過去の記録: ブローウィンズの元選手の選手ページを読めませんでした {p.get('name')} {e!r}")
                continue
            m = re.search(r'club_detail/\?TeamID=(\d+)" itemprop="item"><span itemprop="name">([^<]*)<', page)
            p["now_team"] = htmllib.unescape(m.group(2)).strip() if m else ""
            p["now_team_id"] = m.group(1) if m else ""
            p["now_checked"] = today.isoformat()
            _add_birthday_height(p, page)
        if not p.get("photo") and p.get("now_team_id") and p["now_team_id"] != BLOWINDS_TEAM_ID:
            url = f"https://bleague.bl.kuroco-img.app/files/user/roster/{p['now_team_id']}/{season_now}/{pid}_03.png"
            if _url_exists(url):
                p["photo"] = url
                p["photo_now"] = True
    print(f"[過去の記録] ブローウィンズ: 元選手のいまのクラブを{fetched}人分調べました")
    return players


BLOWINDS_CLUB_SITE = "https://www.fukuiblowinds.com"
# 「#10 ヒシグバータル・オーギル 選手 モンゴル代表選出のお知らせ」
BW_NATIONAL_TITLE = re.compile(r"^(?:#\d+\s*)?(.+?)\s*選手\s*(.*?代表(?:候補)?)\s*(?:メンバー)?\s*選出")


def blowinds_national(fetch, old=None, start=(2023, 7)):
    """ブローウィンズ公式サイトのお知らせ(トップチーム)を月ごとに見て、「〇〇代表選出」のお知らせを集める。
    月ごとの一覧(/news/?year=&month=&contents_type=32)は1ページにその月の分がすべて載る。
    終わった月は前回の分を使い、今月と先月だけ読み直す。写真はお知らせの本文の最初の画像"""
    old = old or {}
    old_items = {o.get("link"): o for o in old.get("items") or []}
    scanned = set(old.get("months") or [])
    today = datetime.now(JST).date()
    months = []
    y, mth = start
    while (y, mth) <= (today.year, today.month):
        months.append(f"{y}-{mth:02d}")
        y, mth = (y + 1, 1) if mth == 12 else (y, mth + 1)
    recent = set(months[-2:])
    found = dict(old_items)
    for ym in months:
        if ym in scanned and ym not in recent:
            continue
        yy, mm = ym.split("-")
        try:
            page = fetch(f"{BLOWINDS_CLUB_SITE}/news/?year={int(yy)}&month={int(mm)}&contents_type=32")
        except Exception as e:
            print(f"[WARN] 過去の記録: ブローウィンズのお知らせ {ym} を読めませんでした {e!r}")
            continue
        scanned.add(ym)
        for link, text in re.findall(r'href="(https://www\.fukuiblowinds\.com/news/detail/id=\d+)"[^>]*>(.*?)</a>', page, flags=re.S):
            t = _wfl_text(text)
            dm = re.match(r"(\d{4})/(\d{2})/(\d{2})\s+(.*)", t)
            if not dm or link in found:
                continue
            title = re.sub(r"\s+[A-Z]+$", "", dm.group(4)).strip()  # 末尾の「TOPTEAM」などの分類を除く
            nm = BW_NATIONAL_TITLE.search(title)
            if not nm or "代表付" in title or "代表取締" in title:
                continue
            item = {"date": f"{dm.group(1)}-{dm.group(2)}-{dm.group(3)}", "title": title, "kind": nm.group(2).strip(),
                    "players": [nm.group(1).strip()], "link": link, "photos": {}, "image": ""}
            try:
                art = fetch(link)
                # お知らせの本文(<main>の中)の最初の画像。ヘッダーのロゴなどを拾わないよう <main> の中だけを見る
                mi = art.find("<main")
                body = art[mi:art.find("</main>", mi)] if mi >= 0 else ""
                im = re.search(r'<img\b[^>]*\bsrc="([^"]*/files/user/(?!common/)[^"]+\.(?:jpe?g|png))"', body)
                if im:
                    item["image"] = htmllib.unescape(im.group(1))
            except Exception as e:
                print(f"[WARN] 過去の記録: ブローウィンズの代表選出のお知らせを読めませんでした {link} {e!r}")
            found[link] = item
    items = sorted(found.values(), key=lambda x: x["date"], reverse=True)
    print(f"[過去の記録] ブローウィンズ: 代表選出のお知らせ {len(items)}件")
    return {"items": items, "months": sorted(scanned)}


def build(fetch, post_json, old, current=None):
    """過去の記録のデータを作る。old は前回の分(無ければ {})。今日すでに作り終えていれば None(書き直さない)。
    current: チーム → 今の選手の名前のリスト(元選手かどうかの見分けに使う。standings.json の誕生日のデータから)"""
    cur_keys = {t: {name_key(n) for n in names} for t, names in (current or {}).items()}
    today = datetime.now(JST).date().isoformat()
    if old.get("date") == today and old.get("v") == VERSION and not old.get("pending"):
        return None
    teams = dict(old.get("teams") or {}) if old.get("v") == VERSION else {}
    old_teams = dict(old.get("teams") or {})
    pending = False
    try:
        book = PlayerBook("UNITED")
        got = united_matches(post_json, book)
        if got:
            players = book.result(cur_keys.get("UNITED"))
            teams["UNITED"] = {"source": "ユナイテッド公式サイトの試合情報", "source_url": f"{UNITED_SITE}/match/", "matches": got,
                               "players": players}
            print(f"[過去の記録] ユナイテッド: 選手{len(players)}人")
    except Exception as e:
        print(f"[WARN] 過去の記録: ユナイテッドの取得に失敗。前回の分を使います {e!r}")
    try:
        got, pend = thunder_matches(fetch, (teams.get("THUNDER") or {}).get("matches"))
        pending = pending or pend
        if got:
            players = thunder_players(got, cur_keys.get("THUNDER"))
            try:
                players = thunder_players_now(fetch, players, (old_teams.get("THUNDER") or {}).get("players"))
            except Exception as e:
                print(f"[WARN] 過去の記録: ブルーサンダーの元選手のいまのチームの取得に失敗 {e!r}")
            teams["THUNDER"] = {"source": "リーグH公式の星取表・試合ページ", "source_url": leagueh.MATRIX_URL, "matches": got,
                                "players": players}
            print(f"[過去の記録] ブルーサンダー: 選手{len(players)}人")
    except Exception as e:
        print(f"[WARN] 過去の記録: ブルーサンダーの取得に失敗。前回の分を使います {e!r}")
        pending = True
    try:
        got = ruck_matches(fetch, (teams.get("RAC") or {}).get("matches"))
        if got:
            # 試合ごとの記録(出場選手・得点者・観客数)。少しずつ読む
            try:
                pending = ruck_lineups(fetch, got, (old_teams.get("RAC") or {}).get("matches")) or pending
            except Exception as e:
                print(f"[WARN] 過去の記録: 丸岡RUCKの試合ごとの記録の取得に失敗 {e!r}")
            teams["RAC"] = {"source": "女子Fリーグ公式の過去の記録・試合一覧", "source_url": f"{WFL_SITE}/archive/", "matches": got,
                            # 試合ごとの記録(出場選手)を読み終えた試合の数(アプリで「読み込み中」を出す)
                            "detail_progress": {"done": sum(1 for m in got if m.get("lineup") is not None),
                                                "total": sum(1 for m in got if "gid=" in (m.get("url") or ""))}}
    except Exception as e:
        print(f"[WARN] 過去の記録: 丸岡RUCKの取得に失敗。前回の分を使います {e!r}")
    # 丸岡RUCKの選手の得点(ゴールランキング)とリーグの表彰
    try:
        extras = ruck_archive_extras(fetch, (old_teams.get("RAC") or {}).get("extras"))
        if "RAC" in teams:
            teams["RAC"]["extras"] = extras
            # 試合ごとの記録(出場選手)があればそこから、無ければゴールランキングから
            from_lineups = ruck_players_from_lineups(teams["RAC"]["matches"], cur_keys.get("RAC"))
            teams["RAC"]["players"] = from_lineups or ruck_players(extras, cur_keys.get("RAC"))
            teams["RAC"]["players_from"] = "lineup" if from_lineups else "ranking"
            # 元所属の選手で、いま女子Fリーグのほかのクラブにいる選手は、そのクラブの選手名簿の生年月日といまのクラブを入れる
            # (女子Fリーグ公式の選手名簿には身長が無いので、身長は入らない。引退した選手などは分からない)
            try:
                ruck_former_profiles(fetch, teams["RAC"]["players"])
            except Exception as e:
                print(f"[WARN] 過去の記録: 丸岡RUCKの元所属の選手の生年月日の取得に失敗 {e!r}")
            teams["RAC"]["awards"] = [dict(a, season=v["season"]) for k, v in sorted(extras["years"].items(), reverse=True)
                                      for a in v.get("awards") or []]
    except Exception as e:
        print(f"[WARN] 過去の記録: 丸岡RUCKのゴールランキング・表彰の取得に失敗。前回の分を使います {e!r}")
        if "RAC" in teams:
            for k in ("extras", "players", "awards"):
                if (old_teams.get("RAC") or {}).get(k):
                    teams["RAC"][k] = old_teams["RAC"][k]
    try:
        old_nat = (old_teams.get("RAC") or {}).get("national")
        ruck_nat = national_selections(fetch, old=old_nat) or [o for o in old_nat or [] if not o.get("link", "").startswith(JFA_SITE)]
        # JFA公式のメンバー発表も合わせる(丸岡RUCK公式サイトにお知らせが出ていない選出もあるため)
        try:
            jfa_nat = jfa_national_selections(fetch, old=old_nat)
        except Exception as e:
            print(f"[WARN] 過去の記録: JFAの代表メンバー発表の取得に失敗 {e!r}")
            jfa_nat = [o for o in old_nat or [] if o.get("link", "").startswith(JFA_SITE)]
        nat = merge_national(ruck_nat, jfa_nat) or old_nat
        if nat and "RAC" in teams:
            teams["RAC"]["national"] = nat
    except Exception as e:
        print(f"[WARN] 過去の記録: 丸岡RUCKの取得に失敗。前回の分を使います {e!r}")
    old_bw = old_teams.get("BLOWINDS") or {}
    seasons = old_bw.get("seasons") or []
    try:
        seasons = blowinds_seasons(fetch) or seasons
    except Exception as e:
        print(f"[WARN] 過去の記録: ブローウィンズのシーズンごとの成績の取得に失敗。前回の分を使います {e!r}")
    old_bw_matches = old_bw.get("matches") or []
    bw_matches = old_bw_matches
    try:
        bw_matches = blowinds_matches(fetch, old_bw_matches) or old_bw_matches
    except Exception as e:
        print(f"[WARN] 過去の記録: ブローウィンズの過去の試合の取得に失敗。前回の分を使います {e!r}")
    # これまでの選手(試合ごとの選手の成績から)
    bw_players = old_bw.get("players") or []
    try:
        pending = blowinds_boxes(fetch, bw_matches, old_bw_matches) or pending
        bw_players = blowinds_players(bw_matches, cur_keys.get("BLOWINDS"), bw_players) or bw_players
        bw_players = blowinds_players_now(fetch, bw_players, old_bw.get("players"))
    except Exception as e:
        print(f"[WARN] 過去の記録: ブローウィンズの選手の取得に失敗。前回の分を使います {e!r}")
    # 代表選出(ブローウィンズ公式サイトのお知らせから)
    bw_nat = {"items": old_bw.get("national") or [], "months": (old_bw.get("national_scan") or {}).get("months") or []}
    try:
        bw_nat = blowinds_national(fetch, bw_nat)
    except Exception as e:
        print(f"[WARN] 過去の記録: ブローウィンズの代表選出の取得に失敗。前回の分を使います {e!r}")
    if seasons or bw_matches:
        teams["BLOWINDS"] = {"source": "Bリーグ公式の日程・結果・クラブページ", "source_url": f"{BLEAGUE}/club_detail/?TeamID={BLOWINDS_TEAM_ID}",
                             "matches": bw_matches, "seasons": seasons, "players": bw_players,
                             "national": bw_nat.get("items") or [], "national_scan": {"months": bw_nat.get("months") or []},
                             # 試合ごとの選手の成績(細かい成績まで)を読み終えた試合の数(アプリで「読み込み中」を出す)
                             "detail_progress": {"done": sum(1 for m in bw_matches if m.get("box") and all(len(r) >= BOX_ROW_LEN for r in m["box"])),
                                                 "total": len(bw_matches)}}
    # 取れなかったチームは前回の分を残す(版を上げて作り直したときも消さない)
    for key, val in old_teams.items():
        if key not in teams and val:
            print(f"[過去の記録] {key}: 今回は取れなかったので前回の分を使います")
            teams[key] = val
    return {"date": today, "v": VERSION, "pending": pending,
            "updated_at": datetime.now(timezone.utc).isoformat(), "teams": teams}
