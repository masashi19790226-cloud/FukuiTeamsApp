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
from datetime import datetime

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
