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
# B.LEAGUE公式の対戦成績(福井=2891)。日付・WIN/LOSE・点数がそのまま載っていて一番確実
BLEAGUE_RECORD_URL = "https://www.bleague.jp/record/?club1=2891&club2=0"
BLOWINDS_LIST_URL = "https://www.fukuiblowinds.com/schedule/list/?year={year}&month={month}"
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


# ---------- ブローウィンズ ----------
# 以前はHTMLのまま日付や点数を探していたが、「09/26」「(土)」「90」「-」「88」が
# 別々のタグに分かれていて一致しなかった。タグを外した文字列で探すように変更。
# 終了した試合の表示例(同じB.LEAGUEクラブ公式サイトの共通レイアウト):
#   AWAY 09/26(土) 14:05 レギュラーシーズン 福井 福井 90 - 88 location_on 会場 岐阜 岐阜 試合レポート

BW_DATE = re.compile(r"(\d{2})/(\d{2})\s*\(\s*[月火水木金土日]\s*\)\s*(\d{1,2}:\d{2})?")


def parse_bleague_record(page: str):
    """
    B.LEAGUE公式「対戦成績」ページから {"2026-09-26": (福井の点, 相手の点)} を作る。
    表示例: AWAY 2026.09.26 VS 岐阜スゥープス … WIN 90-88
    """
    results = {}
    if not page:
        return results
    text = " ".join(html_to_lines(page))
    pat = re.compile(r"(\d{4})\.(\d{2})\.(\d{2})(.{0,300}?)(WIN|LOSE)\s*(\d{2,3})\s*[-‐−–]\s*(\d{2,3})")
    for m in pat.finditer(text):
        # 途中に別の日付が挟まっていたら、その日付の試合の点数ではない
        if re.search(r"\d{4}\.\d{2}\.\d{2}", m.group(4)):
            continue
        a, b = int(m.group(6)), int(m.group(7))
        win = m.group(5) == "WIN"
        mine, opp = (a, b) if (a > b) == win else (b, a)
        results[f"{m.group(1)}-{m.group(2)}-{m.group(3)}"] = (mine, opp)
    return results


def parse_bleague_links(page: str):
    """対戦成績ページの各試合の詳細ページURL {"2026-09-26": "https://www.bleague.jp/game_detail/?ScheduleKey=…"}。"""
    links = {}
    if not page:
        return links
    for m in re.finditer(r'game_detail/\?ScheduleKey=(\d+)[^>]*>\s*(?:<[^>]+>\s*)*(\d{4})\.(\d{2})\.(\d{2})', page):
        links[f"{m.group(2)}-{m.group(3)}-{m.group(4)}"] = f"https://www.bleague.jp/game_detail/?ScheduleKey={m.group(1)}"
    return links


def blowinds_text(pages):
    return " ".join(" ".join(html_to_lines(p)) for p in pages if p)


def blowinds_score(text: str, game_dt: datetime, game: dict):
    date_str = game_dt.strftime("%m/%d")
    for m in BW_DATE.finditer(text):
        if f"{m.group(1)}/{m.group(2)}" != date_str:
            continue
        nxt = BW_DATE.search(text, m.end())
        segment = text[m.end(): nxt.start() if nxt else min(len(text), m.end() + 400)]
        sm = re.search(r"(?<!\d)(\d{2,3})\s*[-‐−–]\s*(\d{2,3})(?!\d)", segment)
        if not sm:
            continue
        a, b = int(sm.group(1)), int(sm.group(2))
        # 点数より前に「福井」があれば福井が左側(自チームの点数が先)
        before = segment[:sm.start()]
        after = segment[sm.end():]
        if "福井" in before:
            return a, b
        if "福井" in after.split("location_on")[-1][:30] or "福井" in after[:60]:
            return b, a
        print(f"[DEBUG] BW: {date_str} の点数 {a}-{b} はあるが、どちらが福井か判定できない")
    return None


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
    公式トップのHTML(2026年10月時点):
      <h2>LATEST  MATCH</h2>  ※LATEST と MATCH の間は空白2つ
      <p class="latest-main-info">10.4<span>(Sun)</span>13:30<small>KICKOFF</small></p>
      <div class="latest-main-score__name">福井ユナイテッドFC</div>
      <ul><li>0</li><li><p>0-1</p><p>0-0</p></li><li>1</li></ul>   ※真ん中は前半・後半の得点
      <div class="latest-main-score__name">富山新庄クラブ</div>
    以前は「LATEST MATCH」(空白1つ)で探していたため見つからず、さらに前半・後半の得点を
    試合の得点と取り違えるおそれがあったので、HTMLの作りから直接読むように変更した。
    """
    m = re.search(r"LATEST\s+MATCH(.*?)(LEAGUE\s+RANKING|</section>)", page, re.S | re.I)
    if not m:
        print("[DEBUG] UNITED: LATEST MATCH 欄が見つからない")
        return None
    block = m.group(1)
    info = re.search(r'latest-main-info">(.*?)</p>', block, re.S)
    info_text = re.sub(r"<[^>]+>", " ", info.group(1)) if info else re.sub(r"<[^>]+>", " ", block)
    dm = re.search(r"(\d{1,2})\.(\d{1,2})\s*\(\s*\w+\s*\)\s*(\d{1,2}:\d{2})\s*KICK\s*OFF", info_text, re.I)
    if not dm:
        print("[DEBUG] UNITED: 日付・キックオフ時刻が見つからない")
        return None
    names = [htmllib.unescape(re.sub(r"<[^>]+>", "", n)).strip()
             for n in re.findall(r'latest-main-score__name">(.*?)</div>', block, re.S)]
    score_ul = re.search(r'latest-main-score.*?<ul>(.*?)</ul>', block, re.S)
    # ul の直下の li のうち、中身が数字だけのもの(左右の得点)。前半・後半の <p> は数えない
    scores = re.findall(r"<li>\s*(\d{1,2})\s*</li>", score_ul.group(1)) if score_ul else []
    if len(names) < 2 or len(scores) < 2:
        print("[DEBUG] UNITED: まだスコアが出ていない(または読み取れない)")
        return None
    return (int(dm.group(1)), int(dm.group(2)), dm.group(3).zfill(5),
            names[0], int(scores[0]), int(scores[-1]), names[1])


UNITED_ASYNC = "https://fukuiunited.co.jp/system/async/async.php"


def united_score_from_result_pages(game_dt: datetime, game: dict):
    """
    公式トップに出ていない試合(次の試合が終わってトップから外れた場合など)は、
    公式サイトの試合一覧から同じ日・同じ時刻の試合の結果ページを探して読む。
    戻り値:((福井の得点, 相手の得点), 結果ページのURL) / 見つからなければ (None, None)
    """
    try:
        import standings  # scripts/standings.py(結果ページの読み取りを共通で使う)

        def post(url, body):
            req = urllib.request.Request(
                url, data=json.dumps(body).encode("utf-8"), method="POST",
                headers={"User-Agent": "Mozilla/5.0 FukuiTeamsAppBot/1.0", "Content-Type": "application/json"},
            )
            with urllib.request.urlopen(req, timeout=20) as res:
                return json.loads(res.read().decode("utf-8", errors="ignore"))

        pt = post(UNITED_ASYNC, {"className": "PostTypes", "method": "get", "slug": ["match"], "post_type_options": True})
        ptid = pt["data"][0]["post_type_id"]
        res = post(UNITED_ASYNC, {"className": "MatchInfo", "method": "get", "post_type_id": [ptid], "category": "",
                                  "orderby": [{"column": "kickoff", "order": "asc"}], "offset": 0, "limit": 300})
        want = game_dt.strftime("%Y/%m/%d %H:%M")
        for g in res.get("data", []):
            if g.get("kickoff", "") != want:
                continue
            url = f"https://fukuiunited.co.jp/match/result.php?id={g['id']}"
            r = standings.parse_united_result(fetch_html(url))
            if r:
                return (r["my"], r["opp"]), url
        return None, None
    except Exception as e:
        print(f"[WARN] UNITED: 結果ページからの取得に失敗 {e!r}")
        return None, None


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
    bw_page = None
    if "blowinds" in teams:
        # トップページ(直近の試合)と、確認が必要な試合がある月の日程一覧の両方を見る
        pages = [safe_fetch("BW", BLOWINDS_URL)]
        months = sorted({(dt.year, dt.month) for g, dt in pending if g.get("team", "blowinds") == "blowinds"})
        for y, m in months[-3:]:
            pages.append(safe_fetch("BW", BLOWINDS_LIST_URL.format(year=y, month=m)))
        bw_page = blowinds_text(pages) or None
    bleague_page = safe_fetch("BLEAGUE", BLEAGUE_RECORD_URL) if "blowinds" in teams else None
    bleague = parse_bleague_record(bleague_page)
    bleague_links = parse_bleague_links(bleague_page)
    if bleague:
        print(f"[INFO] BLEAGUE: {len(bleague)} 試合の結果を検出 {sorted(bleague)}")
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
        source_url = None  # アプリで結果をタップしたときに開く、取得元のページ
        if team == "blowinds":
            # B.LEAGUE公式を優先し、無ければクラブ公式サイトから探す
            score = bleague.get(game["date"])
            source_url = bleague_links.get(game["date"], BLEAGUE_RECORD_URL)
            if not score and bw_page:
                score = blowinds_score(bw_page, game_dt, game)
                source_url = BLOWINDS_URL
        elif team == "rac" and ruck_parsed:
            score = ruck_score(ruck_parsed, game)
            gid = game.get("gid")
            source_url = f"https://w-fleague.jp/score/result.html?gid={gid}" if gid else RUCK_URL
        elif team == "united":
            score = united_score(un_latest, game_dt, game) if un_page else None
            source_url = UNITED_URL
            if not score:
                # トップの LATEST MATCH に無ければ、試合ごとの結果ページから探す
                score, page_url = united_score_from_result_pages(game_dt, game)
                if page_url:
                    source_url = page_url

        label = f"{game['id']} ({game['date']} vs {game['opponent']})"
        if score:
            results[game["id"]] = {
                "my_score": score[0],
                "opponent_score": score[1],
                "checked_at": datetime.utcnow().isoformat(),
                "source_url": source_url,
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
