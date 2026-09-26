"""
Googleアラート(Atomフィード)を定期的にチェックし、新しく見つかった情報を
JSONに追記していくスクリプト。「無料招待用」と「ニュース用」の2系統を扱う。

GitHub Actions (.github/workflows/check-invitations.yml) から定期実行される想定。
ローカルで試す場合は `python3 scripts/check_alerts.py` を実行する。
"""

import json
import os
import re
import urllib.request
import xml.etree.ElementTree as ET
from datetime import datetime, timezone

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

BASE_DIR = os.path.join(os.path.dirname(__file__), "..", "data")
INVITATIONS_PATH = os.path.join(BASE_DIR, "invitations_raw.json")
NEWS_PATH = os.path.join(BASE_DIR, "news_raw.json")

ATOM_NS = "{http://www.w3.org/2005/Atom}"


def strip_html(text: str) -> str:
    """Googleアラートのdescriptionには<b>タグ等が入っているので取り除く"""
    return re.sub(r"<[^>]+>", "", text or "").strip()


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


def check_feeds(feeds: dict, path: str, label: str) -> int:
    existing = load_existing(path)
    existing_ids = {item["id"] for item in existing}
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
            existing.append(
                {
                    "id": entry["id"],
                    "team": team,
                    "title": entry["title"],
                    "link": entry["link"],
                    "published": entry["published"],
                    "detected_at": datetime.now(timezone.utc).isoformat(),
                }
            )
            existing_ids.add(entry["id"])
            new_count += 1

    save(path, existing)
    print(f"[{label}] 新しく見つかった件数: {new_count}件(累計 {len(existing)}件)")
    return new_count


def main():
    check_feeds(INVITATION_FEEDS, INVITATIONS_PATH, "無料招待")
    check_feeds(NEWS_FEEDS, NEWS_PATH, "ニュース")


if __name__ == "__main__":
    main()
