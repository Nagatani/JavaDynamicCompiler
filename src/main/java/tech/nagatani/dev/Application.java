package tech.nagatani.dev;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * このJava Dynamic Compiler Web UIアプリケーションのメインエントリポイントとなるクラスです。
 * {@link SpringBootApplication} アノテーションは、このクラスがSpring Bootアプリケーションであり、
 * 自動設定、コンポーネントスキャン、および追加設定の機能を有効にすることを示します。
 */
@SpringBootApplication
public class Application {

    /**
     * Spring Bootアプリケーションを起動するメインメソッドです。
     * {@link SpringApplication#run(Class, String[])} メソッドを呼び出して、
     * アプリケーションのコンテキストを開始し、組み込みサーバー（例：Tomcat）を起動します。
     * @param args アプリケーション起動時に渡されるコマンドライン引数。
     */
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

}
