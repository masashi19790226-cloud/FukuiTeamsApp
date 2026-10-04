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
import urllib.request

import standings
from datetime import datetime, timedelta, timezone

BASE_DIR = os.path.join(os.path.dirname(__file__), "..", "data")
GAMES_PATH = os.path.join(BASE_DIR, "games.json")
RESULTS_PATH = os.path.join(BASE_DIR, "results.json")
PREVIEWS_PATH = os.path.join(BASE_DIR, "previews.json")
PLAYERS_PATH = os.path.join(BASE_DIR, "players.json")
STANDINGS_PATH = os.path.join(BASE_DIR, "standings.json")

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
        return
    try:
        opp = bleague_club(opp_id)
    except Exception as e:
        print(f"[WARN] Bリーグ 相手クラブページ取得に失敗 {e}")
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
                 "position": rec.get("PO", "")}
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
    # 選手の顔写真(Bリーグ公式の画像。アプリの選手タブで小さく表示する)
    # 画像の場所は「files/user/roster/<TeamID>/<シーズン>/<PlayerID>_03.png」。シーズンの部分はページ内の画像から読む
    sm = re.search(rf"files/user/roster/{team_id}/([^/\"']+)/\d+_\d+\.(?:png|jpg)", page)
    photo_season = sm.group(1) if sm else (season if re.fullmatch(r"\d{4}-\d{2}", season or "") else "")
    for p in players:
        pid = p.pop("pid", None)
        if pid and photo_season:
            p["photo"] = f"https://bleague.bl.kuroco-img.app/files/user/roster/{team_id}/{photo_season}/{pid}_03.png"
    players.sort(key=lambda p: int(p["number"]) if str(p.get("number", "")).isdigit() else 999)
    if not players:
        print(f"[WARN] 選手: Bリーグ公式から{label}の選手を読み取れませんでした")
        return None
    print(f"[選手] {label} {len(players)}人(成績あり{len(stats)}人・選手一覧{len(roster)}人・{season or 'シーズン不明'})")
    return {
        "season": season or "", "source_url": url,
        "updated_at": datetime.now(timezone.utc).isoformat(), "players": players,
    }


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


def attach_player_photos():
    """丸岡RUCK・ユナイテッドの得点の表に、公式サイトの選手紹介の顔写真を付ける(読めなければ付けない)"""
    rac = STANDINGS.get("RAC", {}).get("scorers")
    if rac:
        try:
            standings.add_photos(rac.get("rows"), standings.RUCK_NAME, standings.ruck_members(fetch))
        except Exception as e:
            print(f"[WARN] 写真: 丸岡RUCKの選手紹介の取得に失敗 {e!r}")
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
            {"number": "", "name": r["name"], "position": "",
             "stat": f"今季{r['goals']}得点(リーグ{r['rank']}位)・シュート{r['shots']}本"}
            for r in top
        ]
        preview["players_note"] = "女子Fリーグ公式の得点ランキングから"


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

    load_standings()
    attach_player_photos()

    for team in ["BLOWINDS", "RAC", "UNITED"]:
        try:
            build_one(team, games, results, today, previews)
        except Exception as e:
            # 1チームで失敗しても、他のチームの展望は書き出す
            print(f"[WARN] {team}: 展望を作れませんでした {e!r}")

    with open(PREVIEWS_PATH, "w", encoding="utf-8") as f:
        json.dump(previews, f, ensure_ascii=False, indent=2)
    print(f"[展望] {len(previews)}試合分を書き出しました")

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
