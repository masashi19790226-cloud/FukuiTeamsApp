# 福井チーム情報アプリ(FukuiTeamsApp)

福井ブローウィンズ・福井丸岡ラック・福井ユナイテッドの最新情報・無料招待・チケット情報を
まとめる Android アプリのスターター実装です。Kotlin + Jetpack Compose (Material 3) で、
ワイヤーフレームの画面構成をそのままコード化しています。

## 現在できること

- ホーム / 試合詳細 / 無料招待(募集中・アーカイブ) / 通知設定の4画面をComposeで実装
- 下部ナビゲーションでの画面遷移
- チーム別の色分け(ロイヤルブルー系・鮮やかな青・赤オレンジ系)とイニシャルバッジ表示
- `data/MockData.kt` のダミーデータで画面確認が可能(バックエンド未接続)
- 通知設定のON/OFFはこの端末内のメモリ上のみで保持(再起動で初期化される、DataStore等での永続化は未実装)

## 開発環境での開き方

1. [Android Studio](https://developer.android.com/studio)(Koala以降推奨)をインストール
2. `File > Open` でこのフォルダ(`FukuiTeamsApp`)を選択
3. Gradle Wrapper が同梱されていないため、初回は Android Studio の指示に従って
   `Sync Project with Gradle Files` を実行してください(Wrapperの自動生成を提案されます)。
   手動で作る場合はターミナルで以下を実行:
   ```
   gradle wrapper --gradle-version 8.7
   ```
4. エミュレータまたは実機を接続し、Runボタンで起動

## GitHubで動かす(Android Studio不要)

このプロジェクトには `.github/workflows/android-build.yml` を同梱しています。
GitHubにpushするだけで、GitHub側のサーバーがビルドしてAPKを作ってくれます。

### 手順

1. GitHub上で新しいリポジトリを作成する(Public/Privateどちらでも可)
2. このフォルダの中身をそのリポジトリにpushする
   ```
   cd FukuiTeamsApp
   git init
   git add .
   git commit -m "initial commit"
   git branch -M main
   git remote add origin https://github.com/<あなたのユーザー名>/<リポジトリ名>.git
   git push -u origin main
   ```
3. GitHubのリポジトリページ上部の「Actions」タブを開く
   → 「Build Debug APK」というワークフローが自動的に実行される(数分かかります)
4. 実行が完了(緑のチェック)したら、そのワークフローの実行結果ページ下部
   「Artifacts」から `fukui-teams-app-debug` をダウンロード(zip形式)
5. zipを展開すると `app-debug.apk` が入っているので、それをAndroidスマホに転送してインストール
   (スマホ側で「提供元不明のアプリ」のインストールを一時的に許可する必要があります)

### コードを直す→確認する、のサイクル

- コードの編集は、PC上の軽いエディタ(VS Code等)でも、GitHubのWebエディタでもOK
- 変更を `git push` するたびに、Actionsが自動でビルドし直してAPKを作り直してくれます
- 毎回数分待つ点はAndroid Studioでのリアルタイムプレビューより不便ですが、
  PC側の負荷はほぼゼロで済みます



仕様書(企画・仕様書 v0.2)に沿って、以下を段階的に実装していく想定です。

1. **データ層の実装**:`MockData` を、ニュース収集・試合情報・招待検知バックエンドAPIを叩く
   Repository 実装に置き換える(Retrofit + kotlinx.serialization 等を想定)
2. **無料招待の監視ロジック**:公式SNSポーリング・X検索API・Meta広告ライブラリAPI経由で
   検知したデータをサーバー側でInvitationEventとして蓄積し、アプリはそれを取得するだけの形にする
3. **通知の永続化・実際のプッシュ通知**:DataStoreでON/OFF設定を保存し、
   Firebase Cloud Messaging 等でサーバーからのプッシュを受け取る
4. **チームエンブレム画像の差し替え**:現在は頭文字(B/R/U)の仮バッジ。各クラブから
   使用許諾を得た正式なロゴ画像に置き換える(`TeamBadge` コンポーネントを画像表示に変更)
5. **フォント**:現在はシステムフォント。Noto Sans JP 等を `res/font` に追加して統一感を出す
6. **非公式チケット検索リンク**:試合詳細画面の「Xで探す/メルカリ/ジモティー」ボタンに、
   チーム名・対戦カード・日付から組み立てた検索URLを実際に割り当てる(Intent で外部ブラウザ起動)

## ディレクトリ構成

```
app/src/main/java/com/fukuiteams/app/
  MainActivity.kt
  navigation/NavGraph.kt          ... 画面遷移の定義
  model/Models.kt                 ... Team / Game / NewsItem / InvitationEvent
  data/MockData.kt                ... 開発用ダミーデータ
  ui/theme/                       ... 配色・タイポグラフィ
  ui/components/                  ... TeamBadge, 下部ナビ等の共通部品
  ui/screens/                     ... 画面ごとのComposable
```
