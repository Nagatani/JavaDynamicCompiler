package tech.nagatani.dev;

import java.nio.file.Path;
import java.util.List;

public class CompilationResult {
    private final boolean success;
    private final List<String> diagnostics;
    private final String className;
    private final Path compiledCodePath; // Could be a directory or a JAR file path
    private final String sourceCode;

    public CompilationResult(boolean success, List<String> diagnostics, String className, Path compiledCodePath, String sourceCode) {
        this.success = success;
        this.diagnostics = diagnostics;
        this.className = className;
        this.compiledCodePath = compiledCodePath;
        this.sourceCode = sourceCode;
    }

    public boolean isSuccess() {
        return success;
    }

    public List<String> getDiagnostics() {
        return diagnostics;
    }

    public String getClassName() {
        return className;
    }

    public Path getCompiledCodePath() {
        return compiledCodePath;
    }

    public String getSourceCode() {
        return sourceCode;
    }
}
