# Javaで`Stream.toList()`の結果に`add`すると落ちる理由：戻り値の変更可能性を最小再現から理解する

## この記事で扱う問題

Java 16以降では、ストリームの終端操作として`Stream.toList()`を簡潔に書けます。しかし、取得した値を後段で加工する処理にそのまま渡すと、`List`型であるにもかかわらず`add`で`UnsupportedOperationException`になることがあります。

本記事ではJava 21を対象に、「有効な通知先を抽出した後、必須の監査宛先を追加する」最小例を用いてこの問題を再現します。結論を先に述べると、`Stream.toList()`の戻り値は**変更不可**です。戻り値を後段で変更することが契約なら、必要な可変コレクションを明示して生成しなければなりません。[1]

| 前提 | 内容 |
| --- | --- |
| 対象Java | OpenJDK 21.0.11（`javac --release 21`） |
| 症状 | `recipients.add(...)`で`UnsupportedOperationException`が発生する |
| 原因 | `Stream.toList()`の戻り値を可変の`List`だと解釈したこと |
| 最小修正 | `Collectors.toCollection(ArrayList::new)`で可変の蓄積先を明示する |

## 既存題材との差分

既存のJavaデバッグ記事には、`String.length()`とUnicodeコードポイントの違い、`try-with-resources`と`finally`、JacksonのJSON契約、Spring Boot／JPA／Securityのライフサイクルを扱うものがあります。本題材はそれらと異なり、**Java標準ストリームAPIの戻り値が持つ変更可能性の契約**に限定します。

また、既存のGoタスク管理APIには部分更新、明示的な`null`、Lost Update、親リソースの存在確認を扱う記録があります。しかし今回の失敗は、HTTP境界や永続化ではなく、ストリームから得たコレクションを後段で変更するローカルな境界で起きます。調査では`Stream.toList()`、`Collectors.toList()`、`UnsupportedOperationException`、固定長・変更不可リストに関する既存記事本文を横断検索し、同一の失敗条件・原因・修正中心を持つ記事がないことを確認しました。

## 期待していた挙動と実際の挙動

呼び出し側は、有効な宛先だけを残してから監査宛先を追加したいと考えています。したがって、`alice@example.test`が有効なら、最終的な宛先は`[alice@example.test, audit@example.test]`になるべきです。

```java
List<String> actual = planner.planRecipients(List.of(
        new RecipientPlanner.Recipient("alice@example.test", true),
        new RecipientPlanner.Recipient("inactive@example.test", false)
));

assertEquals(
        List.of("alice@example.test", "audit@example.test"),
        actual,
        "有効な宛先に監査宛先を追加できるべき"
);
```

ところが、不具合状態ではアサーションへ到達する前に例外が送出されます。実際にJava 21で実行した出力は次のとおりです。

```text
before add: java.util.ImmutableCollections$ListN
Exception in thread "main" java.lang.UnsupportedOperationException
    at java.base/java.util.ImmutableCollections$AbstractImmutableCollection.add(...)
    at jp.example.recipients.RecipientPlanner.planRecipients(RecipientPlanner.java:19)
```

ここで`java.util.ImmutableCollections$ListN`という実行時型は有用な**観測事実**です。ただし、修正の根拠を特定の実装型に置くべきではありません。仕様として重要なのは、`Stream.toList()`が返すリストが変更不可であることです。[1]

## 最小再現プロジェクト

再現プロジェクトは`java-stream-tolist-mutability-lab`です。外部ライブラリに依存せず、Java 21の`javac`と`java`だけで実行できます。記事と同じ作業領域では、次の構成になっています。

```text
java-stream-tolist-mutability-lab/
├── src/main/java/jp/example/recipients/RecipientPlanner.java
├── src/test/java/jp/example/recipients/RecipientPlannerTest.java
├── src/test/java/jp/example/recipients/RecipientPlannerDiagnostic.java
├── scripts/test.sh
├── scripts/diagnose.sh
└── docs/
```

不具合は、次の実装だけで再現します。

```java
public List<String> planRecipients(List<Recipient> candidates) {
    List<String> recipients = candidates.stream()
            .filter(Recipient::active)
            .map(Recipient::email)
            .toList();

    System.out.println("before add: " + recipients.getClass().getName());
    recipients.add(AUDIT_RECIPIENT);
    return recipients;
}
```

`Stream.toList()`は、ストリーム要素を遭遇順でリストに蓄積します。公式API仕様は、返却リストが変更不可であり、変更メソッドの呼び出しは常に`UnsupportedOperationException`を送出すると明記しています。[1]

> “The returned List is unmodifiable; calls to any mutator method will always cause `UnsupportedOperationException` to be thrown.” — Java SE 21 `Stream.toList()` API [1]

不具合状態は最初のコミット`63edf61`に固定しました。別作業ツリーで再現するには、以下を実行します。

```bash
cd /home/ubuntu/language-behavior-java-lab/java-stream-tolist-mutability-lab
git worktree add ../java-stream-tolist-mutability-bug 63edf61
cd ../java-stream-tolist-mutability-bug
./scripts/test.sh
```

## 調査：何を観測し、どの仮説を除外したか

「有効な宛先が0件の場合だけ壊れる」「ストリームの絞り込み条件が誤っている」「戻り値の変更可能性を誤解している」という仮説を分けました。重要なのは、例外の存在だけで判断せず、空・非空入力、実行時型、修正後の振る舞いを別々に観測することです。

| 仮説 | 予測 | 最小実験 | 実測結果 | 判定 |
| --- | --- | --- | --- | --- |
| 有効宛先が0件だから失敗する | 有効宛先が1件なら追加できる | `active=0`と`active=1`の両方で追加する | 両方とも`UnsupportedOperationException` | 棄却 |
| フィルタ条件が誤っている | 返却リストに意図しない要素が入る | `filter(Recipient::active)`後の結果を確認する | 例外は監査宛先の追加行で発生する | 棄却 |
| `Stream.toList()`の戻り値が変更不可である | `add`が変更不可例外で失敗する | 実行時型とスタックトレースを保存する | `ImmutableCollections$ListN`と`UnsupportedOperationException`を観測 | 採用 |
| `Collectors.toList()`へ置換すれば契約上安全である | 可変性が仕様で保証される | 公式API仕様を確認する | 型・可変性などは保証されない | 棄却 |
| `ArrayList`を明示すれば追加できる | 空・非空のどちらでも完了する | `toCollection(ArrayList::new)`へ変更する | 2ケースとも成功 | 採用 |

診断プログラムの不具合状態での出力は次のとおりです。

```text
before add: java.util.ImmutableCollections$ListN
active=0: java.lang.UnsupportedOperationException
before add: java.util.ImmutableCollections$ListN
active=1: java.lang.UnsupportedOperationException
```

この結果により、入力件数やフィルタ結果ではなく、`toList()`後に`add`を行うことが失敗条件だと分かります。

## 原因：`List`という型だけでは変更可能性を表せない

`List`はインターフェースです。変数の宣言型が`List<String>`であることは、要素の順序や重複を扱う操作を表すだけで、`add`が成功することまでは約束しません。

`Stream.toList()`では、まさにこの点が仕様として定められています。Java 16から導入されたこのメソッドは、実装クラスを指定するAPIではなく、変更不可のリストを返すAPIです。[1] したがって、今回のコードはJavaの不具合やランタイムの偶発的な差異ではなく、API契約に反しています。

比較対象として`Collectors.toList()`を選びたくなるかもしれません。しかし公式API仕様は、同コレクタが返すリストについて、型・変更可能性・直列化可能性・スレッド安全性を保証しないと明記しています。変更可能なリストが業務ロジックの前提であるなら、`Collectors.toList()`に置き換えて現在の実装へ依存することも、契約を明示する修正ではありません。[2]

> “There are no guarantees on the type, mutability, serializability, or thread-safety of the `List` returned.” — Java SE 21 `Collectors.toList()` API [2]

## 修正：必要な変更可能性を生成時に明示する

今回の要件は、ストリーム処理後に監査宛先を追加することです。そこで、可変の`ArrayList`を蓄積先として明示します。

```java
import java.util.ArrayList;
import java.util.stream.Collectors;

public List<String> planRecipients(List<Recipient> candidates) {
    List<String> recipients = candidates.stream()
            .filter(Recipient::active)
            .map(Recipient::email)
            .collect(Collectors.toCollection(ArrayList::new));

    recipients.add(AUDIT_RECIPIENT);
    return recipients;
}
```

`Collectors.toCollection(Supplier)`は、指定したファクトリで作成するコレクションへ要素を蓄積します。[2] この修正では`ArrayList::new`を明記しているため、`add`を前提とするコードと生成されるコレクションの契約が一致します。

別案として、`new ArrayList<>(stream.toList())`とコピーしてから変更することもできます。すでに変更不可リストを返すメソッド境界を保ち、その後の局所処理だけで可変コピーが必要な場合には適します。一方で今回のメソッドは最初から後段で要素を追加するので、蓄積時に可変の実装を指定する方が意図を直接表せます。

反対に、返したリストを呼び出し側に変更してほしくない設計なら、`Stream.toList()`を維持する方が正しい選択です。その場合は、監査宛先の追加をリスト生成より前のストリーム操作へ移す、または可変処理をメソッド内部に閉じ込めてから変更不可リストとして返します。重要なのは「短い書き方」を選ぶことではなく、**誰がどの時点でリストを変更するか**を契約として決めることです。

## 回帰テスト

修正後も、もともと失敗していたケースを削除しません。加えて、有効な宛先が0件でも監査宛先だけを返せることを対照ケースとして残します。

| ケース | 入力 | 期待する結果 | 修正後の実行結果 |
| --- | --- | --- | --- |
| 元の失敗ケース | 有効な宛先`alice@example.test`を1件含む | `alice@example.test`と監査宛先の2件 | 成功 |
| 対照ケース | 候補はあるが、全て無効 | 監査宛先だけの1件 | 成功 |

全テストは以下のコマンドで実行します。

```bash
cd /home/ubuntu/language-behavior-java-lab/java-stream-tolist-mutability-lab
./scripts/test.sh
```

実測出力は次のとおりです。

```text
before add: java.util.ArrayList
before add: java.util.ArrayList
PASS: 2 recipient-planning scenarios
```

不具合再現と修正を分離するため、ローカルGit履歴も2コミットに分けています。

```text
128991d fix: 可変な宛先リストを明示して監査宛先を追加する
63edf61 test: Stream.toListの変更不可リストを再現する
```

## まとめ

`Stream.toList()`の戻り値は、`List`型であっても変更可能とは限りません。Java 16以降では変更不可であり、後段で`add`・`remove`・`set`を行う処理にそのまま渡すと失敗します。[1]

今回の調査で覚えるべき判断規則は三つです。第一に、コレクションの宣言型だけでは変更可能性を判断しないことです。第二に、`Collectors.toList()`の現在の振る舞いを可変性の仕様保証だとみなさないことです。[2] 第三に、変更が要件なら`toCollection(ArrayList::new)`などで必要な実装と変更可能性を明示し、元の失敗ケースと対照ケースを回帰テストに残すことです。

## References

[1] [Java SE 21 API: `Stream.toList()`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/Stream.html#toList())

[2] [Java SE 21 API: `Collectors.toList()` / `Collectors.toCollection(Supplier)`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/Collectors.html#toList())
