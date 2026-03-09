package ai.pageindex.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.PrintStream;

@SpringBootApplication
public class PageIndexApp {

    public static void main(String[] args) {
        installProgressInterceptor();
        SpringApplication app = new SpringApplication(PageIndexApp.class);
        app.run(args);
    }

    /**
     * Wraps System.out so every println() is also routed to ProgressHub
     * for the thread's current job (if any). Zero changes to pipeline code.
     */
    private static void installProgressInterceptor() {
        PrintStream original = System.out;
        PrintStream intercepted = new PrintStream(original, true) {
            private void emit(String s) { ProgressHub.emit(s != null ? s : ""); }

            @Override public void println(String x)  { super.println(x);            emit(x); }
            @Override public void println(Object x)  { String s = String.valueOf(x); super.println(s); emit(s); }
            @Override public void println()           { super.println();              emit(""); }
            @Override public void print(String x)    { super.print(x); }
        };
        System.setOut(intercepted);
    }
}
