# Java `Stream.toList()` の変更可能性デバッグラボ

このプロジェクトは、**Java 16以降の `Stream.toList()` が返すリストに要素を追加すると `UnsupportedOperationException` になる**挙動を、失敗テスト、診断出力、最小修正、回帰テストで追える教材です。対象はJava 21です。

## 前提環境

| 項目 | 固定条件 |
|---|---|
| JDK | OpenJDK 21（`javac --release 21`） |
| 外部依存 | なし |
| 実行環境 | Linux / macOS / WSL のBash |

## 題材

有効な通知先をストリームで抽出した後、必須の監査宛先を追加する処理を扱います。見た目には `List<String>` であっても、`Stream.toList()` の戻り値は変更不可です。そのため `add` は失敗します。

| Gitコミット | 状態 | 実行結果 |
|---|---|---|
| `63edf61` | `Stream.toList()` の戻り値へ `add` する不具合状態 | `UnsupportedOperationException` で失敗 |
| 現在のHEAD | `Collectors.toCollection(ArrayList::new)` で可変の蓄積先を明示 | 2つの回帰シナリオが成功 |

## 実行方法

修正済みの全テストは、次のコマンドで実行します。

```bash
./scripts/test.sh
```

現在の期待出力は次のとおりです。

```text
before add: java.util.ArrayList
before add: java.util.ArrayList
PASS: 2 recipient-planning scenarios
```

不具合状態を確認するには、初期コミットを別作業ツリーでチェックアウトして同じコマンドを実行します。

```bash
git worktree add ../java-stream-tolist-mutability-bug 63edf61
cd ../java-stream-tolist-mutability-bug
./scripts/test.sh
```

不具合状態では、次の例外が再現します。

```text
before add: java.util.ImmutableCollections$ListN
Exception in thread "main" java.lang.UnsupportedOperationException
```

## 診断方法

空の入力と有効宛先を1件含む入力を比較する診断は、次のコマンドで実行します。

```bash
./scripts/diagnose.sh
```

不具合状態では、入力件数によらず同じ `UnsupportedOperationException` が観測されます。したがって原因は「有効宛先が0件であること」ではなく、後段で変更するリストを `Stream.toList()` で作ったことです。

## プロジェクト構成

```text
src/main/java/jp/example/recipients/RecipientPlanner.java
src/test/java/jp/example/recipients/RecipientPlannerTest.java
src/test/java/jp/example/recipients/RecipientPlannerDiagnostic.java
scripts/test.sh
scripts/diagnose.sh
docs/
```

## 修正の意図

修正は `Collectors.toList()` への単純な書き換えではありません。同APIは戻り値の変更可能性を保証しないため、可変の `ArrayList` を要求する契約を `Collectors.toCollection(ArrayList::new)` で明示しています。詳細な調査経緯と公式仕様の引用は記事下書きに記載しています。
