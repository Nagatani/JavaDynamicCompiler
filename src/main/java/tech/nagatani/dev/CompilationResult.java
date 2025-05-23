package tech.nagatani.dev;

import java.nio.file.Path;
import java.util.List;

/**
 * Javaソースコードのコンパイル試行の結果を保持するクラス。
 * コンパイルの成功ステータス、診断メッセージ（エラーや警告）、抽出されたクラス名、
 * コンパイルされたクラスファイルが格納される一時ディレクトリのパス、および元のソースコードを格納します。
 */
public class CompilationResult {
    private final boolean success; // コンパイルが成功したかどうか
    private final List<String> diagnostics; // コンパイラからの診断メッセージのリスト
    private final String className; // 抽出されたpublicクラス名
    private final Path compiledCodePath; // コンパイルされたクラスファイルが格納される一時ディレクトリのパス。ディレクトリまたはJARファイルのパスになることがあります。
    private final String sourceCode; // コンパイルに使用された元のソースコード

    /**
     * CompilationResultの新しいインスタンスを構築します。
     * @param success コンパイルが成功した場合はtrue、それ以外はfalse。
     * @param diagnostics コンパイラからの診断メッセージのリスト。
     * @param className 抽出されたpublicクラス名。コンパイル失敗時はnullの場合があります。
     * @param compiledCodePath コンパイルされたクラスファイルが格納される一時ディレクトリのパス。コンパイル失敗時はnullの場合があります。
     * @param sourceCode コンパイルに使用された元のソースコード。
     */
    public CompilationResult(boolean success, List<String> diagnostics, String className, Path compiledCodePath, String sourceCode) {
        this.success = success;
        this.diagnostics = diagnostics;
        this.className = className;
        this.compiledCodePath = compiledCodePath;
        this.sourceCode = sourceCode;
    }

    /**
     * コンパイルが成功したかどうかを返します。
     * @return コンパイルが成功した場合はtrue、それ以外はfalse。
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * コンパイラからの診断メッセージのリストを返します。
     * これにはエラーメッセージや警告メッセージが含まれることがあります。
     * @return 診断メッセージのリスト。
     */
    public List<String> getDiagnostics() {
        return diagnostics;
    }

    /**
     * ソースコードから抽出されたpublicクラス名を返します。
     * @return 抽出されたクラス名。コンパイル失敗時やクラス名が見つからなかった場合はnull。
     */
    public String getClassName() {
        return className;
    }

    /**
     * コンパイルされたクラスファイルが格納されている一時ディレクトリのパスを返します。
     * このパスは、後続の実行ステップでクラスパスに追加するために使用されます。
     * @return コンパイルされたコードへのパス。コンパイル失敗時はnull。
     */
    public Path getCompiledCodePath() {
        return compiledCodePath;
    }

    /**
     * コンパイルに使用された元のソースコードを返します。
     * これは、GUIアプリケーションのタイムアウトチェックなど、特定のロジックで使用されることがあります。
     * @return 元のソースコード文字列。
     */
    public String getSourceCode() {
        return sourceCode;
    }
}
