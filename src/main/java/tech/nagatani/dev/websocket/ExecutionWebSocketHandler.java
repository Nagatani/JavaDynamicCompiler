package tech.nagatani.dev.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tech.nagatani.dev.CompilationResult;
import tech.nagatani.dev.DynamicCompiler;
import tech.nagatani.dev.service.InteractiveProcessManager;

import java.io.IOException;
import java.io.OutputStream;
// import java.io.OutputStreamWriter; // 前のステップで削除された
import java.net.URI;
// import java.nio.charset.StandardCharsets; // 前のステップで削除された
// import java.util.Arrays; // 前のステップで削除された
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 対話的なコード実行のためのWebSocket接続を管理するハンドラクラス。
 * Springコンポーネントとしてマークされています。
 */
@Component
public class ExecutionWebSocketHandler extends TextWebSocketHandler {

    // executionIdをキーとしてアクティブなWebSocketセッションを保持するマップ
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final InteractiveProcessManager processManager; // プロセス管理サービス
    private final DynamicCompiler dynamicCompiler; // 動的コンパイルサービス

    /**
     * 必要なサービスを注入してExecutionWebSocketHandlerを構築します。
     * @param processManager プロセス管理サービス
     * @param dynamicCompiler 動的コンパイルサービス
     */
    public ExecutionWebSocketHandler(InteractiveProcessManager processManager, DynamicCompiler dynamicCompiler) {
        this.processManager = processManager;
        this.dynamicCompiler = dynamicCompiler;
    }

    /**
     * 新しいWebSocket接続が確立された後に呼び出されます。
     * URIからexecutionIdを抽出し、セッションを登録し、関連するJavaプロセスの開始を試みます。
     * @param session 新しく確立されたWebSocketセッション
     * @throws Exception エラーが発生した場合
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        URI uri = session.getUri();
        String query = uri.getQuery(); // クエリは "id=<executionId>" であるべき
        String executionId = null;
        // クエリからexecutionIdを抽出
        if (query != null && query.startsWith("id=")) {
            executionId = query.substring(3);
        }

        // executionIdがなければエラー処理
        if (executionId == null || executionId.trim().isEmpty()) {
            System.err.println("WebSocket URIにExecutionIdがありません: " + uri);
            session.sendMessage(new TextMessage("エラー: ExecutionIdが必要です。"));
            session.close(CloseStatus.BAD_DATA.withReason("ExecutionIdが見つかりません"));
            return;
        }
        
        // セッション属性にexecutionIdを保存し、セッションをマップに登録
        session.getAttributes().put("executionId", executionId);
        sessions.put(executionId, session);
        System.out.println("WebSocket接続確立 (executionId: " + executionId + ", Session: " + session.getId() + ")");

        // 関連するコンパイル結果を取得
        CompilationResult compilationResult = processManager.getCompilationResult(executionId);
        if (compilationResult == null) {
            System.err.println("executionId: " + executionId + " のコンパイル結果が見つかりません。");
            session.sendMessage(new TextMessage("エラー: この実行のためのコンパイルデータが見つかりません。期限切れか失敗した可能性があります。"));
            session.close(CloseStatus.POLICY_VIOLATION.withReason("コンパイルデータなし"));
            return;
        }

        // コンパイルが成功していればプロセスを開始
        if (compilationResult.isSuccess()) {
            dynamicCompiler.startProcess(compilationResult, executionId, processManager, this);
        } else {
            session.sendMessage(new TextMessage("エラー: コンパイルが成功しなかったため、プロセスを開始できません。"));
            // オプション: HTTPレスポンス経由でまだ送信されていない場合、診断情報を送信
            // compilationResult.getDiagnostics().forEach(diag -> { try { session.sendMessage(new TextMessage(diag)); } catch (IOException e) {} });
            session.close(CloseStatus.POLICY_VIOLATION.withReason("コンパイル失敗"));
        }
    }

    /**
     * クライアントからテキストメッセージを受信したときに呼び出されます。
     * メッセージペイロードを取得し、対応する実行中のJavaプロセスの標準入力に転送します。
     * @param session メッセージを送信したWebSocketセッション
     * @param message 受信したテキストメッセージ
     * @throws IOException I/Oエラーが発生した場合
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        // System.out.println("サーバーWS受信: " + message.getPayload()); // ログは前のステップで削除

        String executionId = (String) session.getAttributes().get("executionId");
        // executionIdがセッション属性になければエラー
        if (executionId == null) {
            System.err.println("セッション " + session.getId() + " のhandleTextMessage中にexecutionIdがセッション属性に見つかりません。");
            session.sendMessage(new TextMessage("エラー: セッションコンテキストが失われました。入力を処理できません。"));
            return;
        }

        // 対応するプロセスの標準入力を取得
        OutputStream processStdinStream = processManager.getProcessStdin(executionId);
        if (processStdinStream != null) {
            try {
                String payload = message.getPayload(); // クライアントからの入力文字列
                // UTF-8バイトとしてのログや解釈された文字列のログは前のステップで削除
                
                // プラットフォームのデフォルトエンコーディングを使用してバイトを書き込むように戻された
                processStdinStream.write((payload + "\n").getBytes());
                processStdinStream.flush(); // データを即座に送信するために重要
            } catch (IOException e) {
                System.err.println("実行ID " + executionId + " のプロセス標準入力への書き込みエラー: " + e.getMessage());
                // オプション: エラーメッセージをクライアントにWebSocket経由で送信
                sendMessageToSession(executionId, "エラー: 実行中のプログラムに入力を送信できませんでした。");
            }
        } else {
            System.err.println("実行ID " + executionId + " のプロセス標準入力が見つかりません。入力は無視されました: " + message.getPayload());
            session.sendMessage(new TextMessage("エラー: プログラムが実行されていないか、入力を受け付けていません。"));
        }
    }

    /**
     * WebSocket接続が閉じた後に呼び出されます。
     * セッションを削除し、関連するJavaプロセス（実行中の場合）のクリーンアップを開始します。
     * @param session 閉じたWebSocketセッション
     * @param status 接続が閉じた理由を示すクローズステータス
     * @throws Exception エラーが発生した場合
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String executionId = (String) session.getAttributes().get("executionId");
        if (executionId != null) {
            // セッションマップから削除
            sessions.remove(executionId);
            System.out.println("WebSocket接続クローズ (executionId: " + executionId + ", Session: " + session.getId() + ") ステータス: " + status);
            // 関連プロセスのクリーンアップを指示
            processManager.cleanupProcess(executionId);
            
            // この実行IDの一時ディレクトリもクリーンアップされていることを確認
            CompilationResult cr = processManager.getCompilationResult(executionId); // 既に削除されている場合はnullの可能性あり
            if (cr != null && cr.getCompiledCodePath() != null) {
                dynamicCompiler.deleteTempDirectory(cr.getCompiledCodePath());
            } else {
                 // プロセス開始時にCRがprocessManagerから削除された場合、パスを取得する別の方法が必要
                 // これは少しトリッキーです。DynamicCompiler.startProcessはパスをCompilationResultに保存します。
                 // cleanupProcessも一時ディレクトリを削除する場合、これは冗長であるか問題を引き起こす可能性があります。
                 // 今のところ、InteractiveProcessManagerのcleanupProcessが一時フォルダの削除を処理すると仮定します。
                 // ここの 'deleteTempDirectory' 呼び出しは、IPMがそれを処理する場合、削除される可能性があります。
                 // IPM.cleanupProcessを改良して一時フォルダの削除を含めるようにしましょう。
            }
        } else {
            System.out.println("WebSocket接続クローズ (Session: " + session.getId() + ", executionId見つからず) ステータス: " + status);
        }
    }
    
    /**
     * WebSocketトランスポートエラーが発生したときに呼び出されます。
     * @param session エラーが発生したセッション
     * @param exception 発生した例外
     * @throws Exception エラーを処理する場合
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        System.err.println("WebSocketトランスポートエラー (Session " + session.getId() + "): " + exception.getMessage());
        // 適切であれば、ここでafterConnectionClosedロジックを呼び出すことも検討
        // String executionId = (String) session.getAttributes().get("executionId");
        // if (executionId != null) {
        //     processManager.cleanupProcess(executionId);
        // }
        super.handleTransportError(session, exception);
    }


    /**
     * 特定のクライアントセッションにメッセージを送信します。
     * このメソッドは通常、実行中のJavaプロセスからの出力をクライアントに中継するために使用されます。
     * @param executionId メッセージの送信先となるクライアントセッションを識別する実行ID
     * @param message 送信するメッセージ文字列
     */
    public void sendMessageToSession(String executionId, String message) {
        // System.out.println("サーバーWS送信クライアント (" + executionId + "): " + message); // ログは前のステップで削除

        // executionIdに対応するセッションを取得
        WebSocketSession session = sessions.get(executionId);
        // セッションが存在し、開いている場合のみメッセージを送信
        if (session != null && session.isOpen()) {
            try {
                // メッセージをテキストメッセージとして送信
                // 可能であれば、メッセージが断片化されずに完全なテキストメッセージとして送信されるようにする
                session.sendMessage(new TextMessage(message));
            } catch (IOException e) {
                System.err.println("セッション " + executionId + " へのメッセージ送信エラー: " + e.getMessage());
                // 送信に失敗した場合、セッションが壊れている可能性があります。クリーンアップを検討してください。
            }
        } else {
            // System.out.println("セッション " + executionId + " が見つからないか開いていません。メッセージは送信されませんでした: " + message);
        }
    }
}
