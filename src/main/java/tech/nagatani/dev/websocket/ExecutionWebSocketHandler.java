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
import java.io.OutputStreamWriter; // Added import
import java.net.URI;
import java.nio.charset.StandardCharsets; // Added import
import java.util.Arrays; // Added import for Arrays.toString
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ExecutionWebSocketHandler extends TextWebSocketHandler {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final InteractiveProcessManager processManager;
    private final DynamicCompiler dynamicCompiler;

    public ExecutionWebSocketHandler(InteractiveProcessManager processManager, DynamicCompiler dynamicCompiler) {
        this.processManager = processManager;
        this.dynamicCompiler = dynamicCompiler;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        URI uri = session.getUri();
        String query = uri.getQuery(); // Should be "id=<executionId>"
        String executionId = null;
        if (query != null && query.startsWith("id=")) {
            executionId = query.substring(3);
        }

        if (executionId == null || executionId.trim().isEmpty()) {
            System.err.println("ExecutionId is missing in WebSocket URI: " + uri);
            session.sendMessage(new TextMessage("ERROR: ExecutionId is required."));
            session.close(CloseStatus.BAD_DATA.withReason("ExecutionId missing"));
            return;
        }
        
        session.getAttributes().put("executionId", executionId);
        sessions.put(executionId, session);
        System.out.println("WebSocket connection established for executionId: " + executionId + " (Session: " + session.getId() + ")");

        CompilationResult compilationResult = processManager.getCompilationResult(executionId);
        if (compilationResult == null) {
            System.err.println("No compilation result found for executionId: " + executionId);
            session.sendMessage(new TextMessage("ERROR: No compilation data found for this execution. It might have expired or failed."));
            session.close(CloseStatus.POLICY_VIOLATION.withReason("No compilation data"));
            return;
        }

        if (compilationResult.isSuccess()) {
            dynamicCompiler.startProcess(compilationResult, executionId, processManager, this);
        } else {
            session.sendMessage(new TextMessage("ERROR: Compilation was not successful. Cannot start process."));
            // Optionally, send diagnostics if available and not already sent via HTTP response
            // compilationResult.getDiagnostics().forEach(diag -> { try { session.sendMessage(new TextMessage(diag)); } catch (IOException e) {} });
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Compilation failed"));
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        System.out.println("Server WS RCV: " + message.getPayload()); // Logging incoming message

        String executionId = (String) session.getAttributes().get("executionId");
        if (executionId == null) {
            System.err.println("executionId missing in session attributes during handleTextMessage for session: " + session.getId());
            session.sendMessage(new TextMessage("ERROR: Session context lost. Cannot process input."));
            return;
        }

        OutputStream processStdinStream = processManager.getProcessStdin(executionId);
        if (processStdinStream != null) {
            try {
                String payload = message.getPayload();
                byte[] utf8Bytes = payload.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                System.out.println("Server WS SEND to Process STDIN (UTF-8 bytes as java.util.Arrays.toString): " + java.util.Arrays.toString(utf8Bytes));
                System.out.println("Server WS SEND to Process STDIN (String interpreted from UTF-8 bytes): " + new String(utf8Bytes, java.nio.charset.StandardCharsets.UTF_8));
                
                // It's important NOT to close this writer, as it would close the underlying stdin stream.
                // We are adapting the stream, not managing its lifecycle here.
                OutputStreamWriter writer = new OutputStreamWriter(processStdinStream, StandardCharsets.UTF_8);
                writer.write(payload + "\n"); // Use the payload variable
                writer.flush(); // Crucial for sending data immediately
            } catch (IOException e) {
                System.err.println("Error writing to process stdin for executionId " + executionId + ": " + e.getMessage());
                // Optionally, send an error message back to the client via WebSocket
                sendMessageToSession(executionId, "ERROR: Could not send input to the program.");
            }
        } else {
            System.err.println("Process stdin not found for executionId: " + executionId + ". Input ignored: " + message.getPayload());
            session.sendMessage(new TextMessage("ERROR: Program is not running or not accepting input."));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String executionId = (String) session.getAttributes().get("executionId");
        if (executionId != null) {
            sessions.remove(executionId);
            System.out.println("WebSocket connection closed for executionId: " + executionId + " (Session: " + session.getId() + ") with status " + status);
            processManager.cleanupProcess(executionId);
            // Also ensure the temp directory for this executionId is cleaned up
            CompilationResult cr = processManager.getCompilationResult(executionId); // Might be null if already removed
            if (cr != null && cr.getCompiledCodePath() != null) {
                dynamicCompiler.deleteTempDirectory(cr.getCompiledCodePath());
            } else {
                 // If CR was removed from processManager upon process start, we need another way to get path
                 // This is a bit tricky. DynamicCompiler.startProcess stores the path in CompilationResult.
                 // If cleanupProcess also cleans the temp dir, this might be redundant or cause issues.
                 // For now, let's assume cleanupProcess in InteractiveProcessManager should handle temp dir deletion.
                 // The 'deleteTempDirectory' call here might be removed if IPM handles it.
                 // Let's refine IPM.cleanupProcess to include deleting the temp folder.
            }
        } else {
            System.out.println("WebSocket connection closed for session: " + session.getId() + " (no executionId found) with status " + status);
        }
    }
    
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        System.err.println("WebSocket transport error for session " + session.getId() + ": " + exception.getMessage());
        // Consider also calling afterConnectionClosed logic here if appropriate
        // String executionId = (String) session.getAttributes().get("executionId");
        // if (executionId != null) {
        //     processManager.cleanupProcess(executionId);
        // }
        super.handleTransportError(session, exception);
    }


    // Method to send message to a specific session (will be called by the process output readers)
    public void sendMessageToSession(String executionId, String message) {
        System.out.println("Server WS SEND to Client (" + executionId + "): " + message);
        WebSocketSession session = sessions.get(executionId);
        if (session != null && session.isOpen()) {
            try {
                // Ensure messages are sent as whole text messages, not fragments if possible
                session.sendMessage(new TextMessage(message));
            } catch (IOException e) {
                System.err.println("Error sending message to session " + executionId + ": " + e.getMessage());
                // If sending fails, the session might be broken. Consider cleanup.
            }
        } else {
            // System.out.println("Session " + executionId + " not found or not open. Message not sent: " + message);
        }
    }
}
