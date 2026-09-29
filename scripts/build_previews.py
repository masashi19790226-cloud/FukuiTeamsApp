"""
各チームの「次の試合」の展望データを作り、data/previews.json に書き出す。
アプリの一面「次の試合」欄で使う。

作るもの(取れたものだけ入れる。取れなかった項目は空のまま)
- 両チームの成績・順位・直近の勝敗
- 前回対戦の結果
- 相手の注目選手(背番号・ポジション・成績)
- 上をつないだ短い展望文

データの出どころ
- 3チーム共通 : data/games.json と data/results.json(福井側の成績・直近・前回対戦)
- ブローウィンズ: Bリーグ公式サイトのクラブページ(両チームの成績・順位・直近、相手のクラブリーダー)
- ユナイテッド : 公式サイトトップの北信越リーグ順位表
- 丸岡RUCK      : 女子Fリーグの相手クラブページへのリンクのみ(順位表などはページ内で後から読み込まれるため取れない)
"""

import html as htmllib
import json
import os
import re
import time
import urllib.request
from datetime import datetime, timedelta, timezone

BASE_DIR = os.path.join(os.path.dirname(__file__), "..", "data")
GAMES_PATH = os.path.join(BASE_DIR, "games.json")
RESULTS_PATH = os.path.join(BASE_DIR, "results.json")
PREVIEWS_PATH = os.path.join(BASE_DIR, "previews.json")

JST = timezone(timedelta(hours=9))
UA = "Mozilla/5.0 (Linux; Android 14) FukuiSpoBot/1.0 (+https://github.com/masashi19790226-cloud/FukuiTeamsApp)"

BLEAGUE = "https://www.bleague.jp"
BLOWINDS_TEAM_ID = "2891"
# 今季の試合とみなす日付(これより前の試合は直近成績に入れない)
SEASON_START = {"BLOWINDS": "2026-08-01", "UNITED": "2026-03-01", "RAC": "2026-05-01"}

MY_LABEL = {"BLOWINDS": "福井", "UNITED": "福井", "RAC": "丸岡"}

# 女子Fリーグのクラブページ(相手の選手一覧へのリンク用)
WFLEAGUE_CLUBS = {
    "北海道": "hokkaido", "流経": "ryukei", "龍ケ崎": "ryukei", "さいたま": "saitama", "浦安": "urayasu",
    "すみだ": "sumida", "立川": "tachikawa", "湘南": "shonan", "西宮": "nishinomiya", "神戸": "kobe", "宇部": "ube",
}


# ---------- 共通 ----------

def fetch(url: str) -> str:
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept-Language": "ja"})
    with urllib.request.urlopen(req, timeout=25) as res:
        raw = res.read()
        charset = res.headers.get_content_charset() or "utf-8"
    time.sleep(1)
    return raw.decode(charset, errors="replace")


def text_of(fragment: str) -> str:
    t = re.sub(r"<(script|style)[^>]*>.*?</\1>", " ", fragment, flags=re.S | re.I)
    t = re.sub(r"<br\s*/?>|</p>|</li>|</div>|</h\d>|</tr>|</dt>|</dd>", "\n", t, flags=re.I)
    t = re.sub(r"<[^>]+>", " ", t)
    t = htmllib.unescape(t)
    t = re.sub(r"[ \t\u3000]+", " ", t)
    return re.sub(r"\n\s*\n+", "\n", t).strip()


def load_json(path, default):
    if not os.path.exists(path):
        return default
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def mark(my, opp):
    return "○" if my > opp else ("●" if my < opp else "△")


def md(date_str):
    """'2026-09-26' / '2026.09.26' → '9/26'"""
    parts = re.split(r"[-./]", date_str)
    return f"{int(parts[1])}/{int(parts[2])}"


# ---------- 福井側(自前のデータから) ----------

def own_summary(team, games, results, next_game):
    start = SEASON_START.get(team, "2000-01-01")
    # スコアが数字で入っている結果だけ使う
    results = {k: v for k, v in results.items()
               if isinstance(v, dict) and isinstance(v.get("my_score"), int) and isinstance(v.get("opponent_score"), int)}
    finished = sorted(
        [g for g in games if g["team"].upper() == team and g["id"] in results and g["date"] >= start],
        key=lambda g: g.get("sort_key", g["date"]),
    )
    marks = [mark(results[g["id"]]["my_score"], results[g["id"]]["opponent_score"]) for g in finished]
    w, l, d = marks.count("○"), marks.count("●"), marks.count("△")
    record = f"{w}勝{l}敗" + (f"{d}分" if d else "") if finished else ""

    last_meeting = None
    past_vs = [g for g in games if g["team"].upper() == team and g["id"] in results
               and g["opponent"] == next_game["opponent"]]
    if past_vs:
        g = max(past_vs, key=lambda x: x.get("sort_key", x["date"]))
        r = results[g["id"]]
        last_meeting = f"{md(g['date'])} {mark(r['my_score'], r['opponent_score'])}{r['my_score']}-{r['opponent_score']}"
    return {"record": record, "form": "".join(marks[-5:])}, last_meeting


# ---------- ブローウィンズ(Bリーグ公式) ----------

def find_bleague_team_id(name_part: str):
    """順位表・クラブ一覧のリンクから、名前に name_part を含むクラブの TeamID を探す。"""
    for path in ["/standings/?tab=2", "/standings/?tab=1", "/standings/?tab=3", "/standings/", "/club/"]:
        try:
            page = fetch(BLEAGUE + path)
        except Exception as e:
            print(f"[WARN] Bリーグ {path} の取得に失敗 {e}")
            continue
        for m in re.finditer(r"<a\b[^>]*href=[\"'][^\"']*club_detail/\?TeamID=(\d+)[^\"']*[\"'][^>]*>(.*?)</a>",
                             page, flags=re.S | re.I):
            if name_part in text_of(m.group(2)) and m.group(1) != BLOWINDS_TEAM_ID:
                return m.group(1)
    return None


def bleague_club(team_id: str):
    url = f"{BLEAGUE}/club_detail/?TeamID={team_id}"
    page = fetch(url)
    text = text_of(page)
    info = {"url": url}

    m = re.search(r"(\d+)\s*勝\s*(\d+)\s*敗", text)
    if m:
        info["record"] = f"{m.group(1)}勝{m.group(2)}敗"
        info["games"] = int(m.group(1)) + int(m.group(2))
    m = re.search(r"B\.[A-Z]+\s*[｜|]\s*(\S*地区)\s*(\d+)\s*位", text)
    if m:
        info["rank"] = f"{m.group(1)} {m.group(2)}位"

    # 直近の試合(クラブページの「対戦成績」。新しい順に並んでいる)
    recent = []
    for m in re.finditer(r"(HOME|AWAY)\s+(\d{4}\.\d{2}\.\d{2})\s+VS\s+(.+?)\s+(WIN|LOSE)\s+(\d+)\s*-\s*(\d+)", text):
        recent.append({
            "date": m.group(2).replace(".", "-"), "opponent": m.group(3).strip(),
            "my": int(m.group(5)), "opp": int(m.group(6)),
        })
    info["recent"] = recent

    # クラブリーダー(平均得点・リバウンド・アシストの各1位)
    leaders = []
    labels = {"平均得点": "点", "平均リバウンド": "リバウンド", "平均アシスト": "アシスト"}
    for m in re.finditer(r"<a\b[^>]*href=[\"'][^\"']*roster_detail[^\"']*[\"'][^>]*>(.*?)</a>", page, flags=re.S | re.I):
        t = text_of(m.group(1)).replace("\n", " ")
        lm = re.search(r"(平均得点|平均リバウンド|平均アシスト)\s*(.+?)\s*背番号\s*[:：]\s*#?(\d+)\s*ポジション\s*[:：]\s*(\S+)\s*([\d.]+)", t)
        if lm:
            leaders.append({
                "number": lm.group(3), "name": lm.group(2).strip(), "position": lm.group(4),
                "stat": f"{lm.group(1)[:2]}{lm.group(5)}{labels[lm.group(1)]}",
            })
    # 同じ選手が複数部門のリーダーならまとめる
    merged = {}
    for p in leaders:
        if p["number"] in merged:
            merged[p["number"]]["stat"] += "・" + p["stat"].replace("平均", "")
        else:
            merged[p["number"]] = dict(p)
    info["leaders"] = list(merged.values())[:3]
    return info


def blowinds_extra(next_game, preview):
    start = SEASON_START["BLOWINDS"]
    try:
        mine = bleague_club(BLOWINDS_TEAM_ID)
    except Exception as e:
        print(f"[WARN] Bリーグ 福井のクラブページ取得に失敗 {e}")
        mine = {}
    if mine.get("recent"):
        preview["h2h_checked"] = True  # Bリーグ側の直近試合も確認できた
    if mine.get("record"):
        preview["my"]["record"] = mine["record"]
    if mine.get("rank"):
        preview["my"]["rank"] = mine["rank"]

    opp_id = find_bleague_team_id(next_game["opponent"])
    if not opp_id:
        print(f"[WARN] Bリーグ: 「{next_game['opponent']}」のクラブが見つかりません")
        return
    try:
        opp = bleague_club(opp_id)
    except Exception as e:
        print(f"[WARN] Bリーグ 相手クラブページ取得に失敗 {e}")
        return
    preview["opp_link"] = opp["url"]
    season_recent = [g for g in opp.get("recent", []) if g["date"] >= start]
    preview["opp"].update({
        "record": opp.get("record", ""),
        "rank": opp.get("rank", ""),
        "form": "".join(mark(g["my"], g["opp"]) for g in reversed(season_recent[:5])),
    })
    if opp.get("leaders"):
        preview["key_players"] = opp["leaders"]
        games_played = opp.get("games", 0)
        preview["players_note"] = f"今季{games_played}試合の成績から" if games_played else ""

    # 自前のデータに前回対戦がなければ、Bリーグのクラブページの直近試合から探す
    if not preview.get("last_meeting"):
        for g in mine.get("recent", []):
            if next_game["opponent"] in g["opponent"]:
                preview["last_meeting"] = f"{md(g['date'])} {mark(g['my'], g['opp'])}{g['my']}-{g['opp']}"
                break


# ---------- ユナイテッド(公式サイトの順位表) ----------

def united_extra(next_game, preview):
    try:
        text = text_of(fetch("https://fukuiunited.co.jp/"))
    except Exception as e:
        print(f"[WARN] ユナイテッド公式サイトの取得に失敗 {e}")
        return
    part = text.split("LEAGUE RANKING", 1)[-1][:1500]
    table = {}
    for m in re.finditer(r"(\d+)\s*位\s*(.+?)\s+(\d+)\s*(?=\n|$)", part):
        table[m.group(2).strip()] = (int(m.group(1)), int(m.group(3)))
    for name, (rank, pts) in table.items():
        if "福井ユナイテッド" in name:
            preview["my"]["rank"] = f"{rank}位(勝点{pts})"
        elif next_game["opponent"] in name or name in next_game["opponent"]:
            preview["opp"]["rank"] = f"{rank}位(勝点{pts})"
    if not table:
        print("[WARN] ユナイテッド: 順位表が読み取れませんでした")


# ---------- 丸岡RUCK ----------

def ruck_extra(next_game, preview):
    for key, slug in WFLEAGUE_CLUBS.items():
        if key in next_game["opponent"]:
            preview["opp_link"] = f"https://w-fleague.jp/club/{slug}/"
            break


# ---------- 展望文 ----------

def build_summary(team, next_game, p):
    my, opp = p["my"], p["opp"]
    me, them = MY_LABEL[team], next_game["opponent"]
    parts = []
    if my.get("rank") and opp.get("rank") and team == "UNITED":
        parts.append(f"{my['rank'].split('(')[0]}の{me}と{opp['rank'].split('(')[0]}の{them}の対戦。")
    if my.get("form"):
        f = my["form"]
        parts.append(f"{me}は直近{len(f)}試合で{f.count('○')}勝{f.count('●')}敗" + (f"{f.count('△')}分" if "△" in f else "") + "。")
    if opp.get("record"):
        parts.append(f"{them}は{opp['record']}。")
    if p.get("last_meeting"):
        parts.append(f"前回対戦は{p['last_meeting']}。")
    elif team == "BLOWINDS" and p.get("h2h_checked"):
        parts.append("今季初対戦。")  # ブローウィンズは今季の全試合の結果がそろっているので言い切れる
    if p.get("key_players"):
        k = p["key_players"][0]
        parts.append(f"{them}は#{k['number']} {k['name']}({k['stat']})に注意。")
    return "".join(parts)


def build_one(team, games, results, today, previews):
    upcoming = sorted(
        [g for g in games if g["team"].upper() == team and g["date"] >= today and g["id"] not in results],
        key=lambda g: g.get("sort_key", g["date"]),
    )
    if not upcoming:
        return
    game = upcoming[0]
    my, last_meeting = own_summary(team, games, results, game)
    # アプリ側の結果データが少ないチームは、成績が実際より悪く見えることがあるので出さない
    if team != "BLOWINDS" and len(my["form"]) < 3:
        my = {"record": "", "form": ""}
    p = {
        "game_id": game["id"], "team": team,
        "my": {"label": MY_LABEL[team], **my, "rank": ""},
        "opp": {"label": game["opponent"], "record": "", "rank": "", "form": ""},
        "last_meeting": last_meeting, "key_players": [], "players_note": "", "opp_link": None,
    }
    try:
        {"BLOWINDS": blowinds_extra, "UNITED": united_extra, "RAC": ruck_extra}[team](game, p)
    except Exception as e:
        print(f"[WARN] {team}: 追加データの取得中にエラー {e}")
    p["summary"] = build_summary(team, game, p)
    p["updated_at"] = datetime.now(timezone.utc).isoformat()
    previews[game["id"]] = p
    print(f"[展望] {team} {game['date']} vs {game['opponent']}: {p['summary']}")



def main():
    games = load_json(GAMES_PATH, [])
    results = load_json(RESULTS_PATH, {})
    today = datetime.now(JST).strftime("%Y-%m-%d")
    previews = {}

    for team in ["BLOWINDS", "RAC", "UNITED"]:
        try:
            build_one(team, games, results, today, previews)
        except Exception as e:
            # 1チームで失敗しても、他のチームの展望は書き出す
            print(f"[WARN] {team}: 展望を作れませんでした {e!r}")

    with open(PREVIEWS_PATH, "w", encoding="utf-8") as f:
        json.dump(previews, f, ensure_ascii=False, indent=2)
    print(f"[展望] {len(previews)}試合分を書き出しました")


if __name__ == "__main__":
    main()
