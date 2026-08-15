# Java `Stream.toList()` の変更可能性デバッグラボ

このプロジェクトは、**Java 16以降の `Stream.toList()` が返すリストに要素を追加すると `UnsupportedOperationException` になる**挙動を最小再現します。対象はJava 21です。

> この時点のコミットは、意図的に不具合を含みます。`scripts/test.sh` は失敗することが期待値です。

## 前提環境

| 項目 | 固定条件 |
|---|---|
| JDK | OpenJDK 21（`javac --release 21`） |
| 外部依存 | なし |
| 実行環境 | Linux / macOS / WSL のBash |

## 再現する症状

有効な宛先を `Stream.toList()` で作り、後段で必須の監査宛先を `add` する処理を実装しています。利用者の期待は、`alice@example.test` に `audit@example.test` が加わることです。しかし、返却リストは変更不可であるため、追加時に例外が発生します。

## 実行方法

```bash
./scripts/test.sh
```

不具合状態では、次のような出力になります。

```text
before add: java.util.ImmutableCollections$ListN
Exception in thread "main" java.lang.UnsupportedOperationException
```

## プロジェクト構成

```text
src/main/java/jp/example/recipients/RecipientPlanner.java
src/test/java/jp/example/recipients/RecipientPlannerTest.java
scripts/test.sh
```

## 調査の入口

`RecipientPlanner#planRecipients` の `recipients` が、なぜ `List` 型でありながら変更できないのかを、実行時の型、例外、公式API仕様から確認します。修正では、必要な契約を「`List` であること」ではなく「後段で追加できる可変リストであること」として明示します。
