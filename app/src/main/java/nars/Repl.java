package nars;

import nars.op.nlp.Hear;
import org.fusesource.jansi.AnsiConsole;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.history.history.MemoryHistory;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;
import java.io.PrintStream;

/**
 * TODO
 * add volume +/- hotkeys that throttle output
 * and throttle CPU
 */
public class Repl {

    public Repl(NAR nar) throws IOException {

        AnsiConsole.systemInstall();

        TerminalBuilder tb = TerminalBuilder.builder();

//        rightPrompt = new AttributedStringBuilder()
//                .style(AttributedStyle.DEFAULT.background(AttributedStyle.RED))
//                .append(LocalDate.now().format(DateTimeFormatter.ISO_DATE))
//                .append("\n")
//                .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.RED | AttributedStyle.BRIGHT))
//                .append(LocalTime.now().format(new DateTimeFormatterBuilder()
//                        .appendValue(HOUR_OF_DAY, 2)
//                        .appendLiteral(':')
//                        .appendValue(MINUTE_OF_HOUR, 2)
//                        .toFormatter()))
//                .toAnsi();


        Terminal terminal = tb
                //.system(true)
                .encoding("UTF-8")
                .build();

//        String p = new AttributedStringBuilder()
//                .style(AttributedStyle.DEFAULT.background(AttributedStyle.YELLOW))
//                .append("foo")
//                .style(AttributedStyle.DEFAULT)
//                .append("@bar")
//                .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN))
//                .append("\nbaz")
//                .style(AttributedStyle.DEFAULT)
//                .append("> ").toAnsi(terminal);


        LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                //.completer(completer)
                .history(new MemoryHistory())
                .appName("NARchy")
                .build();

        nar.logPriMin(new PrintStream(reader.getTerminal().output()), 0.1f);

        reader.setOpt(LineReader.Option.AUTO_FRESH_LINE);
        reader.setOpt(LineReader.Option.AUTO_LIST);


        NARLoop loop = nar.startFPS(20f);
        while (true) {

            terminal.flush();

            String line = null;
            try {

                line = reader.readLine("> ");
                if (line == null)
                    break;

            } catch (UserInterruptException | EndOfFileException e) {
                // Ignore
                break;
            }


            line = line.trim();
            if (!line.isEmpty()) {

                //terminal.writer().println("\u001B[33m======>\u001B[0m\"" + line + "\"");

                try {
                    nar.input(line);
                } catch (Exception e) {
                    try {
                        Hear.hear(nar, line, "console", 100);
                    } catch (Exception e1) {
                        terminal.writer().println(e1);
                    }
                }

            }

            // If we input the special word then we will mask
            // the next line.
            //ParsedLine pl = reader.getParser().parse(line, 0);
            //String line = pl.line();

        }

        loop.stop();


    }

    public static void main(String[] args) throws IOException {
        //Default e = new Default();
        //new InterNAR2(e, 15000);

        NAR d = NARchy.ui();

        //InterNAR2 ii = new InterNAR2(d, 15001);
        //ii.connect("tcp://localhost:15000");

        //Util.sleep(1000);
        //ii.send("test");

        new Repl(d);
    }


}
