package com.fukuiteams.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.sp
import com.fukuiteams.app.data.PhotoRef
import com.fukuiteams.app.ui.theme.Ink
import com.fukuiteams.app.ui.theme.InkSoft
import com.fukuiteams.app.ui.theme.White

/**
 * 候補の写真を順に試して、読めた1枚を出す(読めなければ次の候補へ。どれも読めなければ何も出さない)。
 * ニュースの画像が無い・読めないときに、そのチームの選手の写真を代わりに出すのに使う。
 */
/** 選手の写真に名前をどう添えるか(記事の画像には付けない) */
enum class PhotoNameLabel {
    /** 付けない */
    NONE,
    /** 写真の下のほうに重ねる */
    OVERLAY,
    /** 写真の左に並べる(小さい写真のとき) */
    BESIDE
}

@Composable
fun FallbackPhoto(
    candidates: List<PhotoRef>,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    /** 選手の写真のとき、名前を添えるか(一面では必ず添える) */
    nameLabel: PhotoNameLabel = PhotoNameLabel.NONE
) {
    val list = remember(candidates) { candidates.filter { it.url.isNotBlank() }.distinctBy { it.url } }
    var index by remember(list) { mutableStateOf(0) }
    val current = list.getOrNull(index) ?: return
    val name = current.label.takeIf { it.isNotBlank() && nameLabel != PhotoNameLabel.NONE }
    when {
        name == null -> FallbackThumb(current, width, height, modifier) { index++ }
        nameLabel == PhotoNameLabel.OVERLAY -> Box(modifier = modifier.size(width, height)) {
            FallbackThumb(current, width, height, Modifier) { index++ }
            Text(
                name,
                fontSize = 8.sp,
                lineHeight = 10.sp,
                color = White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Ink.copy(alpha = 0.6f))
                    .padding(horizontal = 1.dp)
            )
        }
        else -> Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(name, style = MaterialTheme.typography.labelSmall, color = InkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
            FallbackThumb(current, width, height, Modifier) { index++ }
        }
    }
}

/** FallbackPhoto の写真1枚 */
@Composable
private fun FallbackThumb(current: PhotoRef, width: Dp, height: Dp, modifier: Modifier, onFailed: () -> Unit) {
    RemoteThumbnail(
        current.url,
        width = width,
        height = height,
        modifier = modifier,
        // 選手の写真(名前あり)は顔が切れないよう上をそろえ、記事の画像(名前なし)は中央を出す
        alignTop = current.label.isNotBlank(),
        zoomCaption = current.label.ifBlank { null },
        onFailed = onFailed
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
                    PhotoLabel(p.label)
                }
            }
        }
    }
}

/** 「#10 名前」の形のラベルを「背番号」と「名前」に分ける(背番号が無ければ null と全体) */
private val NUMBER_LABEL = Regex("""^(#\S+)\s+(.+)$""")

/**
 * 写真の下の名前。選手の写真は1行目に背番号、2行目に名前を1行で出し、
 * 名前が長いときは文字を小さくして収める(それでも入らなければ末尾を…で省略)。
 * 背番号の無いもの(記事の写真の見出しなど)は従来どおり2行まで
 */
@Composable
private fun PhotoLabel(label: String) {
    if (label.isBlank()) return
    val m = NUMBER_LABEL.matchEntire(label.trim())
    if (m == null) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = InkSoft,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        return
    }
    Text(
        m.groupValues[1],
        style = MaterialTheme.typography.labelSmall,
        color = InkSoft,
        maxLines = 1,
        textAlign = TextAlign.Center
    )
    val base = MaterialTheme.typography.labelSmall
    val baseSize = if (base.fontSize.isSp) base.fontSize.value else 11f
    var size by remember(label) { mutableStateOf(baseSize) }
    var done by remember(label) { mutableStateOf(false) }
    Text(
        m.groupValues[2],
        style = base.copy(fontSize = size.sp, lineHeight = (size + 2f).sp),
        color = InkSoft,
        maxLines = 1,
        softWrap = false,
        overflow = if (done) TextOverflow.Ellipsis else TextOverflow.Clip,
        textAlign = TextAlign.Center,
        onTextLayout = { r ->
            if (!done) {
                if (r.hasVisualOverflow && size > 8f) size -= 1f else done = true
            }
        }
    )
}
