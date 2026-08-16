# 調査記録：`Stream.toList()` の結果へ保険行を追加できない理由

## 症状

発送可能な注文行を抽出したあと、配送保険が指定されていれば `SHIPPING-INSURANCE` 行を追加する。業務上は `BOOK` と保険行の2件を返すべきだが、不具合状態では `add` 時に `UnsupportedOperationException` が発生した。

## 観測結果

不具合状態で `mvn test` を実行した結果を [`../evidence/01-broken-test-output.txt`](../evidence/01-broken-test-output.txt) に保存している。例外発生前の配送行は正しく、フィルタ条件や追加するドメイン値が原因ではない。

| 観測項目 | 結果 |
|---|---|
| フィルタ後の要素 | `ShipmentLine[sku=BOOK, quantity=1, shippable=true]` の1件 |
| `Stream.toList()` の実行時クラス（JDK 21観測値） | `java.util.ImmutableCollections$ListN` |
| `List.add` の結果 | `java.lang.UnsupportedOperationException` |
| 失敗箇所 | `ShipmentPlanner.buildShipmentLines` の保険行追加 |

実行時クラス名は観測結果であり、修正理由ではない。`Stream.toList()` の実装型は保証されないため、原因は公式仕様の「変更不可」という契約に置く。

## 競合仮説の比較

| 仮説 | 予測 | 最小実験 | 結果 | 判定 |
|---|---|---|---|---|
| フィルタ条件が誤っている | `BOOK` が追加前に存在しない | 追加前の内容を出力し、保険なしのケースをテストする | `BOOK` だけが正しく残る | 棄却 |
| 保険行のドメイン値が不正 | 明示的な可変リストに同じ保険行を追加しても失敗する | `ArrayList` を明示した修正後に同じ値を追加する | 正常に追加できる | 棄却 |
| `Stream.toList()` の戻り値が変更不可 | 要素が正しくても `add` 時に例外となる | `toList()` の結果へ直接 `add` するテストを実行する | `UnsupportedOperationException` が発生する | 採用 |

## 原因

`Stream.toList()` は変更不可の `List` を返し、変更メソッドは `UnsupportedOperationException` を送出する。[1] したがって、`toList()` の直後に `add`、`remove`、`sort` などの構造変更を行う設計は、入力値に関係なく契約違反となる。

`Collectors.toList()` へ置換することは根本修正ではない。公式 API は、`Collectors.toList()` の戻り値の型・変更可能性・直列化可能性・スレッド安全性を保証しないと明記している。より強い制御が必要な場合は `toCollection(Supplier)` を使う。[2]

## 最小修正と回帰確認

後続の保険追加には構造変更可能な作業リストが必要である。そのため、次のように `ArrayList` を供給元として明示した。

```java
List<ShipmentLine> shipmentLines = requestedLines.stream()
        .filter(ShipmentLine::shippable)
        .collect(Collectors.toCollection(ArrayList::new));
```

修正後の `mvn test` は 3 テストすべてに成功した。成功出力は [`../evidence/02-fixed-test-output.txt`](../evidence/02-fixed-test-output.txt) に保存している。

## 参考資料

[1]: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/Stream.html "Stream — Java SE 21"
[2]: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/Collectors.html "Collectors — Java SE 21"
