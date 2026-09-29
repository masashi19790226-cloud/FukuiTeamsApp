package com.fukuiteams.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import com.fukuiteams.app.data.HomeAwayRecord
import com.fukuiteams.app.data.HomeAwaySummary
import com.fukuiteams.app.ui.theme.Ink
import com.fukuiteams.app.ui.theme.InkSoft
import com.fukuiteams.app.ui.theme.NewsRed
import com.fukuiteams.app.ui.theme.Paper

/**
 * 「試合」タブに出す、選択中のチームのホーム・アウェイ別成績。
 * 全体・HOME・AWAY の3段で、試合数・勝敗・勝率(勝利数÷試合数×100)を並べる。
 */
@Composable
fun HomeAwayRecordCard(summary: HomeAwaySummary) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel("ホーム・アウェイ別成績")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .border(1.dp, Ink)
                .background(Paper)
                .padding(vertical = 10.dp)
        ) {
            RecordColumn(label = "全体", isHome = null, record = summary.total, modifier = Modifier.weight(1f))
            Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Ink))
            RecordColumn(label = "HOME", isHome = true, record = summary.home, modifier = Modifier.weight(1f))
            Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Ink))
            RecordColumn(label = "AWAY", isHome = false, record = summary.away, modifier = Modifier.weight(1f))
        }
        Text(
            "勝率=勝利数÷試合数×100(引き分けも試合数に含めます)。公式サイトから自動取得した結果と、自分で記録した勝敗から計算しています。",
            style = MaterialTheme.typography.bodySmall,
            color = InkSoft
        )
        if (summary.unknown > 0) {
            Text(
                "結果が分からない試合 ${summary.unknown}件は数えていません",
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft
            )
        }
    }
}

@Composable
private fun RecordColumn(label: String, isHome: Boolean?, record: HomeAwayRecord, modifier: Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        if (isHome == null) {
            Text(
                label,
                modifier = Modifier.background(Ink).padding(horizontal = 5.dp, vertical = 1.dp),
                color = Paper,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                softWrap = false
            )
        } else {
            HomeAwayTag(isHome = isHome)
        }
        if (record.games == 0) {
            Text("データなし", style = MaterialTheme.typography.bodyMedium, color = InkSoft, maxLines = 1, softWrap = false)
        } else {
            Text(
                record.recordLabel(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                softWrap = false
            )
            Text(
                "勝率 ${record.winRatePercentLabel()}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = NewsRed,
                maxLines = 1,
                softWrap = false
            )
            Text("${record.games}試合", style = MaterialTheme.typography.bodySmall, color = InkSoft, maxLines = 1, softWrap = false)
        }
    }
}
