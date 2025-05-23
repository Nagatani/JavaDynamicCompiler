# Java Dynamic Compiler Web UI

## Description
This is a simple web application designed for dynamically compiling and executing Java code snippets through a user-friendly web interface. It allows users to quickly test and verify Java code.

**Important Note**: This application is intended for development, testing, and educational purposes only. Due to the inherent security risks of executing arbitrary code, it is **not suitable for production environments**.

## Features
-   Web-based interface for code input.
-   Dynamic compilation of user-provided Java source code.
-   Execution of the compiled Java code.
-   Display of compilation status, diagnostic messages (errors/warnings), and program output.

## How to Run
To run this application locally, you will need:
-   Java 21 JDK (or newer)
-   Apache Maven

Follow these steps:
1.  **Clone the repository**:
    ```bash
    git clone <repository-url>
    cd <repository-directory>
    ```
2.  **Run the application using Maven**:
    ```bash
    mvn spring-boot:run
    ```
    Alternatively, you can build the JAR file and then run it:
    ```bash
    mvn package
    java -jar target/JavaCompiler-1.0-SNAPSHOT.jar
    ```
    (Note: The JAR file name might vary based on the artifactId and version in `pom.xml`.)
3.  **Access the application**:
    Open your web browser and navigate to `http://localhost:8080/`.

## How to Use
1.  Once the application is running, you will see a form for providing your Java code:
    *   **Or Upload a .java File**: You can click the "Choose File" button (or similar, depending on browser wording for the file input) to select a `.java` file from your computer. The content of this file will automatically populate the "Source Code" text area below.
    *   **Source Code**: Paste or type your Java source code into the text area. If you uploaded a file, its content will appear here. Ensure your code includes a `public class YourClassName {...}` declaration. The application will automatically detect this class name. Your code should include a `public static void main(String[] args)` method within this public class if you want it to be executable.

2.  Click the **"Compile and Run"** button.

3.  The application will process your code and display the results:
    *   **Compilation Status**: Indicates whether the compilation was successful or failed.
    *   **Compiler Diagnostics**: Shows any errors or warnings reported by the compiler.
    *   **Program Output**: Displays any output produced by your program (from `System.out` or `System.err`).

## Important Security Considerations
Executing arbitrary code received from users is a significant security risk. This application does not implement advanced sandboxing or security measures to mitigate these risks.
-   Only run this application in a trusted environment.
-   Do not expose it to untrusted users or networks.
-   Be cautious about the code you compile and run.
