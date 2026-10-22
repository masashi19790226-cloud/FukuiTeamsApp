"""
過去の試合の記録(アプリの「過去の記録」・一面の「○年前の今日」・次の相手との通算の対戦成績で使う)。
build_previews.py から1日1回だけ呼び、data/history.json に書き出す。

- ユナイテッド  : 公式サイトの試合一覧(試合情報ページが読み込んでいるデータ。2019年〜今季の全公式戦)。
                  日付・大会・相手・会場・HOME/AWAY・スコア・PK戦・観客数・得点者。毎回まとめて読み直す(1回の読み込み)
- ブルーサンダー: リーグH公式の星取表(シーズンごと。2024-25〜今季)の福井永平寺の行 → 試合ごとのページ。
                  終わった試合のページは変わらないので、一度読んだ試合は前回の分(history.json)を使う。
                  1回に読む試合のページは THUNDER_MAX_PAGES まで(残りは次の実行で読む)
- 丸岡RUCK・ブローウィンズは、まだ入れていない(女子Fリーグ公式の過去の記録の読み取り・Bリーグ公式のメンテナンス明けに加える)

読めなかったチームは前回の分を残す。
"""

import re
from datetime import datetime, timedelta, timezone

import leagueh

JST = timezone(timedelta(hours=9))

# データの形の版。変えたときに上げると、その日のうちに作り直す
VERSION = 1

UNITED_SITE = "https://fukuiunited.co.jp"
UNITED_ASYNC = UNITED_SITE + "/system/async/async.php"

THUNDER_MAX_PAGES = 30


def _int(v):
    try:
        return int(str(v).strip())
    except (TypeError, ValueError):
        return None


# ---------- ユナイテッド ----------

def united_matches(post_json):
    """ユナイテッド公式サイトの試合一覧から、終わった試合をすべて(古い順)"""
    pt = post_json(UNITED_ASYNC, {"className": "PostTypes", "method": "get", "slug": ["match"], "post_type_options": True})
    ptid = pt["data"][0]["post_type_id"]
    res = post_json(UNITED_ASYNC, {"className": "MatchInfo", "method": "get", "post_type_id": [ptid], "category": "",
                                   "orderby": [{"column": "kickoff", "order": "asc"}], "offset": 0, "limit": 500})
    out = []
    for m in res.get("data", []) or []:
        if ((m.get("match_status") or {}).get("slug") or "") != "over":
            continue
        mk = re.match(r"(\d{4})/(\d{1,2})/(\d{1,2})\s+(\d{1,2}:\d{2})", m.get("kickoff") or "")
        score = m.get("score") or {}
        my, opp = _int(score.get("proponent")), _int(score.get("opponent"))
        opponent = ((m.get("opponent") or {}).get("name") or "").strip()
        if not mk or my is None or opp is None or not opponent:
            continue
        place = m.get("place") or {}
        ha = (place.get("home_away") or "").upper()
        pk = ""
        if str((m.get("pk") or {}).get("is_pk")) == "1":
            p = (score.get("times") or {}).get("pk") or {}
            pm, po = _int(p.get("proponent")), _int(p.get("opponent"))
            if pm is not None and po is not None:
                pk = f"{pm}-{po}"
        # 得点者(同じ選手の得点はまとめる。得点の順)
        goals = {}
        for g in m.get("goal_proponent") or []:
            name = (g.get("player") or "").strip()
            if name:
                goals[name] = goals.get(name, 0) + 1
        season = re.match(r"(\d{4})", ((m.get("season") or {}).get("name") or ""))
        out.append({
            "date": f"{int(mk.group(1)):04d}-{int(mk.group(2)):02d}-{int(mk.group(3)):02d}",
            "time": mk.group(4),
            "season": season.group(1) if season else mk.group(1),
            "comp": ((m.get("competition") or {}).get("name") or "").strip(),
            "title": (m.get("title") or "").strip(),
            "opp": opponent,
            "venue": (place.get("name") or "").strip(),
            # H = ホーム・A = アウェイ・N = どちらでもない(県選手権・天皇杯などの会場)
            "ha": "H" if ha == "HOME" else "A" if ha == "AWAY" else "N",
            "my": my,
            "their": opp,
            "pk": pk,
            "visitors": _int(m.get("visitors")),
            "scorers": [{"name": k, "goals": v} for k, v in goals.items()],
            "url": f"{UNITED_SITE}/match/result.php?id={m.get('id')}",
        })
    out.sort(key=lambda x: (x["date"], x["time"]))
    print(f"[過去の記録] ユナイテッド: {len(out)}試合")
    return out


# ---------- ブルーサンダー ----------

def thunder_matches(fetch, old_matches):
    """リーグH公式の星取表(全シーズン)から福井永平寺の終わった試合。返り値は (試合のリスト, 読み残しがあるか)"""
    by_code = {m.get("code"): m for m in old_matches or [] if m.get("code")}
    top = fetch(leagueh.MATRIX_URL)
    seasons = sorted({int(s) for s in re.findall(r"<option\b[^>]*value=[\"']\./\?s=(\d{4})[\"']", top)})
    if not seasons:
        raise ValueError("星取表のシーズンの一覧が読めません")
    out, pending, read = [], False, 0
    for s in seasons:
        page = fetch(f"{leagueh.MATRIX_URL}?s={s}")
        links = leagueh.matrix_games(page)
        label = f"{s}-{(s + 1) % 100:02d}"
        for link in links:
            code = link["code"]
            if code in by_code:
                m = dict(by_code[code])
                # 星取表の点数が直っていれば合わせる
                m["my"], m["their"] = link["my"], link["opp"]
                out.append(m)
                continue
            if read >= THUNDER_MAX_PAGES:
                pending = True
                continue
            read += 1
            try:
                g = leagueh.parse_game(fetch(link["url"]))
            except Exception as e:
                print(f"[WARN] 過去の記録: ブルーサンダー {code} の取得に失敗 {e!r}")
                pending = True
                continue
            if not g:
                print(f"[WARN] 過去の記録: ブルーサンダー {code} の試合ページを読み取れませんでした")
                continue
            top3 = sorted([p for p in g.get("players") or [] if p.get("goals")], key=lambda p: -p["goals"])[:3]
            out.append({
                "code": code,
                "date": g["date"],
                "time": g.get("time", ""),
                "season": label,
                "comp": "リーグH",
                "title": f"リーグH {label}",
                "opp": g["opponent"],
                "venue": g.get("venue", ""),
                "ha": "H" if g["is_home"] else "A",
                "my": link["my"],
                "their": link["opp"],
                "pk": "",
                "visitors": None,
                "scorers": [{"name": p["name"], "goals": p["goals"]} for p in top3],
                "url": link["url"],
            })
    out.sort(key=lambda x: (x["date"], x.get("time", "")))
    print(f"[過去の記録] ブルーサンダー: {len(seasons)}シーズン・{len(out)}試合(今回読んだ試合ページ{read}件"
          + ("・残りは次回" if pending else "") + ")")
    return out, pending


def build(fetch, post_json, old):
    """過去の記録のデータを作る。old は前回の分(無ければ {})。今日すでに作り終えていれば None(書き直さない)"""
    today = datetime.now(JST).date().isoformat()
    if old.get("date") == today and old.get("v") == VERSION and not old.get("pending"):
        return None
    teams = dict(old.get("teams") or {}) if old.get("v") == VERSION else {}
    pending = False
    try:
        got = united_matches(post_json)
        if got:
            teams["UNITED"] = {"source": "ユナイテッド公式サイトの試合情報", "source_url": f"{UNITED_SITE}/match/", "matches": got}
    except Exception as e:
        print(f"[WARN] 過去の記録: ユナイテッドの取得に失敗。前回の分を使います {e!r}")
    try:
        got, pend = thunder_matches(fetch, (teams.get("THUNDER") or {}).get("matches"))
        pending = pending or pend
        if got:
            teams["THUNDER"] = {"source": "リーグH公式の星取表・試合ページ", "source_url": leagueh.MATRIX_URL, "matches": got}
    except Exception as e:
        print(f"[WARN] 過去の記録: ブルーサンダーの取得に失敗。前回の分を使います {e!r}")
        pending = True
    return {"date": today, "v": VERSION, "pending": pending,
            "updated_at": datetime.now(timezone.utc).isoformat(), "teams": teams}
