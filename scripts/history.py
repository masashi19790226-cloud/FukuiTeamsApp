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

    def __init__(self):
        self.p = {}

    def add(self, name, number, position, season, date, played, started, goals):
        name = (name or "").strip()
        if not name or name in ("???",):
            return
        k = name_key(name)
        r = self.p.setdefault(k, {"name": name, "number": "", "position": "", "seasons": [],
                                  "apps": 0, "starts": 0, "goals": 0, "first": date, "last": date})
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
            for p in _items(m.get("starting_member_proponent")):
                n = (p.get("player") or "").strip()
                book.add(n, p.get("number"), p.get("position"), sea, date, True, True, goals.get(n, 0))
                counted.add(n)
            for p in _items(m.get("substitute_proponent")):
                n = (p.get("player") or "").strip()
                if name_key(n) in came_in and n not in counted:
                    book.add(n, p.get("number"), p.get("position"), sea, date, True, False, goals.get(n, 0))
                    counted.add(n)
            # 先発・交代の記録に無い得点者(記録が抜けている試合)も、出場1・得点として数える
            for n, g in goals.items():
                if n not in counted:
                    book.add(n, "", "", sea, date, True, False, g)
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
            roster = [[p["name"], p.get("number", ""), p.get("position", ""), p.get("goals", 0)] for p in g.get("players") or []]
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
            m = _ruck_match(dict(r, gid="", round=""), season, "")
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
    for m in sorted(matches, key=lambda x: (x["date"], x.get("time", ""))):
        for name, number, position, goals in m.get("roster") or []:
            book.add(name, number, position, m.get("season"), m["date"], True, False, goals)
    return book.result(current_keys)


# ---------- 日本代表の選出(丸岡RUCK) ----------

RUCK_SITE = "https://ruck-fukui.com"
# 「代表」の言葉が入っていても、代表選出のお知らせではない記事
NATIONAL_EXCLUDE = re.compile(r"決定戦|北信越大会|選手権大会|記者会見|地域女子|県大会|代表取締役")
NAME_BAD = re.compile(r"代表|日本|選出|候補|女子|大学|高校|株式|会社|福井|丸岡")
# 「No.10 #池内天紀 選手」「No.9 FP 北川夏奈(イワタグルーブ)」「No.3 横山凜花 福井県立大学2年」
NATIONAL_NO_NAME = re.compile(r"No\.?\s*\d{1,2}\s*(?:FP|GK)?\s*#?\s*([一-龥々]{2,6}?)(?=\s|選手|[（(【、,。]|$)")
# 「北川夏奈選手、池内天紀選手」(名前の前が漢字でないこと)
NATIONAL_NAME = re.compile(r"(?<![一-龥々])([一-龥々]{3,6})\s*選手")


def national_selections(fetch, max_pages=10):
    """丸岡RUCK公式サイトの「代表」のお知らせ(サイト内検索のRSS。古い記事まで)から、日本代表・代表候補に選ばれた選手。
    [{"date","title","kind","players":[名前...],"link"}, ...](新しい順)"""
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
            out.append({"date": date, "title": title, "kind": kind, "players": names, "link": link, "photos": photos})
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
        book = PlayerBook()
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
            teams["THUNDER"] = {"source": "リーグH公式の星取表・試合ページ", "source_url": leagueh.MATRIX_URL, "matches": got,
                                "players": players}
            print(f"[過去の記録] ブルーサンダー: 選手{len(players)}人")
    except Exception as e:
        print(f"[WARN] 過去の記録: ブルーサンダーの取得に失敗。前回の分を使います {e!r}")
        pending = True
    try:
        got = ruck_matches(fetch, (teams.get("RAC") or {}).get("matches"))
        if got:
            teams["RAC"] = {"source": "女子Fリーグ公式の過去の記録・試合一覧", "source_url": f"{WFL_SITE}/archive/", "matches": got}
    except Exception as e:
        print(f"[WARN] 過去の記録: 丸岡RUCKの取得に失敗。前回の分を使います {e!r}")
    try:
        nat = national_selections(fetch) or (old_teams.get("RAC") or {}).get("national")
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
    bw_matches = old_bw.get("matches") or []
    try:
        bw_matches = blowinds_matches(fetch, bw_matches) or bw_matches
    except Exception as e:
        print(f"[WARN] 過去の記録: ブローウィンズの過去の試合の取得に失敗。前回の分を使います {e!r}")
    if seasons or bw_matches:
        teams["BLOWINDS"] = {"source": "Bリーグ公式の日程・結果・クラブページ", "source_url": f"{BLEAGUE}/club_detail/?TeamID={BLOWINDS_TEAM_ID}",
                             "matches": bw_matches, "seasons": seasons}
    # 取れなかったチームは前回の分を残す(版を上げて作り直したときも消さない)
    for key, val in old_teams.items():
        if key not in teams and val:
            print(f"[過去の記録] {key}: 今回は取れなかったので前回の分を使います")
            teams[key] = val
    return {"date": today, "v": VERSION, "pending": pending,
            "updated_at": datetime.now(timezone.utc).isoformat(), "teams": teams}
