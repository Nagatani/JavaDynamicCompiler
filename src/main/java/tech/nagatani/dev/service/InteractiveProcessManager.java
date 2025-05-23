package tech.nagatani.dev.service;

import org.springframework.stereotype.Service;
import tech.nagatani.dev.CompilationResult; // To be created
import tech.nagatani.dev.DynamicCompiler;
import tech.nagatani.dev.websocket.ExecutionWebSocketHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InteractiveProcessManager {

    // Map to hold compilation results temporarily before process starts
    private final Map<String, CompilationResult> pendingCompilations = new ConcurrentHashMap<>();
    private final Map<String, Process> activeProcesses = new ConcurrentHashMap<>();
    private final Map<String, Thread> outputThreads = new ConcurrentHashMap<>();
    private final Map<String, Thread> errorThreads = new ConcurrentHashMap<>();

    // private ExecutionWebSocketHandler webSocketHandler; // Potentially needed later
    // private DynamicCompiler dynamicCompiler; // Potentially needed later

    // Using constructor injection if this service needs other Spring-managed beans.
    // For now, it's self-contained or used by others.
    public InteractiveProcessManager() {
    }

    public void registerCompilationResult(String executionId, CompilationResult compilationResult) {
        pendingCompilations.put(executionId, compilationResult);
    }

    public CompilationResult getCompilationResult(String executionId) {
        return pendingCompilations.get(executionId);
    }

    public void registerProcess(String executionId, Process process, Thread outputThread, Thread errorThread) {
        activeProcesses.put(executionId, process);
        if (outputThread != null) {
            outputThreads.put(executionId + "-stdout", outputThread);
            outputThread.start();
        }
        if (errorThread != null) {
            errorThreads.put(executionId + "-stderr", errorThread);
            errorThread.start();
        }
        pendingCompilations.remove(executionId); // Clean up after process is started
    }

    public Process getProcess(String executionId) {
        return activeProcesses.get(executionId);
    }

    public OutputStream getProcessStdin(String executionId) {
        Process process = activeProcesses.get(executionId);
        if (process != null) {
            return process.getOutputStream();
        }
        return null;
    }

    public void cleanupProcess(String executionId) {
        Process process = activeProcesses.remove(executionId);
        if (process != null) {
            process.destroyForcibly(); // Terminate the process
            try {
                process.waitFor(); // Wait for the process to exit
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Interrupted while waiting for process " + executionId + " to exit.");
            }
        }

        Thread outputThread = outputThreads.remove(executionId + "-stdout");
        if (outputThread != null && outputThread.isAlive()) {
            outputThread.interrupt(); // Interrupt the thread
        }

        Thread errorThread = errorThreads.remove(executionId + "-stderr");
        if (errorThread != null && errorThread.isAlive()) {
            errorThread.interrupt(); // Interrupt the thread
        }
        
        pendingCompilations.remove(executionId); // Also ensure cleanup from pending if it never started
        System.out.println("Cleaned up resources for executionId: " + executionId);
    }
}
