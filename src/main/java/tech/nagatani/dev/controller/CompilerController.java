package tech.nagatani.dev.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import tech.nagatani.dev.DynamicCompiler;
import tech.nagatani.dev.Result;

@Controller
public class CompilerController {

    private final DynamicCompiler dynamicCompiler;

    public CompilerController() {
        this.dynamicCompiler = new DynamicCompiler();
    }

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @PostMapping("/compile")
    public String compile(@RequestParam("sourceCode") String sourceCode,
                          Model model) {
        // Basic input validation
        if (sourceCode == null || sourceCode.trim().isEmpty()) {
            // Handle empty source code - perhaps add a default or show an error
            model.addAttribute("compilationStatus", "ERROR");
            model.addAttribute("diagnostics", "Source code cannot be empty.");
            model.addAttribute("output", "");
            return "result";
        }

        // System.out.println("Received sourceCode: \n" + sourceCode); // Potentially too verbose for logs

        Result result = dynamicCompiler.compile(sourceCode);

        model.addAttribute("compilationStatus", result.compileSuccess() ? "SUCCESS" : "FAILURE");
        
        // Join iterable to a single string for display in <pre> tag
        String diagnosticsOutput = String.join("\n", result.compileOutput());
        model.addAttribute("diagnostics", diagnosticsOutput.isEmpty() && result.compileSuccess() ? "No compilation issues." : diagnosticsOutput);
        
        String executionOutput = String.join("\n", result.output());
        model.addAttribute("output", executionOutput.isEmpty() && result.compileSuccess() ? "No output produced." : executionOutput);

        return "result";
    }
}
