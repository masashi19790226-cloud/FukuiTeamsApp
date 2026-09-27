"""
3チームの公式サイト等を見に行き、終了した試合のスコアを data/results.json に記録する。

- ブローウィンズ : 公式サイトのトップページ(直近の試合カード)
- 丸岡RUCK       : 女子Fリーグ公式のクラブ日程ページ(gid=試合番号ごとのスコアリンク)
- ユナイテッド   : 公式サイトのトップページ「LATEST MATCH」欄

どのサイトも構造が変わると取れなくなるので、GitHub Actionsの実行ログ
(print文の出力)を見ながら調整する想定。
"""

import html as htmllib
import json
import os
import re
import urllib.request
from datetime import datetime, timedelta

BASE_DIR = os.path.join(os.path.dirname(__file__), "..", "data")
GAMES_PATH = os.path.join(BASE_DIR, "games.json")
RESULTS_PATH = os.path.join(BASE_DIR, "results.json")

BLOWINDS_URL = "https://www.fukuiblowinds.com/"
RUCK_URL = "https://w-fleague.jp/club/maruoka/schedule.html"
UNITED_URL = "https://fukuiunited.co.jp/"


# ---------- 共通 ----------

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


def html_to_lines(html: str):
    """タグを改行に置き換えて、空でない行のリストにする。"""
    html = re.sub(r"(?is)<(script|style).*?</\1>", "\n", html)
    text = re.sub(r"<[^>]+>", "\n", html)
    text = htmllib.unescape(text)
    return [ln.strip() for ln in text.splitlines() if ln.strip()]


# ---------- ブローウィンズ(従来どおり) ----------

def blowinds_score(page: str, game_dt: datetime, game: dict):
    date_str = game_dt.strftime("%m/%d")
    time_str = game["time"]
    pattern = re.compile(re.escape(date_str) + r"\s*\([月火水木金土日]\)\s*" + re.escape(time_str))
    m = pattern.search(page)
    if not m:
        return None
    start = m.end()
    next_m = re.compile(r"\d{2}/\d{2}\s*\([月火水木金土日]\)").search(page, start)
    end = next_m.start() if next_m else min(len(page), start + 3000)
    segment = page[start:end]
    if "試合レポート" not in segment and "試合終了" not in segment:
        return None
    score_m = re.search(r"(\d{2,3})\s*[-‐−–]\s*(\d{2,3})", segment)
    if not score_m:
        print(f"[DEBUG] BW: 試合レポートはあるがスコアが見つからない: {date_str} {time_str}")
        return None
    return int(score_m.group(1)), int(score_m.group(2))


# ---------- 丸岡RUCK(女子Fリーグ公式) ----------

def parse_ruck_page(page: str):
    """
    gid(試合番号)→(ホーム側得点, アウェー側得点) の辞書と、
    'YYYY-MM-DD HH:MM' → [gid...] の辞書を返す。
    スコアのリンクは「result.html?gid=104741」で、リンク文字が「0 - 0」。
    """
    scores = {}
    by_datetime = {}
    for m in re.finditer(r"result\.html\?gid=(\d+)[^>]*>(.*?)</a>", page, re.S):
        gid = m.group(1)
        text = htmllib.unescape(re.sub(r"<[^>]+>", " ", m.group(2)))
        sm = re.search(r"(\d{1,2})\s*[-‐−–]\s*(\d{1,2})", text)
        if sm:
            scores[gid] = (int(sm.group(1)), int(sm.group(2)))
        # リンクの直前にある日付・時刻を探す(日付未登録の試合を日時で照合するため)
        before = page[max(0, m.start() - 600):m.start()]
        dts = re.findall(r"(\d{4})\.(\d{2})\.(\d{2}).*?(\d{1,2}:\d{2})", re.sub(r"<[^>]+>", " ", before), re.S)
        if dts:
            y, mo, d, t = dts[-1]
            key = f"{y}-{mo}-{d} {t.zfill(5)}"
            by_datetime.setdefault(key, []).append(gid)
    return scores, by_datetime


def ruck_score(parsed, game: dict):
    scores, by_datetime = parsed
    gid = game.get("gid")
    if not gid:
        # 試合番号がまだ分からない試合は日時で照合(同時刻の試合が複数なら諦める)
        cands = by_datetime.get(f"{game['date']} {game['time']}", [])
        if len(cands) == 1:
            gid = cands[0]
            print(f"[INFO] RUCK: {game['id']} を日時から gid={gid} と判定")
        elif len(cands) > 1:
            print(f"[DEBUG] RUCK: {game['id']} は同時刻の試合が複数あり判定できない: {cands}")
            return None
    if not gid or gid not in scores:
        return None
    home, away = scores[gid]
    # リーグ公式の表記は「ホーム - アウェー」
    return (home, away) if game.get("is_home", True) else (away, home)


# ---------- 福井ユナイテッド(公式トップのLATEST MATCH) ----------

def parse_united_latest(page: str):
    """
    LATEST MATCH欄から (月, 日, 時刻, 左チーム名, 左得点, 右得点, 右チーム名) を取り出す。
    表示例:
      9.27(Sun)11:00KICKOFF / 福井ユナイテッドFC / 1 / 0-1 / 1-1 / 2 / 新潟医療福祉大学FC
    """
    lines = html_to_lines(page)
    try:
        start = next(i for i, ln in enumerate(lines) if "LATEST MATCH" in ln)
    except StopIteration:
        print("[DEBUG] UNITED: LATEST MATCH 欄が見つからない")
        return None
    block = lines[start:start + 40]
    joined = " ".join(block)
    dm = re.search(r"(\d{1,2})\.(\d{1,2})\s*\(\w+\)\s*(\d{1,2}:\d{2})\s*KICK\s*OFF", joined, re.I)
    if not dm:
        print("[DEBUG] UNITED: 日付・キックオフ時刻が見つからない")
        return None
    nums = [i for i, ln in enumerate(block) if re.fullmatch(r"\d{1,2}", ln)]
    if len(nums) < 2:
        print("[DEBUG] UNITED: まだスコアが出ていない")
        return None
    i1, i2 = nums[0], nums[1]
    left_team = block[i1 - 1] if i1 > 0 else ""
    right_team = block[i2 + 1] if i2 + 1 < len(block) else ""
    return (int(dm.group(1)), int(dm.group(2)), dm.group(3).zfill(5),
            left_team, int(block[i1]), int(block[i2]), right_team)


def united_score(latest, game_dt: datetime, game: dict):
    if not latest:
        return None
    mo, d, t, left_team, left, right, right_team = latest
    if (mo, d) != (game_dt.month, game_dt.day) or t != game["time"]:
        return None
    if "ユナイテッド" in left_team:
        return left, right
    if "ユナイテッド" in right_team:
        return right, left
    print(f"[DEBUG] UNITED: どちらが福井か判定できない: {left_team} / {right_team}")
    return None


# ---------- メイン ----------

def safe_fetch(name, url):
    try:
        page = fetch_html(url)
        print(f"[INFO] {name}: 取得 {len(page)} 文字")
        return page
    except Exception as e:
        print(f"[WARN] {name}: 取得失敗 {e}")
        return None


def main():
    games = load_json(GAMES_PATH, [])
    results = load_json(RESULTS_PATH, {})

    # UTCで動くので日本時間に変換。試合開始から2時間たったものだけ確認する
    now = datetime.utcnow() + timedelta(hours=9)
    print(f"[INFO] 現在時刻(日本時間): {now.isoformat()}")

    pending = []
    for game in games:
        if game["id"] in results:
            continue
        try:
            game_dt = datetime.strptime(f"{game['date']} {game['time']}", "%Y-%m-%d %H:%M")
        except ValueError:
            # 「時間未定」の試合は、その日の終わりを目安に確認する
            try:
                game_dt = datetime.strptime(f"{game['date']} 22:00", "%Y-%m-%d %H:%M")
            except ValueError:
                continue
        if game_dt + timedelta(hours=2) > now:
            continue
        pending.append((game, game_dt))

    if not pending:
        print("[INFO] 確認が必要な試合はありません")
        return

    teams = {g.get("team", "blowinds") for g, _ in pending}
    bw_page = safe_fetch("BW", BLOWINDS_URL) if "blowinds" in teams else None
    ruck_page = safe_fetch("RUCK", RUCK_URL) if "rac" in teams else None
    un_page = safe_fetch("UNITED", UNITED_URL) if "united" in teams else None

    ruck_parsed = parse_ruck_page(ruck_page) if ruck_page else None
    if ruck_parsed:
        print(f"[INFO] RUCK: スコア付きの試合 {len(ruck_parsed[0])} 件を検出")
    un_latest = parse_united_latest(un_page) if un_page else None
    if un_latest:
        print(f"[INFO] UNITED: LATEST MATCH = {un_latest}")

    updated = 0
    for game, game_dt in pending:
        team = game.get("team", "blowinds")
        score = None
        if team == "blowinds" and bw_page:
            score = blowinds_score(bw_page, game_dt, game)
        elif team == "rac" and ruck_parsed:
            score = ruck_score(ruck_parsed, game)
        elif team == "united" and un_page:
            score = united_score(un_latest, game_dt, game)

        label = f"{game['id']} ({game['date']} vs {game['opponent']})"
        if score:
            results[game["id"]] = {
                "my_score": score[0],
                "opponent_score": score[1],
                "checked_at": datetime.utcnow().isoformat(),
            }
            updated += 1
            print(f"[INFO] {label}: {score[0]} - {score[1]} を記録")
        else:
            print(f"[INFO] {label}: まだ結果が見つからない")

    if updated:
        save_json(RESULTS_PATH, results)
    print(f"[INFO] 更新件数: {updated}")


if __name__ == "__main__":
    main()
