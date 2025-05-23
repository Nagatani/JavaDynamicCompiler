package tech.nagatani.dev.service;

import org.springframework.stereotype.Service;
import tech.nagatani.dev.CompilationResult; // 作成される予定
// import tech.nagatani.dev.DynamicCompiler; // 将来的に必要になる可能性あり
// import tech.nagatani.dev.websocket.ExecutionWebSocketHandler; // 将来的に必要になる可能性あり

// import java.io.IOException; // 現在は未使用
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 対話型Javaプロセスとその関連リソース（コンパイル結果、アクティブプロセス、I/Oスレッドなど）を管理するサービス。
 * Springの {@link Service} としてマークされています。
 */
@Service
public class InteractiveProcessManager {

    // プロセス開始前にコンパイル結果を一時的に保持するマップ。
    // キーは実行ID (executionId)、値は CompilationResult オブジェクト。
    private final Map<String, CompilationResult> pendingCompilations = new ConcurrentHashMap<>();
    
    // アクティブなJavaプロセスを保持するマップ。
    // キーは実行ID (executionId)、値は Process オブジェクト。
    private final Map<String, Process> activeProcesses = new ConcurrentHashMap<>();
    
    // プロセスの標準出力を読み取るスレッドを保持するマップ。
    // キーは実行ID + "-stdout"、値は Thread オブジェクト。
    private final Map<String, Thread> outputThreads = new ConcurrentHashMap<>();
    
    // プロセスの標準エラー出力を読み取るスレッドを保持するマップ。
    // キーは実行ID + "-stderr"、値は Thread オブジェクト。
    private final Map<String, Thread> errorThreads = new ConcurrentHashMap<>();

    // private ExecutionWebSocketHandler webSocketHandler; // 将来的に必要になる可能性あり
    // private DynamicCompiler dynamicCompiler; // 将来的に必要になる可能性あり

    /**
     * InteractiveProcessManagerの新しいインスタンスを構築します。
     * このサービスが他のSpring管理ビーンを必要とする場合は、コンストラクタインジェクションを使用します。
     * 現状では、自己完結型であるか、他のコンポーネントによって使用されます。
     */
    public InteractiveProcessManager() {
    }

    /**
     * 指定された実行IDに関連付けて、コンパイル結果を登録（一時保存）します。
     * これは通常、プロセスが実際に開始される前に呼び出されます。
     * @param executionId コンパイル結果に紐付ける一意の実行ID。
     * @param compilationResult 登録する {@link CompilationResult} オブジェクト。
     */
    public void registerCompilationResult(String executionId, CompilationResult compilationResult) {
        pendingCompilations.put(executionId, compilationResult);
    }

    /**
     * 指定された実行IDに関連付けられたコンパイル結果を取得します。
     * @param executionId 取得するコンパイル結果の実行ID。
     * @return 対応する {@link CompilationResult}。見つからない場合はnull。
     */
    public CompilationResult getCompilationResult(String executionId) {
        return pendingCompilations.get(executionId);
    }

    /**
     * 新しく開始されたプロセスとそのI/O処理スレッドを登録します。
     * プロセスが登録されると、対応する保留中のコンパイル結果はクリアされます。
     * @param executionId プロセスに紐付ける一意の実行ID。
     * @param process 登録する実行中の {@link Process} オブジェクト。
     * @param outputThread プロセスの標準出力を処理する {@link Thread}。nullの場合あり。
     * @param errorThread プロセスの標準エラー出力を処理する {@link Thread}。nullの場合あり。
     */
    public void registerProcess(String executionId, Process process, Thread outputThread, Thread errorThread) {
        // アクティブなプロセスマップにプロセスを登録
        activeProcesses.put(executionId, process);
        // 標準出力スレッドが存在する場合、登録して開始
        if (outputThread != null) {
            outputThreads.put(executionId + "-stdout", outputThread);
            outputThread.start();
        }
        // 標準エラースレッドが存在する場合、登録して開始
        if (errorThread != null) {
            errorThreads.put(executionId + "-stderr", errorThread);
            errorThread.start();
        }
        // プロセスが開始されたため、保留中のコンパイル結果を削除
        pendingCompilations.remove(executionId);
    }

    /**
     * 指定された実行IDに関連付けられたアクティブなプロセスを取得します。
     * @param executionId 取得するプロセスの実行ID。
     * @return 対応する {@link Process} オブジェクト。見つからない場合はnull。
     */
    public Process getProcess(String executionId) {
        return activeProcesses.get(executionId);
    }

    /**
     * 指定された実行IDに関連付けられたプロセスの標準入力ストリームを取得します。
     * これにより、実行中のプロセスに入力を送信できます。
     * @param executionId 標準入力ストリームを取得するプロセスの実行ID。
     * @return プロセスの {@link OutputStream} (標準入力)。プロセスが見つからない場合はnull。
     */
    public OutputStream getProcessStdin(String executionId) {
        Process process = activeProcesses.get(executionId);
        if (process != null) {
            return process.getOutputStream();
        }
        return null;
    }

    /**
     * 指定された実行IDに関連付けられたプロセスとそのリソースをクリーンアップします。
     * これには、プロセスの強制終了、I/Oスレッドの中断、および関連するマップからのエントリ削除が含まれます。
     * 保留中のコンパイル結果も（まだ存在する場合）クリーンアップされます。
     * @param executionId クリーンアップするプロセスの実行ID。
     */
    public void cleanupProcess(String executionId) {
        // アクティブなプロセスをマップから削除し、取得
        Process process = activeProcesses.remove(executionId);
        if (process != null) {
            process.destroyForcibly(); // プロセスを強制終了
            try {
                process.waitFor(); // プロセスの終了を待機
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // スレッドの割り込み状態を再設定
                System.err.println("プロセス " + executionId + " の終了待機中に割り込みが発生しました。");
            }
        }

        // 標準出力スレッドをマップから削除し、生存していれば中断
        Thread outputThread = outputThreads.remove(executionId + "-stdout");
        if (outputThread != null && outputThread.isAlive()) {
            outputThread.interrupt(); // スレッドを中断
        }

        // 標準エラースレッドをマップから削除し、生存していれば中断
        Thread errorThread = errorThreads.remove(executionId + "-stderr");
        if (errorThread != null && errorThread.isAlive()) {
            errorThread.interrupt(); // スレッドを中断
        }
        
        // 開始されなかった場合に備えて、保留中のコンパイル結果からもクリーンアップ
        CompilationResult cr = pendingCompilations.remove(executionId);
        // 将来的には、ここでcr.getCompiledCodePath()を使用して一時ディレクトリも削除することを検討
        // (DynamicCompilerのdeleteTempDirectoryを呼び出すなど)
        
        System.out.println("実行ID: " + executionId + " のリソースをクリーンアップしました。");
    }
}
