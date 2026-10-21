package com.fukuiteams.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fukuiteams.app.data.PhotoRef
import com.fukuiteams.app.ui.theme.InkSoft

/**
 * 候補の写真を順に試して、読めた1枚を出す(読めなければ次の候補へ。どれも読めなければ何も出さない)。
 * ニュースの画像が無い・読めないときに、そのチームの選手の写真を代わりに出すのに使う。
 */
@Composable
fun FallbackPhoto(
    candidates: List<PhotoRef>,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier
) {
    val list = remember(candidates) { candidates.filter { it.url.isNotBlank() }.distinctBy { it.url } }
    var index by remember(list) { mutableStateOf(0) }
    val current = list.getOrNull(index) ?: return
    RemoteThumbnail(
        current.url,
        width = width,
        height = height,
        modifier = modifier,
        // 選手の写真(名前あり)は顔が切れないよう上をそろえ、記事の画像(名前なし)は中央を出す
        alignTop = current.label.isNotBlank(),
        zoomCaption = current.label.ifBlank { null },
        onFailed = { index++ }
    )
}

/**
 * 選手の写真を横に並べる(名前は写真の下に小さく)。読めなかった写真は飛ばして、次の選手の写真を出す。
 * title があれば上に小見出しを出す。写真が1枚も無いときは何も出さない
 */
@Composable
fun PlayerPhotoRow(
    photos: List<PhotoRef>,
    count: Int = 4,
    title: String? = null,
    photoWidth: Dp = 60.dp,
    photoHeight: Dp = 75.dp
) {
    if (photos.isEmpty()) return
    val failed = remember(photos) { mutableStateListOf<String>() }
    val shown = photos.filter { it.url !in failed }.take(count)
    if (shown.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (title != null) SectionLabel(title)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            shown.forEach { p ->
                Column(
                    modifier = Modifier.width(photoWidth),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    RemoteThumbnail(
                        p.url,
                        width = photoWidth,
                        height = photoHeight,
                        alignTop = true,
                        zoomCaption = p.label,
                        onFailed = { if (p.url !in failed) failed.add(p.url) }
                    )
                    Text(
                        p.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = InkSoft,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
