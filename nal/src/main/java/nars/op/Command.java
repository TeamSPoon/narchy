package nars.op;

import nars.$;
import nars.NAR;
import nars.Op;
import nars.Task;
import nars.task.MutableTask;
import nars.term.Compound;
import nars.term.Term;
import nars.term.atom.Atomic;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintWriter;
import java.io.StringWriter;

import static nars.Op.COMMAND;

/**
 * Operator interface specifically for Command ';' punctuation
 */
@FunctionalInterface
public interface Command extends Operator {

    @Override
    default @Nullable Task run(@NotNull Task t, @NotNull NAR nar) {
        if (t.punc() == COMMAND) {
            Compound c = t.term();
            try {
                run((Atomic) (c.term(1)), ((Compound) (t.term(0))).terms(), nar);
                return t;
            } catch (Throwable error) {
                StringWriter ss = new StringWriter();
                ExceptionUtils.printRootCauseStackTrace(error, new PrintWriter(ss));
                return logTask(ss.toString());
            }
        }
        return null;
    }

    void run(@NotNull Atomic op, @NotNull Term[] args, @NotNull NAR nar);

    static Task task(Compound content) {
        return new MutableTask(content, Op.COMMAND, null);
    }

    static Task logTask(@NotNull Term content) {
        return Command.task($.func("log", content));
    }

    static Task logTask(@NotNull String msg) {
        return Command.logTask($.quote(msg));
    }

    static void log(NAR nar, @NotNull Term content) {
        nar.inputLater( Command.logTask(content) );
    }

    static void log(NAR nar, @NotNull String msg) {
        nar.inputLater( Command.logTask(msg) );
    }

}
