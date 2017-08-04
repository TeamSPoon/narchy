package nars.op.stm;

import jcog.data.MutableInteger;
import nars.NAR;
import nars.Task;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * Created by me on 9/16/16.
 */
abstract public class STM /*extends BagBuffer<Task>*/ implements Consumer<Task> {

    @NotNull
    public final NAR nar;
    boolean allowNonInput;
    public final MutableInteger capacity;


    protected STM(@NotNull NAR nar, MutableInteger capacity) {
        super();
        this.nar = nar;
        this.capacity = capacity;
        nar.onTask(t -> {
            if (stmLinkable(t, allowNonInput))
                accept(t);
        });
        nar.eventClear.on(n -> clear());
    }

    static boolean stmLinkable(@NotNull Task newEvent, boolean allowNonInput) {
        return ( !newEvent.isEternal() && (allowNonInput || newEvent.isInput()));
    }

    abstract public void clear();

    @Override
    public abstract void accept(@NotNull Task t);

}
