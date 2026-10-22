"""
「あの選手は今」(以前チームにいた選手の近況)のデータを作る。build_previews.py から1日1回だけ呼び、
data/standings.json の "ALUMNI" に入れる。アプリは一面の特集に出す。

- 丸岡RUCK : 公式サイト(WordPress)の「退団選手のお知らせ」「移籍選手のお知らせ」の記事から、以前の選手の名前・背番号・
             お知らせの日付・移籍先を読み取る(記事は公開API /wp-json/wp/v2/posts で探す。
             API で読めないときはサイト内検索の RSS で探す。ruck_wp.py)。
             その選手が今、女子Fリーグのほかのクラブにいるかを、各クラブの選手名簿(女子Fリーグ公式)で調べる。
             今季の得点は、女子Fリーグ公式の得点ランキング(standings.json の RAC の scorers。build_previews.py が作る)から。
(ブローウィンズは、Bリーグ公式サイトが一時的に止まっているため、あとで加える)

読めなかったときは前回の分を残す。
"""

import html as htmllib
import json
import re
from datetime import datetime, timedelta, timezone
from urllib.parse import quote

import ruck_wp

JST = timezone(timedelta(hours=9))

RUCK_SITE = "https://ruck-fukui.com"
WFL_SITE = "https://w-fleague.jp"
# これより前のお知らせの選手は入れない(古すぎる記事は名前の書き方がまちまちなため)
SINCE_YEAR = 2019

VERSION = 2


def _text(fragment: str) -> str:
    t = re.sub(r"<[^>]+>", " ", fragment or "")
    t = htmllib.unescape(t).replace("　", " ").replace("\xa0", " ")
    return re.sub(r"\s+", " ", t).strip()


def _key(name: str) -> str:
    t = re.sub(r"[\s　・]+", "", name or "")
    for a, b in (("﨑", "崎"), ("髙", "高"), ("濵", "浜"), ("邉", "邊"), ("𠮷", "吉")):
        t = t.replace(a, b)
    return t


# 「No.3 横山凜花選手」「8番 高尾純奈選手」「19番山川里佳子選手」「No.10 池内天紀 選手」
# (背番号は「No.3」か「8番」の形のときだけ。「2名の選手」のような文を名前と間違えないように)
NAME_RE = re.compile(r"(?:No\.?\s*(\d{1,2})|(\d{1,2})\s*番)\s*([一-龥ぁ-んァ-ヶー々]{2,8}?)\s*選手")


def ruck_departures(fetch):
    """丸岡RUCK公式サイトの退団・移籍のお知らせから、以前の選手。[{"name","number","date","kind","dest","link"}, ...]"""
    found = {}
    for word, kind in (("退団", "退団"), ("移籍", "移籍")):
        posts = ruck_wp.search_posts(fetch, RUCK_SITE, word, 20, "title,date,link,content", "あの選手は今 丸岡RUCK")
        for p in posts:
            title = _text((p.get("title") or {}).get("rendered", ""))
            date = (p.get("date") or "")[:10]
            # 「試合後記者会見」など、退団・移籍のお知らせではない記事は使わない
            if "お知らせ" not in title or word not in title or date[:4] < str(SINCE_YEAR):
                continue
            body = _text((p.get("content") or {}).get("rendered", ""))
            dest = ""
            dm = re.search(r"移籍先\s*[／/:：]\s*(\S+)", body)
            if dm:
                dest = dm.group(1).strip("。、")
            # 本文の前の方(お知らせの文)だけから名前を読む(後ろの選手のコメントには、ほかの選手の名前が出てくることがある)
            head = body[:300]
            for num1, num2, name in NAME_RE.findall(head):
                num = num1 or num2
                k = _key(name)
                if k in found:
                    continue
                found[k] = {"name": name, "number": num, "date": date, "kind": kind, "dest": dest, "link": p.get("link", "")}
    out = sorted(found.values(), key=lambda x: x["date"], reverse=True)
    print(f"[あの選手は今] 丸岡RUCK: 以前の選手 {len(out)}人")
    return out


def wfl_rosters(fetch):
    """女子Fリーグの全クラブ(丸岡RUCK以外)の選手名簿。{名前のキー: {"club","number","name"}}"""
    top = fetch(WFL_SITE + "/")
    slugs = []
    for s in re.findall(r"href=[\"'](?:https?://w-fleague\.jp)?/club/([a-z0-9_-]+)/?[\"']", top):
        if s not in slugs and s != "maruoka":
            slugs.append(s)
    out = {}
    for slug in slugs:
        club_url = f"{WFL_SITE}/club/{slug}/"
        try:
            club = fetch(club_url)
            tm = re.search(r"var\s+team\s*=\s*(\d+)", club)
            title = re.search(r"<title>(.*?)</title>", club, flags=re.S)
            name = _text(title.group(1)).split("|")[0].split("｜")[-1].strip() if title else slug
            if not tm:
                continue
            body = fetch(f"{WFL_SITE}/modules/php/FlPlayer.php?team={tm.group(1)}", {"Referer": club_url})
        except Exception as e:
            print(f"[WARN] あの選手は今: {slug} の名簿の取得に失敗 {e!r}")
            continue
        for tr in re.findall(r"<tr\b[^>]*>(.*?)</tr>", body, flags=re.S | re.I):
            tds = [_text(x) for x in re.findall(r"<td\b[^>]*>(.*?)</td>", tr, flags=re.S | re.I)]
            if len(tds) < 3:
                continue
            # 列は ポジション・背番号・氏名・ローマ字・生年月日・出身 の順
            out[_key(tds[2])] = {"club": name, "number": tds[1], "name": tds[2], "url": club_url}
    print(f"[あの選手は今] 女子Fリーグ: {len(slugs)}クラブ・{len(out)}人の名簿")
    return out


def build(fetch, old, ruck_scorers=None, current_ruck_names=()):
    """「あの選手は今」のデータを作る。今日すでに作っていれば None。
    ruck_scorers: 女子Fリーグの得点ランキングの行(名前・チーム・得点)。current_ruck_names: 今の丸岡RUCKの選手の名前"""
    today = datetime.now(JST).date().isoformat()
    if old.get("date") == today and old.get("v") == VERSION:
        return None
    players = [p for p in (old.get("players") or [])]
    try:
        deps = ruck_departures(fetch)
        rosters = wfl_rosters(fetch)
        goals = {_key(r.get("name", "")): r for r in (ruck_scorers or [])}
        now_ruck = {_key(n) for n in current_ruck_names}
        new = []
        for d in deps:
            k = _key(d["name"])
            # 今また丸岡RUCKにいる選手(レンタル移籍から戻った選手など)は入れない
            if k in now_ruck:
                continue
            r = rosters.get(k)
            g = goals.get(k)
            new.append({
                # 名前は女子Fリーグの名簿の書き方(姓と名の間に空白)があればそれを使う
                "team": "RAC", "name": r["name"] if r else d["name"], "number": d["number"], "left": d["date"], "kind": d["kind"],
                "dest": d["dest"], "link": d["link"],
                # 今の所属(女子Fリーグのクラブにいれば、そのクラブ)
                "now": r["club"] if r else "", "now_number": r["number"] if r else "", "now_url": r["url"] if r else "",
                # 今季の得点(女子Fリーグの得点ランキングに載っていれば)
                "goals": g.get("goals") if g and r else None,
            })
        if new:
            players = [p for p in players if p.get("team") != "RAC"] + new
    except Exception as e:
        print(f"[WARN] あの選手は今: 丸岡RUCKの取得に失敗。前回の分を使います {e!r}")
    return {"date": today, "v": VERSION, "updated_at": datetime.now(timezone.utc).isoformat(), "players": players}
