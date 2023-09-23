package tech.nagatani.dev;
import static spark.Spark.*;

public class WebApp {
    public static void main(String[] args) {
        get("/compile", (req, res) -> "Hello World");

        get("/hello", "application/json", (request, response) -> {

            var dc = new DynamicCompiler();
            var ret = dc.compile("Test.java", "Test");
            System.out.println(ret);
            return ret;
        }, new JsonTransformer());
    }

}
