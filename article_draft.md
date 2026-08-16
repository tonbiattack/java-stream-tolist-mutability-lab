# Java 16+ の `Stream.toList()` のあとに `add` して落ちる理由：変更不可リストと可変作業リストの境界を最小再現する

`stream().filter(...).toList()` は、短く読みやすいコードです。しかし、既存ロジックがその直後に `add`、`remove`、`sort` などを行うと、入力値に問題がなくても `UnsupportedOperationException` で落ちます。典型例は、発送対象の明細をフィルタしたあと、配送保険の明細を追加する処理です。

本稿では、Java 21 と JUnit 5 でこの失敗を再現し、実行値と公式 API の契約から原因を切り分けます。結論は単純です。**後続処理がリストを構造変更するなら、`Stream.toList()` の結果を作業リストにしてはいけません。可変性が必要だとコードに明示する必要があります。**

> `Stream.toList()` の仕様は、戻り値が変更不可であり、任意の変更メソッドの呼び出しが常に `UnsupportedOperationException` を送出すると定めています。[1]

## この記事で扱う問題

対象は、Java の Stream API を使うバックエンド処理、バッチ処理、DTO 変換、業務ロジックです。前提は JDK 21、Maven、JUnit Jupiter 5.11.4 とします。外部サービス、現在時刻、乱数、既定ロケールには依存せず、すべてのデータをコード内の固定値で構築します。

再現する契約は次のとおりです。注文行のうち発送可能なものだけを残し、保険指定がある場合は `SHIPPING-INSURANCE` 行を加えて返します。`BOOK` と発送不可の `CANCELLED-PEN` を受け取った場合、保険指定があれば `BOOK` と保険行の2件が返るべきです。

## 既存題材との差分

題材の重複を避けるため、既存コンテンツを本文検索しました。`Stream.toList` と `UnsupportedOperationException` の一致はなく、`Collectors.toList` は関数型書き換えガイドやDocker移行資料の使用例としてのみ確認できました。今回扱う「`Stream.toList()` の変更不可契約と、後続で可変リストを要求する業務ロジックの不一致」を、失敗テスト・実行時観測・修正の中心にする記事はありませんでした。

また、既存の `BigDecimal` 比較、`ZonedDateTime` の同一瞬間比較、独自値オブジェクトの `equals` / `hashCode` とは異なります。本稿の問題は値の等価性ではなく、**同じ `List` 型に見える戻り値でも、構造変更可能性は同じではない**という API 契約の問題です。

## 期待していた挙動と実際の挙動

不具合状態の実装は、発送可能な行を `toList()` で集め、その変数に保険行を追加します。

```java
List<ShipmentLine> shipmentLines = requestedLines.stream()
        .filter(ShipmentLine::shippable)
        .toList();

if (insuranceRequested) {
    shipmentLines.add(ShipmentLine.insurance());
}
```

呼び出し側からは、次のような結果を期待します。

| 入力 | 保険指定 | 期待する結果 | 不具合状態の結果 |
|---|---:|---|---|
| `BOOK`（発送可）、`CANCELLED-PEN`（発送不可） | `true` | `BOOK` と保険行の2件 | `add` 時に `UnsupportedOperationException` |
| 同じ入力 | `false` | `BOOK` の1件 | 正常終了 |

失敗前に観測した値は次のとおりでした。

```text
beforeAdd: class=java.util.ImmutableCollections$ListN,
lines=[ShipmentLine[sku=BOOK, quantity=1, shippable=true]],
insuranceRequested=true
```

ここで `BOOK` が正しくフィルタされていることが分かります。問題はフィルタ条件でも、保険行の内容でもありません。なお、`ImmutableCollections$ListN` というクラス名は JDK 21 での観測値にすぎず、根本原因の説明に使ってはいけません。`Stream.toList()` がどの具象クラスを返すかは仕様で保証されていないためです。[1]

## 最小再現プロジェクト

プロジェクトは [`/home/ubuntu/java-stream-tolist-mutability-lab`](.) にあります。主要な構成は次のとおりです。

```text
src/main/java/jp/tonbiattack/debuglab/ShipmentLine.java
src/main/java/jp/tonbiattack/debuglab/ShipmentPlanner.java
src/test/java/jp/tonbiattack/debuglab/ShipmentPlannerTest.java
docs/investigation.md
evidence/01-broken-test-output.txt
evidence/02-fixed-test-output.txt
```

不具合を含むコミットは `cf5aedd` です。次のコマンドで、利用者視点の失敗を再現できます。

```bash
git checkout cf5aedd
mvn test
```

失敗を固定するテストは、内部実装ではなく返却すべき配送行を表します。

```java
List<ShipmentLine> actual = planner.buildShipmentLines(requestedLines, true);

assertEquals(2, actual.size(), "発送対象の本体行と保険行を返すべき");
assertEquals("BOOK", actual.getFirst().sku());
assertEquals("SHIPPING-INSURANCE", actual.get(1).sku());
```

実行すると、3テスト中2件は成功し、保険行を追加するこの1件だけが `UnsupportedOperationException` でエラーになります。完全な出力は [`evidence/01-broken-test-output.txt`](evidence/01-broken-test-output.txt) に保存しています。

## 調査：何を観測し、どの仮説を除外したか

`add` が失敗するだけでは、入力の不備、ドメイン値の不備、コレクション契約の不一致を区別できません。そこで、予測可能な小さな実験に分けました。

| 仮説 | 予測 | 最小実験 | 結果 | 判定 |
|---|---|---|---|---|
| フィルタ条件が誤っている | `BOOK` が追加前に存在しない | 追加前のリストを出力し、保険なしケースをテストする | `BOOK` のみ正しく残った | 棄却 |
| 保険行の値が不正 | 可変リストでも同じ要素を追加できない | 明示した `ArrayList` に同じ保険行を追加する | 正常に追加できた | 棄却 |
| `Stream.toList()` の戻り値が変更不可 | 要素が正しくても `add` で例外になる | `toList()` の結果へ直接 `add` するテストを実行する | `UnsupportedOperationException` が発生した | 採用 |

直接確認用のテストは次のとおりです。

```java
List<String> codes = List.of("BOOK", "PEN").stream().toList();

assertThrows(UnsupportedOperationException.class,
        () -> codes.add("ERASER"));
```

これは偶然の JDK 実装差ではありません。`Stream.toList()` は Java 16 で追加された終端操作で、戻り値は変更不可として仕様化されています。[1] [3]

## 修正：なぜこの変更で直るのか

後続の保険追加は、リストのサイズを変える**構造変更**です。したがって、必要なのは「たまたま現在は変更できるかもしれない `List`」ではなく、`ArrayList` のような変更可能な実装を明示して作ることです。

```java
List<ShipmentLine> shipmentLines = requestedLines.stream()
        .filter(ShipmentLine::shippable)
        .collect(Collectors.toCollection(ArrayList::new));
```

`Collectors.toCollection(ArrayList::new)` は、どのコレクションを作るかを供給元で指定します。公式の `Stream.toList()` も、返却オブジェクトをより詳細に制御する必要がある場合は `Collectors.toCollection(Supplier)` を使うよう案内しています。[1]

一見すると、次の置換でも現在の環境では動きそうです。

```java
.collect(Collectors.toList())
```

しかし、これは根本修正ではありません。`Collectors.toList()` の仕様は、戻り値の型、変更可能性、直列化可能性、スレッド安全性について保証しません。制御が必要なら `toCollection(Supplier)` を使うよう明記されています。[2] つまり、可変性がこのメソッドの前提なら、`Collectors.toList()` に期待を置くのではなく `ArrayList::new` をコードへ表現します。

| 選択肢 | 適する条件 | 注意点 |
|---|---|---|
| `stream.toList()` | 取得後に内容を変更しない | 変更メソッドは例外になる。 |
| `collect(Collectors.toList())` | 戻り値の具体的な性質を必要としない | 可変性は仕様で保証されない。 |
| `collect(Collectors.toCollection(ArrayList::new))` | 後続で追加・削除・並べ替えを行う | 可変な作業リストであることを明示できる。 |
| `collect(Collectors.toUnmodifiableList())` | 不変な結果を API 境界で返したい | `null` を許容しない点も意識する。 [2] |

なお、メソッドの公開契約が「読み取り専用の結果を返す」であれば、内部の可変作業リストを組み立てたあとで `List.copyOf(...)` などにより不変化して返す設計もあります。重要なのは、**変更する境界では可変性を明示し、共有する境界では必要に応じて不変性を明示する**ことです。

## 回帰テスト

修正後も、最初に失敗したテストを残しています。追加で、`Stream.toList()` 自体が構造変更不可である対照ケースと、保険追加が不要な通常ケースを残しました。

| テスト | 固定する契約 |
|---|---|
| `requestedInsurance_isAddedAfterNonShippableLinesAreFiltered` | 発送可能な本体行に保険行を追加し、2件返す。 |
| `streamToList_resultIsNotStructurallyModifiable` | `Stream.toList()` の変更不可契約を直接確認する。 |
| `nonShippableLines_areRemovedWhenNoPostProcessingIsNeeded` | 保険が不要な場合、発送不可行だけを除外する。 |

`mvn clean test` の結果は、3テスト成功、失敗0、エラー0でした。成功出力は [`evidence/02-fixed-test-output.txt`](evidence/02-fixed-test-output.txt) にあります。修正済みコミットは `f09e775` です。

## まとめ

判断規則は3つです。

1. `Stream.toList()` は「Listを返す」だけでなく、変更不可の List を返す契約です。
2. 後続で構造変更するなら、可変リストが必要であることを `toCollection(ArrayList::new)` のように明示します。
3. `Collectors.toList()` は可変性を保証しないため、偶然の実装に依存して置き換えてはいけません。

## 参考資料

[1]: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/Stream.html "Stream — Java SE 21"
[2]: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/Collectors.html "Collectors — Java SE 21"
[3]: https://bugs.openjdk.org/browse/JDK-8180352 "JDK-8180352: Add Stream.toList() method"
