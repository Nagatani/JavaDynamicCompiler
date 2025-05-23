package tech.nagatani.dev;
// import static spark.Spark.*; // Commented out SparkJava import

public class WebApp {
    /* // Commented out SparkJava related main method
    public static void main(String[] args) {
        get("/compile", (req, res) -> "Hello World");

        get("/hello", "application/json", (request, response) -> {

            var dc = new DynamicCompiler();
            var ret = dc.compile("Test.java", "Test");
            System.out.println(ret);
            return ret;
        }, new JsonTransformer());
    }
    */
}
