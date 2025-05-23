package tech.nagatani.dev;

import javax.tools.*;
import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.TimeUnit; // タイムアウト用に追加
// import java.nio.charset.StandardCharsets; // UTF-8ストリーム読み取り用に追加 (前のステップで削除された)

// プロセス管理とWebSocket連携のための新しいインポート
import tech.nagatani.dev.service.InteractiveProcessManager;
import tech.nagatani.dev.websocket.ExecutionWebSocketHandler;
import org.springframework.stereotype.Component; // Springコンポーネントとして追加

/**
 * Javaソースコードの動的なコンパイルおよび実行を処理するクラス。
 * Springコンポーネントとして管理されます。
 */
@Component // Spring @Componentアノテーション追加
public class DynamicCompiler {
    private static final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    // publicクラス名（final修飾子があってもなくても）を抽出するための正規表現パターン
    // 例: "public class MyClass", "public final class MyOtherClass"
    private static final Pattern PUBLIC_CLASS_NAME_PATTERN = Pattern.compile("public\\s+(?:final\\s+)?class\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*");

    /**
     * ソースコードを文字列としてメモリ内でJavaFileObjectとして表現するためのカスタムクラス。
     * これにより、実際のファイルを作成せずにコンパイラにソースコードを渡すことができます。
     */
    static class StringSourceJavaObject extends SimpleJavaFileObject {
        private final String sourceCode; // 保持するソースコード文字列

        /**
         * 指定されたクラス名とソースコードで新しいStringSourceJavaObjectを構築します。
         * @param className ソースコード内のクラス名
         * @param sourceCode コンパイルするJavaソースコード
         */
        protected StringSourceJavaObject(String className, String sourceCode) {
            // URIはコンパイラがソースファイルを識別するために使用されます
            super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.sourceCode = sourceCode;
        }

        /**
         * ソースコードの内容を返します。
         * @param ignoreEncodingErrors エンコーディングエラーを無視するかどうか（この実装では使用されません）
         * @return ソースコードの文字シーケンス
         */
        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return sourceCode;
        }
    }

    /**
     * DynamicCompilerの新しいインスタンスを作成します。
     * システムJavaコンパイラが利用可能かどうかを確認します。
     * @throws IllegalStateException Javaコンパイラが見つからない場合
     */
    public DynamicCompiler() {
        if (compiler == null) {
            System.err.println("コンパイラが見つかりません。このアプリケーションは機能できません。");
            // Springコンテキストでより優雅に処理するか、例外をスローすることを検討
            throw new IllegalStateException("Javaコンパイラが利用できません。JDKがインストールされ、正しく設定されていることを確認してください。");
        }
    }

    /**
     * 提供されたソースコード文字列からpublicクラス名を抽出します。
     * @param sourceCode 抽出元のJavaソースコード
     * @return 抽出されたpublicクラス名。見つからない場合やソースコードが無効な場合はnull。
     */
    private String extractPublicClassName(String sourceCode) {
        // ソースコードがnullまたは空の場合は処理しない
        if (sourceCode == null || sourceCode.trim().isEmpty()) {
            return null;
        }
        // PUBLIC_CLASS_NAME_PATTERNを使用してソースコードと照合
        Matcher matcher = PUBLIC_CLASS_NAME_PATTERN.matcher(sourceCode);
        if (matcher.find()) {
            // マッチした場合、最初のキャプチャグループ（クラス名）を返す
            return matcher.group(1);
        }
        // マッチしなかった場合はnullを返す
        return null;
    }

    /**
     * 指定されたJavaソースコードをコンパイルします。
     * コンパイル結果には、成功ステータス、診断メッセージ（エラーや警告）、
     * 抽出されたクラス名、およびコンパイルされたコード（クラスファイル）へのパスが含まれます。
     *
     * @param sourceCode コンパイルするJavaソースコード文字列。
     * @return コンパイル結果を含む {@link CompilationResult} オブジェクト。
     */
    public CompilationResult compileToJar(String sourceCode) {
        // 診断情報（コンパイルエラーなど）を収集するためのコレクタ
        DiagnosticCollector<JavaFileObject> diagnosticsCollector = new DiagnosticCollector<>();
        List<String> diagnosticMessages = new ArrayList<>(); // 診断メッセージを格納するリスト
        Path tempDir = null; // コンパイルされたクラスファイル用の一時ディレクトリ
        String className = extractPublicClassName(sourceCode); // ソースコードからクラス名を抽出

        // クラス名が抽出できなかった場合、エラーとして処理
        if (className == null || className.trim().isEmpty()) {
            diagnosticMessages.add("エラー: publicクラスが見つからないか、クラス名が無効です（例: 'public class MyClass {...}'）。");
            return new CompilationResult(false, diagnosticMessages, null, null, sourceCode);
        }

        try {
            // コンパイルされたクラスファイル用の一時ディレクトリを作成
            tempDir = Files.createTempDirectory("java-compile-");

            // メモリ内のソースコードを表すJavaFileObjectを作成
            JavaFileObject sourceFile = new StringSourceJavaObject(className, sourceCode);
            // コンパイル単位のリスト（この場合は単一ファイル）
            Iterable<? extends JavaFileObject> compilationUnits = Collections.singletonList(sourceFile);
            
            // コンパイラオプション: -d はクラスファイルの出力ディレクトリを指定します
            Iterable<String> options = Arrays.asList("-d", tempDir.toString());

            // 標準ファイルマネージャを取得
            StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnosticsCollector, null, null);
            // コンパイルタスクを作成
            JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnosticsCollector, options, null, compilationUnits);
            boolean success = task.call(); // コンパイルを実行

            // 診断情報を処理してメッセージリストに追加
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnosticsCollector.getDiagnostics()) {
                diagnosticMessages.add(String.format("種類: %s, ソース: %s, 行: %d, メッセージ: %s",
                    diagnostic.getKind(),
                    diagnostic.getSource() != null ? diagnostic.getSource().getName() : "N/A",
                    diagnostic.getLineNumber(),
                    diagnostic.getMessage(null))); // nullはデフォルトロケールを使用
            }

            if (success) {
                System.out.println(className + ": コンパイル成功。出力先: " + tempDir);
                // ここではtempDirを削除しない。実行に必要です。
                // 実行が完全に終了した後にクリーンアップされるべきです。
                return new CompilationResult(true, diagnosticMessages, className, tempDir, sourceCode);
            } else {
                System.out.println(className + ": コンパイル失敗。");
                deleteTempDirectory(tempDir); // コンパイル失敗時は一時ディレクトリをクリーンアップ
                return new CompilationResult(false, diagnosticMessages, className, null, sourceCode);
            }
        } catch (IOException e) {
            diagnosticMessages.add("致命的エラー: 一時ディレクトリの作成またはファイル管理ができませんでした - " + e.getMessage());
            if (tempDir != null) {
                deleteTempDirectory(tempDir); // エラー時にもクリーンアップを試行
            }
            return new CompilationResult(false, diagnosticMessages, className, null, sourceCode);
        }
        // 注意: tempDir削除のための 'finally' ブロックはここからは削除されました。
        // クリーンアップは現在、条件的またはInteractiveProcessManagerによって処理されます。
    }

    /**
     * コンパイルされたJavaクラスを指定された実行IDでプロセスとして開始します。
     * プロセスの標準出力と標準エラー出力を読み取り、WebSocketを通じてクライアントに送信します。
     * GUIアプリケーションが疑われる場合はタイムアウト処理を適用します。
     *
     * @param compilationResult 実行するコードの {@link CompilationResult}。コンパイル成功、クラス名、およびコードパスを含む必要があります。
     * @param executionId この特定の実行を識別する一意のID。
     * @param processManager プロセスと関連リソースを管理する {@link InteractiveProcessManager}。
     * @param webSocketHandler クライアントとのWebSocket通信を処理する {@link ExecutionWebSocketHandler}。
     */
    public void startProcess(CompilationResult compilationResult, String executionId,
                             InteractiveProcessManager processManager, ExecutionWebSocketHandler webSocketHandler) {
        // コンパイルが失敗しているか、必要な情報が欠けている場合はプロセスを開始できない
        if (!compilationResult.isSuccess() || compilationResult.getCompiledCodePath() == null || compilationResult.getClassName() == null) {
            System.err.println("実行ID " + executionId + " のコンパイル失敗または詳細不足のため、プロセスを開始できません。");
            webSocketHandler.sendMessageToSession(executionId, "エラー: コンパイル失敗または詳細不足のため、プロセスを開始できません。");
            return;
        }

        String className = compilationResult.getClassName();
        Path tempDir = compilationResult.getCompiledCodePath(); // コンパイルされたクラスファイルがある一時ディレクトリ
        String sourceCode = compilationResult.getSourceCode(); // 元のソースコード（GUIチェックなどに使用）

        try {
            // 子プロセス（コンパイルされたJavaコード）を起動するためのProcessBuilderを設定
            // java -cp <一時ディレクトリ> <クラス名> を実行
            ProcessBuilder processBuilder = new ProcessBuilder(
                "java",
                // "-Dfile.encoding=UTF-8", // 子プロセスのファイルエンコーディングをUTF-8に設定 (前のステップで削除された)
                "-cp",
                tempDir.toString(),
                className
            );
            Process process = processBuilder.start(); // プロセスを開始

            // GUIチェックとタイムアウトロジック
            // ソースコードにSwingやAWTのインポートが含まれているかを確認
            boolean isSuspectedGui = sourceCode.contains("import javax.swing.*;") || sourceCode.contains("import java.awt.*;");
            if (isSuspectedGui) {
                System.out.println("実行ID " + executionId + " はGUIアプリケーションの可能性があります。");
                boolean exited = false;
                try {
                    // 10秒間のタイムアウトを設定してプロセスの終了を待つ
                    exited = process.waitFor(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // スレッドの割り込み状態を再設定
                    System.err.println("実行ID " + executionId + " のタイムアウト待機が中断されました: " + e.getMessage());
                    // プロセスはonExitまたはクリーンアップによって処理されます
                }

                // プロセスがタイムアウト期間内に終了しなかった場合
                if (!exited) {
                    webSocketHandler.sendMessageToSession(executionId, "情報: これはGUIアプリケーションまたは長時間実行されるプロセスのようです。完全なGUI/長時間プロセスのエミュレーションはまだサポートされていないため、10秒のタイムアウト後に終了しました。");
                    process.destroyForcibly(); // プロセスを強制終了
                    System.out.println("実行ID " + executionId + " のGUIアプリケーション/長時間実行プロセスがタイムアウトし、破棄されました。");
                    // 以下で開始されるI/Oスレッドはストリームが閉じられていることを見つけます。
                    // process.onExit() はそれでも発火します。
                }
            }
            
            // 標準出力を読み取るスレッド - プロセスが生存しているか、タイムアウトチェックから正常に終了した場合のみ進行
            // プロセスが破棄された場合、これらのスレッドは開始され、ストリームが閉じられていることを見つけて終了します。
            Thread outputThread = new Thread(() -> {
                // プロセスの標準出力を読み取る (デフォルトの文字セットを使用)
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        // System.out.println("サーバープロセス STDOUT読み取り: " + line); // ログ出力は前のステップで削除された
                        webSocketHandler.sendMessageToSession(executionId, line); // WebSocket経由でクライアントに送信
                    }
                } catch (IOException e) {
                    // ストリームが閉じられたことによる一般的なエラーは無視
                    if (!e.getMessage().toLowerCase().contains("stream closed")) {
                         System.err.println("実行ID " + executionId + " の出力ストリームリーダーでのIOException: " + e.getMessage());
                    }
                } finally {
                    System.out.println("実行ID " + executionId + " の出力ストリームリーダーが終了しました。");
                }
            });
            outputThread.setName("stdout-reader-" + executionId); // スレッドに名前を設定

            // 標準エラー出力を読み取るスレッド
            Thread errorThread = new Thread(() -> {
                // プロセスの標準エラー出力を読み取る (デフォルトの文字セットを使用)
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        // System.out.println("サーバープロセス STDERR読み取り: " + line); // ログ出力は前のステップで削除された
                        webSocketHandler.sendMessageToSession(executionId, "ERROR: " + line); // エラーとしてクライアントに送信
                    }
                } catch (IOException e) {
                    // ストリームが閉じられたことによる一般的なエラーは無視
                     if (!e.getMessage().toLowerCase().contains("stream closed")) {
                        System.err.println("実行ID " + executionId + " のエラーストリームリーダーでのIOException: " + e.getMessage());
                     }
                } finally {
                    System.out.println("実行ID " + executionId + " のエラーストリームリーダーが終了しました。");
                }
            });
            errorThread.setName("stderr-reader-" + executionId); // スレッドに名前を設定
            
            // プロセスとI/OスレッドをInteractiveProcessManagerに登録
            processManager.registerProcess(executionId, process, outputThread, errorThread);
            System.out.println("実行ID " + executionId + " のプロセスがクラス " + className + " で開始されました。");

            // プロセスの完了を待機し、その後クリーンアップをトリガー
            process.onExit().thenRun(() -> {
                // プロセスがまだ生存しているか確認（タイムアウトで破棄された可能性があるため）
                // ただし、onExitはどのように終了したかに関わらず発火するはずです。
                // タイムアウトした場合はメッセージが若干ずれるかもしれませんが、終了コードは情報を提供します。
                System.out.println("実行ID " + executionId + " のプロセスが終了コード " + process.exitValue() + " で終了しました。");
                // タイムアウトにより強制終了され、既にメッセージが送信されている場合は、「プログラム終了」メッセージの送信を避ける
                // このチェックは少し間接的です。より堅牢な方法は、タイムアウトが発生したかどうかを示すフラグを設定することです。
                if (!isSuspectedGui || process.exitValue() == 0) { // 単純なチェック、改良が必要な場合あり
                     webSocketHandler.sendMessageToSession(executionId, "\nプログラムが終了コード " + process.exitValue() + " で終了しました。");
                } else if (isSuspectedGui && process.exitValue() != 0 && process.exitValue() != 137 && process.exitValue() != 143) { 
                    // 137 SIGKILL、143 SIGTERM。タイムアウトで強制終了された場合、既にメッセージを送信済み。
                    // GUIが疑われ、別のエラーで終了した場合はそれを表示。
                    webSocketHandler.sendMessageToSession(executionId, "\nプログラム（GUIの可能性あり）が終了コード " + process.exitValue() + " で終了しました。");
                }
                // processManager.cleanupProcess(executionId); // クリーンアップは現在WebSocketのクローズによって開始されます
            });

        } catch (IOException e) {
            System.err.println("実行ID " + executionId + " のプロセス開始に失敗しました: " + e.getMessage());
            webSocketHandler.sendMessageToSession(executionId, "エラー: プロセスの開始に失敗しました - " + e.getMessage());
            deleteTempDirectory(tempDir); // プロセス開始失敗時に一時ディレクトリをクリーンアップ
        }
    }

    /**
     * 指定された一時ディレクトリを再帰的に削除します。
     * 主にコンパイルされたクラスファイルやその他のアーティファクトのクリーンアップに使用されます。
     * @param directory 削除するディレクトリのパス。nullの場合は何もしません。
     */
    public void deleteTempDirectory(Path directory) {
        // ディレクトリがnullの場合は何もしない
        if (directory == null) return;
        try {
            // ディレクトリ内のすべてのファイルとサブディレクトリを深さ優先で逆順に処理
            Files.walk(directory)
                .sorted(Comparator.reverseOrder()) // 逆順ソート（ファイル→ディレクトリの順で削除するため）
                .map(Path::toFile)                 // PathをFileオブジェクトに変換
                .forEach(File::delete);            // 各Fileオブジェクトを削除
            System.out.println("一時ディレクトリの削除に成功しました: " + directory);
        } catch (IOException e) {
            System.err.println("警告: 一時ディレクトリ " + directory + " の削除に失敗しました - " + e.getMessage());
        }
    }


    public static void main(String[] args) {
        // mainメソッドは、compile()シグネチャが変更されたため、テストに使用する場合は調整が必要になる場合があります。
        // 現在はアプリケーションのコア機能に必須ではないため、コメントアウトされています。
        /*
        DynamicCompiler dc = new DynamicCompiler();
        String source = "public class Test { public static void main(String[] args) { System.out.println(\"Hello\"); try { Thread.sleep(2000); } catch (InterruptedException e) {} System.err.println(\"Error\"); } }";
        CompilationResult result = dc.compileToJar(source);
        System.out.println("Compilation Success: " + result.isSuccess());
        System.out.println("Compiler Diagnostics:");
        result.getDiagnostics().forEach(System.out::println);

        if (result.isSuccess()) {
            // startProcessをテストするには、InteractiveProcessManagerとExecutionWebSocketHandlerのモック/スタブが必要です
            System.out.println("\nSimulating execution (actual process start requires more setup)...");
            System.out.println("Class Name: " + result.getClassName());
            System.out.println("Temp Dir: " + result.getCompiledCodePath());
            // dc.startProcess(result, "test-exec", mockProcessManager, mockWebSocketHandler);
            // テスト後にtempDirをクリーンアップすることを忘れないでください
            // dc.deleteTempDirectory(result.getCompiledCodePath());
        }
        */
    }
}