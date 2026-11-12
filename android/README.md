# Meltype for Android — 日英自動判別対応の試作版

Android用の仮想キーボード（IME）。日本語QWERTYでは **日英自動判別が実装済み**。既存のC# `Meltype.Core` を **NativeAOT + JNI** で共有し、Kotlinで判別ロジックを再実装していません。12キーのかな／英字フリック入力は既存のKotlinエンジンを継続使用します。

このブランチは、[ninjin0802さんのAndroid試作PR #621](https://github.com/yksr-melt/Meltype/pull/621) を土台にした [本家向け統合PR #1149](https://github.com/yksr-melt/Meltype/pull/1149) の提出ソースです。元のAndroid実装への貢献・クレジットを維持し、レビューの一本化を提案しています。正式リリースではなく、実機検証済みの開発・QA版です。

## 実装済みの主な機能

- **日英自動判別（日本語QWERTY）**: キーを手動で切り替えずに日本語・英語・混在テキストを入力。判別と未確定文字の処理はC# Coreを共有。
- **ライブ変換・候補バー**: かな漢字変換はローカルのOSS版Mozcを使用。変換候補か予測候補を表示・選択。
- **入力配列**: 日本語QWERTY、かな12キーフリック、英字QWERTY、英字12キーフリック、2ページの数字・記号配列。日本語QWERTYに `、` `。` の専用キーを残し、`!` `?` も入力可能。
- **配列切り替え**: QWERTYではスペースを長押しし、押した位置の吹き出しを上下スワイプして指を離すと日本語／英語を選択。12キーの入力方式は引き続き独立。
- **画面適応**: 画面の論理幅・高さ・アスペクト比でキー寸法と分割配列を決定。中央テンキーは幅と高さに十分な余裕がある画面だけ表示。縦横回転と折りたたみ端末に対応。
- **操作・設定**: 触覚フィードバック、絵文字パネル、候補の選択、全角・半角スペース、ライブ変換のオン／オフ。設定画面はシステムバー余白と戻る操作に対応。

## 対応範囲と未対応機能

| 対象 | 状態 |
| --- | --- |
| 日本語QWERTYでの日英自動判別 | **実装済み**。C# `Meltype.Core` を使用 |
| かな／英字12キーフリック | 実装済み。Kotlinの既存エンジンを使用 |
| フリック配列での日英自動判別 | 未対応（今回の対象外） |
| 読みから英単語を直接推薦する独立機能（`ぐーぐる` → `Google` 等） | 未対応。QWERTYの日英自動判別とは別機能 |
| ユーザー辞書・個人情報の登録と補完（個人版） | Android独自の画面・表示。NativeAOT Coreのuserdict.txtを使用 |
| 全端末・全アプリでの互換性、TalkBack対応 | 未検証／保証なし |

**再現可能なQA手順（入力・期待値・テストコード・実機チェックリスト）：[docs/QA-TESTING.md](docs/QA-TESTING.md)**。CI定義は [android-qa.yml](../.github/workflows/android-qa.yml)。

詳しくは [08 日英自動判別](docs/spec/08-auto-detection.html)、[03 構成](docs/spec/03-architecture.html)、[11 テスト](docs/spec/11-testing.html)。これらのHTML仕様書を規範とし、`docs/markdown/spec/` に対応するレビュー用Markdownを置きます。

## 個人版の設定・候補UI（Draft PR #13）

- **ユーザー辞書**と**個人情報**を設定の別項目として提供。どちらも既存の `userdict.txt` に保存される。追加・編集・削除は設定画面内で行い、標準ダイアログを挟まない。
- メールアドレスは `@` より前が1行目、ドメインを小さな2行目。住所は町名から小さな2行目。電話番号は通常サイズで1行表示。通常候補のうち3番目に個人情報補完を挿入し、Coreの候補番号は変更しない。英字・数字・電話番号の直接入力がアプリ側から指定された入力欄でも、入力中の文字や数字から補完する。電話番号の登録内容にハイフンがあっても数字列で照合する。パスワード・学習禁止などの保護された入力欄には個人情報候補を出さない。
- コピーした文字はクリップボードアイコン付き、画像は縮小サムネイル付きの**独立した中央ピル型候補**として表示。キーボードを開いてまだ入力していないとき、またはDeleteで入力欄を完全に空にしたときだけ候補を表示する。コピーから**5分以内**に限定し、**1回貼り付けた候補は再表示しない**（同じ内容を再コピーすれば新しい候補になる）。この制限は自動候補だけで、クリップボード履歴の保存期間は変更しない。入力欄が画像の直接貼り付けに対応する場合のみ、画像ピルから直接貼り付ける。
- スクリーンショットの自動取り込みは既定でオフ。**設定 → 設定 → クリップボード → 最近のスクリーンショット** から明示的に許可すると、直近のスクリーンショットをMediaStore経由で候補に取り込む。Android 14以降の「一部の写真だけ許可」では、新しいスクショが見えない場合がある。写真ライブラリ全体への読み取り権限を必要とすることに注意。
- 取り込まれた画像は既存クリップボード履歴と同じ端末内ストレージに保存され、設定した保存期間（既定30日）後に削除される。バックアップや会話へのアップロードは行わない。
- 画面はMeltype共通の色・余白・操作部品を使用。システムバー・キーボードのinsetsと折りたたみレイアウトに対応する。
- これらは個人Fork用のUIであり、本家PR #1149のCore/Mozc実装には入れない。

## プライバシーと制約

- 個人版のリンクプレビューや翻訳モデル取得にはINTERNET権限を使う。文字入力の判定・ユーザー辞書・スクショ候補は端末内で処理する。
- パスワード欄など、変換・学習を抑止すべき入力欄では、日英自動判別Coreを使用しない。
- 学習データはアプリ専用の領域で扱い、通常のログに入力内容を含めない。
- 触覚フィードバック用に `VIBRATE` 権限を要求する（「権限が一つもない」わけではない）。

## 対応環境と検証

- 最低Android 8.0（API 26）、Android用ABIは `arm64-v8a`・`x86_64`。
- **本家向け統合コミット `965dbf3`** で Android 15（API 35）のInstrumentation **18/18件合格**、JVM単体 **444/444件合格**。[CI結果・各テストのJUnit XMLとログ](https://github.com/KoheiTakada/Meltypetest/actions/runs/40182600362)。Mozcを固定ソースから再ビルドする[独立したAndroid CI](https://github.com/KoheiTakada/Meltype/actions/runs/40182575249)も成功。テスト定義と再現手順は [QA-TESTING.md](docs/QA-TESTING.md) に固定。
- **Pixel 10 Pro Fold／Android 17** で2026-10-25に**統合版APK（ソース `965dbf3`）の実機確認完了との本人報告**。手動QA M01〜M08の個別ログ・実測値やスクリーンショットは未収集。再現する場合は[手動テスト手順](docs/QA-TESTING.md)を使用。
- 旧開発ソース `7a44ba5` の[過去のQA](https://github.com/KoheiTakada/Meltypetest/actions/runs/40163689812)は参考履歴。最新統合版の合否は上記 `965dbf3` のCIと実機確認に基づく。
- [2026-10-25の検証記録](docs/verification/2026-10-25-bilingual.html) が現行の日英自動判別と実機確認の証跡です。`docs/verification/2026-10-13-stage1.html` は**当時の第1段階のみの履歴記録**であり、最新QAの結果ではありません。

## ビルド

必要: JDK 17、Android SDK/NDK、.NET SDK 10、Python 3、Git、Bazelisk（Mozc用）。ABIごとのC# NativeAOT共有ライブラリを先にビルドします。

```sh
cd android
bash tools/build-mozc.sh
ANDROID_NDK_HOME="$ANDROID_HOME/ndk/28.2.13676358" bash tools/build-meltype-native.sh
./gradlew :engine:test :app:testDebugUnitTest :app:assembleDebug
```

Android APK: `android/app/build/outputs/apk/debug/app-debug.apk`。端末テストには別途Androidのテスト用APKとエミュレーター／実機が必要です。

MozcのJNI／辞書成果物やC# NativeAOTの生成物、配布署名鍵はソース管理しません。正式なアプリID・リリース署名・配布方針は本家メンテナー判断待ち。デバッグ／QA署名済みAPKを公式リリースと扱わないでください。

## リポジトリ構成

| パス | 役割 |
| --- | --- |
| `android/app/` | Android IME・画面・設定・候補バー・C JNIブリッジ |
| `android/engine/` | フリックと従来入力用のKotlinエンジン |
| `android/mozc/` | ローカルMozcのJNIと辞書 |
| `src/Meltype.Core/` | Windows等と共有するC#日英判別・未確定文字の処理 |
| `android/tools/build-meltype-native.sh` | C# NativeAOTのAndroid向けビルド |

本家の既存ライセンス（GPL-3.0-or-later）と同梱物の著作権表示を維持します。今回の実装・テスト・文書の編集ではChatGPTによるAI支援を使用しています。

## APK配布と署名

- 配布用Application ID: `app.meltype.keyboard`（ユーザー名・元実装者名は含めない）
- APK名: `Meltype.apk`
- 公開先: [GitHub Releases](https://github.com/KoheiTakada/Meltype/releases)
- 更新時の互換性: **同じApplication ID、同じ秘密署名鍵、増加するversionCode** を維持する。毎回変わるCIデバッグ鍵は配布には使わない。
- 設定・操作方法: [署名と配布手順](docs/SIGNING-DISTRIBUTION.md)。署名鍵はGitHub Secretsに登録し、Git履歴へは置かない。
- 一時検証用のデバッグAPKは `.debug` を付けた別パッケージIDで配布版と分離する。
