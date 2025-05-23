package tech.nagatani.dev.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import tech.nagatani.dev.DynamicCompiler;
// Assuming CompilationResult will be created later
import tech.nagatani.dev.CompilationResult; 
// Assuming InteractiveProcessManager will be created later
import tech.nagatani.dev.service.InteractiveProcessManager; 
import tech.nagatani.dev.websocket.ExecutionWebSocketHandler;

import java.util.UUID;
// Removed java.util.Arrays and java.nio.charset.StandardCharsets as they are no longer needed

@Controller
public class CompilerController {

    private final DynamicCompiler dynamicCompiler;
    private final InteractiveProcessManager processManager;
    // ExecutionWebSocketHandler might not be directly used here,
    // but good to have if future direct interaction is needed.
    private final ExecutionWebSocketHandler webSocketHandler; 

    public CompilerController(DynamicCompiler dynamicCompiler, 
                              InteractiveProcessManager processManager,
                              ExecutionWebSocketHandler webSocketHandler) {
        this.dynamicCompiler = dynamicCompiler;
        this.processManager = processManager;
        this.webSocketHandler = webSocketHandler;
    }

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @PostMapping("/compile")
    public String compile(@RequestParam("sourceCode") String sourceCode,
                          Model model) {
        // Logging statements removed

        if (sourceCode == null || sourceCode.trim().isEmpty()) {
            model.addAttribute("compilationStatus", "FAILURE");
            model.addAttribute("diagnostics", "Source code cannot be empty.");
            model.addAttribute("output", ""); // Keep this for result.html if we redirect
            return "result"; // Or interactive_console.html with error display
        }

        // This method signature will change. For now, let's assume it returns a new CompilationResult
        // and we'll adapt DynamicCompiler later.
        // For now, we'll call the old compile method and adapt its output.
        CompilationResult compilationResult = dynamicCompiler.compileToJar(sourceCode); 

        if (compilationResult.isSuccess()) {
            String executionId = UUID.randomUUID().toString();
            // The 'prepareExecution' logic will be part of InteractiveProcessManager or called by it.
            // For now, let's assume InteractiveProcessManager can store the CompilationResult.
            processManager.registerCompilationResult(executionId, compilationResult); 

            model.addAttribute("executionId", executionId);
            model.addAttribute("compilationStatus", "SUCCESS");
            String diagnosticsOutput = String.join("\n", compilationResult.getDiagnostics());
            model.addAttribute("diagnostics", diagnosticsOutput.isEmpty() ? "No compilation issues." : diagnosticsOutput);
            return "interactive_console.html";
        } else {
            model.addAttribute("compilationStatus", "FAILURE");
            String diagnosticsOutput = String.join("\n", compilationResult.getDiagnostics());
            model.addAttribute("diagnostics", diagnosticsOutput);
            // model.addAttribute("output", ""); // No execution output if compilation fails
            return "result"; // Or interactive_console.html with specific error handling
        }
    }
}
