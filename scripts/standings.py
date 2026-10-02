"""
丸岡RUCK(女子Fリーグ)とユナイテッド(北信越リーグ1部)の順位表を作る。build_previews.py から使う。

- 丸岡RUCK : 女子Fリーグ公式のトップページに並んでいる全試合の結果(日時・会場・両チーム・スコア)から、
              順位表をこのスクリプトで計算する(公式の順位表ページは後から読み込まれる作りで直接読めないため)。
              並べ方は 勝点 → 得失点差 → 総得点 の順。公式の順位と細かい決め方が違うことがあるので、画面では「計算」と明記する。
              レギュラーシーズンと、ファイナルシーズン(丸岡RUCKと同じグループのチームだけ)を別々に作る。
- ユナイテッド: 公式サイトのトップにある「LEAGUE RANKING」(順位・勝点)をそのまま使う。
              北信越リーグのデータサイト(GoalNote)は自動での読み取りが禁止されているので使わない。
"""

import html as htmllib
import re
from datetime import datetime, timedelta, timezone

WFL_TOP = "https://w-fleague.jp/"
RUCK_NAME = "福井丸岡ラック"
# 今季の期間(これより前の試合はトップページに残っていても使わない)
WFL_SEASON = ("2026-06-01", "2027-03-31")
# ファイナルシーズンが始まる日(この日より前がレギュラーシーズン)
WFL_FINAL_START = "2026-11-01"


def _text(fragment: str) -> str:
    t = re.sub(r"<[^>]+>", " ", fragment or "")
    return re.sub(r"\s+", " ", htmllib.unescape(t)).strip()


def wfleague_matches(page: str):
    """トップページの試合一覧(<dl><dt>日付 時刻 会場</dt><dd>左チーム スコア 右チーム</dd></dl>)を読む。
    同じ試合が何度も並んでいる(スライドの作り)ので、1試合1件にまとめる。"""
    page = re.sub(r"<!--.*?-->", "", page, flags=re.S)
    found = {}
    for dt, dd in re.findall(r"<dl[^>]*>\s*<dt>(.*?)</dt>\s*<dd>(.*?)</dd>\s*</dl>", page, flags=re.S):
        head = _text(dt.replace("<br", " <br"))
        m = re.match(r"(\d{4})\.(\d{2})\.(\d{2})\s+\w+\s+(\d{1,2}:\d{2})\s*(.*)", head)
        if not m:
            continue
        left = re.search(r'class="leftTeam".*?title="([^"]+)"', dd, flags=re.S)
        right = re.search(r'class="rightTeam".*?title="([^"]+)"', dd, flags=re.S)
        score = re.search(r'class="score".*?<span>(.*?)</span>', dd, flags=re.S)
        if not (left and right and score):
            continue
        date = f"{m.group(1)}-{m.group(2)}-{m.group(3)}"
        sm = re.match(r"\s*(\d+)\s*-\s*(\d+)\s*$", _text(score.group(1)))
        key = (date, m.group(4), left.group(1), right.group(1))
        found[key] = {
            "date": date, "time": m.group(4), "venue": m.group(5).strip(),
            "home": left.group(1), "away": right.group(1),
            "hs": int(sm.group(1)) if sm else None, "as": int(sm.group(2)) if sm else None,
        }
    return sorted(found.values(), key=lambda x: (x["date"], x["time"]))


def _mark(gf, ga):
    return "○" if gf > ga else ("●" if gf < ga else "△")


def compute_table(matches, teams=None):
    """終わった試合(スコアのある試合)から順位表を作る。teams を渡すと、そのチーム同士の試合だけで作る。"""
    rows = {}

    def row(name):
        return rows.setdefault(name, {"team": name, "played": 0, "win": 0, "draw": 0, "lose": 0,
                                      "gf": 0, "ga": 0, "results": []})

    for mt in matches:
        if teams is not None and not (mt["home"] in teams and mt["away"] in teams):
            continue
        if teams is not None:
            row(mt["home"])
            row(mt["away"])
        if mt["hs"] is None:
            continue
        for me, opp, gf, ga in ((mt["home"], mt["away"], mt["hs"], mt["as"]), (mt["away"], mt["home"], mt["as"], mt["hs"])):
            r = row(me)
            r["played"] += 1
            r["gf"] += gf
            r["ga"] += ga
            r["win" if gf > ga else "lose" if gf < ga else "draw"] += 1
            r["results"].append({"date": mt["date"], "opponent": opp, "gf": gf, "ga": ga})
    table = []
    for r in rows.values():
        r["points"] = r["win"] * 3 + r["draw"]
        r["gd"] = r["gf"] - r["ga"]
        r["form"] = "".join(_mark(x["gf"], x["ga"]) for x in r["results"][-5:])
        r["record"] = f"{r['win']}勝{r['lose']}敗" + (f"{r['draw']}分" if r["draw"] else "")
        del r["results"]
        table.append(r)
    table.sort(key=lambda r: (-r["points"], -r["gd"], -r["gf"], r["team"]))
    for i, r in enumerate(table, 1):
        r["rank"] = i
    return table


def ruck_standings(page: str):
    """丸岡RUCKのリーグの順位表(レギュラーシーズンと、丸岡RUCKのファイナルシーズンのグループ)。"""
    season = [m for m in wfleague_matches(page) if WFL_SEASON[0] <= m["date"] <= WFL_SEASON[1]]
    regular = [m for m in season if m["date"] < WFL_FINAL_START]
    final = [m for m in season if m["date"] >= WFL_FINAL_START]
    # ファイナルシーズンで丸岡RUCKと同じグループ = 丸岡RUCKと対戦するチーム(+丸岡RUCK)
    group = {RUCK_NAME} | {m["away"] if m["home"] == RUCK_NAME else m["home"]
                           for m in final if RUCK_NAME in (m["home"], m["away"])}
    result = {
        "league": "女子Fリーグ",
        "source_url": WFL_TOP,
        "note": "公式サイトの試合結果からアプリで計算(勝点→得失点差→総得点の順)",
        "regular": {"label": "レギュラーシーズン", "table": compute_table(regular)},
    }
    if len(group) > 1:
        result["final"] = {"label": "ファイナルシーズン", "table": compute_table(final, teams=group)}
    print(f"[順位] 女子Fリーグ: 今季の試合{len(season)}件(終了{sum(1 for m in season if m['hs'] is not None)}件)"
          f"・ファイナルシーズンのグループ{len(group)}チーム")
    return result


def find_row(standings, name):
    """順位表から、名前にその文字が入っているチームの行を探す(ファイナルシーズンに試合があればそちらを優先)。"""
    for part in ("final", "regular"):
        t = (standings or {}).get(part, {}).get("table", [])
        for r in t:
            if name and (name in r["team"] or r["team"] in name):
                if part == "final" and r["played"] == 0:
                    break
                return part, r
    return None, None


def united_standings(text: str):
    """ユナイテッド公式サイトのトップの「LEAGUE RANKING」(順位・チーム・勝点)。"""
    part = text.split("LEAGUE RANKING", 1)[-1][:1500]
    table = []
    for m in re.finditer(r"(\d+)\s*位\s*(.+?)\s+(\d+)\s*(?=\n|$)", part):
        table.append({"rank": int(m.group(1)), "team": m.group(2).strip(), "points": int(m.group(3))})
    asof = re.search(r"(\d{4}年\d{1,2}月\d{1,2}日[^\n]{0,20}?終了時点|第\d+節終了時点)", part)
    if not table:
        return None
    return {
        "league": "北信越リーグ1部",
        "source_url": "https://fukuiunited.co.jp/",
        "note": "ユナイテッド公式サイトの順位表(順位・勝点のみ)" + (f"・{asof.group(1)}" if asof else ""),
        "regular": {"label": "順位表", "table": table},
    }


# ---------- 選手の得点 ----------

WFL_GOALRANK = "https://w-fleague.jp/score/goalrank.html"
UNITED_SITE = "https://fukuiunited.co.jp"
UNITED_NAME = "福井ユナイテッドFC"


def _num(s):
    m = re.search(r"-?\d+", s or "")
    return int(m.group(0)) if m else 0


def ruck_scorers(fetch):
    """女子Fリーグ公式の「個人ランキング(ゴール)」。リーグ全体の得点者(1点以上)の一覧。
    ページの表は後から読み込まれる作りなので、ページ内に書かれている読み込み先(tid=今季の番号)を見つけて直接読む。
    ファイナルシーズンに入ると公式側の番号が変わるので、毎回ページから探す。"""
    page = fetch(WFL_GOALRANK)
    m = re.search(r'(/modules/php/FlGoalRanking[\w]*\.php)["\'].*?tid=(\d+)', page, flags=re.S)
    if not m:
        print("[WARN] 得点: 女子Fリーグの得点ランキングの読み込み先が見つかりません")
        return None
    # 公式のページから読み込んだとき(Referer付き)だけ表の中身が返ってくる作りなので、Referer を付ける
    body = fetch(f"https://w-fleague.jp{m.group(1)}?tid={m.group(2)}&rn=500", {"Referer": WFL_GOALRANK})
    body = re.sub(r"<!--.*?-->", "", body, flags=re.S)
    rows = []
    for tr in re.findall(r"<tr>(.*?)</tr>", body, flags=re.S):
        tds = [_text(x) for x in re.findall(r"<td[^>]*>(.*?)</td>", tr, flags=re.S)]
        if len(tds) < 8 or not tds[0].isdigit():
            continue
        rows.append({
            "rank": int(tds[0]), "name": re.sub(r"[\s　]+", " ", tds[1]).strip(), "team": tds[2],
            "goals": _num(tds[3]), "pk": _num(tds[4]) + _num(tds[5]), "shots": _num(tds[6]), "games": _num(tds[7]),
        })
    print(f"[得点] 女子Fリーグ: 得点者{len(rows)}人(丸岡{sum(1 for r in rows if RUCK_NAME in r['team'])}人)"
          + ("" if rows else f" ※表が空でした(受け取った文字数{len(body)})"))
    if not rows:
        return None
    return {
        "label": "得点ランキング",
        "note": "女子Fリーグ公式の個人ランキング(1点以上の選手)",
        "source_url": WFL_GOALRANK,
        "rows": rows,
    }


def parse_united_result(page: str):
    """ユナイテッド公式の試合結果ページ1つ分。ユナイテッド側の得点者・先発・サブ(ベンチ入り)。
    スコア・得点者・メンバー表は、どれもユナイテッドが左(1つめ)に並ぶ作り。念のためチーム名で左右を確かめる。"""
    team_block = re.search(r'match-result-score__team">(.*?)</ul>', page, flags=re.S)
    num_block = re.search(r'match-result-score__number">(.*?)</ul>', page, flags=re.S)
    if not (team_block and num_block):
        return None
    teams = [_text(x) for x in re.findall(r"<p>(.*?)</p>", team_block.group(1), flags=re.S)]
    nums = [int(x) for x in re.findall(r"<li>\s*(\d+)\s*</li>", num_block.group(1))]
    if UNITED_NAME not in teams or len(nums) != 2:
        return None
    ui = teams.index(UNITED_NAME)
    goals = []
    gb = re.search(r'class="match-result-goal">.*?<ul>(.*?)</ul>', page, flags=re.S)
    if gb:
        sides = re.split(r"<li>", gb.group(1))[1:]
        if len(sides) > ui:
            goals = [_text(x) for x in re.findall(r"<span>(.*?)</span>", sides[ui], flags=re.S)]
    # 得点者の数がスコアと合わないとき(オウンゴールの書き方の違いなど)は、そのまま使う(数え直さない)
    members = {"start": [], "sub": []}
    for sec in page.split('class="match-main-data-menber"')[1:]:
        kind = "start" if "STARTING" in sec[:300] else ("sub" if "SUBSTITUTE" in sec[:300] else None)
        if not kind:
            continue
        uls = re.findall(r"<ul>(.*?)</ul>", sec.split('class="match-main-data-menber"')[0], flags=re.S)
        if len(uls) <= ui:
            continue
        for li in re.findall(r"<li>(.*?)</li>", uls[ui], flags=re.S):
            name = re.search(r'class="name">(.*?)</p>', li, flags=re.S)
            no = re.search(r'class="number"><span>(.*?)</span><strong>(.*?)</strong>', li, flags=re.S)
            if name:
                members[kind].append({"name": _text(name.group(1)), "number": _text(no.group(1)) if no else "",
                                      "position": _text(no.group(2)) if no else ""})
    return {"my": nums[ui], "opp": nums[1 - ui], "goals": goals, **members}


def united_scorers(fetch, post_json, cache):
    """ユナイテッドの今季の北信越リーグの試合結果ページを全部読み、選手ごとの得点・先発・ベンチ入りを数える。
    終わった試合の結果ページは変わらないので、一度読んだ試合は cache(standings.json に保存)から使う。"""
    # 試合開始時刻は日本時間なので、日本時間で比べる(GitHub Actions の時計は世界標準時)
    now = datetime.now(timezone(timedelta(hours=9))).replace(tzinfo=None)
    year = now.year
    pt = post_json(f"{UNITED_SITE}/system/async/async.php",
                   {"className": "PostTypes", "method": "get", "slug": ["match"], "post_type_options": True})
    ptid = pt["data"][0]["post_type_id"]
    res = post_json(f"{UNITED_SITE}/system/async/async.php",
                    {"className": "MatchInfo", "method": "get", "post_type_id": [ptid], "category": "",
                     "orderby": [{"column": "kickoff", "order": "asc"}], "offset": 0, "limit": 300})
    games = []
    for g in res.get("data", []):
        title, ko = g.get("title", ""), g.get("kickoff", "")
        if "北信越フットボールリーグ" not in title or not ko.startswith(str(year)):
            continue
        try:
            kt = datetime.strptime(ko, "%Y/%m/%d %H:%M")
        except ValueError:
            continue
        if kt + timedelta(hours=3) > now:  # まだ終わっていない試合
            continue
        games.append((str(g["id"]), title))
    new_cache = {}
    for gid, title in games:
        if gid in cache:
            new_cache[gid] = cache[gid]
            continue
        try:
            r = parse_united_result(fetch(f"{UNITED_SITE}/match/result.php?id={gid}"))
        except Exception as e:
            print(f"[WARN] 得点: ユナイテッド {title} の結果ページの取得に失敗 {e!r}")
            continue
        if r:
            new_cache[gid] = r
    players = {}

    def pl(m):
        p = players.setdefault(m["name"], {"name": m["name"], "number": "", "position": "",
                                            "goals": 0, "starts": 0, "bench": 0})
        if m.get("number"):
            p["number"], p["position"] = m["number"], m.get("position", "")
        return p

    for gid, _ in games:  # 古い試合から順に(背番号は新しい試合のものが残る)
        r = new_cache.get(gid)
        if not r:
            continue
        for m in r["start"]:
            pl(m)["starts"] += 1
        for m in r["sub"]:
            pl(m)["bench"] += 1
        for name in r["goals"]:
            pl({"name": name})["goals"] += 1
    rows = sorted(players.values(), key=lambda p: (-p["goals"], -p["starts"], -p["bench"], p["name"]))
    for p in rows:
        p["team"] = UNITED_NAME
    rank = 0
    for i, p in enumerate(rows):
        if p["goals"] > 0 and (i == 0 or rows[i - 1]["goals"] != p["goals"]):
            rank = i + 1
        p["rank"] = rank if p["goals"] > 0 else 0
    print(f"[得点] ユナイテッド: 今季{len(new_cache)}試合・選手{len(rows)}人・得点{sum(p['goals'] for p in rows)}")
    if not rows:
        return None, new_cache
    return {
        "label": "チーム内の得点",
        "note": f"ユナイテッド公式の試合結果(北信越リーグ{len(new_cache)}試合)から集計",
        "source_url": f"{UNITED_SITE}/match/",
        "games": len(new_cache),
        "rows": rows,
    }, new_cache
