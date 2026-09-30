"""
無料招待(試合の観戦チケットが無料でもらえる情報)かどうかの判定。
check_alerts.py(Googleアラート)と check_official_invites.py(公式サイト・市のページ)の両方で使う。

以前は「プレゼント」「抽選で」「先着○名」などが本文にあれば招待として集めていたため、
来場者プレゼント・スクールの入会特典・冠パートナーのお知らせ・開催報告なども混ざっていた。
いまは「観戦・チケット・招待」に関わる言い方があるものだけを招待とする。
アプリ側(RadarScreen.kt の isRealInvite)も同じ考え方で分類している。
"""

import re

# 招待とみなす言い方(見出しか、本文から抜き出した文のどちらかに入っていること)
INVITE_RE = re.compile(
    r"無料招待|ご招待(?!券は不要)|招待(します|いたします|企画|キャンペーン|席|チケット)|"
    r"無料観戦|観戦無料|無料で(ご)?観戦|"
    r"(観戦|ホームゲーム|試合)?(チケット|観戦券|招待券).{0,15}(プレゼント|進呈|差し上げ|配布|お渡し|当た)|"
    r"(ペア|\d+組).{0,15}(招待|プレゼント)|\d+名(様)?.{0,15}招待"
)

# 招待の言い方が入っていても、招待情報ではないもの
NOT_INVITE_RE = re.compile(
    r"来場者プレゼント|来場プレゼント|入会|スクール|アンバサダー|会員特典|"
    r"開催しました|実施しました|終了しました|"
    r"ファンクラブ.{0,20}招待券|招待券.{0,15}(利用方法|引換|ご利用)|ご招待券は不要"
)


# 招待の受付そのものなので、言い方にかかわらず招待として扱う取得元
ALWAYS_KEEP_SOURCES = ("公式ストア", "公式特設ページ")


def clean_title(title: str) -> str:
    """市のページなどで見出しの前に付いてくる「2026年9月25日 スポーツ -->」のような部分を取り除く。"""
    t = (title or "").strip()
    t = re.sub(r"^.*?-->\s*", "", t)
    t = re.sub(r"^\d{4}年\d{1,2}月\d{1,2}日\s*", "", t)
    return t.strip()


def is_real_invite(title: str, snippet: str = "") -> bool:
    text = f"{title or ''} {snippet or ''}"
    if NOT_INVITE_RE.search(text):
        return False
    return bool(INVITE_RE.search(text))


def filter_invites(items):
    """招待データの一覧から、招待ではないものを取り除き、見出しを整える。(残したもの, 取り除いた件数) を返す。"""
    kept, removed = [], 0
    for item in items:
        item["title"] = clean_title(item.get("title", ""))
        # 公式ストアの¥0チケット・ブローウィンズの招待特設ページは、それ自体が招待の受付なので必ず残す
        if item.get("source") in ALWAYS_KEEP_SOURCES or is_real_invite(item.get("title", ""), item.get("snippet", "")):
            kept.append(item)
        else:
            removed += 1
    return kept, removed
