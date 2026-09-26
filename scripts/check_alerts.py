"""
3チーム分のGoogleアラート(Atomフィード)を定期的にチェックし、
新しく見つかったお知らせを data/invitations_raw.json に追記していくスクリプト。

GitHub Actions (.github/workflows/check-invitations.yml) から定期実行される想定。
ローカルで試す場合は `python3 scripts/check_alerts.py` を実行する。
"""

import json
import os
import re
import urllib.request
import xml.etree.ElementTree as ET
from datetime import datetime, timezone

FEEDS = {
    "BLOWINDS": "https://www.google.com/alerts/feeds/17849435109291614678/858967028642974105",
    "UNITED": "https://www.google.com/alerts/feeds/17849435109291614678/6859633318614752326",
    "RAC": "https://www.google.com/alerts/feeds/17849435109291614678/858967028642975140",
}

DATA_PATH = os.path.join(os.path.dirname(__file__), "..", "data", "invitations_raw.json")

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


def load_existing():
    if not os.path.exists(DATA_PATH):
        return []
    with open(DATA_PATH, "r", encoding="utf-8") as f:
        return json.load(f)


def save(data):
    os.makedirs(os.path.dirname(DATA_PATH), exist_ok=True)
    with open(DATA_PATH, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)


def main():
    existing = load_existing()
    existing_ids = {item["id"] for item in existing}
    new_count = 0

    for team, url in FEEDS.items():
        try:
            xml_bytes = fetch_feed(url)
        except Exception as e:
            print(f"[WARN] {team} のフィード取得に失敗しました: {e}")
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

    save(existing)
    print(f"新しく見つかった件数: {new_count}件(累計 {len(existing)}件)")


if __name__ == "__main__":
    main()
