"""
丸岡RUCK公式サイト(WordPress)の記事・写真を読むための共通の部品。photos.py と alumni.py から使う。

まず WordPress の公開API(/wp-json/wp/v2/...)で読み、読めなかったとき(エラー・JSONでない・0件)は、
自動更新でも読めている RSS(サイト内検索の RSS: /?s=言葉&feed=rss2)と、写真ごとのページ(/写真の名前)から読む。
(API はブラウザからは読めるが、GitHub Actions からは読めていなかったため。2026年10月)

どちらで読んでも、API と同じ形 {"id","title":{"rendered"},"link","date","content":{"rendered"}} で返す。
date はサイトの時刻(日本時間)の "2026-09-22T11:29:23" の形。
"""

import html as htmllib
import json
import re
import xml.etree.ElementTree as ET
from datetime import timedelta, timezone
from email.utils import parsedate_to_datetime
from urllib.parse import quote

JST = timezone(timedelta(hours=9))
CONTENT_NS = "{http://purl.org/rss/1.0/modules/content/}encoded"


def _posts_api(fetch, site, word, per_page, fields):
    return json.loads(fetch(f"{site}/wp-json/wp/v2/posts?search={quote(word)}&per_page={per_page}&_fields={fields}"))


def _posts_feed(fetch, site, word, per_page):
    """サイト内検索の RSS から、API と同じ形の記事のリスト(新しい順)。
    RSS は1ページ10件なので、per_page 件になるまで次のページ(&paged=2, 3, …)も読む(ページが無くなったら終わり)"""
    items = []
    for page in range(1, (per_page + 9) // 10 + 1):
        url = f"{site}/?s={quote(word)}&feed=rss2" + (f"&paged={page}" if page > 1 else "")
        try:
            root = ET.fromstring(fetch(url).encode("utf-8"))
        except Exception:
            if page == 1:
                raise
            break  # 次のページが無い(404)
        got = list(root.iter("item"))
        if not got:
            break
        items += got
        if len(got) < 10:
            break
    out = []
    for item in items:
        date = ""
        try:
            date = parsedate_to_datetime(item.findtext("pubDate") or "").astimezone(JST).strftime("%Y-%m-%dT%H:%M:%S")
        except (TypeError, ValueError):
            pass
        out.append({
            "id": (item.findtext("guid") or "").strip(),
            "title": {"rendered": (item.findtext("title") or "").strip()},
            "link": (item.findtext("link") or "").strip(),
            "date": date,
            "content": {"rendered": item.findtext(CONTENT_NS) or item.findtext("description") or ""},
        })
        if len(out) >= per_page:
            break
    return out


def search_posts(fetch, site, word, per_page, fields, label):
    """言葉で記事を探す。API で読めなければ RSS で。label はログに出す名前"""
    try:
        posts = _posts_api(fetch, site, word, per_page, fields)
        if isinstance(posts, list) and posts:
            return posts
        print(f"[INFO] {label}: API で「{word}」の記事が0件だったので RSS で探します")
    except Exception as e:
        print(f"[INFO] {label}: API で「{word}」の記事を読めなかったので RSS で探します {e!r}")
    posts = _posts_feed(fetch, site, word, per_page)
    print(f"[INFO] {label}: RSS で「{word}」の記事 {len(posts)}件")
    return posts


def _media_api(fetch, site, slugs):
    """API で {写真の名前: 横768pxほどの画像のURL}"""
    media = json.loads(fetch(f"{site}/wp-json/wp/v2/media?slug={','.join(slugs)}&per_page=50&_fields=slug,media_details"))
    by_slug = {}
    for m in media:
        sizes = (m.get("media_details") or {}).get("sizes") or {}
        # 横768pxほど(無ければ大きいもの・中くらいのもの)
        for key in ("medium_large", "large", "medium"):
            if sizes.get(key, {}).get("source_url"):
                by_slug[m.get("slug")] = sizes[key]["source_url"]
                break
    return by_slug


def _media_page(fetch, site, slug):
    """写真ごとのページ(例 https://ruck-fukui.com/20260919-01)の <img> の srcset から、横768pxほどの画像のURL。
    <img width="300" height="200" src="…/20260919-01-300x200.jpg" srcset="…-300x200.jpg 300w, …-1024x683.jpg 1024w, …-768x512.jpg 768w, …">"""
    page = fetch(f"{site}/{slug}")
    own = re.compile(rf"/{re.escape(slug)}(?:-\d+x\d+|-scaled)?\.\w+")
    for tag in re.findall(r"<img\b[^>]*>", page):
        src = re.search(r'\bsrc=["\']([^"\']+)["\']', tag)
        if not src or not own.search(src.group(1)):
            continue
        widths = {}
        ss = re.search(r'\bsrcset=["\']([^"\']+)["\']', tag)
        for part in (ss.group(1).split(",") if ss else []):
            bits = part.strip().split()
            if len(bits) == 2 and bits[1].endswith("w") and bits[1][:-1].isdigit():
                widths[int(bits[1][:-1])] = htmllib.unescape(bits[0])
        # API のときと同じ順(横768px → 1024px → 写真のページの画像)
        return widths.get(768) or widths.get(1024) or htmllib.unescape(src.group(1))
    return ""


def media_urls(fetch, site, slugs, label):
    """写真の名前のリストから {写真の名前: 画像のURL}。API で読めなければ写真ごとのページから"""
    if not slugs:
        return {}
    try:
        got = _media_api(fetch, site, slugs)
        if got:
            return got
        print(f"[INFO] {label}: API で写真が0件だったので写真ごとのページから読みます")
    except Exception as e:
        print(f"[INFO] {label}: API で写真を読めなかったので写真ごとのページから読みます {e!r}")
    out = {}
    for s in slugs:
        try:
            url = _media_page(fetch, site, s)
        except Exception as e:
            print(f"[WARN] {label}: 写真のページ {s} の取得に失敗 {e!r}")
            continue
        if url:
            out[s] = url
    return out
