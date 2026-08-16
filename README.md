# Stream.toList の後に `add` すると失敗する最小再現

このプロジェクトは、`Stream.toList()` で作った配送行リストに、後続処理で配送保険の行を `add` しようとして `UnsupportedOperationException` になる不具合を再現する Java 21 の最小プロジェクトである。

フィルタ処理の結果や追加する `ShipmentLine` の値は正しい。それでも `Stream.toList()` の戻り値は変更不可であるため、構造変更を要求する後続処理との契約が食い違う。

## 前提

| 項目 | 固定値 |
|---|---|
| JDK | 21 |
| Maven | 3.8 以上 |
| テスト | JUnit Jupiter 5.11.4 |

外部サービス、現在時刻、乱数、システム既定ロケールには依存しない。

## 不具合状態の再現

次のコマンドを実行する。

```bash
mvn test
```

`ShipmentPlannerTest#requestedInsurance_isAddedAfterNonShippableLinesAreFiltered` が `UnsupportedOperationException` で失敗する。テスト出力では、次を観測できる。

| 観測 | 不具合状態の結果 |
|---|---|
| フィルタ後の配送行 | `BOOK` の1件で正しい |
| 返却リストのクラス名 | JDK実装の変更不可リスト型 |
| 保険行の追加 | `UnsupportedOperationException` |
| 期待する振る舞い | `BOOK` と `SHIPPING-INSURANCE` の2件を返す |

## 構成

```text
src/main/java/jp/tonbiattack/debuglab/ShipmentLine.java
src/main/java/jp/tonbiattack/debuglab/ShipmentPlanner.java
src/test/java/jp/tonbiattack/debuglab/ShipmentPlannerTest.java
research_notes.md
```

## 不具合状態の意図

この段階の `ShipmentPlanner` は、`stream().filter(...).toList()` の結果をそのまま変更可能な作業リストと見なし、保険行を追加する。修正はまだ適用していない。
