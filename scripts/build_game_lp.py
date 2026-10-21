"""
福井ブローウィンズのホームゲームごとの「試合情報」ページ(公式サイトの /lp/game_... )を読み、
開場時刻・当日のスケジュール・イベントを data/game_lp.json に書き出す。
アプリの一面「本日の試合」と試合詳細で使う。

- 試合情報ページは、公式サイトの「スケジュール」ページとトップページに並ぶリンクから見つける
  (ページのURLは試合ごとに決まった形ではない(例:game_202601003_20261004)ため、作らずに探す)。
- 1つのページに2日分(土日)が載っているので、日付ごとに data/games.json の試合と結び付ける。
- 読めなかった日の試合は、前回のデータを残す(試合当日にページのリンクが外れても表示できるように)。

福井永平寺ブルーサンダーは、リーグH公式の試合ごとのページの「Information」(開場時刻・チケット・観戦案内など)を読む
(この先 THUNDER_DAYS 日以内の試合だけ。日ごとの試合一覧から試合の番号を探す)。
"""

import html as htmllib
import json
import os
import re
import time
import urllib.request
from datetime import datetime, timedelta, timezone
from urllib.parse import urljoin

import leagueh

BASE_DIR = os.path.join(os.path.dirname(__file__), "..", "data")
GAMES_PATH = os.path.join(BASE_DIR, "games.json")
OUT_PATH = os.path.join(BASE_DIR, "game_lp.json")

JST = timezone(timedelta(hours=9))
UA = "Mozilla/5.0 (Linux; Android 14) FukuiSpoBot/1.0 (+https://github.com/masashi19790226-cloud/FukuiTeamsApp)"
SITE = "https://www.fukuiblowinds.com"
LIST_PAGES = [SITE + "/schedule/", SITE + "/"]
# 過ぎた試合のデータを残す日数
KEEP_PAST_DAYS = 2
# ブルーサンダーの試合情報を読む範囲(今日から何日先まで)
THUNDER_DAYS = 14


def fetch(url: str) -> str:
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept-Language": "ja"})
    with urllib.request.urlopen(req, timeout=25) as res:
        raw = res.read()
        charset = res.headers.get_content_charset() or "utf-8"
    time.sleep(1)
    return raw.decode(charset, errors="replace")


def clean(fragment: str, br: str = "\n") -> str:
    """HTMLの断片を文字だけにする。<br> は br に置き換える。"""
    t = re.sub(r"<br\s*/?>", br, fragment or "", flags=re.I)
    t = re.sub(r"<[^>]+>", "", t)
    t = htmllib.unescape(t)
    lines = [re.sub(r"[ \t　]+", " ", x).strip() for x in t.split("\n")]
    return "\n".join(x for x in lines if x)


def lp_links():
    """スケジュール・トップページから、試合情報ページのURLを集める。"""
    found = []
    for page_url in LIST_PAGES:
        try:
            page = fetch(page_url)
        except Exception as e:
            print(f"[WARN] 試合情報: {page_url} の取得に失敗 {e}")
            continue
        for href in re.findall(r"href=[\"']([^\"']*/lp/game_[\w-]+/?)[\"']", page):
            url = urljoin(page_url, href)
            if not url.endswith("/"):
                url += "/"
            if url not in found:
                found.append(url)
    return found


def tab_dates(section: str, year: int):
    """タブの「10.3」「10.4」を、順番どおりの日付(YYYY-MM-DD)の一覧にする。"""
    dates = []
    for m, d in re.findall(r'class="-day">\s*(\d{1,2})\.(\d{1,2})\s*<', section):
        dates.append(f"{year:04d}-{int(m):02d}-{int(d):02d}")
    return dates


def parse_lp(url: str, page: str):
    """試合情報ページ1つ分を、日付ごとの情報にする。{日付: {...}}"""
    # HTMLのコメント(<!-- -->)で隠された古い行は画面に出ないので読まない
    # (以前、ハーフタイム・試合終了後の行が2回ずつ出ていた原因)
    page = re.sub(r"<!--.*?-->", "", page, flags=re.S)
    ym = re.search(r"/lp/game_(\d{4})", url)
    year = int(ym.group(1)) if ym else datetime.now(JST).year
    title_m = re.search(r"<title>(.*?)</title>", page, flags=re.S)
    title = clean(title_m.group(1), " ").split("|")[0].strip() if title_m else ""
    days = {}

    # 開場・試合開始(チケット欄。日付ごとのかたまり)
    for block in re.split(r'<div class="game-ticket__block"', page)[1:]:
        dm = re.search(r'class="-day">\s*(\d{1,2})\.(\d{1,2})\s*<', block)
        if not dm:
            continue
        date = f"{year:04d}-{int(dm.group(1)):02d}-{int(dm.group(2)):02d}"
        info = days.setdefault(date, {})
        om = re.search(r"開場\s*</dt>\s*<dd>\s*([\d:]+)", block)
        sm = re.search(r"試合開始\s*</dt>\s*<dd>\s*([\d:]+)", block)
        if om:
            info["open"] = om.group(1)
        if sm:
            info["start"] = sm.group(1)

    # 当日のスケジュール(タブの順番と panel-1, panel-2 … が対応)
    sched = page.split('class="game-sc -schedule"', 1)[-1] if 'class="game-sc -schedule"' in page else ""
    tabs = tab_dates(sched.split('game-schedule__timeline', 1)[0], year)
    for num, body in re.findall(r'id="panel-(\d+)"(.*?)</section>', sched, flags=re.S):
        idx = int(num) - 1
        if idx >= len(tabs):
            continue
        items = []
        for t, c in re.findall(
            r'<dt class="game-timeline__time[^"]*">(.*?)</dt>\s*<dd class="game-timeline__content">(.*?)</dd>',
            body, flags=re.S,
        ):
            time_label = clean(t, "")
            text = clean(c)
            if time_label and text:
                items.append({"time": time_label, "text": text})
        if items:
            days.setdefault(tabs[idx], {})["timeline"] = items

    # イベント(タブの順番と event-1, event-2 … が対応)
    ev = page.split('class="game-sc -event"', 1)[-1] if 'class="game-sc -event"' in page else ""
    ev_tabs = tab_dates(ev.split('class="game-event"', 1)[0], year)
    for num, body in re.findall(r'id="event-(\d+)"(.*?)</section>', ev, flags=re.S):
        idx = int(num) - 1
        if idx >= len(ev_tabs):
            continue
        events = []
        for et, ed in re.findall(
            r'<dl class="game-event__item__summarys">\s*<dt>(.*?)</dt>\s*<dd class="game-event__item__summary">(.*?)</dd>',
            body, flags=re.S,
        ):
            name = clean(et, " ")
            text = clean(ed, " ")
            if name:
                events.append({"title": name, "text": text[:120]})
        if events:
            days.setdefault(ev_tabs[idx], {})["events"] = events

    for info in days.values():
        info["url"] = url
        info["title"] = title
    return days


def thunder_info(games, result, now_iso):
    """ブルーサンダーの今日から THUNDER_DAYS 日以内の試合の、リーグH公式の試合ページの Information を result に入れる"""
    today = datetime.now(JST).date()
    targets = [g for g in games if g.get("team") == "thunder" and not g.get("category")]
    for g in sorted(targets, key=lambda x: x.get("sort_key", x["date"])):
        try:
            d = datetime.strptime(g["date"], "%Y-%m-%d").date()
        except ValueError:
            continue
        if not (today <= d <= today + timedelta(days=THUNDER_DAYS)):
            continue
        # シーズンは「2026-27」の最初の年(7月以降はその年、6月以前は前の年)
        season = d.year if d.month >= 7 else d.year - 1
        try:
            codes = leagueh.day_game_codes(fetch(leagueh.day_search_url(g["date"], season)))
        except Exception as e:
            print(f"[WARN] 試合情報(ブルーサンダー): {g['date']} の試合一覧の取得に失敗 {e}")
            continue
        if not codes:
            print(f"[試合情報] ブルーサンダー {g['date']} の試合がリーグH公式に見つかりません")
            continue
        url = f"{leagueh.LEAGUEH}/schedule/{codes[0]}/"
        try:
            info = leagueh.game_info(fetch(url))
        except Exception as e:
            print(f"[WARN] 試合情報(ブルーサンダー): {url} の取得に失敗 {e}")
            continue
        if not info:
            print(f"[試合情報] ブルーサンダー {g['id']} {g['date']} はまだ案内が載っていません({url})")
            continue
        info["url"] = url
        info["date"] = g["date"]
        info["updated_at"] = now_iso
        result[g["id"]] = info
        print(f"[試合情報] {g['id']} {g['date']} 開場{info.get('open') or '?'} イベント{len(info.get('events', []))}件(ブルーサンダー)")


def main():
    with open(GAMES_PATH, "r", encoding="utf-8") as f:
        games = json.load(f)
    # ブローウィンズの試合を日付で引けるようにする
    by_date = {g["date"]: g["id"] for g in games if str(g.get("team", "")).upper() == "BLOWINDS"}

    old = {}
    if os.path.exists(OUT_PATH):
        with open(OUT_PATH, "r", encoding="utf-8") as f:
            old = json.load(f)

    result = {}
    now_iso = datetime.now(timezone.utc).isoformat()
    for url in lp_links():
        try:
            days = parse_lp(url, fetch(url))
        except Exception as e:
            print(f"[WARN] 試合情報: {url} の読み取りに失敗 {e}")
            continue
        for date, info in days.items():
            game_id = by_date.get(date)
            if not game_id:
                print(f"[試合情報] {date} の試合が日程に見つかりません({url})")
                continue
            info["date"] = date
            info["updated_at"] = now_iso
            result[game_id] = info
            print(f"[試合情報] {game_id} {date} 開場{info.get('open', '?')} "
                  f"スケジュール{len(info.get('timeline', []))}件 イベント{len(info.get('events', []))}件")

    # ブルーサンダー(リーグH公式の試合ページの Information)。1つのチームの不調で、ほかの分が止まらないようにする
    try:
        thunder_info(games, result, now_iso)
    except Exception as e:
        print(f"[WARN] 試合情報(ブルーサンダー): 読み取りに失敗 {e!r}")

    # 今回読めなかった試合は、試合日から KEEP_PAST_DAYS 日までは前回のデータを残す
    today = datetime.now(JST).date()
    for game_id, info in old.items():
        if game_id in result:
            continue
        try:
            d = datetime.strptime(info.get("date", ""), "%Y-%m-%d").date()
        except ValueError:
            continue
        if d >= today - timedelta(days=KEEP_PAST_DAYS):
            result[game_id] = info

    with open(OUT_PATH, "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=2)
    print(f"[試合情報] {len(result)}試合分を書き出しました")


if __name__ == "__main__":
    main()
