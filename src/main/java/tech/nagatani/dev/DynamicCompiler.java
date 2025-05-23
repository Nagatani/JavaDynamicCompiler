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
import java.util.concurrent.TimeUnit; // Added for timeout
import java.nio.charset.StandardCharsets; // Added for UTF-8 stream reading

// New imports for process management and WebSocket integration
import tech.nagatani.dev.service.InteractiveProcessManager;
import tech.nagatani.dev.websocket.ExecutionWebSocketHandler;
import org.springframework.stereotype.Component; // Make this a Spring component

@Component // Added Spring @Component annotation
public class DynamicCompiler {
    private static final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    private static final Pattern PUBLIC_CLASS_NAME_PATTERN = Pattern.compile("public\\s+(?:final\\s+)?class\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*");

    // Custom JavaFileObject to hold source code in a String
    static class StringSourceJavaObject extends SimpleJavaFileObject {
        private final String sourceCode;

        protected StringSourceJavaObject(String className, String sourceCode) {
            super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.sourceCode = sourceCode;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return sourceCode;
        }
    }

    public DynamicCompiler() {
        if (compiler == null) {
            System.err.println("Compiler not found. This application cannot function.");
            // Consider throwing an exception or handling this more gracefully in a Spring context
            throw new IllegalStateException("Java Compiler not available. Please ensure a JDK is installed and configured correctly.");
        }
    }

    private String extractPublicClassName(String sourceCode) {
        if (sourceCode == null || sourceCode.trim().isEmpty()) {
            return null;
        }
        Matcher matcher = PUBLIC_CLASS_NAME_PATTERN.matcher(sourceCode);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    // Refactored method: compileToJar
    public CompilationResult compileToJar(String sourceCode) {
        DiagnosticCollector<JavaFileObject> diagnosticsCollector = new DiagnosticCollector<>();
        List<String> diagnosticMessages = new ArrayList<>();
        Path tempDir = null;
        String className = extractPublicClassName(sourceCode);

        if (className == null || className.trim().isEmpty()) {
            diagnosticMessages.add("ERROR: Could not find a public class (e.g., 'public class MyClass {...}') or class name is invalid in the provided source code.");
            return new CompilationResult(false, diagnosticMessages, null, null, sourceCode);
        }

        try {
            tempDir = Files.createTempDirectory("java-compile-");
            JavaFileObject sourceFile = new StringSourceJavaObject(className, sourceCode);
            Iterable<? extends JavaFileObject> compilationUnits = Collections.singletonList(sourceFile);
            Iterable<String> options = Arrays.asList("-d", tempDir.toString());

            StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnosticsCollector, null, null);
            JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnosticsCollector, options, null, compilationUnits);
            boolean success = task.call();

            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnosticsCollector.getDiagnostics()) {
                diagnosticMessages.add(String.format("Kind: %s, Source: %s, Line: %d, Message: %s",
                    diagnostic.getKind(),
                    diagnostic.getSource() != null ? diagnostic.getSource().getName() : "N/A",
                    diagnostic.getLineNumber(),
                    diagnostic.getMessage(null)));
            }

            if (success) {
                System.out.println(className + ": Compilation successful. Output in " + tempDir);
                // Do not delete tempDir here; it's needed for execution.
                // It should be cleaned up after execution is completely finished.
                return new CompilationResult(true, diagnosticMessages, className, tempDir, sourceCode);
            } else {
                System.out.println(className + ": Compilation failed.");
                deleteTempDirectory(tempDir); // Clean up if compilation failed
                return new CompilationResult(false, diagnosticMessages, className, null, sourceCode);
            }
        } catch (IOException e) {
            diagnosticMessages.add("FATAL ERROR: Could not create temporary directory or manage files - " + e.getMessage());
            if (tempDir != null) {
                deleteTempDirectory(tempDir); // Attempt cleanup on error
            }
            return new CompilationResult(false, diagnosticMessages, className, null, sourceCode);
        }
        // Note: 'finally' block for tempDir deletion is removed from here.
        // Cleanup is now conditional or handled by InteractiveProcessManager.
    }

    // New method: startProcess
    public void startProcess(CompilationResult compilationResult, String executionId,
                             InteractiveProcessManager processManager, ExecutionWebSocketHandler webSocketHandler) {
        if (!compilationResult.isSuccess() || compilationResult.getCompiledCodePath() == null || compilationResult.getClassName() == null) {
            System.err.println("Cannot start process due to compilation failure or missing details for executionId: " + executionId);
            webSocketHandler.sendMessageToSession(executionId, "Error: Compilation failed or details missing. Cannot start process.");
            return;
        }

        String className = compilationResult.getClassName();
        Path tempDir = compilationResult.getCompiledCodePath();
        String sourceCode = compilationResult.getSourceCode(); // Get source code

        try {
            ProcessBuilder processBuilder = new ProcessBuilder("java", "-cp", tempDir.toString(), className);
            Process process = processBuilder.start();

            // GUI Check and Timeout Logic
            boolean isSuspectedGui = sourceCode.contains("import javax.swing.*;") || sourceCode.contains("import java.awt.*;");
            if (isSuspectedGui) {
                System.out.println("Suspected GUI application for executionId: " + executionId);
                boolean exited = false;
                try {
                    exited = process.waitFor(10, TimeUnit.SECONDS); // 10-second timeout
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.err.println("Timeout wait interrupted for executionId: " + executionId + " " + e.getMessage());
                    // Process will be handled by onExit or cleanup
                }

                if (!exited) {
                    webSocketHandler.sendMessageToSession(executionId, "INFO: This appears to be a GUI application or a long-running process. It was terminated after a 10-second timeout as full GUI/long-process emulation is not yet supported.");
                    process.destroyForcibly();
                    System.out.println("Suspected GUI application/long-running process timed out and was destroyed for executionId: " + executionId);
                    // I/O threads started below will find streams closed.
                    // process.onExit() will still fire.
                }
            }
            
            // Thread to read stdout - only proceed if process is alive or exited normally from timeout check
            // If process was destroyed, these threads will start, find streams closed, and exit.
            Thread outputThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        webSocketHandler.sendMessageToSession(executionId, line);
                    }
                } catch (IOException e) {
                    if (!e.getMessage().toLowerCase().contains("stream closed")) {
                         System.err.println("IOException in output stream reader for " + executionId + ": " + e.getMessage());
                    }
                } finally {
                    System.out.println("Output stream reader ended for " + executionId);
                }
            });
            outputThread.setName("stdout-reader-" + executionId);

            // Thread to read stderr
            Thread errorThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        webSocketHandler.sendMessageToSession(executionId, "ERROR: " + line);
                    }
                } catch (IOException e) {
                     if (!e.getMessage().toLowerCase().contains("stream closed")) {
                        System.err.println("IOException in error stream reader for " + executionId + ": " + e.getMessage());
                     }
                } finally {
                    System.out.println("Error stream reader ended for " + executionId);
                }
            });
            errorThread.setName("stderr-reader-" + executionId);
            
            processManager.registerProcess(executionId, process, outputThread, errorThread);
            System.out.println("Process started for executionId: " + executionId + " with class " + className);

            // Wait for the process to complete and then trigger cleanup
            process.onExit().thenRun(() -> {
                // Check if process is still alive; it might have been destroyed by timeout
                // However, onExit should fire regardless of how it terminated.
                // The message might be slightly off if timed out, but the exit code will be informative.
                System.out.println("Process " + executionId + " exited with code " + process.exitValue());
                // Avoid sending "Program finished" if it was killed due to timeout and a message was already sent.
                // This check is a bit indirect. A more robust way would be to set a flag if timeout occurred.
                if (!isSuspectedGui || process.exitValue() == 0) { // Simplistic check, may need refinement
                     webSocketHandler.sendMessageToSession(executionId, "\nProgram finished with exit code: " + process.exitValue());
                } else if (isSuspectedGui && process.exitValue() != 0 && process.exitValue() != 137 && process.exitValue() != 143) { 
                    // 137 SIGKILL, 143 SIGTERM. If killed by timeout, we already sent a message.
                    // If it's GUI and exited with another error, then show it.
                    webSocketHandler.sendMessageToSession(executionId, "\nProgram (suspected GUI) finished with exit code: " + process.exitValue());
                }
                // processManager.cleanupProcess(executionId); // Cleanup is now initiated by WebSocket close
            });

        } catch (IOException e) {
            System.err.println("Failed to start process for executionId " + executionId + ": " + e.getMessage());
            webSocketHandler.sendMessageToSession(executionId, "Error: Failed to start process - " + e.getMessage());
            deleteTempDirectory(tempDir); // Clean up tempDir if process fails to start
        }
    }

    // Helper method to delete temp directory
    public void deleteTempDirectory(Path directory) {
        if (directory == null) return;
        try {
            Files.walk(directory)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
            System.out.println("Successfully deleted temporary directory: " + directory);
        } catch (IOException e) {
            System.err.println("Warning: Failed to delete temporary directory " + directory + " - " + e.getMessage());
        }
    }


    public static void main(String[] args) {
        // Main method might need adjustments if used for testing, as compile() signature changed.
        // For now, it's commented out as it's not essential for the app's core functionality.
        /*
        DynamicCompiler dc = new DynamicCompiler();
        String source = "public class Test { public static void main(String[] args) { System.out.println(\"Hello\"); try { Thread.sleep(2000); } catch (InterruptedException e) {} System.err.println(\"Error\"); } }";
        CompilationResult result = dc.compileToJar(source);
        System.out.println("Compilation Success: " + result.isSuccess());
        System.out.println("Compiler Diagnostics:");
        result.getDiagnostics().forEach(System.out::println);

        if (result.isSuccess()) {
            // To test startProcess, you'd need mock/stub for InteractiveProcessManager and ExecutionWebSocketHandler
            System.out.println("\nSimulating execution (actual process start requires more setup)...");
            System.out.println("Class Name: " + result.getClassName());
            System.out.println("Temp Dir: " + result.getCompiledCodePath());
            // dc.startProcess(result, "test-exec", mockProcessManager, mockWebSocketHandler);
            // Remember to clean up tempDir after test
            // dc.deleteTempDirectory(result.getCompiledCodePath());
        }
        */
    }
}