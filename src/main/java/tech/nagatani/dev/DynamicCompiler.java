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

    public Result compile(String sourceCode) {
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        List<String> diagnosticMessages = new ArrayList<>();
        List<String> executeMessages = new ArrayList<>();
        boolean compileSuccess = false;
        Path tempDir = null;

        String className = extractPublicClassName(sourceCode);

        if (className == null || className.trim().isEmpty()) {
            diagnosticMessages.add("ERROR: Could not find a public class (e.g., 'public class MyClass {...}') or class name is invalid in the provided source code.");
            return new Result(false, diagnosticMessages, Collections.emptyList());
        }

        try {
            // Create a temporary directory for compiled class files
            tempDir = Files.createTempDirectory("java-compile-");

            JavaFileObject sourceFile = new StringSourceJavaObject(className, sourceCode);
            Iterable<? extends JavaFileObject> compilationUnits = Collections.singletonList(sourceFile);
            
            // Options: -d specifies the output directory for class files
            Iterable<String> options = Arrays.asList("-d", tempDir.toString());

            StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null);
            JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, options, null, compilationUnits);
            compileSuccess = task.call();

            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                diagnosticMessages.add(String.format("Code: %s%nKind: %s%nSource: %s%nMessage: %s%nLine: %d%nPosition: %d%n",
                    diagnostic.getCode(),
                    diagnostic.getKind(),
                    diagnostic.getSource() != null ? diagnostic.getSource().getName() : "N/A",
                    diagnostic.getMessage(null), // null for default locale
                    diagnostic.getLineNumber(),
                    diagnostic.getPosition()));
            }

            if (compileSuccess) {
                System.out.println(className + ": Compilation successful.");
                try {
                    // Execute the compiled class
                    // Ensure the temp directory is in the classpath
                    ProcessBuilder processBuilder = new ProcessBuilder("java", "-cp", tempDir.toString(), className);
                    Process process = processBuilder.start();

                    // Capture stdout
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            executeMessages.add(line);
                        }
                    }

                    // Capture stderr
                    try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                        String line;
                        while ((line = errorReader.readLine()) != null) {
                            executeMessages.add("ERROR: " + line);
                        }
                    }

                    int exitCode = process.waitFor();
                    if (exitCode != 0) {
                        executeMessages.add("ERROR: Program exited with code " + exitCode);
                    }

                } catch (IOException | InterruptedException e) {
                    executeMessages.add("ERROR: Failed to execute compiled class - " + e.getMessage());
                    // e.printStackTrace(); // Consider logging this server-side
                }
            } else {
                System.out.println(className + ": Compilation failed.");
            }
        } catch (IOException e) {
            // This would be an error in creating temp dir or other file operations
            diagnosticMessages.add("FATAL ERROR: Could not create temporary directory or manage files - " + e.getMessage());
            // e.printStackTrace(); // Consider logging this server-side
        } finally {
            if (tempDir != null) {
                try {
                    // Recursively delete the temporary directory
                    Files.walk(tempDir)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
                } catch (IOException e) {
                    // Log this error, as cleanup failed
                    System.err.println("Warning: Failed to delete temporary directory " + tempDir + " - " + e.getMessage());
                    // e.printStackTrace();
                }
            }
        }
        return new Result(compileSuccess, diagnosticMessages, executeMessages);
    }

    public static void main(String[] args) {
        // Example usage (optional, can be removed or adapted)
        /*
        DynamicCompiler dc = new DynamicCompiler();
        String source = "public class Test {\n" +
                        "    public static void main(String[] args) {\n" +
                        "        System.out.println(\"Hello from dynamically compiled code!\");\n" +
                        "        System.err.println(\"This is an error message.\");\n" +
                        "        if(args.length > 0) System.out.println(\"Args: \" + args[0]);\n" +
                        "    }\n" +
                        "}";
        // Result ret = dc.compile(source, "Test"); // Old call
        Result ret = dc.compile(source); // New call

        System.out.println("Compilation Success: " + ret.compileSuccess());
        System.out.println("Compiler Diagnostics:");
        ret.compileOutput().forEach(System.out::println);
        System.out.println("\nExecution Output:");
        ret.output().forEach(System.out::println);
        */
    }
}