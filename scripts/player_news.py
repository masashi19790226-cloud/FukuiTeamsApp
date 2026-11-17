"""
選手ごとのニュース(Web版の選手ページ・一面の「推し選手」で使う)を data/player_news.json に集める。

- 今の選手(data/standings.json の BIRTHDAYS(4チーム)と VERCOSTA の選手名簿)の名前とチーム名で Bingニュースを検索する
- 移籍した元選手(data/history.json で、いまのクラブ now_team が分かっている人)は、名前といまのクラブ名で検索する
  (記事は元のチームの「チーム|名前」に入れる。Web版の選手ページは元のチームの選手として開くため)
  (GoogleニュースのRSSは robots.txt で自動取得が禁止されているため使わない。Bingニュースの検索は禁止されていない)
- 1回の実行で PER_RUN 人ずつ順番に調べる(20分おきの実行で、全員をおよそ数時間で1周する)
- 見出しか説明文に選手の名前(空白を除いた形)が入っている記事だけ残す
- 1人あたり新しい順に KEEP_PER_PLAYER 件まで、KEEP_DAYS 日より前の記事は消す
- チームのニュース(news_raw.json)とは別のファイル(Android版の通知には出さない)

check_alerts.py の main から呼ぶ(失敗しても、ほかの処理の結果は残す)。
"""

import json
import os
import re
import urllib.request
from datetime import datetime, timedelta, timezone

BASE_DIR = os.path.join(os.path.dirname(__file__), "..", "data")
STANDINGS_PATH = os.path.join(BASE_DIR, "standings.json")
HISTORY_PATH = os.path.join(BASE_DIR, "history.json")
PLAYER_NEWS_PATH = os.path.join(BASE_DIR, "player_news.json")

PER_RUN = 10
KEEP_PER_PLAYER = 30
KEEP_DAYS = 365

# 検索に足すチームの言葉(同じ名前の別の人の記事を減らすため)
TEAM_QUERY_WORD = {
    "BLOWINDS": "ブローウィンズ", "RAC": "丸岡", "UNITED": "福井ユナイテッド",
    "THUNDER": "ブルーサンダー", "VERCOSTA": "ヴェルコスタ",
}
NOISE = re.compile(r"求人|アルバイト|楽天市場|Amazon|メルカリ|ヤフオク|中古|通販|送料|価格\.com|ラクマ", re.I)


def name_key(name: str) -> str:
    return re.sub(r"[\s　・･]+", "", name or "").replace("髙", "高").replace("﨑", "崎")


def player_list():
    """調べる選手 [(チーム, 名前, 検索に足す言葉)]。今の選手と、移籍した元選手(いまのクラブが分かっている人)。読めなければ空"""
    try:
        with open(STANDINGS_PATH, encoding="utf-8") as f:
            st = json.load(f)
    except Exception as e:
        print(f"[WARN] 選手のニュース: standings.json を読めませんでした {e!r}")
        return []
    out, seen = [], set()
    for p in (st.get("BIRTHDAYS") or {}).get("players") or []:
        team, name = p.get("team"), (p.get("name") or "").strip()
        if team in TEAM_QUERY_WORD and name and (team, name_key(name)) not in seen:
            seen.add((team, name_key(name)))
            out.append((team, name, TEAM_QUERY_WORD[team]))
    for p in ((st.get("VERCOSTA") or {}).get("roster") or {}).get("players") or []:
        name = (p.get("name") or "").strip()
        if name and ("VERCOSTA", name_key(name)) not in seen:
            seen.add(("VERCOSTA", name_key(name)))
            out.append(("VERCOSTA", name, TEAM_QUERY_WORD["VERCOSTA"]))
    # 移籍した元選手(読めなくても今の選手の分は続ける)
    try:
        with open(HISTORY_PATH, encoding="utf-8") as f:
            teams = (json.load(f) or {}).get("teams") or {}
        for team, sec in teams.items():
            if team not in TEAM_QUERY_WORD or not isinstance(sec, dict):
                continue
            for p in sec.get("players") or []:
                name, now_team = (p.get("name") or "").strip(), (p.get("now_team") or "").strip()
                if p.get("now") is False and name and now_team and (team, name_key(name)) not in seen:
                    seen.add((team, name_key(name)))
                    out.append((team, name, now_team))
    except Exception as e:
        print(f"[WARN] 選手のニュース: history.json の元選手を読めませんでした {e!r}")
    out.sort(key=lambda x: (x[0], name_key(x[1])))
    return out


def _description(item_xml: str) -> str:
    import html as htmllib
    m = re.search(r"<description>(.*?)</description>", item_xml, flags=re.S)
    return re.sub(r"<[^>]+>", " ", htmllib.unescape(m.group(1))) if m else ""


def check_player_news(fetch_feed, parse_bing_rss, bing_url: str) -> int:
    """選手のニュースを PER_RUN 人分だけ調べて、data/player_news.json を書き直す。新しく見つかった件数を返す"""
    players = player_list()
    if not players:
        return 0
    try:
        with open(PLAYER_NEWS_PATH, encoding="utf-8") as f:
            data = json.load(f)
    except (FileNotFoundError, ValueError):
        data = {}
    store = data.get("players") or {}
    cursor = int(data.get("cursor") or 0) % len(players)
    now = datetime.now(timezone.utc)
    cutoff = now - timedelta(days=KEEP_DAYS)
    new_count = 0
    for i in range(min(PER_RUN, len(players))):
        team, name, word = players[(cursor + i) % len(players)]
        key = f"{team}|{name_key(name)}"
        q = f"{name_key(name)} {word}"
        try:
            text = fetch_feed(bing_url.format(q=urllib.request.quote(q))).decode("utf-8", errors="replace")
        except Exception as e:
            print(f"[WARN] 選手のニュース/{q} の取得に失敗しました: {e}")
            continue
        have = store.get(key) or []
        links = {a.get("link") for a in have}
        nk = name_key(name)
        for item in re.findall(r"<item>(.*?)</item>", text, flags=re.S):
            got = parse_bing_rss(f"<item>{item}</item>")
            if not got:
                continue
            e, desc = got[0], _description(item)
            if e["link"] in links or not e["published"]:
                continue
            if nk not in name_key(e["title"]) and nk not in name_key(desc):
                continue  # 名前が出てこない記事は入れない
            if NOISE.search(e["title"]):
                continue
            have.append({"title": e["title"], "link": e["link"], "published": e["published"],
                         "source": e.get("source", ""), "detected_at": now.isoformat()})
            links.add(e["link"])
            new_count += 1

        def when(a):
            try:
                return datetime.fromisoformat(str(a.get("published")).replace("Z", "+00:00"))
            except ValueError:
                return now
        have = [a for a in have if when(a) >= cutoff]
        have.sort(key=when, reverse=True)
        store[key] = have[:KEEP_PER_PLAYER]
    # 今の選手にいない人の分は消す
    alive = {f"{t}|{name_key(n)}" for t, n, _ in players}
    store = {k: v for k, v in store.items() if k in alive and v}
    out = {"updated_at": now.isoformat(), "cursor": (cursor + PER_RUN) % len(players), "players": store}
    with open(PLAYER_NEWS_PATH, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=1)
    print(f"[選手のニュース] {min(PER_RUN, len(players))}人を調べ、新しく{new_count}件(記事のある選手 {len(store)}人)")
    return new_count
