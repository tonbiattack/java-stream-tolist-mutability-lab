# 調査メモ：`Stream.toList()` と変更可能な後続処理の契約不一致

## 既存題材との重複調査

`tonbiattack/qiita` と `tonbiattack/private-go-task-management-api` を GitHub のコード検索で確認した。

| 検索語 | 結果 | 判断 |
|---|---|---|
| `Stream.toList` | `qiita` 内で本文一致なし | 同一題材の記事は確認できない。 |
| `UnsupportedOperationException` | `qiita`・Go API 内で本文一致なし | 変更不可リストを後続で更新する失敗条件とは重複しない。 |
| `Collectors.toList` | 既存の関数型書き換えガイドとDocker移行資料に出現 | APIの使用例またはシリアライズ文脈であり、`Stream.toList()` と可変性の契約差、失敗テスト、最小修正を中心とする題材ではない。 |

既存の `BigDecimal` 比較、`ZonedDateTime` の瞬間同値、独自値オブジェクトの `equals` / `hashCode`、`LocalDateTime` のタイムゾーン解釈とも、発火条件・原因・修正の中心が異なる。今回の固有の契約は、**Java 16以降の `Stream.toList()` が返す変更不可リストと、既存の後続処理が要求する構造変更可能な `List` の境界**である。

## 選定題材

> フィルタ済みの注文行を `stream().filter(...).toList()` で取得した後、配送保険の行を `add` する。リストの内容が正しく見えるため後続の追加も可能だと想定してしまうが、`Stream.toList()` の戻り値は変更不可であり、`UnsupportedOperationException` が発生する。

最小修正は、後続処理の契約が「リストを構造変更する」ことである点を明示し、`new ArrayList<>(stream.toList())` として可変な作業リストを1回だけ作ることとする。`Collectors.toList()` への置換は、変更可能性が公式に保証されないため、根本修正として採用しない。

## 公式資料の根拠

| 資料 | 観測した契約 | 記事での使用 |
|---|---|---|
| [Stream — Java SE 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/Stream.html) | `toList()` は変更不可のリストを返し、すべての変更メソッド呼び出しで `UnsupportedOperationException` が送出される。戻り値の実装型・直列化可能性・同一性には保証がない。より強い制御が必要なら `Collectors.toCollection(Supplier)` を使う。 | 例外の根本原因と、具体実装へ依存しない説明の根拠。 |
| [Collectors — Java SE 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/Collectors.html) | `Collectors.toList()` は、戻り値の型・変更可能性・直列化可能性・スレッド安全性を保証しない。より細かく制御するには `toCollection(Supplier)` を使う。 | `Collectors.toList()` への置換だけでは契約上の修正にならない根拠。 |
| [JDK-8180352](https://bugs.openjdk.org/browse/JDK-8180352) | `Stream.toList()` は JDK 16 で導入された。設計議論では、変更不可の結果を返すこと、共有や最適化を容易にすることが検討された。 | 導入バージョンと設計背景の補足。 |

## 公式APIからの注記

> Stream API は `toList()` について、戻り値が変更不可であり、**任意の変更メソッドが常に `UnsupportedOperationException` を送出する**と定めている。また、より強い制御が必要な場合は `Collectors.toCollection(Supplier)` の使用を案内している。[1]

`Collectors.toList()` は現在の実装で変更可能に見える場合があっても、仕様上は変更可能性を保証しない。そのため、この教材の根本修正では使用しない。

## 競合仮説の初期設計

| 仮説 | 予測 | 最小実験 | 想定判定 |
|---|---|---|---|
| A. フィルタ条件が誤っており、追加対象がない | `add` の前に期待行が欠けている | 追加前の内容を固定テストで確認する | 棄却予定 |
| B. ドメイン要素が不正で `add` に失敗する | `ArrayList` に同じ要素を追加しても失敗する | 同じ要素を明示的な可変リストへ追加する | 棄却予定 |
| C. `Stream.toList()` の戻り値が変更不可である | 入力・要素が正しくても `add` 時点で `UnsupportedOperationException` が起きる | 返却リストのクラス、追加前後、例外を観測する | 採用予定 |

## 前提バージョン

* JDK: 21
* ビルド: Maven 3.8 以上
* テスト: JUnit Jupiter 5.11.4
* 外部サービス、現在時刻、乱数、システム既定ロケールには依存しない。

## 参考URL

1. https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/Stream.html
2. https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/Collectors.html
3. https://bugs.openjdk.org/browse/JDK-8180352
