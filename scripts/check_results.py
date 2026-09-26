"""
福井ブローウィンズ公式サイトのトップページを見に行き、終了した試合のスコアを
見つけたら data/results.json に記録するスクリプト。

前提: トップページには直近の試合カードが並んでいて、終了した試合には
「試合レポート」というボタンとスコア(例: 90 - 88)が表示される。
この構造は実際のHTMLを直接確認できないまま書いているため、
最初はうまく取れない可能性がある。GitHub Actionsの実行ログ
(print文の出力)を見ながら調整する想定。
"""

import json
import os
import re
import urllib.request
from datetime import datetime

BASE_DIR = os.path.join(os.path.dirname(__file__), "..", "data")
GAMES_PATH = os.path.join(BASE_DIR, "games.json")
RESULTS_PATH = os.path.join(BASE_DIR, "results.json")
HOMEPAGE_URL = "https://www.fukuiblowinds.com/"

WEEKDAY_JP = ["月", "火", "水", "木", "金", "土", "日"]


def load_json(path, default):
    if not os.path.exists(path):
        return default
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def save_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)


def fetch_html(url: str) -> str:
    req = urllib.request.Request(
        url,
        headers={
            "User-Agent": (
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                "AppleWebKit/537.36 (KHTML, like Gecko) "
                "Chrome/120.0 Safari/537.36 FukuiTeamsAppBot/1.0"
            )
        },
    )
    with urllib.request.urlopen(req, timeout=20) as res:
        return res.read().decode("utf-8", errors="ignore")


def find_score_for_game(html: str, date_str: str, time_str: str):
    """
    date_str: 'MM/DD' 形式(例 '09/26')
    time_str: 'HH:MM' 形式(例 '14:05')
    見つかれば (福井のスコア, 相手のスコア) を返す。見つからなければ None。
    """
    pattern = re.compile(re.escape(date_str) + r"\s*\([月火水木金土日]\)\s*" + re.escape(time_str))
    m = pattern.search(html)
    if not m:
        return None

    start = m.end()
    next_date_pattern = re.compile(r"\d{2}/\d{2}\s*\([月火水木金土日]\)")
    next_m = next_date_pattern.search(html, start)
    end = next_m.start() if next_m else min(len(html), start + 3000)
    segment = html[start:end]

    if "試合レポート" not in segment and "試合終了" not in segment:
        return None

    score_pattern = re.compile(r"(\d{2,3})\s*[-‐−–]\s*(\d{2,3})")
    score_m = score_pattern.search(segment)
    if not score_m:
        print(f"[DEBUG] 「試合レポート」は見つかったが、スコアの数字パターンが見つからなかった: {date_str} {time_str}")
        return None
    return int(score_m.group(1)), int(score_m.group(2))


def main():
    games = load_json(GAMES_PATH, [])
    results = load_json(RESULTS_PATH, {})

    try:
        html = fetch_html(HOMEPAGE_URL)
    except Exception as e:
        print(f"[WARN] ホームページの取得に失敗しました: {e}")
        return

    print(f"[INFO] 取得したHTMLの文字数: {len(html)}")

    now = datetime.now()
    updated = 0

    for game in games:
        gid = game["id"]
        if gid in results:
            continue

        try:
            game_dt = datetime.strptime(f"{game['date']} {game['time']}", "%Y-%m-%d %H:%M")
        except ValueError:
            continue

        if game_dt > now:
            continue

        date_str = game_dt.strftime("%m/%d")
        score = find_score_for_game(html, date_str, game["time"])
        if score:
            my_score, opponent_score = score
            results[gid] = {
                "my_score": my_score,
                "opponent_score": opponent_score,
                "checked_at": datetime.utcnow().isoformat(),
            }
            updated += 1
            print(f"[INFO] {gid} ({game['date']} vs {game['opponent']}): {my_score} - {opponent_score} を記録しました")
        else:
            print(f"[INFO] {gid} ({game['date']} vs {game['opponent']}): まだ結果が見つからない")

    if updated:
        save_json(RESULTS_PATH, results)
    print(f"[INFO] 更新件数: {updated}")


if __name__ == "__main__":
    main()
