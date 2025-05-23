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

## Testing Interactive Console

1.  Run the JavaCompiler web application.
2.  Navigate to the application in your browser (typically `http://localhost:8080/`).
3.  Copy the content of `src/test/java/tech/nagatani/dev/testprograms/InteractiveTest.java` (shown below) into the source code input area.
    ```java
    package tech.nagatani.dev.testprograms;

    import java.util.Scanner;

    public class InteractiveTest {
        public static void main(String[] args) {
            @SuppressWarnings("resource") // Suppress warning for System.in Scanner
            Scanner scanner = new Scanner(System.in);
            System.out.println("SERVER_MSG: インタラクティブテストプログラムが開始されました。"); // "Interactive Test Program Started."
            System.out.println("SERVER_MSG: お名前を入力してください:"); // "Please enter your name:"
            String name = scanner.nextLine(); // Program will wait here for input
            System.out.println("SERVER_MSG: こんにちは、" + name + "さん！"); // "Hello, [name]!"
            System.out.println("SERVER_MSG: 何か日本語で入力してみてください:"); // "Please enter something in Japanese:"
            String japaneseInput = scanner.nextLine();
            System.out.println("SERVER_MSG: 入力された日本語: " + japaneseInput); // "Entered Japanese: [japaneseInput]"
            System.out.println("SERVER_MSG: プログラム終了。"); // "Program finished."
        }
    }
    ```
4.  Click "Compile and Run".
5.  You should be taken to the interactive console page.
6.  Expected initial output:
    ```
    SERVER_MSG: インタラクティブテストプログラムが開始されました。
    SERVER_MSG: お名前を入力してください:
    ```
7.  In the input field, type a name (e.g., "Jules" or a Japanese name like "ジュール") and press Enter or click "Send".
8.  Expected output after entering name (using "ジュール" as example):
    ```
    > ジュール
    SERVER_MSG: こんにちは、ジュールさん！
    SERVER_MSG: 何か日本語で入力してみてください:
    ```
    *(Note: The `> ジュール` part is an echo from the client-side JavaScript.)*
9.  In the input field, type some Japanese text (e.g., "こんにちは世界") and press Enter or click "Send".
10. Expected output after entering Japanese text:
    ```
    > こんにちは世界
    SERVER_MSG: 入力された日本語: こんにちは世界
    SERVER_MSG: プログラム終了。
    Program finished with exit code: 0 
    ```
    *(Note: The `> こんにちは世界` part is an echo from the client-side JavaScript, and the `Program finished with exit code: 0` is sent by the server upon process termination.)*
11. The session should then indicate that the program has finished. This test verifies that multi-byte characters (like Japanese) are correctly handled in the interactive console.

### Conceptual Outline for Automated Testing (Future Work)

Automated testing for the interactive console feature would typically involve:

1.  **Test Framework:** Use a framework like Selenium for browser automation to submit the code and navigate to the interactive console page.
2.  **WebSocket Client:** Employ a Java WebSocket client (e.g., from a library like `org.java-websocket` or Spring's WebSocket client) to connect to the `/ws/execute?id=<executionId>` endpoint. The `executionId` would need to be scraped from the interactive console page after code submission or obtained through other means if the test directly triggers backend logic.
3.  **Coordination:**
    *   The Selenium test would submit the Java code.
    *   Once the interactive console page loads, the test would extract the `executionId`.
    *   The Java WebSocket client would connect using this `executionId`.
4.  **Interaction & Assertions:**
    *   The WebSocket client would wait for the initial "SERVER_MSG: Please enter your name:" message.
    *   It would then send a test name (e.g., "TestUser") over the WebSocket.
    *   It would then assert that it receives "SERVER_MSG: Hello, TestUser!" and "SERVER_MSG: Program finished." followed by the "Program finished with exit code: 0" message.
5.  **Challenges:** Timing and synchronization between the browser actions (Selenium) and WebSocket interactions would be critical. Managing process lifecycles and ensuring cleanup after tests would also be important.

## Future Development: Enhanced GUI Support

The current system has a basic timeout mechanism for Java applications that seem to be GUI-based (e.g., using Swing or AWT). If a suspected GUI application doesn't terminate within a short period (currently 10 seconds), it's automatically stopped. This is a rudimentary way to prevent server resources from being held indefinitely by non-interactive GUI programs.

True interactive support for Java GUI applications in a web browser is a complex challenge. Here are some potential avenues for future exploration:

*   **Xvfb (X Virtual FrameBuffer):** On Linux/macOS servers, Xvfb could be used to create a headless display environment. The Java Swing application could run in this virtual display, and its window could be captured as images or a video stream and sent to the web client. User interactions (clicks, keystrokes) from the web client would need to be translated into X11 events.
*   **`java.awt.Robot` for Screenshots:** The `java.awt.Robot` class can programmatically capture screenshots of AWT/Swing windows. This could provide periodic visual updates to the client, though it wouldn't be fully interactive like a video stream.
*   **VNC-like Streaming:** More complex solutions could involve integrating or building a VNC-like server that streams the GUI application's display to a VNC client (or custom component) embedded in the web page. This would offer better interactivity.
*   **WebAssembly (WASM) Port of Java UI Toolkit:** A highly ambitious approach could involve projects aiming to run Java and Swing/AWT (or parts of it) directly in the browser using WebAssembly, potentially with a canvas target. This is a significant research and development area.
*   **Focus on Specific UI Components:** Instead of full desktop emulation, future work could focus on capturing and representing a limited set of common Swing components (like dialogs, buttons, text areas) as HTML elements, with interactions relayed back to the server. This would be a partial emulation.

These approaches represent significant undertakings and would require substantial additional research, development, and infrastructure considerations.

## Debugging Character Encoding Issues

To help diagnose and troubleshoot potential character encoding issues, detailed logging has been added to both the server-side and client-side components of the interactive console. These logs provide a trace of the data as it flows from the client to the server, into the executed Java program, back from the program, and finally back to the client.

### Accessing the Logs

*   **Server-Side Logs:**
    Server-side logs are printed to the console where you launched the Spring Boot Java application. These log messages are typically prefixed with:
    *   `Server WS RCV:` (when the server's WebSocket handler receives a message from the client)
    *   `Server WS SEND to Process STDIN (...)` (showing the raw bytes and interpreted string being sent to the executed program's input)
    *   `Server Process STDOUT READ:` (when the server reads a line from the executed program's standard output)
    *   `Server Process STDERR READ:` (when the server reads a line from the executed program's standard error)
    *   `Server WS SEND to Client (...)` (when the server's WebSocket handler sends a message back to the client)

*   **Client-Side Logs:**
    Client-side logs are displayed in your web browser's Developer Console. To open the Developer Console:
    *   In most browsers (Chrome, Firefox, Edge), press `F12`.
    *   Alternatively, you can usually right-click on the page and select "Inspect" or "Inspect Element", then navigate to the "Console" tab.
    *   These log messages are prefixed with:
        *   `Client WS SEND:` (when the client sends a message to the server via WebSocket)
        *   `Client WS RCV:` (when the client receives a message from the server via WebSocket)

### Reporting Issues

If you continue to experience character encoding problems, please perform the test using Japanese characters as described in the "Testing Interactive Console" section. Then, copy and paste the relevant sections from BOTH the server-side console output AND the browser's Developer Console output when reporting the issue. This will provide valuable information for further diagnosis.
