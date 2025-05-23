package tech.nagatani.dev.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import tech.nagatani.dev.websocket.ExecutionWebSocketHandler; // To be created next

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ExecutionWebSocketHandler executionWebSocketHandler;

    public WebSocketConfig(ExecutionWebSocketHandler executionWebSocketHandler) {
        this.executionWebSocketHandler = executionWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(executionWebSocketHandler, "/ws/execute").setAllowedOrigins("*");
    }
}
