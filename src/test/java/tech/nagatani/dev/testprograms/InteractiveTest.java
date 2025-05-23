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
