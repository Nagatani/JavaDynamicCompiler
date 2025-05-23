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
    // package tech.nagatani.dev.testprograms; // Optional to include package in README example

    import java.util.Scanner;

    public class InteractiveTest {
        public static void main(String[] args) {
            @SuppressWarnings("resource")
            Scanner scanner = new Scanner(System.in);
            System.out.println("SERVER_MSG: Interactive Test Program Started.");
            System.out.println("SERVER_MSG: Please enter your name:");
            String name = scanner.nextLine();
            System.out.println("SERVER_MSG: Hello, " + name + "!");
            System.out.println("SERVER_MSG: Program finished.");
        }
    }
    ```
4.  Click "Compile and Run".
5.  You should be taken to the interactive console page.
6.  Expected output:
    ```
    SERVER_MSG: Interactive Test Program Started.
    SERVER_MSG: Please enter your name:
    ```
7.  In the input field at the bottom of the page, type your name (e.g., "Jules") and press Enter or click "Send".
8.  Expected output (after your input and program response):
    ```
    > Jules 
    SERVER_MSG: Hello, Jules!
    SERVER_MSG: Program finished.
    Program finished with exit code: 0 
    ```
    *(Note: The `> Jules` part is an echo from the client-side JavaScript, and the `Program finished with exit code: 0` is sent by the server upon process termination.)*
9.  The session should then indicate that the program has finished, and the input field might become disabled.

## Future Development: Enhanced GUI Support

The current system has a basic timeout mechanism for Java applications that seem to be GUI-based (e.g., using Swing or AWT). If a suspected GUI application doesn't terminate within a short period (currently 10 seconds), it's automatically stopped. This is a rudimentary way to prevent server resources from being held indefinitely by non-interactive GUI programs.

True interactive support for Java GUI applications in a web browser is a complex challenge. Here are some potential avenues for future exploration:

*   **Xvfb (X Virtual FrameBuffer):** On Linux/macOS servers, Xvfb could be used to create a headless display environment. The Java Swing application could run in this virtual display, and its window could be captured as images or a video stream and sent to the web client. User interactions (clicks, keystrokes) from the web client would need to be translated into X11 events.
*   **`java.awt.Robot` for Screenshots:** The `java.awt.Robot` class can programmatically capture screenshots of AWT/Swing windows. This could provide periodic visual updates to the client, though it wouldn't be fully interactive like a video stream.
*   **VNC-like Streaming:** More complex solutions could involve integrating or building a VNC-like server that streams the GUI application's display to a VNC client (or custom component) embedded in the web page. This would offer better interactivity.
*   **WebAssembly (WASM) Port of Java UI Toolkit:** A highly ambitious approach could involve projects aiming to run Java and Swing/AWT (or parts of it) directly in the browser using WebAssembly, potentially with a canvas target. This is a significant research and development area.
*   **Focus on Specific UI Components:** Instead of full desktop emulation, future work could focus on capturing and representing a limited set of common Swing components (like dialogs, buttons, text areas) as HTML elements, with interactions relayed back to the server. This would be a partial emulation.

These approaches represent significant undertakings and would require substantial additional research, development, and infrastructure considerations.
