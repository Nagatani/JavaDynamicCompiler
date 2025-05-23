package tech.nagatani.dev.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import tech.nagatani.dev.websocket.ExecutionWebSocketHandler; // 次に作成される予定 (実際には既に作成済み)

/**
 * アプリケーションのWebSocketサポートを設定するクラス。
 * {@link EnableWebSocket} アノテーションによりWebSocket機能を有効にし、
 * {@link WebSocketConfigurer} を実装してWebSocketハンドラを登録します。
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ExecutionWebSocketHandler executionWebSocketHandler; // 対話型実行のためのWebSocketハンドラ

    /**
     * {@link ExecutionWebSocketHandler} を依存性注入（DI）によって初期化するコンストラクタです。
     * @param executionWebSocketHandler 対話型コード実行を処理するWebSocketハンドラ。
     */
    public WebSocketConfig(ExecutionWebSocketHandler executionWebSocketHandler) {
        this.executionWebSocketHandler = executionWebSocketHandler;
    }

    /**
     * WebSocketハンドラをレジストリに登録します。
     * これにより、特定のパス（この場合は "/ws/execute"）へのWebSocket接続が
     * 指定されたハンドラ（{@link ExecutionWebSocketHandler}）によって処理されるようになります。
     * {@code setAllowedOrigins("*")} はクロスオリジンリクエストをすべてのオリジンから許可します。
     * 本番環境では、より具体的なオリジンを指定することが推奨されます。
     * @param registry WebSocketハンドラを登録するためのレジストリ。
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // "/ws/execute" パスに executionWebSocketHandler を登録
        // setAllowedOrigins("*") ですべてのオリジンからの接続を許可 (開発用)
        registry.addHandler(executionWebSocketHandler, "/ws/execute").setAllowedOrigins("*");
    }
}
