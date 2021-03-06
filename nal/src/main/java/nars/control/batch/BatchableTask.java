package nars.control.batch;

import jcog.data.byt.DynBytes;
import jcog.pri.Prioritizable;
import jcog.pri.Weight;
import jcog.util.ArrayUtils;
import nars.NAR;
import nars.task.ITask;

import java.util.Arrays;
import java.util.function.BiConsumer;

import static jcog.data.byt.RecycledDynBytes.tmpKey;
import static nars.Op.COMMAND;

public abstract class BatchableTask extends Weight implements ITask, Prioritizable {

    /** key cache */
    public byte[] key = null;
    private int hash = 0;

    /** singleton */
    protected BatchableTask(float pri) {
        super(pri);
        key(null, ArrayUtils.EMPTY_OBJECT_ARRAY);
    }

    protected <A> BatchableTask(float pri, BiConsumer<A, DynBytes> argWriter, A... args) {
        super(pri);
        key(argWriter, args);
    }

    @Override
    public final byte punc() {
        return COMMAND;
    }

    final public ITask next(NAR n) {
        throw new UnsupportedOperationException();
    }

//    /** write a homoiconizing key describing the computation represented by this task */
//    abstract public void key(DynBytes/*ByteArrayDataOutput*/ target);

//        abstract protected BatchableTask merge(BatchableTask b);

    /** run, adding any subsequent tasks to the provided task queue */
    abstract public void run(TaskBatch.TaskQueue next);


    protected <A> byte[] key(BiConsumer<A, DynBytes> argWriter, A... args) {
        if (args.length == 0) {
            hash = 0;
            return key = ArrayUtils.EMPTY_BYTE_ARRAY;
        }

        DynBytes x = tmpKey();
        x.writeByte(args.length);
        for (A a : args) {
            argWriter.accept(a, x);
        }
        key = x.arrayCopy();
        hash = x.hashCode();
        x.clear();
        return key;
    }

    private byte[] key() {
//        if (key==null) {
//            DynBytes x = tmpKey();
//            key(x);
//            key = x.arrayCopy();
//            hash = x.hashCode();
//            x.clear();
//        }
        assert(key!=null);
        return key;
    }

    @Override
    public final boolean equals(Object obj) {
        return this==obj || Arrays.equals(key(), ((BatchableTask)obj).key());
    }

    @Override
    public final int hashCode() {
        key();
        return hash;
    }
}
