package tech.nagatani.dev.testprograms;

import java.util.Scanner;

public class InteractiveTest {
    public static void main(String[] args) {
        @SuppressWarnings("resource") // Suppress warning for System.in Scanner
        Scanner scanner = new Scanner(System.in);
        System.out.println("SERVER_MSG: Interactive Test Program Started.");
        System.out.println("SERVER_MSG: Please enter your name:");
        String name = scanner.nextLine(); // Program will wait here for input
        System.out.println("SERVER_MSG: Hello, " + name + "!");
        System.out.println("SERVER_MSG: Program finished.");
    }
}
