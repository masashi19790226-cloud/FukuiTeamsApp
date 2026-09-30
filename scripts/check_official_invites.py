"""
3チームの公式サイト・公式ストアを直接見に行き、無料招待・プレゼント情報を
data/invitations_raw.json に追記する。Googleアラートでは拾えない情報の穴埋め用。

見ている場所
- ブローウィンズ : 公式ニュース一覧(本文まで確認)、トップページの招待特設ページ(/invitation/...)
- ユナイテッド   : 公式ニュース一覧(本文まで確認)、公式オンラインストアの¥0招待チケット
- 丸岡RUCK       : 公式サイトのRSS(本文まで確認)
- 福井市         : 市のスポーツ・スポーツ課のページ一覧(本文まで確認、チーム名が出てくるものだけ)

新しい記事だけ本文を読み、読んだ記事は data/official_seen.json に記録して二度読まない。
サイトの作りが変わると取れなくなるので、GitHub Actions の実行ログを見て調整する。
"""

import html as htmllib
import json
import os
import re
import time
import urllib.request
import xml.etree.ElementTree as ET
from datetime import datetime, timedelta, timezone
from urllib.parse import urljoin

from invite_filter import ALWAYS_KEEP_SOURCES, clean_title, filter_invites, is_real_invite

BASE_DIR = os.path.join(os.path.dirname(__file__), "..", "data")
INVITATIONS_PATH = os.path.join(BASE_DIR, "invitations_raw.json")
SEEN_PATH = os.path.join(BASE_DIR, "official_seen.json")
NEWS_PATH = os.path.join(BASE_DIR, "news_raw.json")
# 公式サイトのお知らせをニュース欄に載せる期間
NEWS_DAYS = 30

JST = timezone(timedelta(hours=9))
UA = "Mozilla/5.0 (Linux; Android 14) FukuiSpoBot/1.0 (+https://github.com/masashi19790226-cloud/FukuiTeamsApp)"

# 1回の実行で本文を読みに行く記事数の上限(サイトに負担をかけないため)
MAX_DETAILS_PER_SOURCE = 12
# 公開日がこれより古い記事は「今さら見つけたもの」として通知対象にしない
BACKFILL_DAYS = 3

# タイトルにこれが入っていれば招待・プレゼント情報とみなす
TITLE_WORDS = re.compile(r"招待|プレゼント|無料観戦|観戦無料|ご優待|抽選で")
# 本文でこれが入っている文を探す
BODY_WORDS = re.compile(
    r"無料招待|ご招待|招待券|招待席|無料観戦|無料でご|プレゼント|抽選で|先着\d+名|"
    r"チケット.{0,10}(配布|進呈|差し上げ|お渡し)"
)

# 市町村のページなど、チームが決まっていない所から見つけたときのチーム判定
TEAM_WORDS = [
    ("BLOWINDS", re.compile(r"ブローウィンズ|BLOWINDS", re.I)),
    ("UNITED", re.compile(r"ユナイテッド|UNITED", re.I)),
    ("RAC", re.compile(r"RUCK|ラック|丸岡", re.I)),
]

# 福井市:スポーツ関連ページの一覧(市民向けの無料招待はここに載る)
FUKUI_CITY_LISTS = [
    "https://www.city.fukui.lg.jp/dept/kankoubunka/sports/index.html",
    "https://www.city.fukui.lg.jp/kyoiku/sports/sports/index.html",
]
# 毎回載っている定型文(小中高は無料、など)は招待とみなさない
BOILERPLATE = re.compile(
    r"小中高.{0,10}(観戦|入場)?無料|高校生以下.{0,20}無料|未就学児.{0,30}無料|"
    r"ご招待券は不要|招待券.{0,15}(利用方法|引換|ご利用)|ファンクラブ.{0,20}招待券"
)


# ---------- 共通 ----------

def fetch(url: str) -> str:
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept-Language": "ja"})
    with urllib.request.urlopen(req, timeout=25) as res:
        raw = res.read()
        charset = res.headers.get_content_charset() or "utf-8"
    return raw.decode(charset, errors="replace")


def text_of(fragment: str) -> str:
    t = re.sub(r"<(script|style)[^>]*>.*?</\1>", " ", fragment, flags=re.S | re.I)
    t = re.sub(r"<br\s*/?>|</p>|</li>|</div>|</h\d>", "\n", t, flags=re.I)
    t = re.sub(r"<[^>]+>", " ", t)
    t = htmllib.unescape(t)
    t = re.sub(r"[ \t\u3000]+", " ", t)
    return re.sub(r"\n\s*\n+", "\n", t).strip()


def links(page_html: str, base_url: str, href_pattern: str):
    """ページ内の <a> から href が pattern に合うものを (絶対URL, リンク文字) で返す(重複なし・出現順)。"""
    found = {}
    for m in re.finditer(r"<a\b[^>]*href=[\"']([^\"']+)[\"'][^>]*>(.*?)</a>", page_html, flags=re.S | re.I):
        href = htmllib.unescape(m.group(1))
        if not re.search(href_pattern, href):
            continue
        url = urljoin(base_url, href)
        label = text_of(m.group(2)).replace("\n", " ")
        if url not in found or len(label) > len(found[url]):
            found[url] = label
    return list(found.items())


DATE_RE = re.compile(r"(20\d\d)\s*[./年-]\s*(\d{1,2})\s*[./月-]\s*(\d{1,2})")


def split_label(label: str):
    """一覧のリンク文字「タイトル 2026/08/31 タイトル CATEGORY」から (タイトル, 日付) を取り出す。"""
    m = DATE_RE.search(label)
    date = None
    if m:
        try:
            date = datetime(int(m.group(1)), int(m.group(2)), int(m.group(3)), 9, 0, tzinfo=JST)
        except ValueError:
            date = None
        before, after = label[:m.start()].strip(), label[m.end():].strip()
        title = after or before
    else:
        title = label
    title = re.sub(r"\s+(NEW|[A-Z]{3,})(\s+[A-Z]{3,})*\s*$", "", title).strip()  # 末尾のカテゴリ英字
    title = re.sub(r"^(NEW\s+)?([A-Z]{3,}\s+)+", "", title).strip()
    return title, date


def body_text(page_html: str) -> str:
    """記事ページの本文だけに近づける。メニュー・フッター・リンク(関連記事一覧など)は除く。"""
    t = re.sub(r"<(header|nav|footer|aside)\b[^>]*>.*?</\1>", " ", page_html, flags=re.S | re.I)
    t = re.sub(r"<a\b[^>]*>.*?</a>", " ", t, flags=re.S | re.I)
    return text_of(t)


def find_snippet(body: str):
    """本文から招待らしい文を1つ返す。定型文だけ・招待ではない文(来場者プレゼントなど)だけなら None。"""
    for sentence in re.split(r"[。\n]", body):
        s = sentence.strip()
        if len(s) < 6 or not BODY_WORDS.search(s):
            continue
        if BOILERPLATE.search(s) and not re.search(r"無料招待|ご招待(?!券)|抽選で|先着\d+名", s):
            continue
        # 観戦・チケット・招待に関わる文だけを招待とみなす(invite_filter.py)
        if not is_real_invite(s):
            continue
        return s[:90]
    return None


def page_date(body: str):
    m = DATE_RE.search(body)
    if not m:
        return None
    try:
        return datetime(int(m.group(1)), int(m.group(2)), int(m.group(3)), 9, 0, tzinfo=JST)
    except ValueError:
        return None


def load_json(path, default):
    if not os.path.exists(path):
        return default
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def save_json(path, data):
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)


class Collector:
    def __init__(self):
        self.items = load_json(INVITATIONS_PATH, [])
        self.known_links = {i.get("link") for i in self.items} | {i.get("id") for i in self.items}
        self.seen = load_json(SEEN_PATH, {})
        self.added = 0
        self.now = datetime.now(timezone.utc)
        self.news = load_json(NEWS_PATH, [])
        self.news_ids = {n.get("id") for n in self.news}
        self.news_added = 0

    def add_news(self, team, title, url, date, source):
        """公式サイトのお知らせをニュース欄にも載せる(30日以内の記事だけ)。"""
        news_id = f"official-news:{url}"
        if not title or news_id in self.news_ids:
            return
        if date is None or self.now - date.astimezone(timezone.utc) > timedelta(days=NEWS_DAYS):
            return
        self.news.append({
            "id": news_id, "team": team, "title": title, "link": url,
            "published": date.isoformat(),
            # 何日も前の記事を初めて取り込むときは、通知が一斉に来ないよう検知日時を公開日に合わせる
            "detected_at": (date.astimezone(timezone.utc) if self.now - date.astimezone(timezone.utc) > timedelta(days=BACKFILL_DAYS)
                            else self.now).isoformat(),
            "source": source,
        })
        self.news_ids.add(news_id)
        self.news_added += 1

    def is_seen(self, url):
        return url in self.seen or url in self.known_links or f"official:{url}" in self.known_links

    def mark_seen(self, url):
        self.seen[url] = self.now.isoformat()

    def add(self, team, title, url, date, snippet, source):
        if f"official:{url}" in self.known_links or url in self.known_links:
            return
        title = clean_title(title)
        if source not in ALWAYS_KEEP_SOURCES and not is_real_invite(title, snippet or ""):
            print(f"  - 招待ではないので除外: [{team}] {title}")
            return
        published = date.isoformat() if date else self.now.isoformat()
        detected = self.now
        # 何日も前の記事を今さら見つけた場合は、通知が一斉に来ないよう検知日時を公開日に合わせる
        if date and self.now - date.astimezone(timezone.utc) > timedelta(days=BACKFILL_DAYS):
            detected = date.astimezone(timezone.utc)
        self.items.append({
            "id": f"official:{url}",
            "team": team,
            "title": title,
            "link": url,
            "published": published,
            "detected_at": detected.isoformat(),
            "snippet": snippet or "",
            "source": source,
        })
        self.known_links.add(f"official:{url}")
        self.added += 1
        print(f"  + [{team}] {title} ({source})" + (f" …{snippet}" if snippet else ""))

    def check_news_list(self, team, list_url, href_pattern, source):
        """ニュース一覧 → 新しい記事の本文を読んで招待かどうか判定。"""
        try:
            page = fetch(list_url)
        except Exception as e:
            print(f"[WARN] {source}: 一覧の取得に失敗 {e}")
            return
        entries = links(page, list_url, href_pattern)
        print(f"[{source}] 一覧から {len(entries)} 件")
        read = 0
        for url, label in entries:
            title, date = split_label(label)
            self.add_news(team, title, url, date, source)
            if self.is_seen(url):
                continue
            # 見出しだけで招待とはっきり分かるときは本文を読まない。それ以外は本文で確かめる
            if is_real_invite(title):
                self.add(team, title, url, date, None, source)
                self.mark_seen(url)
                continue
            if read >= MAX_DETAILS_PER_SOURCE:
                continue  # 次回の実行で読む
            read += 1
            try:
                body = body_text(fetch(url))
            except Exception as e:
                print(f"[WARN] {source}: 本文の取得に失敗 {url} {e}")
                continue
            self.mark_seen(url)
            snippet = find_snippet(body)
            if snippet:
                self.add(team, title or "(タイトル不明)", url, date or page_date(body), snippet, source)
            time.sleep(1)

    def check_city(self, list_urls, href_pattern, source):
        """市町村のページ一覧 → チーム名が出てくる新しいページの本文を読んで招待か判定。"""
        entries = {}
        for list_url in list_urls:
            try:
                page = fetch(list_url)
            except Exception as e:
                print(f"[WARN] {source}: 一覧の取得に失敗 {list_url} {e}")
                continue
            for url, label in links(page, list_url, href_pattern):
                if url not in entries or len(label) > len(entries[url]):
                    entries[url] = label
            time.sleep(1)
        print(f"[{source}] 一覧から {len(entries)} 件")
        read = 0
        for url, label in entries.items():
            if self.is_seen(url):
                continue
            if read >= MAX_DETAILS_PER_SOURCE:
                break  # 残りは次回
            read += 1
            try:
                page_html = fetch(url)
            except Exception as e:
                print(f"[WARN] {source}: 本文の取得に失敗 {url} {e}")
                continue
            self.mark_seen(url)
            body = body_text(page_html)
            # 一覧のリンク文字がページ名。短すぎるときだけページの見出し(h1)で補う
            title = label.strip()
            if len(title) < 6:
                heads = [text_of(h).replace("\n", " ").strip()
                         for h in re.findall(r"<h1[^>]*>(.*?)</h1>", page_html, flags=re.S | re.I)]
                heads = [h for h in heads if len(h) >= 6]
                title = heads[-1] if heads else (title or "(タイトル不明)")
            team = next((t for t, rx in TEAM_WORDS if rx.search(title)), None) \
                or next((t for t, rx in TEAM_WORDS if rx.search(body)), None)
            if team is None:
                continue  # チームと関係ないページ
            snippet = None if is_real_invite(title) else find_snippet(body)
            if is_real_invite(title) or snippet:
                self.add(team, title, url, page_date(body), snippet, source)
            time.sleep(1)

    def check_blowinds_invitation_pages(self):
        url = "https://www.fukuiblowinds.com/"
        try:
            page = fetch(url)
        except Exception as e:
            print(f"[WARN] ブローウィンズ特設: 取得に失敗 {e}")
            return
        for link, label in links(page, url, r"/invitation/[\w-]+"):
            if self.is_seen(link):
                continue
            try:
                body = body_text(fetch(link))
            except Exception as e:
                print(f"[WARN] ブローウィンズ特設: {link} {e}")
                continue
            self.mark_seen(link)
            m = re.search(r"募集期間[:：]?\s*([^\n]{3,30})", body)
            title = "無料ご招待(特設ページ)" + (f" 募集 {m.group(1).strip()}" if m else "")
            self.add("BLOWINDS", title, link, None, find_snippet(body), "公式特設ページ")
            time.sleep(1)

    def check_united_store(self):
        for page_url in ["https://fukuiunited.stores.jp/", "https://fukuiunited.stores.jp/?page=2"]:
            try:
                page = fetch(page_url)
            except Exception as e:
                print(f"[WARN] ユナイテッド公式ストア: 取得に失敗 {e}")
                continue
            for url, label in links(page, page_url, r"/items/[0-9a-f]{12,}"):
                if self.is_seen(url):
                    continue
                if "招待" in label or re.search(r"¥\s*0(?![\d,])", label):
                    title = re.sub(r"\s*¥\s*0\s*$", "", label).strip()
                    self.add("UNITED", title, url, None, "公式ストアで無料チケットとして受付中", "公式ストア")
                    self.mark_seen(url)
            time.sleep(1)

    def check_ruck_feed(self):
        url = "https://ruck-fukui.com/feed/"
        try:
            root = ET.fromstring(fetch(url).encode("utf-8"))
        except Exception as e:
            print(f"[WARN] 丸岡RUCK: RSSの取得に失敗 {e}")
            return
        ns = {"content": "http://purl.org/rss/1.0/modules/content/"}
        count = 0
        for item in root.iter("item"):
            count += 1
            link = (item.findtext("link") or "").strip()
            if not link:
                continue
            title = text_of(item.findtext("title") or "")
            body = text_of((item.findtext("content:encoded", namespaces=ns) or "") + "\n" + (item.findtext("description") or ""))
            date = None
            try:
                from email.utils import parsedate_to_datetime
                date = parsedate_to_datetime(item.findtext("pubDate") or "")
            except Exception:
                pass
            self.add_news("RAC", title, link, date, "丸岡RUCK公式")
            if self.is_seen(link):
                continue
            self.mark_seen(link)
            if is_real_invite(title):
                self.add("RAC", title, link, date, None, "公式サイト")
                continue
            snippet = find_snippet(body)
            if snippet:
                self.add("RAC", title, link, date, snippet, "公式サイト")
        print(f"[丸岡RUCK] RSSから {count} 件")

    def save(self):
        # 以前の判定で集めた「招待ではないもの」(来場者プレゼント・入会特典・開催報告など)を取り除く
        self.items, removed = filter_invites(self.items)
        if removed:
            print(f"[公式サイト] 招待ではない{removed}件を取り除きました")
        save_json(INVITATIONS_PATH, self.items)
        # 記録は新しい600件だけ残す
        trimmed = dict(sorted(self.seen.items(), key=lambda kv: kv[1], reverse=True)[:600])
        save_json(SEEN_PATH, trimmed)
        print(f"[公式サイト] 新しく見つかった招待: {self.added}件(累計 {len(self.items)}件)")
        save_json(NEWS_PATH, self.news)
        print(f"[公式サイト] ニュースに追加: {self.news_added}件")


def main():
    c = Collector()
    c.check_news_list("BLOWINDS", "https://www.fukuiblowinds.com/news/", r"/news/detail/(id=)?\d+", "ブローウィンズ公式")
    c.check_blowinds_invitation_pages()
    c.check_news_list("UNITED", "https://fukuiunited.co.jp/news/", r"news/detail\.php\?(post_)?id=\d+", "ユナイテッド公式")
    c.check_united_store()
    c.check_ruck_feed()
    c.check_city(FUKUI_CITY_LISTS, r"/p\d{5,}\.html", "福井市")
    c.save()


if __name__ == "__main__":
    main()
