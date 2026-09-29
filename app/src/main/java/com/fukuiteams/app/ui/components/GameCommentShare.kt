package com.fukuiteams.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.datastore.preferences.core.Preferences
import com.fukuiteams.app.data.GameOutcome
import com.fukuiteams.app.data.GamePhotos
import com.fukuiteams.app.data.RemoteGameResult
import com.fukuiteams.app.data.gameLogDataStore
import com.fukuiteams.app.data.recordedComment
import com.fukuiteams.app.data.saveGameComment
import com.fukuiteams.app.data.shareText
import com.fukuiteams.app.model.Game
import com.fukuiteams.app.ui.theme.InkSoft
import kotlinx.coroutines.launch
import java.io.File
import java.net.URLEncoder

/**
 * 過去の試合のコメント(観戦メモ)欄。保存したコメントは一面「速報」の記事と、SNS投稿の文章に使われる。
 */
@Composable
fun GameCommentEditor(game: Game) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs by context.gameLogDataStore.data.collectAsState<Preferences, Preferences?>(initial = null)
    val saved = recordedComment(game.id, prefs)
    // 保存済みのコメントが読み込まれたら、入力欄にも入れる
    var text by remember(game.id, saved) { mutableStateOf(saved) }
    val changed = text.trim() != saved

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("コメント(観戦メモ)", style = MaterialTheme.typography.bodySmall, color = InkSoft)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(300) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 5,
            placeholder = { Text("例:最後の3ポイントで会場が揺れた!") }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        saveGameComment(context, game.id, text)
                        Toast.makeText(context, "コメントを保存しました", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = changed,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) { Text(if (changed) "保存" else "保存済み", fontWeight = FontWeight.Bold) }
            Text(
                "一面の速報記事とSNS投稿の文章に使われます",
                style = MaterialTheme.typography.labelSmall,
                color = InkSoft,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * X・Instagram への投稿ボタン。試合結果とコメントから文章を作り、写真があれば1枚目を添える。
 * ・X:アプリが入っていれば写真付きで開く。無ければブラウザの投稿画面を文章入りで開く
 * ・Instagram:写真が必要。文章はコピーしておくので、投稿画面で貼り付ける
 * ・その他:スマホの共有メニュー(LINE など)
 */
@Composable
fun GameShareCard(game: Game, result: RemoteGameResult?, outcome: GameOutcome?) {
    val context = LocalContext.current
    val prefs by context.gameLogDataStore.data.collectAsState<Preferences, Preferences?>(initial = null)
    val comment = recordedComment(game.id, prefs)
    val photos = remember(game.id, GamePhotos.version) { GamePhotos.list(context, game.id) }
    val text = shareText(game, result, outcome, comment)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("SNSに投稿", style = MaterialTheme.typography.bodySmall, color = InkSoft)
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth()
        )
        if (photos.isNotEmpty()) {
            Text("写真の1枚目を添えます", style = MaterialTheme.typography.labelSmall, color = InkSoft)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            ShareButton("Xに投稿", Modifier.weight(1f)) { shareToX(context, text, photos.firstOrNull()) }
            ShareButton("Instagram", Modifier.weight(1f)) { shareToInstagram(context, text, photos.firstOrNull()) }
            ShareButton("その他", Modifier.weight(0.8f)) { shareToOthers(context, text, photos.firstOrNull()) }
        }
    }
}

@Composable
private fun ShareButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
    ) { Text(label, maxLines = 1, softWrap = false, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
}

/** アプリ内の写真を、他のアプリへ渡せる形(content://)にする。 */
private fun photoUri(context: Context, photo: File): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photo)

private fun sendIntent(context: Context, text: String, photo: File?): Intent =
    Intent(Intent.ACTION_SEND).apply {
        putExtra(Intent.EXTRA_TEXT, text)
        if (photo != null) {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, photoUri(context, photo))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            type = "text/plain"
        }
    }

private fun shareToX(context: Context, text: String, photo: File?) {
    try {
        // Xのアプリがあれば、写真と文章を入れた状態で投稿画面を開く
        context.startActivity(sendIntent(context, text, photo).setPackage("com.twitter.android"))
    } catch (e: Exception) {
        // アプリが無ければブラウザの投稿画面(写真は付けられない)
        try {
            val url = "https://x.com/intent/post?text=" + URLEncoder.encode(text, "UTF-8")
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e2: Exception) {
            Toast.makeText(context, "Xを開けませんでした", Toast.LENGTH_SHORT).show()
        }
    }
}

private fun shareToInstagram(context: Context, text: String, photo: File?) {
    if (photo == null) {
        Toast.makeText(context, "Instagramへの投稿には写真が必要です。先に写真を追加してください", Toast.LENGTH_LONG).show()
        return
    }
    // Instagram は文章を受け取らないので、コピーしておいて投稿画面で貼り付けてもらう
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("ふくスポ", text))
    try {
        context.startActivity(sendIntent(context, text, photo).setPackage("com.instagram.android"))
        Toast.makeText(context, "文章をコピーしました。投稿画面で貼り付けてください", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Instagramのアプリが見つかりませんでした", Toast.LENGTH_SHORT).show()
    }
}

private fun shareToOthers(context: Context, text: String, photo: File?) {
    try {
        context.startActivity(Intent.createChooser(sendIntent(context, text, photo), "共有先を選ぶ"))
    } catch (e: Exception) {
        Toast.makeText(context, "共有できませんでした", Toast.LENGTH_SHORT).show()
    }
}
