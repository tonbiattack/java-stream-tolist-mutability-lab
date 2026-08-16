# Stream.toList の後に `add` すると失敗する最小再現

このプロジェクトは、`Stream.toList()` で作った配送行リストに、後続処理で配送保険の行を `add` しようとして `UnsupportedOperationException` になる不具合を再現し、最小修正を示す Java 21 のデバッグ教材である。

フィルタ処理の結果や追加する `ShipmentLine` の値は正しい。それでも `Stream.toList()` の戻り値は変更不可であるため、構造変更を要求する後続処理との契約が食い違う。

## 前提

| 項目 | 固定値 |
|---|---|
| JDK | 21 |
| Maven | 3.8 以上 |
| テスト | JUnit Jupiter 5.11.4 |

外部サービス、現在時刻、乱数、システム既定ロケールには依存しない。

## 現在の修正済み状態を検証する

次のコマンドを実行する。

```bash
mvn clean test
```

全3テストが成功する。成功出力は [`evidence/02-fixed-test-output.txt`](evidence/02-fixed-test-output.txt) に保存している。

| 観測 | 修正後の結果 |
|---|---|
| 作業リストのクラス（JDK 21観測値） | `java.util.ArrayList` |
| 保険行の追加 | 正常終了 |
| 戻り値 | `BOOK` と `SHIPPING-INSURANCE` の2件 |
| 全テスト | 3件成功 |

## 不具合状態を再現する

不具合を含む初期コミットへ切り替え、テストを実行する。

```bash
git checkout cf5aedd
mvn test
```

`ShipmentPlannerTest#requestedInsurance_isAddedAfterNonShippableLinesAreFiltered` が `UnsupportedOperationException` で失敗する。失敗出力は [`evidence/01-broken-test-output.txt`](evidence/01-broken-test-output.txt) に保存している。確認後は次のコマンドで戻す。

```bash
git switch main
```

## 原因と最小修正

`Stream.toList()` は変更不可のリストを返し、変更メソッドは常に `UnsupportedOperationException` を送出する。[1] そのため、次の実装は保険行の追加時に失敗する。

```java
List<ShipmentLine> shipmentLines = requestedLines.stream()
        .filter(ShipmentLine::shippable)
        .toList();
shipmentLines.add(ShipmentLine.insurance());
```

修正後は、後続処理が必要とする**構造変更可能な作業リスト**を明示する。

```java
List<ShipmentLine> shipmentLines = requestedLines.stream()
        .filter(ShipmentLine::shippable)
        .collect(Collectors.toCollection(ArrayList::new));
```

`Collectors.toList()` への置換だけでは不十分である。公式 API は戻り値の変更可能性を保証していないため、可変性が契約なら `toCollection(ArrayList::new)` のように供給元を明示する。[2]

## テストが固定する契約

| テスト | 固定する振る舞い |
|---|---|
| `requestedInsurance_isAddedAfterNonShippableLinesAreFiltered` | 発送対象だけを残した上で、保険行を追加して2件返す。 |
| `streamToList_resultIsNotStructurallyModifiable` | `Stream.toList()` の結果への構造変更が例外になる。 |
| `nonShippableLines_areRemovedWhenNoPostProcessingIsNeeded` | 保険が不要なときは発送不可行だけを除外する。 |

## 構成

```text
src/main/java/jp/tonbiattack/debuglab/ShipmentLine.java
src/main/java/jp/tonbiattack/debuglab/ShipmentPlanner.java
src/test/java/jp/tonbiattack/debuglab/ShipmentPlannerTest.java
docs/investigation.md
evidence/01-broken-test-output.txt
evidence/02-fixed-test-output.txt
```

## 参考資料

[1]: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/Stream.html "Stream — Java SE 21"
[2]: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/Collectors.html "Collectors — Java SE 21"
