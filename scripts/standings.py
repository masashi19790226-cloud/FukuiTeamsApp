"""
丸岡RUCK(女子Fリーグ)とユナイテッド(北信越リーグ1部)の順位表を作る。build_previews.py から使う。

- 丸岡RUCK : 女子Fリーグ公式のトップページに並んでいる全試合の結果(日時・会場・両チーム・スコア)から、
              順位表をこのスクリプトで計算する(公式の順位表ページは後から読み込まれる作りで直接読めないため)。
              並べ方は 勝点 → 得失点差 → 総得点 の順。公式の順位と細かい決め方が違うことがあるので、画面では「計算」と明記する。
              レギュラーシーズンと、ファイナルシーズン(丸岡RUCKと同じグループのチームだけ)を別々に作る。
- ユナイテッド: 公式サイトのトップにある「LEAGUE RANKING」(順位・勝点)をそのまま使う。
              北信越リーグのデータサイト(GoalNote)は自動での読み取りが禁止されているので使わない。
"""

import html as htmllib
import re
from datetime import datetime, timedelta, timezone

WFL_TOP = "https://w-fleague.jp/"
RUCK_NAME = "福井丸岡ラック"
# 今季の期間(これより前の試合はトップページに残っていても使わない)
WFL_SEASON = ("2026-06-01", "2027-03-31")
# ファイナルシーズンが始まる日(この日より前がレギュラーシーズン)
WFL_FINAL_START = "2026-11-01"


def _text(fragment: str) -> str:
    t = re.sub(r"<[^>]+>", " ", fragment or "")
    return re.sub(r"\s+", " ", htmllib.unescape(t)).strip()


def wfleague_matches(page: str):
    """トップページの試合一覧(<dl><dt>日付 時刻 会場</dt><dd>左チーム スコア 右チーム</dd></dl>)を読む。
    同じ試合が何度も並んでいる(スライドの作り)ので、1試合1件にまとめる。"""
    page = re.sub(r"<!--.*?-->", "", page, flags=re.S)
    found = {}
    for dt, dd in re.findall(r"<dl[^>]*>\s*<dt>(.*?)</dt>\s*<dd>(.*?)</dd>\s*</dl>", page, flags=re.S):
        head = _text(dt.replace("<br", " <br"))
        m = re.match(r"(\d{4})\.(\d{2})\.(\d{2})\s+\w+\s+(\d{1,2}:\d{2})\s*(.*)", head)
        if not m:
            continue
        left = re.search(r'class="leftTeam".*?title="([^"]+)"', dd, flags=re.S)
        right = re.search(r'class="rightTeam".*?title="([^"]+)"', dd, flags=re.S)
        score = re.search(r'class="score".*?<span>(.*?)</span>', dd, flags=re.S)
        if not (left and right and score):
            continue
        date = f"{m.group(1)}-{m.group(2)}-{m.group(3)}"
        sm = re.match(r"\s*(\d+)\s*-\s*(\d+)\s*$", _text(score.group(1)))
        key = (date, m.group(4), left.group(1), right.group(1))
        found[key] = {
            "date": date, "time": m.group(4), "venue": m.group(5).strip(),
            "home": left.group(1), "away": right.group(1),
            "hs": int(sm.group(1)) if sm else None, "as": int(sm.group(2)) if sm else None,
        }
    return sorted(found.values(), key=lambda x: (x["date"], x["time"]))


def _mark(gf, ga):
    return "○" if gf > ga else ("●" if gf < ga else "△")


def compute_table(matches, teams=None):
    """終わった試合(スコアのある試合)から順位表を作る。teams を渡すと、そのチーム同士の試合だけで作る。"""
    rows = {}

    def row(name):
        return rows.setdefault(name, {"team": name, "played": 0, "win": 0, "draw": 0, "lose": 0,
                                      "gf": 0, "ga": 0, "results": []})

    for mt in matches:
        if teams is not None and not (mt["home"] in teams and mt["away"] in teams):
            continue
        if teams is not None:
            row(mt["home"])
            row(mt["away"])
        if mt["hs"] is None:
            continue
        for me, opp, gf, ga in ((mt["home"], mt["away"], mt["hs"], mt["as"]), (mt["away"], mt["home"], mt["as"], mt["hs"])):
            r = row(me)
            r["played"] += 1
            r["gf"] += gf
            r["ga"] += ga
            r["win" if gf > ga else "lose" if gf < ga else "draw"] += 1
            r["results"].append({"date": mt["date"], "opponent": opp, "gf": gf, "ga": ga})
    table = []
    for r in rows.values():
        r["points"] = r["win"] * 3 + r["draw"]
        r["gd"] = r["gf"] - r["ga"]
        r["form"] = "".join(_mark(x["gf"], x["ga"]) for x in r["results"][-5:])
        r["record"] = f"{r['win']}勝{r['lose']}敗" + (f"{r['draw']}分" if r["draw"] else "")
        del r["results"]
        table.append(r)
    table.sort(key=lambda r: (-r["points"], -r["gd"], -r["gf"], r["team"]))
    for i, r in enumerate(table, 1):
        r["rank"] = i
    return table


def ruck_standings(page: str):
    """丸岡RUCKのリーグの順位表(レギュラーシーズンと、丸岡RUCKのファイナルシーズンのグループ)。"""
    season = [m for m in wfleague_matches(page) if WFL_SEASON[0] <= m["date"] <= WFL_SEASON[1]]
    regular = [m for m in season if m["date"] < WFL_FINAL_START]
    final = [m for m in season if m["date"] >= WFL_FINAL_START]
    # ファイナルシーズンで丸岡RUCKと同じグループ = 丸岡RUCKと対戦するチーム(+丸岡RUCK)
    group = {RUCK_NAME} | {m["away"] if m["home"] == RUCK_NAME else m["home"]
                           for m in final if RUCK_NAME in (m["home"], m["away"])}
    result = {
        "league": "女子Fリーグ",
        "source_url": WFL_TOP,
        "note": "公式サイトの試合結果からアプリで計算(勝点→得失点差→総得点の順)",
        "regular": {"label": "レギュラーシーズン", "table": compute_table(regular)},
    }
    if len(group) > 1:
        result["final"] = {"label": "ファイナルシーズン", "table": compute_table(final, teams=group)}
    print(f"[順位] 女子Fリーグ: 今季の試合{len(season)}件(終了{sum(1 for m in season if m['hs'] is not None)}件)"
          f"・ファイナルシーズンのグループ{len(group)}チーム")
    return result


def find_row(standings, name):
    """順位表から、名前にその文字が入っているチームの行を探す(ファイナルシーズンに試合があればそちらを優先)。"""
    for part in ("final", "regular"):
        t = (standings or {}).get(part, {}).get("table", [])
        for r in t:
            if name and (name in r["team"] or r["team"] in name):
                if part == "final" and r["played"] == 0:
                    break
                return part, r
    return None, None


def united_standings(text: str):
    """ユナイテッド公式サイトのトップの「LEAGUE RANKING」(順位・チーム・勝点)。"""
    part = text.split("LEAGUE RANKING", 1)[-1][:1500]
    table = []
    for m in re.finditer(r"(\d+)\s*位\s*(.+?)\s+(\d+)\s*(?=\n|$)", part):
        table.append({"rank": int(m.group(1)), "team": m.group(2).strip(), "points": int(m.group(3))})
    asof = re.search(r"(\d{4}年\d{1,2}月\d{1,2}日[^\n]{0,20}?終了時点|第\d+節終了時点)", part)
    if not table:
        return None
    return {
        "league": "北信越リーグ1部",
        "source_url": "https://fukuiunited.co.jp/",
        "note": "ユナイテッド公式サイトの順位表(順位・勝点のみ)" + (f"・{asof.group(1)}" if asof else ""),
        "regular": {"label": "順位表", "table": table},
    }


# ---------- 選手の得点 ----------

WFL_GOALRANK = "https://w-fleague.jp/score/goalrank.html"
UNITED_SITE = "https://fukuiunited.co.jp"
UNITED_NAME = "福井ユナイテッドFC"


def _num(s):
    m = re.search(r"-?\d+", s or "")
    return int(m.group(0)) if m else 0


def ruck_scorers(fetch):
    """女子Fリーグ公式の「個人ランキング(ゴール)」。リーグ全体の得点者(1点以上)の一覧。
    ページの表は後から読み込まれる作りなので、ページ内に書かれている読み込み先(tid=今季の番号)を見つけて直接読む。
    ファイナルシーズンに入ると公式側の番号が変わるので、毎回ページから探す。"""
    page = fetch(WFL_GOALRANK)
    m = re.search(r'(/modules/php/FlGoalRanking[\w]*\.php)["\'].*?tid=(\d+)', page, flags=re.S)
    if not m:
        print("[WARN] 得点: 女子Fリーグの得点ランキングの読み込み先が見つかりません")
        return None
    # 公式のページから読み込んだとき(Referer付き)だけ表の中身が返ってくる作りなので、Referer を付ける
    body = fetch(f"https://w-fleague.jp{m.group(1)}?tid={m.group(2)}&rn=500", {"Referer": WFL_GOALRANK})
    body = re.sub(r"<!--.*?-->", "", body, flags=re.S)
    rows = []
    for tr in re.findall(r"<tr>(.*?)</tr>", body, flags=re.S):
        tds = [_text(x) for x in re.findall(r"<td[^>]*>(.*?)</td>", tr, flags=re.S)]
        if len(tds) < 8 or not tds[0].isdigit():
            continue
        rows.append({
            "rank": int(tds[0]), "name": re.sub(r"[\s　]+", " ", tds[1]).strip(), "team": tds[2],
            "goals": _num(tds[3]), "pk": _num(tds[4]) + _num(tds[5]), "shots": _num(tds[6]), "games": _num(tds[7]),
        })
    print(f"[得点] 女子Fリーグ: 得点者{len(rows)}人(丸岡{sum(1 for r in rows if RUCK_NAME in r['team'])}人)"
          + ("" if rows else f" ※表が空でした(受け取った文字数{len(body)})"))
    if not rows:
        return None
    return {
        "label": "得点ランキング",
        "note": "女子Fリーグ公式の個人ランキング(1点以上の選手)",
        "source_url": WFL_GOALRANK,
        "rows": rows,
    }


def parse_united_result(page: str):
    """ユナイテッド公式の試合結果ページ1つ分。ユナイテッド側の得点者・先発・サブ(ベンチ入り)。
    スコア・得点者・メンバー表は、どれもユナイテッドが左(1つめ)に並ぶ作り。念のためチーム名で左右を確かめる。"""
    team_block = re.search(r'match-result-score__team">(.*?)</ul>', page, flags=re.S)
    num_block = re.search(r'match-result-score__number">(.*?)</ul>', page, flags=re.S)
    if not (team_block and num_block):
        return None
    teams = [_text(x) for x in re.findall(r"<p>(.*?)</p>", team_block.group(1), flags=re.S)]
    nums = [int(x) for x in re.findall(r"<li>\s*(\d+)\s*</li>", num_block.group(1))]
    if UNITED_NAME not in teams or len(nums) != 2:
        return None
    ui = teams.index(UNITED_NAME)
    goals = []
    gb = re.search(r'class="match-result-goal">.*?<ul>(.*?)</ul>', page, flags=re.S)
    if gb:
        sides = re.split(r"<li>", gb.group(1))[1:]
        if len(sides) > ui:
            goals = [_text(x) for x in re.findall(r"<span>(.*?)</span>", sides[ui], flags=re.S)]
    # 得点者の数がスコアと合わないとき(オウンゴールの書き方の違いなど)は、そのまま使う(数え直さない)
    members = {"start": [], "sub": []}
    for sec in page.split('class="match-main-data-menber"')[1:]:
        kind = "start" if "STARTING" in sec[:300] else ("sub" if "SUBSTITUTE" in sec[:300] else None)
        if not kind:
            continue
        uls = re.findall(r"<ul>(.*?)</ul>", sec.split('class="match-main-data-menber"')[0], flags=re.S)
        if len(uls) <= ui:
            continue
        for li in re.findall(r"<li>(.*?)</li>", uls[ui], flags=re.S):
            name = re.search(r'class="name">(.*?)</p>', li, flags=re.S)
            no = re.search(r'class="number"><span>(.*?)</span><strong>(.*?)</strong>', li, flags=re.S)
            if name:
                members[kind].append({"name": _text(name.group(1)), "number": _text(no.group(1)) if no else "",
                                      "position": _text(no.group(2)) if no else ""})
    return {"my": nums[ui], "opp": nums[1 - ui], "goals": goals, **members}


def united_scorers(fetch, post_json, cache):
    """ユナイテッドの今季の北信越リーグの試合結果ページを全部読み、選手ごとの得点・先発・ベンチ入りを数える。
    終わった試合の結果ページは変わらないので、一度読んだ試合は cache(standings.json に保存)から使う。"""
    # 試合開始時刻は日本時間なので、日本時間で比べる(GitHub Actions の時計は世界標準時)
    now = datetime.now(timezone(timedelta(hours=9))).replace(tzinfo=None)
    year = now.year
    pt = post_json(f"{UNITED_SITE}/system/async/async.php",
                   {"className": "PostTypes", "method": "get", "slug": ["match"], "post_type_options": True})
    ptid = pt["data"][0]["post_type_id"]
    res = post_json(f"{UNITED_SITE}/system/async/async.php",
                    {"className": "MatchInfo", "method": "get", "post_type_id": [ptid], "category": "",
                     "orderby": [{"column": "kickoff", "order": "asc"}], "offset": 0, "limit": 300})
    games = []
    for g in res.get("data", []):
        title, ko = g.get("title", ""), g.get("kickoff", "")
        if "北信越フットボールリーグ" not in title or not ko.startswith(str(year)):
            continue
        try:
            kt = datetime.strptime(ko, "%Y/%m/%d %H:%M")
        except ValueError:
            continue
        if kt + timedelta(hours=3) > now:  # まだ終わっていない試合
            continue
        games.append((str(g["id"]), title))
    new_cache = {}
    for gid, title in games:
        if gid in cache:
            new_cache[gid] = cache[gid]
            continue
        try:
            r = parse_united_result(fetch(f"{UNITED_SITE}/match/result.php?id={gid}"))
        except Exception as e:
            print(f"[WARN] 得点: ユナイテッド {title} の結果ページの取得に失敗 {e!r}")
            continue
        if r:
            new_cache[gid] = r
    players = {}

    def pl(m):
        p = players.setdefault(m["name"], {"name": m["name"], "number": "", "position": "",
                                            "goals": 0, "starts": 0, "bench": 0})
        if m.get("number"):
            p["number"], p["position"] = m["number"], m.get("position", "")
        return p

    for gid, _ in games:  # 古い試合から順に(背番号は新しい試合のものが残る)
        r = new_cache.get(gid)
        if not r:
            continue
        for m in r["start"]:
            pl(m)["starts"] += 1
        for m in r["sub"]:
            pl(m)["bench"] += 1
        for name in r["goals"]:
            pl({"name": name})["goals"] += 1
    rows = sorted(players.values(), key=lambda p: (-p["goals"], -p["starts"], -p["bench"], p["name"]))
    for p in rows:
        p["team"] = UNITED_NAME
    rank = 0
    for i, p in enumerate(rows):
        if p["goals"] > 0 and (i == 0 or rows[i - 1]["goals"] != p["goals"]):
            rank = i + 1
        p["rank"] = rank if p["goals"] > 0 else 0
    print(f"[得点] ユナイテッド: 今季{len(new_cache)}試合・選手{len(rows)}人・得点{sum(p['goals'] for p in rows)}")
    if not rows:
        return None, new_cache
    return {
        "label": "チーム内の得点",
        "note": f"ユナイテッド公式の試合結果(北信越リーグ{len(new_cache)}試合)から集計",
        "source_url": f"{UNITED_SITE}/match/",
        "games": len(new_cache),
        "rows": rows,
    }, new_cache


# ---------- 自チームの選手の顔写真(公式サイトの選手紹介) ----------

RUCK_MEMBERS = "https://ruck-fukui.com/members"
UNITED_TEAM = f"{UNITED_SITE}/team/"


def _name_key(name: str) -> str:
    """名前を比べるための形(空白を取る)"""
    return re.sub(r"[\s\u3000]+", "", name or "")


def ruck_members(fetch):
    """丸岡RUCK公式の選手紹介から {名前: {photo, number, position, height, hometown}}。
    <div class="member-item"><p><img src="...profile2026-17-300x300.jpg"></p><h4 class="team-name">荒井 一花(17 FP)</h4>"""
    page = fetch(RUCK_MEMBERS)
    out = {}
    for block in page.split('class="member-item"')[1:]:
        img = re.search(r'<img[^>]+src="([^"]+)"', block)
        head = re.search(r'class="team-name">(.*?)</h4>', block, flags=re.S)
        if not head:
            continue
        text = _text(head.group(1))
        m = re.match(r"(.+?)\s*[（(]\s*(\d+)\s*([A-Za-z]+)?\s*[)）]", text)
        name = (m.group(1) if m else text).strip()
        photo = img.group(1) if img else ""
        # 一覧用の小さい画像(150x150)があればそれを使う
        photo = re.sub(r"-\d+x\d+(\.\w+)$", r"-150x150\1", photo)
        # 選手紹介には「2001年11月14日 福井県生まれ 173cm A型」のように項目名なしで並んでいる。身長と出身地を読む
        body = _text(block)
        h = re.search(r"(\d{3}(?:\.\d)?)\s*cm", body)
        height = h.group(1) if h and 140 <= float(h.group(1)) <= 235 else ""
        b = re.search(r"([^\s\d()（）]{2,12}?)生まれ", body)
        out[_name_key(name)] = {"photo": photo, "number": m.group(2) if m else "", "position": (m.group(3) or "") if m else "",
                                "height": height, "hometown": b.group(1) if b else ""}
    print(f"[写真] 丸岡RUCK: 選手紹介から{len(out)}人")
    return out


def united_photos(fetch):
    """ユナイテッド公式のトップチームのページから {名前: 写真URL}。
    <img class="teammate__photo" src="https://fukuiunited.co.jp/upload/team/....png" alt="杉本 拓也" />"""
    page = fetch(UNITED_TEAM)
    out = {}
    for tag in re.findall(r"<img[^>]+teammate__photo[^>]*>", page):
        src = re.search(r'src="([^"]+)"', tag)
        alt = re.search(r'alt="([^"]*)"', tag)
        if src and alt and alt.group(1).strip():
            out[_name_key(htmllib.unescape(alt.group(1)))] = src.group(1)
    print(f"[写真] ユナイテッド: トップチームのページから{len(out)}人")
    return out


def add_photos(rows, team_word, photos):
    """得点の表の、自チームの選手の行に写真(と、分かれば背番号・ポジション)を付ける"""
    for r in rows or []:
        if team_word not in r.get("team", ""):
            continue
        info = photos.get(_name_key(r.get("name", "")))
        if not info:
            continue
        if isinstance(info, dict):
            if info.get("photo"):
                r["photo"] = info["photo"]
            if not r.get("number") and info.get("number"):
                r["number"] = info["number"]
            if not r.get("position") and info.get("position"):
                r["position"] = info["position"]
        else:
            r["photo"] = info


# ---------- 女子Fリーグの相手クラブの選手写真 ----------
# 女子Fリーグ公式サイトの選手名簿には写真が無いため、各クラブの公式サイトの選手紹介ページから取る。
# 得点表のチーム名 → (読み方, ページ)。
#   "page"  : 選手紹介ページのHTMLから、選手名の近くにある写真を探す(名前が文字で書かれているサイト)
#   "studio": STUDIO(サイト作成サービス)のCMSから、選手名と写真を読む
# 選手名が画像の中に書かれていて文字で読めないサイト(さいたまサイコロ・アニージャ湘南)と、
# 公式サイトが無いクラブ(流経大メニーナ龍ケ崎)は入れていない(写真なしで表示)。
# シーズンが変わってページの場所が変わったら、ここを書き換える。
WFL_CLUB_PHOTO_SOURCES = {
    "エスポラーダ北海道イルネーヴェ": ("page", "https://espolada.com/profile/irneve/"),
    "バルドラール浦安ラス・ボニータス": ("page", "https://www.bardral-urayasu.com/lasbonitas/"),
    "立川アスレティックFCレディース": ("page", "https://tachikawa-athletic.jp/ladies-players-staff2026-27/"),
    "SWHレディース西宮": ("page", "https://www.swh2003.com/team/index.html"),
    "アルコ神戸": ("page", "https://arco-kobe.com/players/"),
    "ミネルバ宇部": ("page", "https://www.minerva-ube.jp/"),
    "フウガドールすみだレディース": ("studio", {
        "project_id": "ht0DWGt9lWUFxPnbBoak", "schema_key": "IMzOWTim", "filters": "RNwDuIn7:ref[equals]dsEOsiLQ",
    }),
}


def _photo_name_key(name: str) -> str:
    """写真さがし用に名前をそろえる(空白・中黒を取り、異体字をふつうの字にする)"""
    s = re.sub(r"[\s　・･.]+", "", htmllib.unescape(name or ""))
    for a, b in (("髙", "高"), ("﨑", "崎"), ("濵", "浜"), ("邉", "辺"), ("邊", "辺"), ("齋", "斎"), ("齊", "斉")):
        s = s.replace(a, b)
    return s


def _pick_img_src(tag: str, base_url: str) -> str:
    """<img> タグから写真のURLを取り出す。遅延読み込み(data-src など)にも対応し、
    srcset があれば幅300px以上のうち一番小さい画像を選ぶ(大きすぎる画像を読み込まないため)。"""
    def attr(n):
        m = re.search(r"\s" + n + r"\s*=\s*[\"']([^\"']+)[\"']", tag, flags=re.I)
        return htmllib.unescape(m.group(1)).strip() if m else ""
    srcset = attr("data-srcset") or attr("srcset")
    best = None
    for part in srcset.split(","):
        bits = part.strip().split()
        if len(bits) >= 2 and bits[1].endswith("w") and bits[1][:-1].isdigit() and not bits[0].startswith("data:"):
            w = int(bits[1][:-1])
            if w >= 300 and (best is None or w < best[0]):
                best = (w, bits[0])
    src = best[1] if best else next(
        (u for u in (attr("data-src"), attr("data-lazy-src"), attr("src")) if u and not u.startswith("data:")), "")
    if not src:
        return ""
    from urllib.parse import urljoin
    url = urljoin(base_url, src)
    # スマホのアプリは http の画像を読めないので https にする
    return "https://" + url[len("http://"):] if url.startswith("http://") else url


def photos_from_page(page: str, base_url: str, names):
    """選手紹介ページのHTMLから {名前のキー: 写真URL} を作る。
    ページを「画像」と「文字」の並びにして、選手名が書かれた文字の近く(前に8つまで、無ければ後ろに8つまで)の
    画像を、その選手の写真とする。画像の代替文字(alt)に名前があれば、それを優先する。
    ロゴ・アイコンや、ページ内で3回以上使われている画像(「準備中」の画像など)は使わない。"""
    html = re.sub(r"<(script|style)\b.*?</\1>", " ", page, flags=re.S | re.I)
    html = re.sub(r"<!--.*?-->", " ", html, flags=re.S)
    toks = []
    for m in re.finditer(r"<img\b[^>]*>|<[^>]+>|[^<]+", html, flags=re.I):
        s = m.group(0)
        if s[:4].lower() == "<img":
            alt = re.search(r"\salt\s*=\s*[\"']([^\"']*)[\"']", s, flags=re.I)
            toks.append(("img", _pick_img_src(s, base_url), _photo_name_key(alt.group(1)) if alt else ""))
        elif s[0] != "<":
            t = _photo_name_key(s)
            if t:
                toks.append(("text", t, ""))
    count = {}
    for k in toks:
        if k[0] == "img" and k[1]:
            count[k[1]] = count.get(k[1], 0) + 1

    def ok(i):
        if i < 0 or i >= len(toks) or toks[i][0] != "img" or not toks[i][1]:
            return False
        u = toks[i][1]
        return count.get(u, 0) <= 2 and not re.search(r"logo|icon|sponsor|banner|\.svg|\.gif|spacer|blank|loading", u, flags=re.I)

    out = {}
    for name in names:
        key = _photo_name_key(name)
        if not key or key in out:
            continue
        found = None
        for i, k in enumerate(toks):
            if k[0] == "img" and key in k[2] and ok(i):
                found = k[1]
                break
            if k[0] == "text" and key in k[1]:
                for d in list(range(1, 9)):
                    if ok(i - d):
                        found = toks[i - d][1]
                        break
                if not found:
                    for d in range(1, 9):
                        if ok(i + d):
                            found = toks[i + d][1]
                            break
                if found:
                    break
        if found:
            out[key] = found
    return out


def photos_from_studio(fetch, conf):
    """STUDIO の CMS(選手の一覧)から {名前のキー: 写真URL}。title が選手名、avatar が写真。"""
    import base64
    import json as jsonlib
    from urllib.parse import quote
    q = {"project_id": conf["project_id"], "schema_key": conf["schema_key"], "filters": conf["filters"],
         "orders": "order", "offset": 0, "limit": 100}
    url = "https://api.cms.studiodesignapp.com/v2/search?q=" + quote(base64.b64encode(
        jsonlib.dumps(q, separators=(",", ":")).encode("utf-8")).decode("ascii"))
    data = jsonlib.loads(fetch(url))
    items = data if isinstance(data, list) else data.get("data", [])
    out = {}
    for it in items:
        f = (((it or {}).get("document") or {}).get("fields") or {}).get("default", {}).get("mapValue", {}).get("fields", {})
        name = (f.get("title") or {}).get("stringValue", "")
        avatar = (f.get("avatar") or {}).get("stringValue", "")
        if name and avatar.startswith("http"):
            out[_photo_name_key(name)] = avatar
    return out


def add_wfl_club_photos(rows, fetch, cache):
    """女子Fリーグの得点表の、相手クラブの選手の行に、各クラブ公式サイトの写真を付ける。
    クラブのページは1日1回だけ読み直し、それ以外は cache(standings.json に保存)を使う。
    返り値は (新しい cache, クラブごとの結果の一言)。"""
    today = datetime.now(timezone(timedelta(hours=9))).strftime("%Y-%m-%d")
    new_cache, report = {}, {}
    for team, (kind, src) in WFL_CLUB_PHOTO_SOURCES.items():
        names = [r.get("name", "") for r in rows or [] if r.get("team") == team]
        old = (cache or {}).get(team) or {}
        photos = old.get("photos") or {}
        if old.get("date") != today or not photos:
            try:
                if kind == "studio":
                    photos = photos_from_studio(fetch, src)
                else:
                    # 得点表に載っているそのクラブの選手の名前で、ページの中の写真を探す
                    photos = photos_from_page(fetch(src), src, names)
                new_cache[team] = {"date": today, "photos": photos}
                report[team] = f"{len(photos)}人"
            except Exception as e:
                report[team] = f"取得失敗 {type(e).__name__}"
                if photos:
                    new_cache[team] = old  # 前回の写真を使い続ける
        else:
            new_cache[team] = old
            report[team] = f"{len(photos)}人(前回の分)"
        hit = 0
        for r in rows or []:
            if r.get("team") != team or r.get("photo"):
                continue
            url = photos.get(_photo_name_key(r.get("name", "")))
            if url:
                r["photo"] = url
                hit += 1
        report[team] += f"・得点表に{hit}人"
    print("[写真] 女子Fリーグの相手クラブ: " + " / ".join(f"{k} {v}" for k, v in report.items()))
    return new_cache, report
