"""
結婚などで名字が変わった選手(前の名前 → 今の名前)。チームごとに、同じ選手として数えるために使う。
公式の名簿・得点ランキングは、その時点の名前で載っているため、名前だけでは別の選手に見えてしまう。
背番号や時期で同じ選手と分かったものを、手で書き足す(2026年10月)。
  - 丸岡RUCK 北川夏奈(〜2022-23) → 宮本夏奈(2023-24〜)。どちらも背番号9・日本代表(候補)
  - 丸岡RUCK 北嶋笑(〜2021-22)   → 林笑(2023-24〜)
"""

import re

NAME_CHANGES = {
    "RAC": {
        "北川夏奈": "宮本 夏奈",
        "北嶋笑": "林 笑",
    },
}


def _key(name: str) -> str:
    return re.sub(r"[\s　・]+", "", name or "")


def current_name(team: str, name: str) -> str:
    """前の名前なら今の名前(空白入り)、そうでなければそのまま"""
    return NAME_CHANGES.get(team, {}).get(_key(name), name)


def former_names(team: str, name: str):
    """今の名前に対する前の名前の一覧(空白なし)"""
    k = _key(name)
    return [old for old, new in NAME_CHANGES.get(team, {}).items() if _key(new) == k]
