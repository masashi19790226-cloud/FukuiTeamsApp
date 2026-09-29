package com.fukuiteams.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fukuiteams.app.data.GamesRepository
import com.fukuiteams.app.data.SpecialDay
import com.fukuiteams.app.model.Game
import com.fukuiteams.app.ui.theme.Ink
import com.fukuiteams.app.ui.theme.InkSoft
import com.fukuiteams.app.ui.theme.Ivory
import com.fukuiteams.app.ui.theme.NewsRed
import com.fukuiteams.app.ui.theme.Paper

/**
 * 特別な日(コラボ企画など)の特集枠。二重の赤枠に、関係するチームの色の帯と「★ SPECIAL DAY」の見出し。
 * 一面ではカウントダウン付きで、試合詳細ではその試合の上に出す。
 */
@Composable
fun SpecialDayBanner(day: SpecialDay, showCountdown: Boolean = true, onOpenGame: ((Game) -> Unit)? = null) {
    // その日の、関係チームの試合
    val games = GamesRepository.games
        .filter { it.team in day.teams && it.sortKey.take(8) == day.date.replace("-", "") }
        .sortedBy { day.teams.indexOf(it.team) }
    val until = day.daysUntil()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(3.dp, NewsRed)
            .padding(3.dp)
            .border(1.dp, NewsRed)
            .background(Paper)
    ) {
        Column {
            // 関係するチームの色を並べた帯
            Row(modifier = Modifier.fillMaxWidth().height(6.dp)) {
                day.teams.forEach { team ->
                    Box(modifier = Modifier.weight(1f).height(6.dp).background(team.color))
                }
            }
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "★ SPECIAL DAY",
                        modifier = Modifier.background(NewsRed).padding(horizontal = 8.dp, vertical = 2.dp),
                        color = Ivory,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        if (day.confirmed) "公式発表あり" else "公式発表前",
                        modifier = Modifier.border(1.dp, if (day.confirmed) NewsRed else InkSoft)
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                        color = if (day.confirmed) NewsRed else InkSoft,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                Headline(day.title, fontSize = 21)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(day.dateLabel(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    if (showCountdown && until != null) {
                        Text(
                            when {
                                until == 0L -> "きょう開催!"
                                until > 0L -> "あと${until}日"
                                else -> ""
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = NewsRed
                        )
                    }
                }
                // 関係チームの試合(タップでその試合を開く)
                games.forEach { game ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (onOpenGame != null) Modifier.clickable { onOpenGame(game) } else Modifier)
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(modifier = Modifier.width(4.dp).height(30.dp).background(game.team.color))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                game.team.displayName,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = game.team.color
                            )
                            Text(
                                "${game.timeLabel}  vs ${game.opponent}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Ink,
                                maxLines = 2
                            )
                        }
                        if (onOpenGame != null) Text("›", style = MaterialTheme.typography.titleMedium, color = Ink)
                    }
                }
                if (day.note.isNotBlank()) {
                    Text(day.note, style = MaterialTheme.typography.bodySmall, color = InkSoft)
                }
            }
        }
    }
}

/** 日程の行などに付ける小さな「★コラボ」の印。 */
@Composable
fun SpecialDayBadge(modifier: Modifier = Modifier) {
    Text(
        "★コラボ",
        modifier = modifier.background(NewsRed).padding(horizontal = 5.dp, vertical = 1.dp),
        color = Ivory,
        fontSize = 10.sp,
        fontWeight = FontWeight.ExtraBold,
        maxLines = 1,
        softWrap = false
    )
}
