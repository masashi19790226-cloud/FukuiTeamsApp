"""
Googleアラート(Atomフィード)を定期的にチェックし、新しく見つかった情報を
JSONに追記していくスクリプト。「無料招待用」と「ニュース用」の2系統を扱う。

GitHub Actions (.github/workflows/check-invitations.yml) から定期実行される想定。
ローカルで試す場合は `python3 scripts/check_alerts.py` を実行する。
"""

import html
import json
import os
import re
import urllib.request
import xml.etree.ElementTree as ET
from datetime import datetime, timedelta, timezone

from invite_filter import filter_invites, is_real_invite

# 無料招待・プレゼント関連(「招待 OR プレゼント」で絞り込み済み)
INVITATION_FEEDS = {
    "BLOWINDS": "https://www.google.com/alerts/feeds/17849435109291614678/858967028642974105",
    "UNITED": "https://www.google.com/alerts/feeds/17849435109291614678/6859633318614752326",
    "RAC": "https://www.google.com/alerts/feeds/17849435109291614678/858967028642975140",
}

# チーム名だけの一般ニュース用
NEWS_FEEDS = {
    "BLOWINDS": "https://www.google.com/alerts/feeds/17849435109291614678/14992852420762577242",
    "UNITED": "https://www.google.com/alerts/feeds/17849435109291614678/14992852420762577495",
    "RAC": "https://www.google.com/alerts/feeds/17849435109291614678/11869471234840652241",
}

# Bingニュースの検索結果(RSS)。Googleアラートより新しい記事が早く出ることが多いので、ニュースに加える。
# (GoogleニュースのRSSは robots.txt で自動取得が禁止されているため使わない。Bingニュースの検索は禁止されていない)
BING_NEWS_QUERIES = {
    # パブリックビューイング(PV・観戦会)の告知も拾えるよう、チーム名とPVの言葉の組み合わせでも探す
    "BLOWINDS": ["福井ブローウィンズ", "ブローウィンズ パブリックビューイング", "ブローウィンズ 観戦会"],
    "RAC": ["丸岡RUCK", "丸岡ラック", "丸岡RUCK パブリックビューイング"],
    "UNITED": ["福井ユナイテッド", "福井ユナイテッド パブリックビューイング"],
}
BING_NEWS_URL = "https://www.bing.com/news/search?q={q}&format=rss&setlang=ja&cc=JP"

BASE_DIR = os.path.join(os.path.dirname(__file__), "..", "data")
INVITATIONS_PATH = os.path.join(BASE_DIR, "invitations_raw.json")
NEWS_PATH = os.path.join(BASE_DIR, "news_raw.json")
# 45日より前のニュースの保管先(1年分)。アプリでは「過去のトピック」を開いたとき・検索したときだけ読み込む
ARCHIVE_PATH = os.path.join(BASE_DIR, "news_archive.json")
ARCHIVE_KEEP_DAYS = 365

ATOM_NS = "{http://www.w3.org/2005/Atom}"


def strip_html(text: str) -> str:
    """Googleアラートのタイトルには<b>タグや &quot; などが入っているので取り除く(二重に変換されていることがある)"""
    t = re.sub(r"<[^>]+>", "", text or "")
    for _ in range(2):
        t = html.unescape(t)
    return re.sub(r"<[^>]+>", "", t).strip()


# 記事の中身からチームを判定する。アラートの検索語どうしが重なって、
# 別チームのフィードに記事が入ってくることがあるため(丸岡RUCKの記事がユナイテッド側に入る等)。
TEAM_WORDS = {
    "BLOWINDS": re.compile(r"ブローウィンズ|BLOWINDS", re.I),
    # 「ユナイテッド」だけだと海外サッカー(マンチェスター・ユナイテッド等)まで入るので福井とセットのときだけ
    "UNITED": re.compile(r"福井\s*ユナイテッド|福井U(?![A-Za-z0-9])|FUKUI\s*UNITED|ユナイテッド.{0,20}福井|福井.{0,20}ユナイテッド", re.I),
    "RAC": re.compile(r"丸岡\s*(RUCK|ラック)|丸岡\s*de\s*フットサル|RUCK.{0,20}丸岡|丸岡.{0,20}RUCK|福井丸岡", re.I),
}

# ニュースから外すもの(通販・求人・フリマなど、チーム名が入っていても記事ではないもの)
NEWS_NOISE = re.compile(r"求人|アルバイト|バイト募集|楽天市場|Amazon|メルカリ|ヤフオク|中古|通販|送料|価格\.com|ラクマ", re.I)

# ニュースとして残す期間
NEWS_KEEP_DAYS = 45


def is_old_title(title: str) -> bool:
    """見出しに去年以前の年やシーズン(「2024」「2024-25」など)しか出てこない記事は古い情報とみなす。"""
    this_year = datetime.now(timezone.utc).year
    years = [int(y) for y in re.findall(r"(?<!\d)(20\d\d)(?!\d)", title or "")]
    seasons = [int(a) + 1 for a in re.findall(r"(?<!\d)(20\d\d)\s*[-–/]\s*\d{2}(?!\d)", title or "")]
    mentioned = years + seasons
    return bool(mentioned) and max(mentioned) < this_year


# X(旧Twitter)の投稿。Googleアラートでは「○○ on X: "…"」「… / X」「… - X」の形の見出しになる
X_POST = re.compile(r"\bon X\b|[/\-]\s*X\s*$|^X$")


def is_personal_x_post(title: str) -> bool:
    """個人のXの投稿か。チームの【公式】アカウントの投稿は残す。"""
    t = title or ""
    return bool(X_POST.search(t)) and "【公式】" not in t


def is_good_news(title: str) -> bool:
    """3チームのどれかがはっきり書かれていて、ノイズでも古い情報でも個人のXの投稿でもない記事だけ残す。"""
    if not any(rx.search(title or "") for rx in TEAM_WORDS.values()):
        return False
    if is_personal_x_post(title):
        return False
    if NEWS_NOISE.search(title or ""):
        return False
    return not is_old_title(title)


def clean_news(items):
    """保存済みニュースから、関係ない・古い・期限切れのものを取り除く。公式サイト由来は期間だけで判定。"""
    cutoff = datetime.now(timezone.utc) - timedelta(days=NEWS_KEEP_DAYS)
    kept, removed = [], 0
    for item in items:
        official = str(item.get("id", "")).startswith("official")
        try:
            when = datetime.fromisoformat(str(item.get("published") or item.get("detected_at")).replace("Z", "+00:00"))
            if when.tzinfo is None:
                when = when.replace(tzinfo=timezone.utc)
        except ValueError:
            when = None
        if (when and when < cutoff) or (not official and not is_good_news(item.get("title", ""))):
            removed += 1
            continue
        kept.append(item)
    return kept, removed


def guess_team(title: str, feed_team: str) -> str:
    hits = [team for team, rx in TEAM_WORDS.items() if rx.search(title or "")]
    if len(hits) == 1:
        return hits[0]
    return feed_team  # どれにも当てはまらない・複数当てはまるときはフィードの割り当てのまま


def repair_existing(items):
    """保存済みの記事の文字化け(&quot;など)とチームの割り当てを直す。公式サイト由来はそのまま。"""
    fixed = 0
    for item in items:
        if str(item.get("id", "")).startswith("official:"):
            continue
        title = strip_html(item.get("title", ""))
        team = guess_team(title, item.get("team", ""))
        if title != item.get("title") or team != item.get("team"):
            item["title"], item["team"] = title, team
            fixed += 1
    return fixed


def fetch_feed(url: str) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": "FukuiTeamsAppBot/1.0"})
    with urllib.request.urlopen(req, timeout=20) as res:
        return res.read()


def parse_entries(xml_bytes: bytes):
    root = ET.fromstring(xml_bytes)
    entries = []
    for entry in root.findall(f"{ATOM_NS}entry"):
        entry_id = entry.findtext(f"{ATOM_NS}id", default="")
        title = strip_html(entry.findtext(f"{ATOM_NS}title", default=""))
        link_el = entry.find(f"{ATOM_NS}link")
        link = link_el.get("href") if link_el is not None else ""
        published = entry.findtext(f"{ATOM_NS}published", default="") or entry.findtext(
            f"{ATOM_NS}updated", default=""
        )
        entries.append(
            {
                "id": entry_id,
                "title": title,
                "link": link,
                "published": published,
            }
        )
    return entries


def load_existing(path):
    if not os.path.exists(path):
        return []
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def save(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)


def item_time(item):
    """記事の日時(公開日、なければ見つけた日時)。読めなければ None"""
    try:
        when = datetime.fromisoformat(str(item.get("published") or item.get("detected_at")).replace("Z", "+00:00"))
        return when if when.tzinfo else when.replace(tzinfo=timezone.utc)
    except ValueError:
        return None


def archive_old_news():
    """
    ニュースのうち保存期間(45日)を過ぎたものを、捨てずに news_archive.json へ移す。
    保管庫は1年分(365日)を残し、それより古いものは消す。関係ない記事(ノイズ)は移さない。
    """
    news = load_existing(NEWS_PATH)
    archive = load_existing(ARCHIVE_PATH)
    now = datetime.now(timezone.utc)
    cutoff = now - timedelta(days=NEWS_KEEP_DAYS)
    archive_cutoff = now - timedelta(days=ARCHIVE_KEEP_DAYS)
    archive_ids = {a["id"] for a in archive}
    moved = 0
    for item in news:
        when = item_time(item)
        if when is None or when >= cutoff or item["id"] in archive_ids:
            continue
        official = str(item.get("id", "")).startswith("official")
        if not official and not is_good_news(item.get("title", "")):
            continue
        archive.append(item)
        archive_ids.add(item["id"])
        moved += 1
    before = len(archive)
    archive = [a for a in archive if (item_time(a) or now) >= archive_cutoff]
    archive.sort(key=lambda a: str(a.get("published") or a.get("detected_at") or ""), reverse=True)
    save(ARCHIVE_PATH, archive)
    print(f"[過去のニュース] {moved}件を保管庫へ移しました(1年より前の{before - len(archive)}件を削除、保管中 {len(archive)}件)")


def archived_ids():
    """保管庫にある記事のID(同じ記事を新着として入れ直さないため)"""
    return {a["id"] for a in load_existing(ARCHIVE_PATH)}


def check_feeds(feeds: dict, path: str, label: str, is_news: bool = False) -> int:
    existing = load_existing(path)
    skipped = 0
    if is_news:
        existing, removed = clean_news(existing)
        if removed:
            print(f"[{label}] 関係ない・古い記事を{removed}件取り除きました")
    if not is_news:
        # 招待:以前の判定で集めた「招待ではないもの」を取り除く
        existing, removed = filter_invites(existing)
        if removed:
            print(f"[{label}] 招待ではない{removed}件を取り除きました")
    repaired = repair_existing(existing)
    if repaired:
        print(f"[{label}] 既存の{repaired}件のタイトル・チームを修正しました")
    existing_ids = {item["id"] for item in existing}
    if is_news:
        existing_ids |= archived_ids()
    new_count = 0

    for team, url in feeds.items():
        try:
            xml_bytes = fetch_feed(url)
        except Exception as e:
            print(f"[WARN] {label}/{team} のフィード取得に失敗しました: {e}")
            continue

        for entry in parse_entries(xml_bytes):
            if not entry["id"] or entry["id"] in existing_ids:
                continue
            if is_news and not is_good_news(entry["title"]):
                existing_ids.add(entry["id"])
                skipped += 1
                continue
            # 招待のアラートは、見出しから招待とはっきり分かるものだけ入れる
            # (Googleアラートは本文の「招待」「プレゼント」でも引っかかるため、関係ない記事が多い)
            if not is_news and not is_real_invite(entry["title"]):
                existing_ids.add(entry["id"])
                skipped += 1
                continue
            existing.append(
                {
                    "id": entry["id"],
                    "team": guess_team(entry["title"], team),
                    "title": entry["title"],
                    "link": entry["link"],
                    "published": entry["published"],
                    "detected_at": datetime.now(timezone.utc).isoformat(),
                }
            )
            existing_ids.add(entry["id"])
            new_count += 1

    save(path, existing)
    print(f"[{label}] 新しく見つかった件数: {new_count}件(累計 {len(existing)}件)" + (f"、対象外{skipped}件" if skipped else ""))
    return new_count


def bing_image(url: str) -> str:
    """Bingのサムネイル画像のURLを、安全な https にして、小さめの大きさを指定する"""
    url = (url or "").strip()
    if not url:
        return ""
    if url.startswith("http://"):
        url = "https://" + url[len("http://"):]
    return url + ("&" if "?" in url else "?") + "w=160&h=160&c=7"


def parse_bing_rss(xml_text: str):
    """BingニュースのRSSから記事を取り出す。リンクは Bing の転送用URLなので、中の元の記事のURLを使う。"""
    from email.utils import parsedate_to_datetime
    from urllib.parse import parse_qs, urlparse

    entries = []
    for item in re.findall(r"<item>(.*?)</item>", xml_text, flags=re.S):
        def tag(name):
            m = re.search(rf"<{name}>(.*?)</{name}>", item, flags=re.S)
            return html.unescape(m.group(1)).strip() if m else ""

        title = strip_html(tag("title"))
        link = tag("link")
        real = parse_qs(urlparse(link).query).get("url", [""])[0] or link
        try:
            published = parsedate_to_datetime(tag("pubDate")).astimezone(timezone.utc).isoformat()
        except Exception:
            published = ""
        if title and real:
            entries.append({
                "id": "bing:" + real,
                "title": title,
                "link": real,
                "published": published,
                # サムネイル画像(Bingが用意している小さな画像)。無ければ空
                "image": bing_image(tag("News:Image")),
                # 媒体名(「FNNプライムオンライン on MSN」→「FNNプライムオンライン」)
                "source": re.sub(r"\s+on MSN$", "", strip_html(tag("News:Source"))),
            })
    return entries


def check_bing_news() -> int:
    """Bingニュースの新着を、ニュース(news_raw.json)に加える。Googleアラートと同じ基準で絞り込む。"""
    existing = load_existing(NEWS_PATH)
    existing_ids = {item["id"] for item in existing} | archived_ids()
    existing_links = {item.get("link") for item in existing}
    cutoff = datetime.now(timezone.utc) - timedelta(days=NEWS_KEEP_DAYS)
    new_count, skipped = 0, 0
    for team, queries in BING_NEWS_QUERIES.items():
        for q in queries:
            try:
                url = BING_NEWS_URL.format(q=urllib.request.quote(q))
                text = fetch_feed(url).decode("utf-8", errors="replace")
            except Exception as e:
                print(f"[WARN] Bingニュース/{q} の取得に失敗しました: {e}")
                continue
            for entry in parse_bing_rss(text):
                if entry["id"] in existing_ids or entry["link"] in existing_links:
                    continue
                # 古い記事(公開日が保存期間より前)・関係ない記事は入れない
                try:
                    old = bool(entry["published"]) and datetime.fromisoformat(entry["published"]) < cutoff
                except ValueError:
                    old = False
                if old or not entry["published"] or not is_good_news(entry["title"]):
                    existing_ids.add(entry["id"])
                    skipped += 1
                    continue
                existing.append({
                    "id": entry["id"],
                    "team": guess_team(entry["title"], team),
                    "title": entry["title"],
                    "link": entry["link"],
                    "published": entry["published"],
                    "source": entry["source"],
                    "image": entry["image"],
                    "detected_at": datetime.now(timezone.utc).isoformat(),
                })
                existing_ids.add(entry["id"])
                existing_links.add(entry["link"])
                new_count += 1
    save(NEWS_PATH, existing)
    print(f"[Bingニュース] 新しく見つかった件数: {new_count}件(累計 {len(existing)}件)" + (f"、対象外{skipped}件" if skipped else ""))
    return new_count


def main():
    check_feeds(INVITATION_FEEDS, INVITATIONS_PATH, "無料招待")
    # 45日を過ぎたニュースは、消す前に保管庫(1年分)へ移す
    try:
        archive_old_news()
    except Exception as e:
        print(f"[WARN] 過去のニュースの保管に失敗しました: {e!r}")
    check_feeds(NEWS_FEEDS, NEWS_PATH, "ニュース", is_news=True)
    # Bingニュースは1か所が失敗しても、ほかの処理の結果は残す
    try:
        check_bing_news()
    except Exception as e:
        print(f"[WARN] Bingニュースの処理に失敗しました: {e!r}")


if __name__ == "__main__":
    main()
