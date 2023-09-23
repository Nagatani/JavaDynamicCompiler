package tech.nagatani.dev;

public record Result(
        boolean compileSuccess,
        Iterable<String> compileOutput,
        Iterable<String> output) {
}
