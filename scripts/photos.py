"""
試合・イベントの写真を集める。build_previews.py から1日1回だけ呼び、data/standings.json の "PHOTOS" に入れる。
アプリは選手の顔写真と混ぜて、特集・一面の近況・試合の詳細に出す(選手の顔写真ばかりにならないように)。

- 丸岡RUCK : 公式サイト(WordPress)の試合結果の記事に載っている写真(ギャラリー)。
             記事は WordPress の公開API(/wp-json/wp/v2/posts)で探し、写真は /wp-json/wp/v2/media で
             ちょうどよい大きさ(横768pxほど)の画像のURLを調べる。新しい記事から RUCK_POSTS 件、1記事 RUCK_PER_POST 枚まで

読めなかったときは前回の分を残す。
"""

import html as htmllib
import json
import re
from datetime import datetime, timedelta, timezone
from urllib.parse import quote

JST = timezone(timedelta(hours=9))

RUCK_SITE = "https://ruck-fukui.com"
RUCK_POSTS = 6
RUCK_PER_POST = 12

# データの形の版。変えたときに上げると、その日のうちに作り直す
VERSION = 1


def _title(t: str) -> str:
    t = htmllib.unescape(re.sub(r"<[^>]+>", "", t or "")).strip()
    # 「【第11節】メットライフ生命日本女子Fリーグ2026-27 結果」→「第11節 結果」のように短くする
    m = re.search(r"【([^】]+)】", t)
    tail = "結果" if "結果" in t else ""
    short = f"{m.group(1)} {tail}".strip() if m else t
    return short[:28]


def ruck_gallery(fetch):
    """丸岡RUCK公式サイトの試合結果の記事の写真。[{"url","label","link","date"}, ...](新しい記事から)"""
    posts = json.loads(fetch(f"{RUCK_SITE}/wp-json/wp/v2/posts?search={quote('結果')}&per_page={RUCK_POSTS}&_fields=id,title,link,date,content"))
    out = []
    for p in posts:
        content = (p.get("content") or {}).get("rendered", "")
        # ギャラリーの写真は <a href='https://ruck-fukui.com/20260919-01'> のように写真ごとのページへのリンクが付いている
        slugs = []
        for href in re.findall(r"<a\b[^>]*href=['\"]https?://ruck-fukui\.com/([\w-]+)/?['\"][^>]*>\s*<img", content):
            if href not in slugs:
                slugs.append(href)
        slugs = slugs[:RUCK_PER_POST]
        if not slugs:
            continue
        media = json.loads(fetch(f"{RUCK_SITE}/wp-json/wp/v2/media?slug={','.join(slugs)}&per_page=50&_fields=slug,media_details"))
        by_slug = {}
        for m in media:
            sizes = (m.get("media_details") or {}).get("sizes") or {}
            # 横768pxほど(無ければ大きいもの・中くらいのもの)
            for key in ("medium_large", "large", "medium"):
                if sizes.get(key, {}).get("source_url"):
                    by_slug[m.get("slug")] = sizes[key]["source_url"]
                    break
        label = _title((p.get("title") or {}).get("rendered", ""))
        for s in slugs:
            if s in by_slug:
                out.append({"url": by_slug[s], "label": label, "link": p.get("link", ""), "date": (p.get("date") or "")[:10]})
    print(f"[写真] 丸岡RUCK: {len(posts)}記事から {len(out)}枚")
    return out


def build(fetch, old):
    """写真のデータを作る。old は前回の分(無ければ {})。今日すでに作っていれば None(書き直さない)。
    チームごとに失敗しても、前回の分を残す"""
    today = datetime.now(JST).date().isoformat()
    if old.get("date") == today and old.get("v") == VERSION:
        return None
    teams = dict(old.get("teams") or {})
    for team, func in (("RAC", ruck_gallery),):
        try:
            got = func(fetch)
            if got:
                teams[team] = got
        except Exception as e:
            print(f"[WARN] 写真: {team} の取得に失敗。前回の分を使います {e!r}")
    return {"date": today, "v": VERSION, "updated_at": datetime.now(timezone.utc).isoformat(), "teams": teams}
