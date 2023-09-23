package tech.nagatani.dev;

import javax.tools.*;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DynamicCompiler {
    private static final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

    public DynamicCompiler() {
        if (compiler == null) {
            System.out.println("Compiler not found.");
            return;
        }
    }

    public Result compile(String source, String className) {

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null);

        Iterable<? extends JavaFileObject> javaFiles = fileManager.getJavaFileObjects(source);
        Iterable<String> options = Arrays.asList("-d", "./");

        JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, options, null, javaFiles);
        boolean result = task.call();

        // コンパイル結果と診断情報を表示
        List<String> diagnosticMessages = new ArrayList<>();
        for (Diagnostic<?> diagnostic : diagnostics.getDiagnostics()) {
            diagnosticMessages.add(diagnostic.toString());
        }

        var executeMessage = new ArrayList<String>();
        // コンパイル結果の表示
        if (result) {
            System.out.println(source + ": Compilation successful.");
            // コンパイルが成功した場合、プログラムを実行
            try {
                Process process = Runtime.getRuntime().exec("java " + className);
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

                String line;
                while ((line = reader.readLine()) != null) {
                    executeMessage.add(line);
                }

                process.waitFor();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            System.out.println(source + ": Compilation failed.");
        }

        return new Result(result, diagnosticMessages, executeMessage);
    }

    public static void main(String[] args) {

        var dc = new DynamicCompiler();
        var ret = dc.compile("Test.java", "Test");

        System.out.println(ret);
    }
}