"""
「あの選手は今」(以前チームにいた選手の近況)のデータを作る。build_previews.py から1日1回だけ呼び、
data/standings.json の "ALUMNI" に入れる。アプリは一面の特集に出す。

- 丸岡RUCK : 公式サイト(WordPress)の「退団選手のお知らせ」「移籍選手のお知らせ」の記事から、以前の選手の名前・背番号・
             お知らせの日付・移籍先を読み取る(記事は公開API /wp-json/wp/v2/posts で探す。
             API で読めないときはサイト内検索の RSS で探す。ruck_wp.py)。
             その選手が今、女子Fリーグのほかのクラブにいるかを、各クラブの選手名簿(女子Fリーグ公式)で調べる。
             今季の得点は、女子Fリーグ公式の得点ランキング(standings.json の RAC の scorers。build_previews.py が作る)から。
(ブローウィンズは、Bリーグ公式サイトが一時的に止まっているため、あとで加える)

読めなかったときは前回の分を残す。
"""

import html as htmllib
import json
import re
from datetime import datetime, timedelta, timezone
from urllib.parse import quote

import name_changes
import ruck_wp

JST = timezone(timedelta(hours=9))

RUCK_SITE = "https://ruck-fukui.com"
WFL_SITE = "https://w-fleague.jp"
# これより前のお知らせの選手は入れない(公式サイトのお知らせが始まった年)
SINCE_YEAR = 2017

VERSION = 4

# 元選手を探すお知らせの言葉と、そのときの種類(見出しにこの言葉がある記事を読む)
DEPARTURE_WORDS = (("退団", "退団"), ("移籍", "移籍"), ("引退", "引退"), ("卒団", "卒団"), ("契約満了", "退団"))
# 見出しに言葉があっても、元選手のお知らせではない記事(新しく入る選手・試合の記事など)
DEPARTURE_EXCLUDE = re.compile(r"加入|入団|昇格|記者会見|結果|試合|←")


def _text(fragment: str) -> str:
    t = re.sub(r"<[^>]+>", " ", fragment or "")
    t = htmllib.unescape(t).replace("　", " ").replace("\xa0", " ")
    return re.sub(r"\s+", " ", t).strip()


def _key(name: str) -> str:
    t = re.sub(r"[\s　・]+", "", name or "")
    for a, b in (("﨑", "崎"), ("髙", "高"), ("濵", "浜"), ("邉", "邊"), ("𠮷", "吉")):
        t = t.replace(a, b)
    return t


# 「No.3 横山凜花選手」「8番 高尾純奈選手」「19番山川里佳子選手」「No.10 池内天紀 選手」
# (背番号は「No.3」か「8番」の形のときだけ。「2名の選手」のような文を名前と間違えないように)
NAME_RE = re.compile(r"(?:No\.?\s*(\d{1,2})|(\d{1,2})\s*番)\s*([一-龥ぁ-んァ-ヶー々]{2,8}?)\s*選手")
# 「11.小林心選手」「#3 横山凜花選手」(背番号の後ろに点・前に#)
NAME_RE2 = re.compile(r"(?:(?<!\d)(\d{1,2})\.|#\s*(\d{1,2}))\s*([一-龥ぁ-んァ-ヶー々]{2,8}?)\s*選手")
# 背番号の無い「北川夏奈選手」(名前の前が漢字でないこと)
NAME_RE3 = re.compile(r"(?<![一-龥々])([一-龥々]{3,6})\s*選手")
NAME_BAD = re.compile(r"代表|日本|選出|候補|女子|大学|高校|株式|会社|福井|丸岡|退団|移籍|引退|名の|全選手|各選手")


def post_photos(html: str):
    """お知らせの本文の写真(選手の写真)のURL。絵文字の画像は除く。横300pxほどの小さい画像があればそれを使う"""
    out = []
    for tag in re.findall(r"<img\b[^>]*>", html or ""):
        if "wp-smiley" in tag or "s.w.org" in tag:
            continue
        src = re.search(r"\bsrc=[\"']([^\"']+)[\"']", tag)
        if not src:
            continue
        url = htmllib.unescape(src.group(1))
        ss = re.search(r"\bsrcset=[\"']([^\"']+)[\"']", tag)
        if ss:
            for part in ss.group(1).split(","):
                bits = part.strip().split()
                if len(bits) == 2 and bits[1] == "300w":
                    url = htmllib.unescape(bits[0])
                    break
        if url not in out:
            out.append(url)
    return out


def ruck_departures(fetch):
    """丸岡RUCK公式サイトの退団・移籍のお知らせから、以前の選手。[{"name","number","date","kind","dest","link"}, ...]"""
    found = {}
    seen_links = set()
    for word, kind in DEPARTURE_WORDS:
        posts = ruck_wp.search_posts(fetch, RUCK_SITE, word, 50, "title,date,link,content", "あの選手は今 丸岡RUCK")
        for p in posts:
            title = _text((p.get("title") or {}).get("rendered", ""))
            date = (p.get("date") or "")[:10]
            # 「試合後記者会見」「新加入選手」など、元選手のお知らせではない記事は使わない
            if word not in title or DEPARTURE_EXCLUDE.search(title) or date[:4] < str(SINCE_YEAR):
                continue
            if p.get("link") in seen_links:
                continue
            seen_links.add(p.get("link"))
            body = _text((p.get("content") or {}).get("rendered", ""))
            dest = ""
            dm = re.search(r"移籍先\s*[／/:：]\s*(\S+)", body)
            if dm:
                dest = dm.group(1).strip("。、")
            # 本文の前の方(お知らせの文)だけから名前を読む(後ろの選手のコメントには、ほかの選手の名前が出てくることがある)
            head = body[:300]
            # 背番号付きの書き方(「No.3 ○○選手」「8番 ○○選手」「11.○○選手」「#3 ○○選手」)を先に、無ければ「○○選手」
            names = [(a or b, n) for a, b, n in NAME_RE.findall(head) + NAME_RE2.findall(head)]
            if not names:
                names = [("", n) for n in NAME_RE3.findall(title + " " + head)]
            uniq = []
            for num, n in names:
                if not NAME_BAD.search(n) and _key(n) not in [_key(x[1]) for x in uniq]:
                    uniq.append((num, n))
            names = uniq
            # 選手の写真:お知らせの写真の数と選手の数が同じなら順に、選手が1人なら最初の写真を、その選手の写真にする
            photos = post_photos((p.get("content") or {}).get("rendered", ""))
            for i, (num, name) in enumerate(names):
                k = _key(name)
                if k in found:
                    continue
                photo = photos[i] if len(photos) == len(names) else (photos[0] if len(names) == 1 and photos else "")
                found[k] = {"name": name, "number": num, "date": date, "kind": kind, "dest": dest, "link": p.get("link", ""),
                            "photo": photo}
    out = sorted(found.values(), key=lambda x: x["date"], reverse=True)
    print(f"[あの選手は今] 丸岡RUCK: 以前の選手 {len(out)}人")
    return out


def wfl_rosters(fetch):
    """女子Fリーグの全クラブ(丸岡RUCK以外)の選手名簿。{名前のキー: {"club","number","name"}}"""
    top = fetch(WFL_SITE + "/")
    slugs = []
    for s in re.findall(r"href=[\"'](?:https?://w-fleague\.jp)?/club/([a-z0-9_-]+)/?[\"']", top):
        if s not in slugs and s != "maruoka":
            slugs.append(s)
    out = {}
    for slug in slugs:
        club_url = f"{WFL_SITE}/club/{slug}/"
        try:
            club = fetch(club_url)
            tm = re.search(r"var\s+team\s*=\s*(\d+)", club)
            title = re.search(r"<title>(.*?)</title>", club, flags=re.S)
            name = _text(title.group(1)).split("|")[0].split("｜")[-1].strip() if title else slug
            if not tm:
                continue
            body = fetch(f"{WFL_SITE}/modules/php/FlPlayer.php?team={tm.group(1)}", {"Referer": club_url})
        except Exception as e:
            print(f"[WARN] あの選手は今: {slug} の名簿の取得に失敗 {e!r}")
            continue
        for tr in re.findall(r"<tr\b[^>]*>(.*?)</tr>", body, flags=re.S | re.I):
            tds = [_text(x) for x in re.findall(r"<td\b[^>]*>(.*?)</td>", tr, flags=re.S | re.I)]
            if len(tds) < 3:
                continue
            # 列は ポジション・背番号・氏名・ローマ字・生年月日・出身 の順
            out[_key(tds[2])] = {"club": name, "number": tds[1], "name": tds[2], "url": club_url}
    print(f"[あの選手は今] 女子Fリーグ: {len(slugs)}クラブ・{len(out)}人の名簿")
    return out


def _photo_key(name: str) -> str:
    """写真さがし用に名前をそろえる(standings.py の _photo_name_key と同じ)"""
    s = re.sub(r"[\s　・･.]+", "", htmllib.unescape(name or ""))
    for a, b in (("髙", "高"), ("﨑", "崎"), ("濵", "浜"), ("邉", "辺"), ("邊", "辺"), ("齋", "斎"), ("齊", "斉")):
        s = s.replace(a, b)
    return s


def build(fetch, old, ruck_scorers=None, current_ruck_names=(), club_photos=None, national=None):
    """「あの選手は今」のデータを作る。今日すでに作っていれば None。
    ruck_scorers: 女子Fリーグの得点ランキングの行(名前・チーム・得点・写真)。current_ruck_names: 今の丸岡RUCKの選手の名前。
    club_photos: 女子Fリーグの各クラブ公式サイトの選手の写真 {クラブ名: {名前のキー: URL}}(今いるクラブの写真に使う)。
    national: 日本代表・代表候補の選出(history.json の RAC の national)。退団のお知らせが無い元選手を拾うのに使う"""
    today = datetime.now(JST).date().isoformat()
    if old.get("date") == today and old.get("v") == VERSION:
        return None
    players = [p for p in (old.get("players") or [])]
    try:
        deps = ruck_departures(fetch)
        rosters = wfl_rosters(fetch)
        goals = {_key(r.get("name", "")): r for r in (ruck_scorers or [])}
        now_ruck = {_key(n) for n in current_ruck_names}
        new = []
        for d in deps:
            k = _key(d["name"])
            # 今また丸岡RUCKにいる選手(レンタル移籍から戻った選手など)は入れない
            if k in now_ruck:
                continue
            r = rosters.get(k)
            g = goals.get(k)
            # 写真:退団・移籍のお知らせの写真 → 今いるクラブの公式サイトの写真 → 得点表の写真
            photo = d.get("photo", "")
            if not photo and r:
                photo = ((club_photos or {}).get(r["club"]) or {}).get(_photo_key(r["name"]), "")
            if not photo and g and r:
                photo = g.get("photo", "")
            new.append({
                # 名前は女子Fリーグの名簿の書き方(姓と名の間に空白)があればそれを使う
                "team": "RAC", "name": r["name"] if r else d["name"], "number": d["number"], "left": d["date"], "kind": d["kind"],
                "dest": d["dest"], "link": d["link"],
                # その選手の写真(退団・移籍のお知らせ、または今いるクラブの写真。分からなければ空)
                "photo": photo,
                # 今の所属(女子Fリーグのクラブにいれば、そのクラブ)
                "now": r["club"] if r else "", "now_number": r["number"] if r else "", "now_url": r["url"] if r else "",
                # 今季の得点(女子Fリーグの得点ランキングに載っていれば)
                "goals": g.get("goals") if g and r else None,
            })
        # 退団のお知らせが公式サイトに無い元選手(日本代表・代表候補に選ばれた選手のうち、今の選手名簿にいない選手)
        known = {_key(x["name"]) for x in new} | now_ruck
        for n in national or []:
            for name in n.get("players") or []:
                # 結婚などで名字が変わった選手は、今の名前で見る(今もチームにいれば元選手ではない)
                name = name_changes.current_name("RAC", name)
                k = _key(name)
                if not name or k in known:
                    continue
                known.add(k)
                r = rosters.get(k)
                g = goals.get(k)
                photo = ((n.get("photos") or {}).get(name)) or (
                    ((club_photos or {}).get(r["club"]) or {}).get(_photo_key(r["name"]), "") if r else "")
                new.append({
                    "team": "RAC", "name": r["name"] if r else name, "number": "", "left": "", "kind": "元選手",
                    "dest": "", "link": n.get("link", ""),
                    "photo": photo,
                    "now": r["club"] if r else "", "now_number": r["number"] if r else "", "now_url": r["url"] if r else "",
                    "goals": g.get("goals") if g and r else None,
                    # 公式サイトに退団のお知らせが無い(日本代表の選出のお知らせから分かった元選手)
                    "note": f"{n.get('date', '')[:4]}年に{n.get('kind', '日本代表')}に選ばれた元選手",
                })
        if new:
            players = [p for p in players if p.get("team") != "RAC"] + new
    except Exception as e:
        print(f"[WARN] あの選手は今: 丸岡RUCKの取得に失敗。前回の分を使います {e!r}")
    return {"date": today, "v": VERSION, "updated_at": datetime.now(timezone.utc).isoformat(), "players": players}
