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
import androidx.compose.ui.unit.dp
import com.fukuiteams.app.data.APP_AUTHOR
import com.fukuiteams.app.ui.components.DoubleRule
import com.fukuiteams.app.ui.components.Headline
import com.fukuiteams.app.ui.components.MastheadTopBar
import com.fukuiteams.app.ui.components.SectionLabel
import com.fukuiteams.app.ui.theme.Ink
import com.fukuiteams.app.ui.theme.InkSoft
import com.fukuiteams.app.ui.theme.Paper

private data class HowToSection(val label: String, val title: String, val lines: List<String>)

/**
 * 使い方の本文。アプリに実際にある機能だけを書く。
 * 機能を追加・変更したときは、ここも合わせて直すこと。
 */
private val HOW_TO_SECTIONS = listOf(
    HowToSection(
        "はじめに", "画面の切り替え方",
        listOf(
            "画面の一番下にあるボタンで、「一面」「試合」「選手」「トピック」「通知」の5つの画面を切り替えます。",
            "画面の一番上の帯には、現在の日時と、データの最終更新日時(GitHubの自動更新が最後に動いた時刻)を「9/30(水) 22:45」の同じ形で1行に表示します。最終更新が3時間より前のときは赤字になります(自動更新が止まっている可能性があります)。",
            "多くの画面で、上のチーム名のボタン(ブローウィンズ・丸岡RUCK・ユナイテッド)を押すと、そのチームの情報だけに絞り込めます。「すべて」を押すと3チームまとめて表示します。このボタンは画面の上に固定されていて、下にスクロールしても常に表示されます。",
            "一面・試合・選手・トピックの画面は、画面を下に引っ張るか、右上の更新ボタンで最新の情報を読み込み直します。"
        )
    ),
    HowToSection(
        "一面", "ホーム(一面)",
        listOf(
            "試合の当日は、一番上に「本日の試合」が出ます。開始時刻・HOME/AWAY・会場・試合時間ごろの天気が分かり、会場名を押すと地図が開きます。ブローウィンズのホームゲームの日は、公式の「試合情報」ページから読み取った開場時刻・当日のスケジュール・イベントも表示し、公式ページへのリンクも付けます。",
            "「速報」には、いちばん新しい試合の結果が載ります。試合画面でコメントを書いておくと、その最初の一文が大見出しになり、記事の本文にもコメントが入ります。",
            "「次の試合」では、チーム名の右に試合まで「あと○日」(前日は「あと1日(明日)」、当日は「きょう試合」)を赤い札で表示します。日時・対戦相手・会場のほか、データがあれば「データで見る展望」(順位・成績・直近の勝敗・前回対戦)と「相手の注目選手」を表示します。",
            "「各チームの近況」「今後の日程」「公式サイト」「ニュース」も一面で確認できます。試合を押すと、試合画面でその試合の詳細が開きます。",
            "通知設定は、画面の一番下の「通知」から開けます。"
        )
    ),
    HowToSection(
        "特集", "特別な日(コラボ企画など)",
        listOf(
            "コラボ企画など特別な日が45日以内に近づくと、一面に「★ SPECIAL DAY」の特集枠が出ます。開催の10日前からは「あと○日」のカウントダウンも付きます。その日の各チームの試合が並び、押すとその試合の詳細が開きます。",
            "その日の試合には、日程に「★コラボ」の印が付き、試合詳細にも特集枠が出ます。ウィジェットにも「★コラボ」と表示されます。",
            "公式の発表前は「公式発表前」と表示します。内容はアプリを入れ直さなくても更新されます。"
        )
    ),
    HowToSection(
        "試合", "試合情報(試合タブ)",
        listOf(
            "「今後の試合」「過去の試合」を切り替えて、選んだチームの日程を一覧できます。日付の下にHOME/AWAYを表示します。",
            "ブローウィンズのホームゲームは、試合詳細にも「試合情報(公式)」として開場時刻・当日のスケジュール・イベントと公式ページへのリンクが出ます(公式ページが公開された試合のみ)。",
            "今後の試合を押すと詳細が開き、「Googleカレンダーに追加」「無料招待の情報を見る」(トピックの「招待」が開きます)「チケットを購入する(公式サイト)」が使えます。",
            "「譲渡・招待チケットを探す」では、Xの投稿とSNS広告(Meta広告ライブラリ)をチーム名で検索できます。取引は各サービス上で行われ、アプリは内容を保証しません。"
        )
    ),
    HowToSection(
        "試合詳細", "持ち物チェック",
        listOf(
            "今後の試合(試合当日を含む)の詳細に「持ち物チェック」があります。見出しを押すと一覧が開きます(試合当日は最初から開いています)。",
            "ブローウィンズの試合は、バスケ観戦用の持ち物(必需品・応援・観戦グッズ・飲み物・食べ物・身の回り・あそび)が分類ごとに並びます。ほかのチームはチケット・財布・タオルなどの基本の持ち物で、ユナイテッドの試合(屋外)には雨具が加わります。",
            "チェックは試合ごとに保存され、アプリを閉じても消えません。",
            "「すべてチェック」「すべて解除」は、いま開いている試合だけに効きます。",
            "一覧を開いて「編集」を押すと、持ち物を追加・削除できます。変更はそのチームの全試合に反映されます。最初からある持ち物を消しても、「元に戻す」で戻せます。"
        )
    ),
    HowToSection(
        "試合結果", "試合結果と成績",
        listOf(
            "過去の試合の右端に「○90-88」のような結果が出ます。公式サイトから自動で取得したもので、押すと取得元のページが開きます。まだ結果が取れていない試合は「結果待ち」と表示します。",
            "過去の試合を押すと、すぐ下に記録欄が開きます。観戦方法(現地・配信・見ていない)と、自動取得の結果がまだ無い試合の勝敗を記録できます。記録はこの端末だけに保存されます。",
            "「観戦成績」では、現地観戦・それ以外・全体に分けた勝敗と勝率を表示します。行を押すと、その区分の試合一覧が開きます。",
            "過去の試合の記録欄と試合詳細に「コメント(観戦メモ)」を書けます。コメントは一面の「速報」の記事と、SNS投稿の文章に使われます。",
            "「SNSに投稿」の「Xに投稿」「Instagram」「その他」で、試合の日付・HOME/AWAY・スコア・勝敗とコメント、チームのハッシュタグ(#福井ブローウィンズ・#福井丸岡RUCK・#福井ユナイテッド)の文章(写真があれば1枚目)を投稿できます。Instagramは写真が必要で、文章はコピーされるので投稿画面で貼り付けてください。",
            "「ホーム・アウェイ別成績」では、全体・HOME・AWAYごとの勝敗と勝率(勝利数÷試合数×100)を表示します。結果が分からない試合は数えません。"
        )
    ),
    HowToSection(
        "写真", "観戦の写真",
        listOf(
            "過去の試合を押して開く記録欄の「写真追加」から、スマホの写真を登録できます(1回で最大10枚)。",
            "登録した写真は、その試合の詳細に写真説明付きで載ります。写真を押すと拡大、長押しで削除できます。",
            "写真はアプリの中に保存されます。アプリを削除(アンインストール)すると写真も消えます。スマホのギャラリーにある元の写真は消えません。"
        )
    ),
    HowToSection(
        "トピック", "トピック(ニュース・無料招待)",
        listOf(
            "ニュースと無料招待の情報を1つの画面にまとめて表示します。最初は3チームすべての情報が出ます。上のボタンで1チームに絞り込めます。記事ごとに、どのチームの情報かを一番上の左に表示します。",
            "「招待」「ニュース」「チケット」「イベント」「その他」のボタンで絞り込めます。分類は見出しと本文の抜粋に含まれる言葉から自動で決めています。「招待」は試合の観戦チケットが無料でもらえる情報(無料招待・ご招待・観戦チケットプレゼントなど)だけで、来場者プレゼントや入会特典は含みません。「チケット」は販売・先行抽選など、「イベント」はファン感謝祭・観戦会・キャンペーン・体験会など、「その他」は放送・グッズ・募集など、どれにも当てはまらないものが「ニュース」です。一面のニュース欄に出る記事は、分類にかかわらず、すべて「ニュース」でも見られます。",
            "「チケット」では、公式X(旧Twitter)の投稿を検索するボタンも出ます。Xの投稿はXの仕組み上アプリで自動取得できないため、ボタンからXで確認してください。",
            "2日以内の情報には NEW が付きます。記事ごとに引用元(福井新聞・ブローウィンズ公式など)を枠付きで表示し、押すと元の記事がブラウザで開きます。",
            "見出しの下に、情報の最終更新日時(GitHubの自動更新が最後に動いた時刻)を表示します。3時間以上止まっているときは赤字で知らせます。"
        )
    ),
    HowToSection(
        "招待", "無料招待(トピックの「招待」)",
        listOf(
            "無料招待は、トピックの「招待」で見ます。公式サイト・市のページ・Googleアラートから自動で集め、試合の観戦チケットが無料でもらえる情報だけを残しています。試合詳細の「無料招待の情報を見る」を押しても開きます(3チームすべて表示)。",
            "招待ごとに「応募済み」「応募不要」を押して記録できます(もう一度押すと外れます)。本文に「応募不要」「申込不要」などと書かれている招待は、自動で「応募不要」になります。記録はこの端末だけに保存されます。",
            "「受付中」と「過去の招待」を切り替えられます。書かれている日付が過ぎたもの、日付の無いものは見つけてから14日たったものを「過去の招待」に移します。半年より前の情報は表示しません。",
            "その試合向けの招待が見つかると、一面や日程の試合に「招待あり」が付きます。"
        )
    ),
    HowToSection(
        "ニュース", "ニュース",
        listOf(
            "一面の下に最新のニュースが3件並びます。「ニュースをもっと見る(トピック)」を押すと、トピックですべてのニュースを見られます。3チームの公式サイトのお知らせ(「公式」と表示)と、Googleアラートで見つけた記事を自動で集めています。",
            "ニュース欄には、データの最終更新時刻と、自動更新で取得に失敗したものがあればその内容を表示します。",
            "記事ごとに引用元(福井新聞・ブローウィンズ公式など)を枠付きで表示します。記事を押すと元のページが開きます。"
        )
    ),
    HowToSection(
        "選手", "選手の数字",
        listOf(
            "画面の一番下の「選手」から開きます(試合タブの「選手の数字を見る」からも開けます)。上に固定された「▼ チーム名」「▼ 相手:○○」を押すと、自チームの選手・次の対戦相手の選手の位置までそれぞれ移動します。",
            "ブローウィンズは、Bリーグ公式のクラブページ「選手情報」から、全選手の背番号・名前・ポジションと今季の成績(試合数・平均出場時間・平均得点・リバウンド・アシスト)を表示します。「背番号」「出場時間」「得点」「リバウンド」「アシスト」で並べ替えられ、選手を押すとシュート成功率・貢献度なども見られます。一覧の上には「○月○日 ○:○ 時点の数字」と、いつ取り直した数字かを表示します。画面を下に引っ張るか右上の更新ボタンで、最新のデータを読み込みます。次の対戦相手も、Bリーグのチームなら同じ形で全選手の成績を表示します(取れないときは各部門1位の注目選手)。",
            "取得できていない数字は「データなし」と表示します。丸岡RUCKとユナイテッドの選手の成績は、今のところ取得していません。"
        )
    ),
    HowToSection(
        "通知", "通知",
        listOf(
            "「通知」画面で、チームごと・内容ごと(無料招待の新着・ニュース・試合開始1時間前)に通知を受け取るか選べます。スマホの設定でこのアプリの通知がオフになっているときは、通知画面の上に赤枠のお知らせが出ます(押すとスマホの通知設定が開きます)。",
            "新しい無料招待やニュースは、およそ1時間おきに確認して通知します。試合開始前の通知は、開始時刻の1時間前に届きます(開始時刻が決まっている試合のみ)。",
            "通知を押すと、無料招待はトピックの「招待」、ニュースはトピック、試合開始前の通知はその試合の詳細が開きます。",
            "アプリを入れた直後・入れ直した直後は、それまでの情報をまとめて通知しないようにしています(次に見つかった新着から通知します)。",
            "通知画面の下から「アプリの使い方」「更新履歴」を開けます。"
        )
    ),
    HowToSection(
        "ウィジェット", "ホーム画面のウィジェット",
        listOf(
            "スマホのホーム画面を長押しして「ウィジェット」を選び、「ふくスポ 次の試合」を置けます。",
            "3チームそれぞれの次の試合を表示します。チーム名の前の札は、HOMEが赤、AWAYが紺です。その下に日付・時刻、相手・会場が並び、その試合向けの招待があれば「招待あり」が付きます。",
            "ウィジェットを押すとアプリが開きます。表示はおよそ1時間ごとと、アプリの一面を開いたときに更新されます。"
        )
    ),
    HowToSection(
        "その他", "その他",
        listOf(
            "「更新履歴」では、これまでに追加・修正した内容と、いま入っている版の番号を確認できます。",
            "試合日程・結果・ニュース・招待は、GitHub上で1時間おきに自動更新されています。アプリはそれを読み込むので、アプリを入れ直さなくても新しい情報が反映されます。"
        )
    )
)

@Composable
fun HowToUseScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            MastheadTopBar(
                section = "アプリの使い方",
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
                    Headline("ふくスポの使い方", fontSize = 22)
                    Text(
                        "福井ブローウィンズ・福井丸岡RUCK・福井ユナイテッドの試合・結果・ニュース・無料招待をまとめて見られるアプリです。",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSoft
                    )
                    Text("制作:$APP_AUTHOR", style = MaterialTheme.typography.bodySmall, color = InkSoft)
                    DoubleRule(modifier = Modifier.padding(top = 6.dp))
                }
            }
            items(HOW_TO_SECTIONS) { section -> HowToCard(section) }
            item { Text(" ", modifier = Modifier.padding(bottom = 8.dp)) }
        }
    }
}

@Composable
private fun HowToCard(section: HowToSection) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Ink)
            .background(Paper)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SectionLabel(section.label)
        Headline(section.title, fontSize = 17)
        section.lines.forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("・", style = MaterialTheme.typography.bodyMedium, color = Ink)
                Text(line, style = MaterialTheme.typography.bodyMedium, color = Ink)
            }
        }
    }
}
