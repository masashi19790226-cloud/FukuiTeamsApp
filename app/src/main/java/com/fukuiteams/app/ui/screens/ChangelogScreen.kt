package com.fukuiteams.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fukuiteams.app.data.APP_AUTHOR
import com.fukuiteams.app.data.CHANGELOG
import com.fukuiteams.app.data.ChangelogEntry
import com.fukuiteams.app.data.appVersionName
import com.fukuiteams.app.ui.components.DoubleRule
import com.fukuiteams.app.ui.components.Headline
import com.fukuiteams.app.ui.components.MastheadTopBar
import com.fukuiteams.app.ui.components.SectionLabel
import com.fukuiteams.app.ui.theme.Ink
import com.fukuiteams.app.ui.theme.InkSoft
import com.fukuiteams.app.ui.theme.Paper

@Composable
fun ChangelogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            MastheadTopBar(
                section = "更新履歴",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(modifier = Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Headline("ふくスポのあゆみ", fontSize = 22)
                    Text(
                        "いま入っている版:v${appVersionName(context)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSoft
                    )
                    Text(
                        "制作:$APP_AUTHOR",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSoft
                    )
                    DoubleRule(modifier = Modifier.padding(top = 6.dp))
                }
            }
            items(CHANGELOG) { entry -> ChangelogCard(entry) }
            item { Text(" ", modifier = Modifier.padding(bottom = 8.dp)) }
        }
    }
}

@Composable
private fun ChangelogCard(entry: ChangelogEntry) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Ink)
            .background(Paper)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SectionLabel(entry.date)
        Headline(entry.title, fontSize = 17)
        entry.items.forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("・", style = MaterialTheme.typography.bodyMedium, color = Ink)
                Text(line, style = MaterialTheme.typography.bodyMedium, color = Ink)
            }
        }
    }
}
