"""
各チームの「次の試合」の展望データを作り、data/previews.json に書き出す。
アプリの一面「次の試合」欄で使う。

作るもの(取れたものだけ入れる。取れなかった項目は空のまま)
- 両チームの成績・順位・直近の勝敗
- 前回対戦の結果
- 相手の注目選手(背番号・ポジション・成績)
- 福井側の主力選手(ブローウィンズのみ。Bリーグ公式のクラブリーダー)
- 上をつないだ短い展望文
- 福井ブローウィンズの全選手の今季成績(data/players.json。アプリの「選手の数字」で使う)
- ブローウィンズの次の対戦相手(Bリーグのクラブ)の全選手の今季成績(同じく data/players.json の BLOWINDS_OPP)

データの出どころ
- 3チーム共通 : data/games.json と data/results.json(福井側の成績・直近・前回対戦)
- ブローウィンズ: Bリーグ公式サイトのクラブページ(両チームの成績・順位・直近、相手のクラブリーダー)
- ユナイテッド : 公式サイトトップの北信越リーグ順位表
- ユナイテッド : 選手の得点は公式サイトの試合結果ページから集計(standings.py)
- 丸岡RUCK      : 女子Fリーグ公式の試合結果から計算した順位表と、公式の得点ランキング(standings.py)
"""

import html as htmllib
import json
import os
import re
import time
import urllib.parse
import urllib.request

import standings
import leagueh
from datetime import datetime, timedelta, timezone

BASE_DIR = os.path.join(os.path.dirname(__file__), "..", "data")
GAMES_PATH = os.path.join(BASE_DIR, "games.json")
RESULTS_PATH = os.path.join(BASE_DIR, "results.json")
PREVIEWS_PATH = os.path.join(BASE_DIR, "previews.json")
PLAYERS_PATH = os.path.join(BASE_DIR, "players.json")
STANDINGS_PATH = os.path.join(BASE_DIR, "standings.json")
HISTORY_PATH = os.path.join(BASE_DIR, "history.json")

JST = timezone(timedelta(hours=9))
UA = "Mozilla/5.0 (Linux; Android 14) FukuiSpoBot/1.0 (+https://github.com/masashi19790226-cloud/FukuiTeamsApp)"

BLEAGUE = "https://www.bleague.jp"
BLOWINDS_TEAM_ID = "2891"
# 今季の試合とみなす日付(これより前の試合は直近成績に入れない)
SEASON_START = {"BLOWINDS": "2026-08-01", "UNITED": "2026-03-01", "RAC": "2026-05-01", "THUNDER": "2026-08-01"}

MY_LABEL = {"BLOWINDS": "福井", "UNITED": "福井", "RAC": "丸岡", "THUNDER": "福井永平寺"}

# 女子Fリーグのクラブページ(相手の選手一覧へのリンク用)
WFLEAGUE_CLUBS = {
    "北海道": "hokkaido", "流経": "ryukei", "龍ケ崎": "ryukei", "さいたま": "saitama", "浦安": "urayasu",
    "すみだ": "sumida", "立川": "tachikawa", "湘南": "shonan", "西宮": "nishinomiya", "神戸": "kobe", "宇部": "ube",
}


# ---------- 共通 ----------

def fetch(url: str, headers: dict = None) -> str:
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept-Language": "ja", **(headers or {})})
    with urllib.request.urlopen(req, timeout=25) as res:
        raw = res.read()
        charset = res.headers.get_content_charset() or "utf-8"
    time.sleep(1)
    return raw.decode(charset, errors="replace")


def post_json(url: str, body: dict):
    """JSONを送ってJSONを受け取る(ユナイテッド公式サイトの試合一覧の読み込みと同じ方法)。"""
    req = urllib.request.Request(
        url, data=json.dumps(body).encode("utf-8"), method="POST",
        headers={"User-Agent": UA, "Accept-Language": "ja", "Content-Type": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=25) as res:
        raw = res.read()
    time.sleep(1)
    return json.loads(raw.decode("utf-8", errors="replace"))


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


def blowinds_opp_fallback(next_game, preview, why):
    """Bリーグ公式の相手クラブのページを読めなかったとき(メンテナンス中など)に、相手の欄を空で上書きしないようにする。
    1. 同じ試合について前回作った展望(previews.json)の相手のデータを残す
    2. それも無ければ、前回取れていた相手の全選手の成績(players.json の BLOWINDS_OPP)から、平均得点の上位3人を「注目選手」にする"""
    gid = next_game["id"]
    old = load_json(PREVIEWS_PATH, {}).get(gid) or {}
    old_opp = old.get("opp") or {}
    if old.get("key_players") or old_opp.get("record"):
        for k in ("record", "rank", "form"):
            if old_opp.get(k):
                preview["opp"][k] = old_opp[k]
        for k in ("key_players", "players_note", "opp_link", "opp_team_id"):
            if old.get(k):
                preview[k] = old[k]
        print(f"[INFO] Bリーグ: {why}ため、{next_game['opponent']}の欄は前回の展望の内容を残します")
        return
    opp_players = load_json(PLAYERS_PATH, {}).get("BLOWINDS_OPP") or {}
    if opp_players.get("game_id") != gid and opp_players.get("team_name") != next_game["opponent"]:
        return
    rows = [p for p in opp_players.get("players") or [] if p.get("ppg")]
    rows.sort(key=lambda p: -(float(p["ppg"]) if re.fullmatch(r"[\d.]+", p["ppg"]) else -1))
    top = rows[:3]
    if not top:
        return
    preview["key_players"] = [
        {"number": p.get("number", ""), "name": p.get("name", ""), "position": p.get("position", ""),
         "stat": f"平均{p['ppg']}点" + (f"・{p['rpg']}リバウンド" if p.get("rpg") else "")}
        for p in top
    ]
    when = ""
    try:
        when = datetime.fromisoformat(opp_players.get("updated_at", "")).astimezone(JST).strftime("%-m/%-d")
    except ValueError:
        pass
    preview["players_note"] = (f"今季{top[0].get('games')}試合の成績から" if top[0].get("games") else "") + \
        f"(Bリーグ公式を読めないため、{when + '時点の' if when else '前回取れた'}選手一覧から)"
    if opp_players.get("source_url"):
        preview["opp_link"] = opp_players["source_url"]
        tm = re.search(r"TeamID=(\d+)", opp_players["source_url"])
        if tm:
            preview["opp_team_id"] = tm.group(1)
    print(f"[INFO] Bリーグ: {why}ため、{next_game['opponent']}の注目選手は前回取れた選手一覧から作りました")


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
    if mine.get("leaders"):
        # 福井側のクラブリーダー(平均得点・リバウンド・アシストの各1位)。アプリの「選手の数字」で使う
        preview["my_key_players"] = mine["leaders"]
        if mine.get("games"):
            preview["my_players_note"] = f"今季{mine['games']}試合の成績から"

    opp_id = find_bleague_team_id(next_game["opponent"])
    if not opp_id:
        print(f"[WARN] Bリーグ: 「{next_game['opponent']}」のクラブが見つかりません")
        blowinds_opp_fallback(next_game, preview, "相手のクラブが見つからなかった")
        return
    try:
        opp = bleague_club(opp_id)
    except Exception as e:
        print(f"[WARN] Bリーグ 相手クラブページ取得に失敗 {e}")
        blowinds_opp_fallback(next_game, preview, "相手クラブのページを読めなかった")
        return
    if not opp.get("record") and not opp.get("leaders"):
        # ページは返ってきたが中身が無い(メンテナンス中の画面など)
        blowinds_opp_fallback(next_game, preview, "相手クラブのページに成績が無かった")
        return
    preview["opp_link"] = opp["url"]
    preview["opp_team_id"] = opp_id  # 相手の全選手の成績(players.json)を取りに行くのに使う
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


# ---------- ブローウィンズの全選手(Bリーグ公式のクラブページ「選手情報」) ----------

ROSTER_URL = f"{BLEAGUE}/club_detail/?TeamID={BLOWINDS_TEAM_ID}&tab=1"

# 成績表の見出し(英字の略語)→ players.json の項目名
STAT_KEYS = {
    "G": "games", "MINPG": "min_pg", "PPG": "ppg", "RPG": "rpg", "APG": "apg",
    "STPG": "spg", "BSPG": "bpg", "FG%": "fg_pct", "3FG%": "three_pct", "FT%": "ft_pct", "EFFPG": "eff",
}


def _cells(row_html):
    """1行分の各セルの文字。セルの終わりの印(</td>)が省略されていても読めるよう、セルの始まりで区切る。"""
    parts = re.split(r"<t[dh]\b[^>]*>", row_html, flags=re.I)[1:]
    return [text_of(re.sub(r"</t[dh]>.*", "", c, flags=re.S | re.I)).replace("\n", " ").strip() for c in parts]


def _rows(table_html):
    """表の各行。行の終わりの印(</tr>)が省略されていても1行ずつに分かれるよう、行の始まりで区切る。"""
    return re.split(r"<tr\b[^>]*>", table_html, flags=re.I)[1:]


def _player_link(html):
    """選手ページへのリンクから (PlayerID, リンクの文字) を取り出す。無ければ (None, "")。"""
    m = re.search(r"<a\b[^>]*href=[\"'][^\"']*roster_detail/\?PlayerID=(\d+)[^\"']*[\"'][^>]*>(.*?)</a>",
                  html, flags=re.S | re.I)
    if not m:
        return None, ""
    return m.group(1), text_of(m.group(2)).replace("\n", " ").strip()


def parse_player_stats(page):
    """「選手シーズン成績」の表(平均)から、選手ごとの成績を取り出す。
    見出し行(PLAYER・PPG などの英字の略語)で列を決め、選手は PlayerID で見分ける。"""
    for table in re.findall(r"<table\b.*?</table>", page, flags=re.S | re.I):
        header, players, season = None, [], None
        for row in _rows(table):
            cells = _cells(row)
            if header is None:
                if "PLAYER" in cells and "PPG" in cells and "G" in cells:
                    header = cells
                continue
            pid, link_name = _player_link(row)
            if not pid or len(cells) < len(header):
                continue
            # 背番号「#」の見出しは上の段だけ(2段ぶち抜き)にあり、英字の見出し段には無い。
            # そのため選手の行はセルが見出しより多くなるので、右端(最後の列)でそろえて対応させる。
            offset = len(cells) - len(header)
            rec = dict(zip(header, cells[offset:]))
            # 表の先頭のシーズン(=今季)の行だけ使う
            season = season or rec.get("SEASON")
            if rec.get("SEASON") and rec.get("SEASON") != season:
                continue
            idx = header.index("PLAYER") + offset
            number = cells[idx - 1].strip() if idx > 0 else ""
            p = {"pid": pid, "number": number, "name": link_name or rec.get("PLAYER", ""),
                 "position": rec.get("PO", ""), "_type": rec.get("TYPE", "")}
            for code, key in STAT_KEYS.items():
                if rec.get(code, "") != "":
                    v = rec[code]
                    # 成功率は必ず「50.0%」の形にする(% が抜けていたら付ける)
                    if code.endswith("%") and re.fullmatch(r"[\d.]+", v):
                        v += "%"
                    p[key] = v
            if all(x["pid"] != pid for x in players):
                players.append(p)
        if header and players:
            return season, players
    return None, []


# 選手ページ(roster_detail)の「合計」の表から取り出す、シュートの成功数・試投数
SHOT_TOTAL_KEYS = {"FGM": "fgm", "FGA": "fga", "3FGM": "three_m", "3FGA": "three_a", "FTM": "ftm", "FTA": "fta"}


def parse_shot_totals(page, season, type_, games=None):
    """Bリーグ公式の選手ページの「シーズン成績(合計)」の表から、指定シーズン・大会の
    フィールドゴール・3ポイント・フリースローの成功数と試投数を取り出す。無ければ None。
    (クラブページの表は平均しか無いため、選手ページの合計の表を使う)"""
    found = []
    for table in re.findall(r"<table\b.*?</table>", page, flags=re.S | re.I):
        header = None
        for row in _rows(table):
            cells = _cells(row)
            if header is None:
                # 合計の表は FGM・FGA(平均の表は FGMPG・FGAPG)
                if "SEASON" in cells and "FGM" in cells and "FGA" in cells and "FTA" in cells:
                    header = cells
                continue
            if len(cells) < len(header):
                continue
            rec = dict(zip(header, cells[:len(header)]))
            if rec.get("SEASON") != season or (type_ and rec.get("TYPE") != type_):
                continue
            vals = {key: rec.get(code, "") for code, key in SHOT_TOTAL_KEYS.items()}
            if all(re.fullmatch(r"\d+", v or "") for v in vals.values()):
                found.append((rec.get("G", ""), vals))
        if header:
            break
    if not found:
        return None
    # 同じシーズンに複数の行(シーズン途中の移籍など)があるときは、クラブページと試合数が同じ行を使う
    for g, vals in found:
        if games and g == games:
            return vals
    return found[0][1]


# 選手ページの「試合ごとの成績」の表の列 → players.json での名前
GAME_LOG_KEYS = {
    "MIN": "min", "PTS": "pts", "FGM": "fgm", "FGA": "fga", "3FGM": "tpm", "3FGA": "tpa",
    "FTM": "ftm", "FTA": "fta", "TR": "reb", "AS": "ast", "ST": "stl", "BS": "blk", "TO": "tov", "EFF": "eff",
}


def parse_game_log(page):
    """Bリーグ公式の選手ページの「試合ごとの成績」の表(今季の各試合)を、新しい順のリストにする。
    表の見出しは2段で、左の5列(DAY・VS・H/A・W/L・S)は上の段だけにあるため、2段目の前に足して列をそろえる。"""
    m = re.search(r'<table\b[^>]*id="scores_table".*?</table>', page, flags=re.S | re.I)
    tables = [m.group(0)] if m else re.findall(r"<table\b.*?</table>", page, flags=re.S | re.I)
    for table in tables:
        header = None
        games = []
        for row in _rows(table):
            cells = _cells(row)
            if header is None:
                if "MIN" in cells and "PTS" in cells and "FGM" in cells and "SEASON" not in cells:
                    header = ["DAY", "VS", "H/A", "W/L", "S"] + cells
                continue
            if len(cells) < 5:
                continue
            dm = re.search(r"(\d{4})\.(\d{1,2})\.(\d{1,2})", cells[0])
            if not dm:
                continue
            rec = dict(zip(header, cells))
            g = {
                "date": f"{dm.group(1)}-{int(dm.group(2)):02d}-{int(dm.group(3)):02d}",
                "opp": rec.get("VS", "").strip(),
                "ha": "H" if "HOME" in rec.get("H/A", "").upper() else ("A" if "AWAY" in rec.get("H/A", "").upper() else ""),
                "wl": "W" if "WIN" in rec.get("W/L", "").upper() else ("L" if "LOSE" in rec.get("W/L", "").upper() else ""),
                "start": rec.get("S", "").strip() != "",
            }
            for code, key in GAME_LOG_KEYS.items():
                v = rec.get(code, "").strip()
                if v:
                    g[key] = v
            games.append(g)
        if header:
            games.sort(key=lambda x: x["date"], reverse=True)
            return games
    return []


def attach_shot_totals(players, season, label, old_cache):
    """各選手に、シュートの成功数・試投数(合計)と、試合ごとの成績(game_log)を付ける。
    選手ページを読むのは、試合数が前回から変わった選手だけ(毎日4時台は全員読み直す)。
    返り値は次回用の覚え書き {PlayerID: {"season", "type", "g", "v": {...}, "log": [...]}}。"""
    full_refresh = datetime.now(JST).hour == 4
    cache, fetched = {}, 0
    for p in players:
        pid, games, type_ = p.get("pid"), p.get("games"), p.get("_type", "")
        if not pid or not games or not season:
            continue  # 今季まだ出場していない選手
        hit = old_cache.get(pid)
        if (hit and not full_refresh and hit.get("season") == season and hit.get("type") == type_
                and hit.get("g") == games and hit.get("v") and "log" in hit):
            vals, log = hit["v"], hit["log"]
        else:
            try:
                page = fetch(f"{BLEAGUE}/roster_detail/?PlayerID={pid}")
                fetched += 1
                vals = parse_shot_totals(page, season, type_, games)
                log = parse_game_log(page)
            except Exception as e:
                print(f"[WARN] 選手: {label} {p.get('name', '')} の選手ページを読めませんでした {e!r}")
                same = hit and hit.get("season") == season
                vals = hit.get("v") if same else None
                log = hit.get("log", []) if same else []
        if vals:
            p.update(vals)
        if log:
            p["game_log"] = log
        if vals or log:
            cache[pid] = {"season": season, "type": type_, "g": games, "v": vals or {}, "log": log or []}
    print(f"[選手] {label} シュートの成功数・試投数 {len(cache)}人分(選手ページ{fetched}件を読み込み)")
    return cache


def parse_roster(page):
    """ページ下の「選手」一覧(背番号・名前・ポジション)。試合に出ていない選手もここには載る。"""
    roster = []
    for m in re.finditer(r"<a\b[^>]*href=[\"'][^\"']*roster_detail/\?PlayerID=(\d+)[^\"']*[\"'][^>]*>(.*?)</a>",
                         page, flags=re.S | re.I):
        pid = m.group(1)
        t = text_of(m.group(2)).replace("\n", " ")
        if "背番号" in t:  # クラブリーダー欄のリンクは除く
            continue
        rm = re.match(r"\s*(\d+)\s+(.+?)\s+ポジション\s*[:：]\s*(\S+)\s*#\s*(\d+)", t)
        if not rm:
            continue
        words = rm.group(2).split()
        half = len(words) // 2
        # 画像の代替文字と名前で同じ名前が2回並ぶので、同じなら1回分にする
        name = " ".join(words[:half]) if half and words[:half] == words[half:] else rm.group(2).strip()
        if all(x["pid"] != pid for x in roster):
            roster.append({"pid": pid, "number": rm.group(4), "name": name, "position": rm.group(3)})
    return roster


BLOWINDS_CLUB_SITE = "https://www.fukuiblowinds.com"
BLOWINDS_CLUB_PLAYERS = BLOWINDS_CLUB_SITE + "/team/players/"


def blowinds_club_photos():
    """ブローウィンズ公式サイトの選手紹介から、選手の写真URLを集める。
    一覧(/team/players/)に並ぶ各選手の詳細ページ(/team/players/detail/id=○?PlayerID=○)の
    <img src=".../files/user/common/13_Kawashima_2026-27_HP.jpg" alt="川島 聖那"> を使う。
    返り値は ({("pid", PlayerID): URL, ("name", 名前): URL}, {PlayerID: [URL, 名前]})。PlayerID はBリーグ公式と同じ番号。
    2つ目は players.json に残しておき、次からは読み直さない(写真の差し替えに追いつくよう、毎日4時台は全員読み直す)。"""
    page = fetch(BLOWINDS_CLUB_PLAYERS)
    links = []
    for m in re.finditer(r"team/players/detail/id=(\d+)(?:\?|&amp;|&)PlayerID=(\d+)", page):
        if (m.group(1), m.group(2)) not in links:
            links.append((m.group(1), m.group(2)))
    old_cache = load_json(PLAYERS_PATH, {}).get("BLOWINDS", {}).get("club_photos", {})
    full_refresh = datetime.now(JST).hour == 4
    out, cache = {}, {}
    fetched = 0
    for detail_id, pid in links:
        hit = old_cache.get(pid)
        if hit and not full_refresh:
            out[("pid", pid)] = hit[0]
            if hit[1]:
                out[("name", standings._name_key(hit[1]))] = hit[0]
            cache[pid] = hit
            continue
        url = f"{BLOWINDS_CLUB_SITE}/team/players/detail/id={detail_id}?PlayerID={pid}"
        try:
            html = fetch(url)
            fetched += 1
        except Exception as e:
            print(f"[WARN] 写真: ブローウィンズの選手ページを読めませんでした {url} {e!r}")
            continue
        photo, alt = None, ""
        for tag in re.findall(r"<img\b[^>]*>", html, flags=re.I):
            src = re.search(r"\b(?:data-src|src)\s*=\s*[\"']([^\"']+)[\"']", tag)
            if not src:
                continue
            u = urllib.parse.urljoin(url, htmllib.unescape(src.group(1)))
            # 選手の写真は files/user/common/ の下にある(ロゴ・スポンサーのバナーは除く)
            if "/files/user/common/" in u and "/img/logo/" not in u and u.startswith(BLOWINDS_CLUB_SITE):
                photo = u
                am = re.search(r"\balt\s*=\s*[\"']([^\"']*)[\"']", tag)
                alt = htmllib.unescape(am.group(1)) if am else ""
                break
        if photo:
            out[("pid", pid)] = photo
            cache[pid] = [photo, alt.strip()]
            if alt.strip():
                out[("name", standings._name_key(alt))] = photo
    print(f"[写真] ブローウィンズ: 公式サイトの選手紹介 {len(links)}人中 {len([k for k in out if k[0] == 'pid'])}人の写真(詳細ページ{fetched}件を読み込み)")
    return out, cache


# クラブページの「クラブ成績」(今季のチームの平均得点・成功率など、リーグ内の順位つき)
TEAM_STAT_RE = re.compile(
    r'grades-heading-title1[^>]*>([^<]+)</span>\s*<span class="grades-heading-title2">([^<]+)</span>'
    r'[\s\S]*?grades-text-ranking-2"><span class="num font-blg">([^<]*)</span>位'
    r'[\s\S]*?grades-text"><span class="font-blg">([^<]*)</span>([^<]*)<')


# リーグごとのクラブ数(クラブ成績の「○位」が何チーム中かを出すため)。
# Bリーグ公式の順位表で数えた 2026-27 シーズンの数。クラブ数が変わったシーズンはここを直す
LEAGUE_TEAM_COUNTS = {"B.PREMIER": 26, "B.ONE": 25}


def parse_league(page):
    """クラブページの「B.ONE｜中地区 1位」から リーグ名(例「B.ONE」)。無ければ空"""
    m = re.search(r"(B\.[A-Z0-9]+)\s*[｜|]\s*\S*地区", text_of(page))
    return m.group(1) if m else ""


def parse_team_stats(page):
    """クラブページの「クラブ成績」の8項目(PPG・FG%・3FG%・FT%・RPG・APG・BPG・SPG)。
    [{"key", "label", "rank", "value", "unit"}] と、公式の更新日時の文字(例「2026年10月04日19:07更新」)。読めなければ空"""
    items = []
    for key, label, rank, value, unit in TEAM_STAT_RE.findall(page):
        value = value.strip()
        if not re.fullmatch(r"\d+(?:\.\d+)?", value):
            continue
        items.append({"key": key.strip(), "label": htmllib.unescape(label).strip(),
                      "rank": int(rank) if rank.strip().isdigit() else None,
                      "value": value, "unit": unit.strip()})
        if len(items) >= 12:
            break
    um = re.search(r"クラブ成績</h2>[\s\S]{0,300}?(\d{4}年\d{1,2}月\d{1,2}日\s*[\d:]+)\s*更新", page)
    return items, (um.group(1) if um else "")


def build_team_players(team_id, label):
    """Bリーグ公式のクラブページ「選手情報」から、1クラブ分の全選手の今季成績を作る。読めなければ None。"""
    url = f"{BLEAGUE}/club_detail/?TeamID={team_id}&tab=1"
    page = fetch(url)
    season, stats = parse_player_stats(page)
    roster = parse_roster(page)
    by_pid = {p["pid"]: p for p in stats}
    players = []
    for r in roster:
        p = by_pid.pop(r["pid"], None)
        if p:
            # 名前・背番号・ポジションは選手一覧の表記にそろえ、成績を付ける
            p.update({k: v for k, v in r.items() if v})
            players.append(p)
        else:
            players.append(dict(r))  # 今季まだ試合に出ていない選手(成績はデータなし)
    players += list(by_pid.values())  # 一覧に無いが成績表にはいる選手
    # 選手の顔写真(アプリの選手タブで表示する)。ブローウィンズ以外はBリーグ公式の画像
    # 画像の場所は「files/user/roster/<TeamID>/<シーズン>/<PlayerID>_03.png」。シーズンの部分はページ内の画像から読む
    sm = re.search(rf"files/user/roster/{team_id}/([^/\"']+)/\d+_\d+\.(?:png|jpg)", page)
    photo_season = sm.group(1) if sm else (season if re.fullmatch(r"\d{4}-\d{2}", season or "") else "")
    # ブローウィンズは、クラブ公式サイトの選手紹介の写真を使う(取れなかった選手だけBリーグ公式の画像)
    club, club_cache = {}, {}
    if team_id == BLOWINDS_TEAM_ID:
        try:
            club, club_cache = blowinds_club_photos()
        except Exception as e:
            print(f"[WARN] 写真: ブローウィンズ公式サイトの選手紹介の取得に失敗 {e!r}")
    # シュートの成功数・試投数(合計)。前回の覚え書きは players.json のどのチームの分からでも使う
    shot_cache = {}
    try:
        old_shots = {}
        for section in load_json(PLAYERS_PATH, {}).values():
            if isinstance(section, dict):
                old_shots.update(section.get("shot_cache") or {})
        shot_cache = attach_shot_totals(players, season, label, old_shots)
    except Exception as e:
        print(f"[WARN] 選手: {label} のシュートの成功数・試投数の取得に失敗 {e!r}")
    for p in players:
        p.pop("_type", None)
        pid = p.pop("pid", None)
        club_photo = club.get(("pid", pid)) or club.get(("name", standings._name_key(p.get("name", ""))))
        if club_photo:
            p["photo"] = club_photo
        elif pid and photo_season:
            p["photo"] = f"https://bleague.bl.kuroco-img.app/files/user/roster/{team_id}/{photo_season}/{pid}_03.png"
    players.sort(key=lambda p: int(p["number"]) if str(p.get("number", "")).isdigit() else 999)
    if not players:
        print(f"[WARN] 選手: Bリーグ公式から{label}の選手を読み取れませんでした")
        return None
    print(f"[選手] {label} {len(players)}人(成績あり{len(stats)}人・選手一覧{len(roster)}人・{season or 'シーズン不明'})")
    result = {
        "season": season or "", "source_url": url,
        "updated_at": datetime.now(timezone.utc).isoformat(), "players": players,
    }
    # チーム全体の今季の数字(クラブ成績)。読めなくても選手のデータはそのまま出す
    try:
        team_stats, team_stats_updated = parse_team_stats(page)
        if team_stats:
            result["team_stats"] = team_stats
            result["team_stats_updated"] = team_stats_updated
            league = parse_league(page)
            if league:
                result["league"] = league
                if league in LEAGUE_TEAM_COUNTS:
                    result["league_teams"] = LEAGUE_TEAM_COUNTS[league]
        print(f"[選手] {label} クラブ成績 {len(team_stats)}項目")
    except Exception as e:
        print(f"[WARN] 選手: {label} のクラブ成績の読み取りに失敗 {e!r}")
    if club_cache:
        result["club_photos"] = club_cache  # 次回の写真取得で使う(アプリは使わない)
    if shot_cache:
        result["shot_cache"] = shot_cache  # 次回のシュート数の取得で使う(アプリは使わない)
    return result


def build_players(previews):
    """ブローウィンズと、次の対戦相手(Bリーグのクラブが分かったとき)の全選手の成績。
    ブローウィンズが読めなければ None(前回のファイルを残す)。相手だけ読めないときは相手を入れない。"""
    mine = build_team_players(BLOWINDS_TEAM_ID, "ブローウィンズ")
    if not mine:
        return None
    result = {"BLOWINDS": mine}
    p = next((x for x in previews.values() if x.get("team") == "BLOWINDS"), None)
    if p and p.get("opp_team_id"):
        try:
            opp = build_team_players(p["opp_team_id"], p["opp"]["label"])
            if opp:
                opp["team_name"] = p["opp"]["label"]
                opp["game_id"] = p["game_id"]
                result["BLOWINDS_OPP"] = opp
        except Exception as e:
            print(f"[WARN] 選手: 対戦相手({p['opp']['label']})の取得に失敗しました {e!r}")
    if "BLOWINDS_OPP" not in result and p:
        # 相手を読めなかったとき、同じ試合の相手の分が前回あれば残す(アプリの選手タブ・相手の注目選手に使う)
        old_opp = load_json(PLAYERS_PATH, {}).get("BLOWINDS_OPP") or {}
        if old_opp.get("game_id") == p.get("game_id") or old_opp.get("team_name") == (p.get("opp") or {}).get("label"):
            result["BLOWINDS_OPP"] = old_opp
    return result


# ---------- ユナイテッド(公式サイトの順位表) ----------

# 丸岡RUCK・ユナイテッドの順位表(main で1回だけ作り、展望と data/standings.json の両方で使う)
STANDINGS = {}
WFL_MATCHES = []


def load_standings():
    """丸岡RUCK(女子Fリーグ)とユナイテッド(北信越リーグ1部)の順位表を作る。読めなかったリーグは入れない。"""
    global WFL_MATCHES
    try:
        page = fetch(standings.WFL_TOP)
        WFL_MATCHES = standings.wfleague_matches(page)
        STANDINGS["RAC"] = standings.ruck_standings(page)
    except Exception as e:
        print(f"[WARN] 順位: 女子Fリーグ公式サイトの取得に失敗 {e!r}")
    try:
        u = standings.united_standings(text_of(fetch("https://fukuiunited.co.jp/")))
        if u:
            STANDINGS["UNITED"] = u
        else:
            print("[WARN] ユナイテッド: 順位表が読み取れませんでした")
    except Exception as e:
        print(f"[WARN] ユナイテッド公式サイトの取得に失敗 {e!r}")
    # ブローウィンズ(Bリーグ公式の地区別の順位表)。読めなかったときは前回の内容を残す
    try:
        b = standings.bleague_standings(fetch, BLOWINDS_TEAM_ID)
        if b:
            STANDINGS["BLOWINDS"] = b
    except Exception as e:
        print(f"[WARN] 順位: Bリーグ公式の順位表の読み取りに失敗 {e!r}")
    # ブルーサンダー(リーグH公式の順位表と、試合ごとのページから数えた得点)。読めなかったときは前回の内容を残す
    try:
        load_thunder()
    except Exception as e:
        print(f"[WARN] 順位: ブルーサンダー(リーグH公式)の読み取りに失敗 {e!r}")
    # 選手の得点。読めなかったときは前回の内容を残す(main で standings.json にまとめるときに前回分を使う)
    old = load_json(STANDINGS_PATH, {})
    if "RAC" in STANDINGS:
        try:
            sc = standings.ruck_scorers(fetch)
            if sc:
                STANDINGS["RAC"]["scorers"] = sc
            elif old.get("RAC", {}).get("scorers"):
                STANDINGS["RAC"]["scorers"] = old["RAC"]["scorers"]
        except Exception as e:
            print(f"[WARN] 得点: 女子Fリーグの得点ランキングの取得に失敗 {e!r}")
            if old.get("RAC", {}).get("scorers"):
                STANDINGS["RAC"]["scorers"] = old["RAC"]["scorers"]
    if "UNITED" in STANDINGS:
        old_u = old.get("UNITED", {})
        try:
            sc, cache = standings.united_scorers(fetch, post_json, old_u.get("match_cache", {}))
            STANDINGS["UNITED"]["match_cache"] = cache
            if sc:
                STANDINGS["UNITED"]["scorers"] = sc
            elif old_u.get("scorers"):
                STANDINGS["UNITED"]["scorers"] = old_u["scorers"]
        except Exception as e:
            print(f"[WARN] 得点: ユナイテッドの試合結果の取得に失敗 {e!r}")
            for k in ("scorers", "match_cache"):
                if old_u.get(k):
                    STANDINGS["UNITED"][k] = old_u[k]


# ---------- ブルーサンダー(リーグH公式) ----------

def load_thunder():
    """リーグH公式の順位表(男子)と、福井永平寺の試合ごとの記録から数えた選手の得点を STANDINGS["THUNDER"] に入れる。
    試合ごとの記録は "match_cache"(試合番号 → 日付・点数・選手の成績)に残し、終わってから2日たった試合は読み直さない。
    順位表が読めなかったときは何も入れない(standings.json の前回の内容が残る)"""
    old_t = load_json(STANDINGS_PATH, {}).get("THUNDER", {})
    cache = dict(old_t.get("match_cache") or {})
    today = datetime.now(JST).date()
    global THUNDER_MATRIX
    try:
        THUNDER_MATRIX = fetch(leagueh.MATRIX_URL)
        links = leagueh.matrix_games(THUNDER_MATRIX)
    except Exception as e:
        print(f"[WARN] ブルーサンダー: リーグH公式の星取表の取得に失敗 {e!r}")
        links = None
    if links is not None:
        read = 0
        for link in links:
            hit = cache.get(link["code"])
            if hit and hit.get("my") == link["my"] and hit.get("opp") == link["opp"]:
                try:
                    # 公式の記録は試合の後で直ることがあるので、試合から2日たつまでは読み直す
                    if (today - datetime.strptime(hit.get("date", ""), "%Y-%m-%d").date()).days >= 2:
                        continue
                except ValueError:
                    pass
            try:
                g = leagueh.parse_game(fetch(link["url"]))
            except Exception as e:
                print(f"[WARN] ブルーサンダー: 試合ページの取得に失敗 {link['url']} {e!r}")
                g = None
            if g:
                g["url"] = link["url"]
                cache[link["code"]] = g
                read += 1
        # 星取表に無くなった試合(取り消しなど)は数えない
        if links:
            cache = {k: v for k, v in cache.items() if k in {l["code"] for l in links}}
        print(f"[順位] ブルーサンダー: 終わった試合 {len(links)} 件(試合ページを{read}件読み込み)")
    st = leagueh.standings_from(fetch(leagueh.RANKINGS_URL),
                                {leagueh.TEAM_SLUG: leagueh.form_of(cache)} if cache else None)
    if not st:
        print("[WARN] ブルーサンダー: リーグH公式の順位表を読み取れませんでした")
        return
    st["match_cache"] = cache
    sc = leagueh.scorers_from(cache)
    if sc:
        st["scorers"] = sc
    elif old_t.get("scorers"):
        st["scorers"] = old_t["scorers"]
    STANDINGS["THUNDER"] = st
    # 個人ランキング(得点)。次の対戦相手の注目選手に使う(standings.json には入れない)
    global THUNDER_LEADERS
    try:
        THUNDER_LEADERS = leagueh.leaders(fetch(leagueh.LEADERS_URL))
        print(f"[順位] ブルーサンダー: 個人ランキング(得点) {len(THUNDER_LEADERS)}人")
    except Exception as e:
        print(f"[WARN] ブルーサンダー: 個人ランキングの取得に失敗 {e!r}")


# リーグH公式の個人ランキング(得点)。load_thunder で読む
THUNDER_LEADERS = []
# リーグH公式の星取表のページ(load_thunder で読み、相手チームの試合を探すのにも使う)
THUNDER_MATRIX = ""


def thunder_opponent(games, results, today):
    """ブルーサンダーの次の対戦相手の全選手(リーグH公式のチームページ)と、今季の得点・シュート・メンバー入りした試合数
    (相手の試合ごとのページから数える)を、standings.json の "THUNDER" の得点の表(scorers の rows)に加える。
    相手の試合ごとの記録は "opp_cache" に残し、相手が変わったら作り直す。終わってから2日たった試合は読み直さない"""
    st = STANDINGS.get("THUNDER")
    if not st or not THUNDER_MATRIX:
        return
    upcoming = sorted(
        [g for g in games if g.get("team") == "thunder" and not g.get("category") and g["date"] >= today and g["id"] not in results],
        key=lambda g: g.get("sort_key", g["date"]),
    )
    if not upcoming:
        return
    nxt = upcoming[0]
    row = next((r for r in st.get("regular", {}).get("table", [])
                if r.get("slug") and r.get("slug") != leagueh.TEAM_SLUG and leagueh.same_team(nxt["opponent"], r["team"])), None)
    if not row:
        print(f"[WARN] ブルーサンダー: 次の相手({nxt['opponent']})がリーグH公式の順位表に見つかりません")
        return
    slug = row["slug"]
    old = load_json(STANDINGS_PATH, {}).get("THUNDER", {}).get("opp_cache") or {}
    cache = dict(old.get("games") or {}) if old.get("slug") == slug else {}
    links = leagueh.matrix_games(THUNDER_MATRIX, slug)
    read = 0
    for link in links:
        hit = cache.get(link["code"])
        if hit:
            try:
                if (datetime.strptime(today, "%Y-%m-%d").date() - datetime.strptime(hit.get("date", ""), "%Y-%m-%d").date()).days >= 2:
                    continue
            except ValueError:
                pass
        try:
            page = fetch(link["url"])
        except Exception as e:
            print(f"[WARN] ブルーサンダー: 相手の試合ページの取得に失敗 {link['url']} {e!r}")
            continue
        players = leagueh.game_players(page, slug)
        if players:
            cache[link["code"]] = {"date": leagueh.game_date(page), "players": players}
            read += 1
    cache = {k: v for k, v in cache.items() if k in {l["code"] for l in links}}
    try:
        roster = leagueh.team_roster(fetch(f"{leagueh.LEAGUEH}/team/{slug}/"))
    except Exception as e:
        print(f"[WARN] ブルーサンダー: 相手のチームページの取得に失敗 {e!r}")
        roster = []
    rows = leagueh.opponent_rows(nxt["opponent"], roster, cache)
    if not rows:
        return
    sc = st.get("scorers") or {"label": "今季の得点", "note": "", "source_url": leagueh.MATRIX_URL, "rows": []}
    # 相手チームの行は、チーム名を日程の相手の名前にしておく(アプリはこの名前で相手の選手を探す)
    sc["rows"] = [r for r in sc["rows"] if "ブルーサンダー" in r.get("team", "")] + rows
    st["scorers"] = sc
    st["opp_cache"] = {"slug": slug, "games": cache}
    print(f"[順位] ブルーサンダー: 次の相手 {nxt['opponent']} の選手 {len(rows)}人(試合ページを{read}件読み込み・{len(cache)}試合分)")


def thunder_extra(next_game, preview):
    """ブルーサンダーの展望:リーグH公式の順位表から両チームの順位・勝敗、
    福井永平寺の直近の勝敗(試合ごとの記録から)、相手の注目選手(個人ランキングの得点上位)"""
    st = STANDINGS.get("THUNDER")
    if st:
        for r in st.get("regular", {}).get("table", []):
            if r.get("slug") == leagueh.TEAM_SLUG or "福井永平寺" in r["team"]:
                side = "my"
            elif leagueh.same_team(next_game["opponent"], r["team"]):
                side = "opp"
                if r.get("slug"):
                    preview["opp_link"] = f"{leagueh.LEAGUEH}/team/{r['slug']}/profile/"
            else:
                continue
            if not r.get("played"):
                continue
            preview[side]["rank"] = f"{r['rank']}位(勝ち点{r['points']})"
            preview[side]["record"] = r["record"]
            if r.get("form"):
                preview[side]["form"] = r["form"]
    top = [x for x in THUNDER_LEADERS if leagueh.same_team(next_game["opponent"], x["team"])][:3]
    if top:
        preview["key_players"] = [
            {"number": x["number"], "name": x["name"], "position": x["position"],
             "stat": f"今季{x['goals']}得点(リーグ{x['rank']}位)", "photo": x["photo"]}
            for x in top
        ]
        preview["players_note"] = "リーグH公式の個人ランキング(得点)から"


# 丸岡RUCK公式の選手紹介(写真・身長・出身地)。1回の実行で読めた分を、得点の表と誕生日のデータの両方で使う
RUCK_MEMBERS = None


def ruck_members_once():
    """丸岡RUCK公式の選手紹介を読む(この実行でまだ読んでいなければ)。失敗したら少し待ってもう一度。読めなければ例外"""
    global RUCK_MEMBERS
    if RUCK_MEMBERS:
        return RUCK_MEMBERS
    last = None
    for attempt in range(2):
        try:
            got = standings.ruck_members(fetch)
            if got:
                RUCK_MEMBERS = got
                return got
            last = ValueError("選手が見つかりません")
        except Exception as e:
            last = e
        time.sleep(3)
    raise last


def inherit_scorer_photos(rows, team_key):
    """前回の data/standings.json の得点の表から、同じ名前の選手の写真を写す(写真の無い行だけ)。写した人数を返す"""
    old_rows = load_json(STANDINGS_PATH, {}).get(team_key, {}).get("scorers", {}).get("rows") or []
    old = {standings._name_key(r.get("name", "")): r for r in old_rows if r.get("photo")}
    count = 0
    for r in rows or []:
        if r.get("photo"):
            continue
        prev = old.get(standings._name_key(r.get("name", "")))
        if prev:
            r["photo"] = prev["photo"]
            count += 1
    return count


def attach_wfl_numbers(rac):
    """女子Fリーグの得点の表の、背番号が空の行(丸岡RUCK以外のクラブの選手)に、各クラブの選手名簿の背番号を付ける。
    名簿は1日1回だけ読み直し、それ以外は number_cache(standings.json に保存。アプリは使わない)を使う。
    名簿を読めなかったときは、前回の名簿の背番号を使う(一時的な失敗で背番号が消えないように)。"""
    import alumni
    today = datetime.now(JST).strftime("%Y-%m-%d")
    old = load_json(STANDINGS_PATH, {}).get("RAC", {}).get("scorers", {}).get("number_cache") or {}
    numbers = old.get("numbers") or {}
    if old.get("date") != today or not numbers:
        try:
            rosters = alumni.wfl_rosters(fetch)
            got = {k: v.get("number", "") for k, v in rosters.items() if v.get("number")}
            if got:
                numbers = got
                old = {"date": today, "numbers": numbers}
        except Exception as e:
            print(f"[WARN] 背番号: 女子Fリーグの選手名簿の取得に失敗 {e!r}(前回の名簿を使います)")
    rac["number_cache"] = old if numbers else {}
    hit = 0
    for r in rac.get("rows") or []:
        if r.get("number"):
            continue
        n = numbers.get(alumni._key(r.get("name", "")))
        if n:
            r["number"] = n
            hit += 1
    print(f"[背番号] 女子Fリーグの相手クラブの選手 {hit}人に背番号を付けました(名簿 {len(numbers)}人)")


def attach_player_photos():
    """丸岡RUCK・ユナイテッドの得点の表に、公式サイトの選手紹介の顔写真を付ける(読めなければ付けない)"""
    rac = STANDINGS.get("RAC", {}).get("scorers")
    if rac:
        try:
            standings.add_photos(rac.get("rows"), standings.RUCK_NAME, ruck_members_once())
            rac["photo_report"] = {standings.RUCK_NAME: "ok"}
        except Exception as e:
            print(f"[WARN] 写真: 丸岡RUCKの選手紹介の取得に失敗 {e!r}")
            rac["photo_report"] = {standings.RUCK_NAME: f"取得失敗 {type(e).__name__}"}
            # 読めなかったときは、前回の得点の表に付いていた写真を引き継ぐ(一時的な失敗で写真が消えないように)
            inherited = inherit_scorer_photos(rac.get("rows"), "RAC")
            if inherited:
                rac["photo_report"][standings.RUCK_NAME] += f"(前回の写真を{inherited}人分引き継ぎ)"
        # 相手クラブの選手の写真(各クラブの公式サイトから。1日1回だけ読み直す)
        try:
            old_cache = load_json(STANDINGS_PATH, {}).get("RAC", {}).get("scorers", {}).get("club_photo_cache", {})
            cache, report = standings.add_wfl_club_photos(rac.get("rows"), fetch, old_cache)
            rac["club_photo_cache"] = cache  # 次回の写真さがしで使う(アプリは使わない)
            rac["photo_report"].update(report)  # どのクラブの写真が取れたか(確認用。アプリは使わない)
        except Exception as e:
            print(f"[WARN] 写真: 女子Fリーグの相手クラブの写真の取得に失敗 {e!r}")
        # 相手クラブの選手の背番号(女子Fリーグ公式の各クラブの選手名簿から。1日1回だけ読み直す)
        try:
            attach_wfl_numbers(rac)
        except Exception as e:
            print(f"[WARN] 背番号: 女子Fリーグの相手クラブの名簿の取得に失敗 {e!r}")
    uni = STANDINGS.get("UNITED", {}).get("scorers")
    if uni:
        try:
            standings.add_photos(uni.get("rows"), standings.UNITED_NAME, standings.united_photos(fetch))
        except Exception as e:
            print(f"[WARN] 写真: ユナイテッドの選手ページの取得に失敗 {e!r}")


def team_scorers(team_key, name, limit=3):
    """得点の一覧から、名前にその文字が入っているチームの得点者を多い順に。"""
    rows = STANDINGS.get(team_key, {}).get("scorers", {}).get("rows", [])
    hit = [r for r in rows if r.get("goals", 0) > 0 and name and (name in r["team"] or r["team"] in name)]
    return sorted(hit, key=lambda r: -r["goals"])[:limit]


def united_extra(next_game, preview):
    table = STANDINGS.get("UNITED", {}).get("regular", {}).get("table", [])
    for r in table:
        name = r["team"]
        if "福井ユナイテッド" in name:
            preview["my"]["rank"] = f"{r['rank']}位(勝点{r['points']})"
        elif next_game["opponent"] in name or name in next_game["opponent"]:
            preview["opp"]["rank"] = f"{r['rank']}位(勝点{r['points']})"


# ---------- 丸岡RUCK ----------

def ruck_extra(next_game, preview):
    for key, slug in WFLEAGUE_CLUBS.items():
        if key in next_game["opponent"]:
            preview["opp_link"] = f"https://w-fleague.jp/club/{slug}/"
            break
    # 女子Fリーグの全試合結果から計算した順位表で、両チームの順位・勝敗・直近の調子を入れる
    st = STANDINGS.get("RAC")
    if not st:
        return
    for side, name in (("my", standings.RUCK_NAME), ("opp", next_game["opponent"])):
        part, row = standings.find_row(st, name)
        if not row:
            continue
        # レギュラーシーズンは「4位(勝点20)」、ファイナルシーズンは「FS 2位(勝点6)」
        label = "FS " if part == "final" else ""
        preview[side]["rank"] = f"{label}{row['rank']}位(勝点{row['points']})"
        preview[side]["record"] = row["record"]
        preview[side]["form"] = row["form"]
    # 前回対戦(今季、終わった試合でいちばん新しいもの)
    if not preview.get("last_meeting"):
        for m in reversed(WFL_MATCHES):
            if m["hs"] is None or standings.RUCK_NAME not in (m["home"], m["away"]):
                continue
            other = m["away"] if m["home"] == standings.RUCK_NAME else m["home"]
            if next_game["opponent"] in other or other in next_game["opponent"]:
                gf, ga = (m["hs"], m["as"]) if m["home"] == standings.RUCK_NAME else (m["as"], m["hs"])
                preview["last_meeting"] = f"{md(m['date'])} {mark(gf, ga)}{gf}-{ga}"
                break
    # 相手の注目選手 = 相手チームで得点の多い選手(女子Fリーグ公式の得点ランキングから)
    top = team_scorers("RAC", next_game["opponent"])
    if top:
        preview["key_players"] = [
            {"number": r.get("number", ""), "name": r["name"], "position": "",
             "stat": f"今季{r['goals']}得点(リーグ{r['rank']}位)・シュート{r['shots']}本"}
            for r in top
        ]
        preview["players_note"] = "女子Fリーグ公式の得点ランキングから"


# ---------- 展望文 ----------

def build_summary(team, next_game, p):
    my, opp = p["my"], p["opp"]
    me, them = MY_LABEL[team], next_game["opponent"]
    parts = []
    if my.get("rank") and opp.get("rank") and team in ("UNITED", "THUNDER"):
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
        no = f"#{k['number']} " if k.get("number") else ""
        parts.append(f"{them}は{no}{k['name']}({re.sub(r'[(（].*?[)）]', '', k['stat'].split('・')[0])})に注意。")
    if team == "UNITED":
        top = team_scorers("UNITED", "福井ユナイテッド", limit=1)
        if top:
            parts.append(f"{me}のチーム得点王は{top[0]['name']}({top[0]['goals']}得点)。")
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
        {"BLOWINDS": blowinds_extra, "UNITED": united_extra, "RAC": ruck_extra, "THUNDER": thunder_extra}[team](game, p)
    except Exception as e:
        print(f"[WARN] {team}: 追加データの取得中にエラー {e}")
    p["summary"] = build_summary(team, game, p)
    p["updated_at"] = datetime.now(timezone.utc).isoformat()
    previews[game["id"]] = p
    print(f"[展望] {team} {game['date']} vs {game['opponent']}: {p['summary']}")



def main():
    games = load_json(GAMES_PATH, [])
    # 展望(次の試合・直近の成績)はトップチームの試合だけで作る。U-20・サテライトなど(category 付き)の試合は使わない
    games = [g for g in games if not g.get("category")]
    results = load_json(RESULTS_PATH, {})
    today = datetime.now(JST).strftime("%Y-%m-%d")
    previews = {}

    load_standings()
    attach_player_photos()
    # ブルーサンダーの次の対戦相手の全選手(順位表を読めたときだけ)
    try:
        thunder_opponent(games, results, today)
    except Exception as e:
        print(f"[WARN] ブルーサンダー: 次の相手の選手の取得に失敗 {e!r}")

    for team in ["BLOWINDS", "RAC", "UNITED", "THUNDER"]:
        try:
            build_one(team, games, results, today, previews)
        except Exception as e:
            # 1チームで失敗しても、他のチームの展望は書き出す
            print(f"[WARN] {team}: 展望を作れませんでした {e!r}")

    with open(PREVIEWS_PATH, "w", encoding="utf-8") as f:
        json.dump(previews, f, ensure_ascii=False, indent=2)
    print(f"[展望] {len(previews)}試合分を書き出しました")

    # 3チームの選手の誕生日(1日1回だけ作り直す。アプリの一面と通知で使う)。
    # 自動更新で保存されるファイルに入れるため、standings.json の "BIRTHDAYS" に入れる
    try:
        import birthdays
        old_bd = load_json(STANDINGS_PATH, {}).get("BIRTHDAYS", {})
        # 1日1回(データの形を変えたとき=版が上がったとき、manual_profiles.json を直したときは、その日のうちにもう一度)
        # 丸岡RUCKの選手に写真が1人も無いとき(前回、選手紹介を読めなかったとき)も、その日のうちに作り直す
        ruck_missing = not any(p.get("photo") for p in old_bd.get("players") or [] if p.get("team") == "RAC")
        if (old_bd.get("date") != datetime.now(JST).strftime("%Y-%m-%d")
                or old_bd.get("v") != birthdays.VERSION
                or old_bd.get("manual", "") != birthdays._load_manual()[1]
                or ruck_missing) and STANDINGS:
            ruck_photos = None
            try:
                ruck_photos = ruck_members_once()
            except Exception as e:
                print(f"[WARN] 誕生日: 丸岡RUCKの写真の取得に失敗 {e!r}")
            bd = birthdays.build(fetch, old_bd, ruck_photos)
            if bd:
                STANDINGS["BIRTHDAYS"] = bd
                print(f"[誕生日] {len(bd['players'])}人分")
    except Exception as e:
        print(f"[WARN] 誕生日: 作成に失敗しました {e!r}")

    # 「あの選手は今」(以前チームにいた選手の近況。1日1回だけ作り直す)。standings.json の "ALUMNI" に入れる
    try:
        import alumni
        old_all = load_json(STANDINGS_PATH, {})
        bd = STANDINGS.get("BIRTHDAYS") or old_all.get("BIRTHDAYS", {})
        ruck_now = [p.get("name", "") for p in bd.get("players") or [] if p.get("team") == "RAC"]
        scorers_rows = (STANDINGS.get("RAC") or old_all.get("RAC", {})).get("scorers", {}).get("rows") or []
        # 女子Fリーグの各クラブ公式サイトの選手の写真(得点表の写真を付けるときに読んだ分)と、日本代表の選出(過去の記録)
        club_cache = (STANDINGS.get("RAC") or old_all.get("RAC", {})).get("scorers", {}).get("club_photo_cache") or {}
        club_photos = {t: (v or {}).get("photos") or {} for t, v in club_cache.items()}
        national = (load_json(HISTORY_PATH, {}).get("teams", {}).get("RAC") or {}).get("national") or []
        al = alumni.build(fetch, old_all.get("ALUMNI", {}), scorers_rows, ruck_now, club_photos, national)
        if al and STANDINGS:
            STANDINGS["ALUMNI"] = al
            print(f"[あの選手は今] {len(al['players'])}人分")
    except Exception as e:
        print(f"[WARN] あの選手は今: 作成に失敗しました {e!r}")

    # 試合・イベントの写真(1日1回だけ作り直す。アプリで選手の顔写真と混ぜて使う)。standings.json の "PHOTOS" に入れる
    try:
        import photos
        ph = photos.build(fetch, load_json(STANDINGS_PATH, {}).get("PHOTOS", {}))
        if ph and STANDINGS:
            STANDINGS["PHOTOS"] = ph
    except Exception as e:
        print(f"[WARN] 写真: 作成に失敗しました {e!r}")

    # 過去の試合の記録(1日1回だけ作り直す。アプリの「過去の記録」で使う)。data/history.json に書き出す
    try:
        import history
        # 今の選手の名前(元選手かどうかの見分けに使う。誕生日のデータに4チームの選手名簿がある)
        bd_all = STANDINGS.get("BIRTHDAYS") or load_json(STANDINGS_PATH, {}).get("BIRTHDAYS", {})
        current = {}
        for p in bd_all.get("players") or []:
            if p.get("team") and p.get("name"):
                current.setdefault(p["team"], []).append(p["name"])
        hi = history.build(fetch, post_json, load_json(HISTORY_PATH, {}), current)
        if hi:
            with open(HISTORY_PATH, "w", encoding="utf-8") as f:
                json.dump(hi, f, ensure_ascii=False, indent=1)
            print(f"[過去の記録] " + "・".join(f"{k} {len(v.get('matches') or [])}試合" for k, v in hi["teams"].items()))
    except Exception as e:
        print(f"[WARN] 過去の記録: 作成に失敗しました {e!r}")

    # 丸岡RUCK・ユナイテッドの順位表。読めなかったリーグは前回の内容を残す
    if STANDINGS:
        merged = load_json(STANDINGS_PATH, {})
        now = datetime.now(timezone.utc).isoformat()
        for k, v in STANDINGS.items():
            v["updated_at"] = now
            merged[k] = v
        with open(STANDINGS_PATH, "w", encoding="utf-8") as f:
            json.dump(merged, f, ensure_ascii=False, indent=2)
        print(f"[順位] {', '.join(STANDINGS)} を書き出しました")

    # 選手の成績。読み取れなかったときは前回のファイルを残す
    try:
        players = build_players(previews)
        if players:
            with open(PLAYERS_PATH, "w", encoding="utf-8") as f:
                json.dump(players, f, ensure_ascii=False, indent=2)
    except Exception as e:
        print(f"[WARN] 選手: 取得に失敗しました {e!r}")


if __name__ == "__main__":
    main()
