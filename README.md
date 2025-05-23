# Java ダイナミックコンパイラ Web UI

## 説明 (Description)
これは、ユーザーフレンドリーなウェブインターフェースを通じてJavaコードスニペットを動的にコンパイルおよび実行するために設計されたシンプルなウェブアプリケーションです。ユーザーはJavaコードを迅速にテストおよび検証することができます。

**重要な注意**: このアプリケーションは、開発、テスト、および教育目的のみを対象としています。任意のコードを実行することに伴う固有のセキュリティリスクのため、**本番環境での使用には適していません**。

## 特徴 (Features)
-   コード入力用のウェブベースインターフェース
-   ユーザーが提供したJavaソースコードの動的コンパイル
-   コンパイルされたJavaコードの実行
-   コンパイル状況、診断メッセージ（エラー/警告）、およびプログラム出力の表示

## 実行方法 (How to Run)
このアプリケーションをローカルで実行するには、以下が必要です：
-   Java 21 JDK (またはそれ以降)
-   Apache Maven

以下の手順に従ってください：
1.  **リポジトリをクローンする**:
    ```bash
    git clone <repository-url>
    cd <repository-directory>
    ```
2.  **Mavenを使用してアプリケーションを実行する**:
    ```bash
    mvn spring-boot:run
    ```
    または、JARファイルをビルドしてから実行することもできます：
    ```bash
    mvn package
    java -jar target/JavaCompiler-1.0-SNAPSHOT.jar
    ```
    （注意：JARファイル名は `pom.xml` 内の `artifactId` および `version` によって異なる場合があります。）
3.  **アプリケーションにアクセスする**:
    ウェブブラウザを開き、`http://localhost:8080/` にアクセスしてください。

## 使用方法 (How to Use)
1.  アプリケーションが実行されると、Javaコードを入力するためのフォームが表示されます：
    *   **.java ファイルをアップロードする場合**: 「ファイルを選択」ボタン（またはブラウザの文言によっては同様のファイル入力フィールド）をクリックして、お使いのコンピュータから `.java` ファイルを選択できます。このファイルの内容は、下の「ソースコード」テキストエリアに自動的に入力されます。
    *   **ソースコード**: テキストエリアにJavaソースコードを貼り付けるか、入力してください。ファイルをアップロードした場合、その内容がここに表示されます。コードには `public class YourClassName {...}` のような公開クラス宣言を含めるようにしてください。アプリケーションはこのクラス名を自動的に検出します。実行可能にしたい場合は、この公開クラス内に `public static void main(String[] args)` メソッドを含める必要があります。

2.  **「コンパイルして実行」** ボタンをクリックします。

3.  アプリケーションがコードを処理し、結果を表示します：
    *   **コンパイル状況**: コンパイルが成功したか失敗したかを示します。
    *   **コンパイラ診断**: コンパイラによって報告されたエラーや警告を表示します。
    *   **プログラム出力**: プログラムによって生成された出力（`System.out` または `System.err` からのもの）を表示します。

## 重要なセキュリティに関する考慮事項 (Important Security Considerations)
ユーザーから受け取った任意のコードを実行することは、重大なセキュリティリスクを伴います。このアプリケーションは、これらのリスクを軽減するための高度なサンドボックス化やセキュリティ対策を実装していません。
-   信頼できる環境でのみこのアプリケーションを実行してください。
-   信頼できないユーザーやネットワークに公開しないでください。
-   コンパイルおよび実行するコードには注意してください。

## 対話型コンソールのテスト (Testing Interactive Console)

1.  JavaCompilerウェブアプリケーションを実行します。
2.  ブラウザでアプリケーションにアクセスします（通常は `http://localhost:8080/`）。
3.  `src/test/java/tech/nagatani/dev/testprograms/InteractiveTest.java` の内容（以下に表示）をソースコード入力エリアにコピーします。
    ```java
    // package tech.nagatani.dev.testprograms; // READMEの例ではパッケージ宣言は任意です

    import java.util.Scanner;

    public class InteractiveTest {
        public static void main(String[] args) {
            @SuppressWarnings("resource")
            Scanner scanner = new Scanner(System.in);
            System.out.println("SERVER_MSG: Interactive Test Program Started.");
            System.out.println("SERVER_MSG: Please enter your name:");
            String name = scanner.nextLine();
            System.out.println("SERVER_MSG: Hello, " + name + "!");
            System.out.println("SERVER_MSG: Program finished.");
        }
    }
    ```
4.  「コンパイルして実行」ボタンをクリックします。
5.  対話型コンソールページに移動します。
6.  期待される出力：
    ```
    SERVER_MSG: Interactive Test Program Started.
    SERVER_MSG: Please enter your name:
    ```
7.  ページ下部の入力フィールドにあなたの名前（例： "Jules"）を入力し、Enterキーを押すか「送信」ボタンをクリックします。
8.  期待される出力（あなたの入力とプログラムの応答の後）：
    ```
    > Jules 
    SERVER_MSG: Hello, Jules!
    SERVER_MSG: Program finished.
    Program finished with exit code: 0 
    ```
    （注意： `> Jules` の部分はクライアント側のJavaScriptからのエコーであり、`Program finished with exit code: 0` はプロセス終了時にサーバーから送信されます。）
9.  その後、セッションはプログラムが終了したことを示し、入力フィールドが無効になる場合があります。

## 今後の開発：GUIサポートの強化 (Future Development: Enhanced GUI Support)

現在のシステムには、GUIベースと思われるJavaアプリケーション（例：SwingやAWTを使用）に対する基本的なタイムアウトメカニズムがあります。疑わしいGUIアプリケーションが短時間（現在は10秒）内に終了しない場合、自動的に停止されます。これは、非対話型のGUIプログラムによってサーバーリソースが無期限に保持されるのを防ぐための初歩的な方法です。

ウェブブラウザ内でのJava GUIアプリケーションの真の対話型サポートは複雑な課題です。将来の探求のためのいくつかの潜在的な道筋を以下に示します：

*   **Xvfb (X Virtual FrameBuffer):** Linux/macOSサーバーでは、Xvfbを使用してヘッドレスディスプレイ環境を作成できます。Java Swingアプリケーションはこの仮想ディスプレイで実行し、そのウィンドウを画像またはビデオストリームとしてキャプチャし、ウェブクライアントに送信することができます。ウェブクライアントからのユーザーインタラクション（クリック、キーストローク）は、X11イベントに変換する必要があります。
*   **スクリーンショット用の `java.awt.Robot`:** `java.awt.Robot` クラスは、AWT/Swingウィンドウのスクリーンショットをプログラムでキャプチャできます。これにより、クライアントに定期的な視覚的更新を提供できますが、ビデオストリームのような完全な対話型にはなりません。
*   **VNCライクなストリーミング:** より複雑な解決策として、GUIアプリケーションの表示をウェブページに埋め込まれたVNCクライアント（またはカスタムコンポーネント）にストリーミングするVNCライクなサーバーを統合または構築することが考えられます。これにより、より良い対話性が提供されます。
*   **Java UIツールキットのWebAssembly (WASM) 移植:** 非常に野心的なアプローチとして、WebAssemblyを使用してJavaおよびSwing/AWT（またはその一部）をブラウザで直接実行し、潜在的にキャンバスターゲットを使用するプロジェクトが考えられます。これは重要な研究開発分野です。
*   **特定のUIコンポーネントへの焦点:** 完全なデスクトップエミュレーションの代わりに、将来の作業では、一般的なSwingコンポーネント（ダイアログ、ボタン、テキストエリアなど）の限定されたセットをキャプチャし、HTML要素として表現し、インタラクションをサーバーに中継することに焦点を当てる可能性があります。これは部分的なエミュレーションになります。

これらのアプローチは重要な取り組みであり、相当な追加の研究、開発、およびインフラストラクチャの考慮が必要です。
```
