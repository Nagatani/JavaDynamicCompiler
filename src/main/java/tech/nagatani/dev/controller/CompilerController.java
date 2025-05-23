package tech.nagatani.dev.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import tech.nagatani.dev.DynamicCompiler;
import tech.nagatani.dev.CompilationResult; 
import tech.nagatani.dev.service.InteractiveProcessManager; 
import tech.nagatani.dev.websocket.ExecutionWebSocketHandler; // WebSocketハンドラも注入されるが、現時点では直接使用されていない

import java.util.UUID;
import java.util.Arrays; // ログ出力用に追加 (前のステップで追加されたもの)
import java.nio.charset.StandardCharsets; // ログ出力用に追加 (前のステップで追加されたもの)

/**
 * Javaコードのコンパイルと実行に関するHTTPリクエストを処理するコントローラ。
 * このクラスは、ユーザーインターフェースからのリクエストを受け付け、
 * {@link DynamicCompiler} や {@link InteractiveProcessManager} などのサービスと連携して
 * コードのコンパイル、実行準備、および結果表示を行います。
 */
@Controller
public class CompilerController {

    private final DynamicCompiler dynamicCompiler; // 動的コンパイルサービス
    private final InteractiveProcessManager processManager; // 対話型プロセス管理サービス
    // ExecutionWebSocketHandlerは将来的に直接的な対話が必要な場合に備えて注入されるかもしれませんが、
    // 現状の /compile エンドポイントでは直接使用されていません。
    // プロセスの起動は ExecutionWebSocketHandler.afterConnectionEstablished で行われます。
    private final ExecutionWebSocketHandler webSocketHandler; 

    /**
     * 必要なサービス（{@link DynamicCompiler}, {@link InteractiveProcessManager}, {@link ExecutionWebSocketHandler}）を
     * 依存性注入（DI）によって初期化するコンストラクタです。
     * @param dynamicCompiler 動的コンパイルサービス。
     * @param processManager 対話型プロセス管理サービス。
     * @param webSocketHandler WebSocket実行ハンドラ。
     */
    public CompilerController(DynamicCompiler dynamicCompiler, 
                              InteractiveProcessManager processManager,
                              ExecutionWebSocketHandler webSocketHandler) {
        this.dynamicCompiler = dynamicCompiler;
        this.processManager = processManager;
        this.webSocketHandler = webSocketHandler;
    }

    /**
     * アプリケーションのルートURL ("/") へのGETリクエストを処理します。
     * 初期ページ（コード入力フォーム）である "index.html" を表示します。
     * @return 表示するビューの名前 ("index")。
     */
    @GetMapping("/")
    public String index() {
        return "index"; // "index.html" を返す
    }

    /**
     * "/compile" URLへのPOSTリクエストを処理し、提供されたJavaソースコードをコンパイルします。
     * コンパイルが成功した場合、対話型コンソールページへリダイレクトするための準備を行います。
     * 失敗した場合、結果ページにエラー情報を表示します。
     * 
     * @param sourceCode HTTPリクエストパラメータ "sourceCode" から受け取るJavaソースコード文字列。
     * @param model Spring MVCモデル。ビューにデータを渡すために使用されます。
     * @return コンパイル成功時は "interactive_console.html"、失敗時は "result.html" のビュー名。
     */
    @PostMapping("/compile")
    public String compile(@RequestParam("sourceCode") String sourceCode,
                          Model model) {
        // 受信したソースコードのログ出力（最初の100文字とUTF-8バイト表現）
        System.out.println("CompilerController RCV sourceCode (first 100 chars): " + (sourceCode != null && sourceCode.length() > 100 ? sourceCode.substring(0, 100) + "..." : sourceCode));
        if (sourceCode != null) {
            // StandardCharsets と Arrays がインポートされていることを確認
            // import java.nio.charset.StandardCharsets;
            // import java.util.Arrays;
            System.out.println("CompilerController RCV sourceCode (UTF-8 bytes from getBytes()): " + java.util.Arrays.toString(sourceCode.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }

        // 基本的な入力検証: sourceCodeがnullまたは空文字の場合
        if (sourceCode == null || sourceCode.trim().isEmpty()) {
            model.addAttribute("compilationStatus", "FAILURE"); // コンパイル状況を「失敗」としてモデルに追加
            model.addAttribute("diagnostics", "ソースコードは空にできません。"); // 診断メッセージをモデルに追加
            model.addAttribute("output", ""); // 出力は空（result.htmlがリダイレクト先の場合、この属性を期待する可能性がある）
            return "result"; // エラー情報を表示するため "result.html" へ（またはエラー表示付きの interactive_console.html）
        }

        // DynamicCompilerサービスを使用してソースコードをコンパイル（JARへのコンパイルや準備）
        // このメソッドはCompilationResultを返すことを想定（前のステップでDynamicCompilerを適合させた）
        CompilationResult compilationResult = dynamicCompiler.compileToJar(sourceCode); 

        // コンパイル成功の場合
        if (compilationResult.isSuccess()) {
            // 対話型セッションのための一意な実行IDを生成
            String executionId = UUID.randomUUID().toString();
            
            // コンパイル結果をInteractiveProcessManagerに登録。
            // これにより、後続のWebSocket接続時にExecutionWebSocketHandlerがこの情報を取得し、
            // 対応するプロセスを開始できるようになります。
            processManager.registerCompilationResult(executionId, compilationResult); 

            // モデルに属性を追加して "interactive_console.html" に渡す
            model.addAttribute("executionId", executionId); // 生成された実行ID
            model.addAttribute("compilationStatus", "SUCCESS"); // コンパイル状況「成功」
            String diagnosticsOutput = String.join("\n", compilationResult.getDiagnostics());
            // 診断メッセージが空の場合は「コンパイルの問題なし」と表示
            model.addAttribute("diagnostics", diagnosticsOutput.isEmpty() ? "コンパイルの問題はありません。" : diagnosticsOutput);
            return "interactive_console.html"; // 対話型コンソールページへ
        } else {
            // コンパイル失敗の場合
            model.addAttribute("compilationStatus", "FAILURE"); // コンパイル状況「失敗」
            String diagnosticsOutput = String.join("\n", compilationResult.getDiagnostics());
            model.addAttribute("diagnostics", diagnosticsOutput); // 診断メッセージ
            // model.addAttribute("output", ""); // コンパイル失敗時は実行出力なし
            return "result"; // 結果表示ページへ（またはエラー処理付きの interactive_console.html）
        }
    }
}
