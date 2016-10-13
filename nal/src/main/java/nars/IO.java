package nars;

import nars.concept.AtomConcept;
import nars.concept.CompoundConcept;
import nars.index.TermIndex;
import nars.task.AbstractTask;
import nars.task.MutableTask;
import nars.term.Compound;
import nars.term.Term;
import nars.term.Terms;
import nars.term.atom.Atom;
import nars.term.atom.Atomic;
import nars.term.compound.GenericCompound;
import nars.term.compound.Statement;
import nars.term.container.TermContainer;
import nars.term.var.AbstractVariable;
import nars.term.var.GenericVariable;
import nars.time.Tense;
import nars.truth.Truth;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.nustaq.serialization.*;

import java.io.*;
import java.util.function.Function;

import static nars.Op.ATOM;
import static nars.Symbols.*;
import static nars.time.Tense.DTERNAL;
import static nars.time.Tense.XTERNAL;

/**
 * Created by me on 5/29/16.
 */
public class IO {


    public static final byte SPECIAL_OP = (byte) (Op.values().length + 1);

    static boolean hasTruth(char punc) {
        return punc == Symbols.BELIEF || punc == Symbols.GOAL;
    }


    @NotNull
    public static MutableTask readTask(@NotNull DataInput in, @NotNull TermIndex t) throws IOException {

        Term term = readTerm(in, t);

        //TODO combine these into one byte
        char punc = (char) in.readByte();
        int eviLength = in.readByte();

        Truth truth;
        if (hasTruth(punc)) {
            truth = readTruth(in);
        } else {
            truth = null;
        }

        long occ = in.readLong();
        long cre = in.readLong();


        long[] evi = new long[eviLength];
        for (int i = 0; i < eviLength; i++) {
            evi[i] = in.readLong();
        }

        float pri = in.readFloat();
        float dur = in.readFloat();
        float qua = in.readFloat();

        MutableTask mm = new MutableTask(term, punc, truth).time(cre, occ);
        mm.evidence(evi);
        mm.setBudget(pri, dur, qua);
        return mm;
    }

    @NotNull
    public static Truth readTruth(@NotNull DataInput in) throws IOException {
        float f = in.readFloat();
        float c = in.readFloat();
        return $.t(f, c);
    }

    public static void writeTask(@NotNull DataOutput out, @NotNull Task t) throws IOException {

        writeTerm(out, t.term());

        char p = t.punc();
        long[] evi = t.evidence();
        int evil;
        evil = evi.length;

        //TODO combine these into one byte
        out.writeByte(p);
        out.writeByte(evil);

        if (hasTruth(p)) {
            writeTruth(out, t.freq(), t.conf());
        }
        out.writeLong(t.occurrence());
        out.writeLong(t.creation());

        for (int i = 0; i < evil; i++)
            out.writeLong(evi[i]);

        out.writeFloat(t.pri());
        out.writeFloat(t.dur());
        out.writeFloat(t.qua());
    }

    public static void writeTruth(@NotNull DataOutput out, float freq, float conf) throws IOException {
        out.writeFloat(freq);
        out.writeFloat(conf);
    }

    public static void writeAtomic(@NotNull DataOutput out, @NotNull Atomic a) throws IOException {
        out.writeUTF(a.toString());
    }

    @Nullable
    public static Atomic readVariable(@NotNull DataInput in, @NotNull Op o, @NotNull TermIndex t) throws IOException {
        return $.v(o, in.readByte());
    }

    public static void writeVariable(@NotNull DataOutput out, @NotNull AbstractVariable v) throws IOException {
        out.writeByte(v.id);
    }

    @Nullable
    public static Atomic readAtomic(@NotNull DataInput in, @NotNull Op o, @NotNull TermIndex t) throws IOException {
        String s = in.readUTF();
        Term key;
        switch (o) {
            case ATOM:
                key = new Atom(s);
                break;
            default:
                throw new UnsupportedOperationException();
        }

        return (Atomic) t.get(key, true);
    }


    /**
     * called by readTerm after determining the op type
     */
    @Nullable
    public static Term readTerm(@NotNull DataInput in, @NotNull TermIndex t) throws IOException {

        byte ob = in.readByte();
        if (ob == SPECIAL_OP)
            return readSpecialTerm(in, t);

        Op o = Op.values()[ob];
        if (o.var)
            return readVariable(in, o, t);
        else if (o.atomic)
            return readAtomic(in, o, t);
        else
            return readCompound(in, o, t);
    }

    public
    @Nullable
    static Term readSpecialTerm(@NotNull DataInput in, @NotNull TermIndex t) throws IOException {
        String toParse = in.readUTF();
        Term x = t.parseRaw(toParse);
        if (x == null)
            throw new IOException("Undecoded term: " + toParse);
        return x;
    }

    public static void writeTerm(@NotNull DataOutput out, @NotNull Term term) throws IOException {

        if (isSpecial(term)) {
            out.writeByte(SPECIAL_OP);
            out.writeUTF(term.toString());
            return;
        }

        out.writeByte(term.op().ordinal());

        if (term instanceof Atomic) {

            if (term instanceof AbstractVariable) {
                writeVariable(out, (AbstractVariable) term);
            } else {
                writeAtomic(out, (Atomic) term);
            }
        } else
            writeCompound(out, (Compound) term);
    }

    public static void writeTermSeq(@NotNull DataOutput out, @NotNull Term term) throws IOException {


        if (term instanceof Atomic) {
            if (isSpecial(term)) {
                out.writeByte(SPECIAL_OP);
            }
            //out.writeUTF(term.toString());
            writeUTFWithoutLength(term.toString(), out);
            //out.writeByte(term.op().ordinal()); //put operator last
        } else {
            writeCompoundSeq(out, (Compound) term);
        }
    }

    public static boolean isSpecial(@NotNull Term term) {
        return term instanceof GenericVariable;
    }

    public static void writeCompound(@NotNull DataOutput out, @NotNull Compound c) throws IOException {

        //how many subterms to follow
        writeTermContainer(out, c.subterms());

        //TODO write only a byte for image, int for temporal
        if (c.op().image || c.op().temporal)
            out.writeInt(c.dt());
    }

    public static void writeCompoundSeq(@NotNull DataOutput out, @NotNull Compound c) throws IOException {

        writeTermContainerSeq(out, c.subterms());

        @NotNull Op o = c.op();
        out.writeByte(o.ordinal()); //put operator last
        if (o.image)
            out.writeByte(c.dt());
        /* else if (o.temporal..) */

    }

    static void writeTermContainer(@NotNull DataOutput out, @NotNull TermContainer c) throws IOException {
        int siz = c.size();
        out.writeByte(siz);
        for (int i = 0; i < siz; i++) {
            writeTerm(out, c.term(i));
        }
    }

    static void writeTermContainerSeq(@NotNull DataOutput out, @NotNull TermContainer c) throws IOException {
        int siz = c.size();

        for (int i = 0; i < siz; i++) {
            writeTermSeq(out, c.term(i));
//            if (i < siz-1)
//                out.writeByte(',');
        }

    }

    @Nullable
    public static Term[] readTermContainer(@NotNull DataInput in, @NotNull TermIndex t) throws IOException {
        int siz = in.readByte();
        Term[] s = new Term[siz];
        for (int i = 0; i < siz; i++) {
            s[i] = readTerm(in, t);
        }

        return s;
    }

    /**
     * called by readTerm after determining the op type
     * TODO make a version which reads directlyinto TermIndex
     */
    @Nullable
    static Compound readCompound(@NotNull DataInput in, @NotNull Op o, @NotNull TermIndex t) throws IOException {

        Term[] v = readTermContainer(in, t);

        int dt = DTERNAL;
        if (o.hasNumeric) //TODO o.hasNumeric
            dt = in.readInt();

        return (Compound) t.the(o, dt, v);
//        if (key == null)
//            throw new UnsupportedOperationException();
//        return (Compound) t.normalize(key, true);
    }

    public static byte[] asBytes(@NotNull Task t) {
        try {
            ByteArrayOutputStream bs = new ByteArrayOutputStream();
            IO.writeTask(new DataOutputStream(bs), t);
            return bs.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] asBytes(@NotNull Term t) {
        try {
            ByteArrayOutputStream bs = new ByteArrayOutputStream();
            IO.writeTerm(new DataOutputStream(bs), t);
            return bs.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Task taskFromBytes(@NotNull byte[] b, @NotNull TermIndex index) {
        try {
            return IO.readTask(new DataInputStream(new ByteArrayInputStream(b)), index);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static Term termFromBytes(@NotNull byte[] b, @NotNull TermIndex index) {
        try {
            return IO.readTerm(new DataInputStream(new ByteArrayInputStream(b)), index);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * serialization and deserialization of terms, tasks, etc.
     */
    public static class DefaultCodec extends FSTConfiguration {

        final TermIndex index;

        public DefaultCodec(TermIndex t) {
            super(null);

            this.index = t;

            createDefaultConfiguration();
            //setStreamCoderFactory(new FBinaryStreamCoderFactory(this));
            setForceSerializable(true);


            //setCrossPlatform(false);
            setShareReferences(false);
            setPreferSpeed(true);
            setCrossPlatform(false);


            registerClass(Atom.class, GenericCompound.class,
                    AbstractTask.class,
                    Term[].class,
                    TermContainer.class,
                    //long[].class, char.class,
                    Op.class);


            registerSerializer(AbstractTask.class, new FSTBasicObjectSerializer() {

                @Override
                public void readObject(FSTObjectInput in, Object toRead, FSTClazzInfo clzInfo, FSTClazzInfo.FSTFieldInfo referencedBy) throws Exception {
                }

                @NotNull
                @Override
                public Object instantiate(Class objectClass, @NotNull FSTObjectInput in, FSTClazzInfo serializationInfo, FSTClazzInfo.FSTFieldInfo referencee, int streamPosition) throws IOException {
                    return readTask(in, index);
                }

                @Override
                public void writeObject(@NotNull FSTObjectOutput out, Object toWrite, FSTClazzInfo clzInfo, FSTClazzInfo.FSTFieldInfo referencedBy, int streamPosition) throws IOException {
                    writeTask(out, (Task) toWrite);
                }
            }, true);


            registerSerializer(Atom.class, terms, true);
            registerSerializer(Atomic.class, terms, true);

            registerSerializer(GenericCompound.class, terms, true);

            registerSerializer(AtomConcept.class, terms, true);
            registerSerializer(CompoundConcept.class, terms, true);

        }

//        @Nullable
//        final FSTBasicObjectSerializer termContainers = new FSTBasicObjectSerializer() {
//
//            @Override
//            public void readObject(FSTObjectInput in, Object toRead, FSTClazzInfo clzInfo, FSTClazzInfo.FSTFieldInfo referencedBy) throws Exception {
//            }
//
//            @Nullable
//            @Override
//            public Object instantiate(Class objectClass, @NotNull FSTObjectInput in, FSTClazzInfo serializationInfo, FSTClazzInfo.FSTFieldInfo referencee, int streamPosition) throws IOException {
//                return readTermContainer(in, index);
//            }
//
//            @Override
//            public void writeObject(@NotNull FSTObjectOutput out, Object toWrite, FSTClazzInfo clzInfo, FSTClazzInfo.FSTFieldInfo referencedBy, int streamPosition) throws IOException {
//                writeTermContainer(out, (TermContainer) toWrite);
//            }
//        };

        @Nullable
        final FSTBasicObjectSerializer terms = new FSTBasicObjectSerializer() {

            @Override
            public void readObject(FSTObjectInput in, Object toRead, FSTClazzInfo clzInfo, FSTClazzInfo.FSTFieldInfo referencedBy) throws Exception {
            }

            @Nullable
            @Override
            public Object instantiate(Class objectClass, @NotNull FSTObjectInput in, FSTClazzInfo serializationInfo, FSTClazzInfo.FSTFieldInfo referencee, int streamPosition) throws IOException {
                return readTerm(in, index);
            }

            @Override
            public void writeObject(@NotNull FSTObjectOutput out, Object toWrite, FSTClazzInfo clzInfo, FSTClazzInfo.FSTFieldInfo referencedBy, int streamPosition) throws IOException {
                writeTerm(out, (Term) toWrite);
            }
        };


    }


    public interface Printer {

        //    static void appendSeparator(@NotNull Appendable p) throws IOException {
        //        p.append(ARGUMENT_SEPARATOR);
        //        //if (pretty) p.append(' ');
        //    }
        //
        //    static void writeCompound1(@NotNull Op op, @NotNull Term singleTerm, @NotNull Appendable writer) throws IOException {
        //        writer.append(COMPOUND_TERM_OPENER);
        //        writer.append(op.str);
        //        writer.append(ARGUMENT_SEPARATOR);
        //        singleTerm.append(writer);
        //        writer.append(COMPOUND_TERM_CLOSER);
        //    }

        static void compoundAppend(@NotNull Compound c, @NotNull Appendable p) throws IOException {

            p.append(COMPOUND_TERM_OPENER);

            c.op().append(c, p);

            if (c.size() == 1)
                p.append(ARGUMENT_SEPARATOR);

            appendArgs(c, p);

            appendCloser(p);

        }

        static void compoundAppend(String o, @NotNull TermContainer c, @NotNull Function<Term, Term> filter, @NotNull Appendable p) throws IOException {

            p.append(COMPOUND_TERM_OPENER);

            p.append(o);

            if (c.size() == 1)
                p.append(ARGUMENT_SEPARATOR);

            appendArgs(c, filter, p);

            appendCloser(p);

        }


        static void appendArgs(@NotNull Compound c, @NotNull Appendable p) throws IOException {
            int nterms = c.size();

            boolean bb = nterms > 1;
            for (int i = 0; i < nterms; i++) {
                if ((i != 0) || bb) {
                    p.append(Symbols.ARGUMENT_SEPARATOR);
                }
                c.term(i).append(p);
            }
        }

        static void appendArgs(@NotNull TermContainer c, @NotNull Function<Term, Term> filter, @NotNull Appendable p) throws IOException {
            int nterms = c.size();

            boolean bb = nterms > 1;
            for (int i = 0; i < nterms; i++) {
                if ((i != 0) || bb) {
                    p.append(Symbols.ARGUMENT_SEPARATOR);
                }
                filter.apply(c.term(i)).append(p);
            }
        }

        static void appendCloser(@NotNull Appendable p) throws IOException {
            p.append(COMPOUND_TERM_CLOSER);
        }

        static void append(@NotNull Compound c, @NotNull Appendable p) throws IOException {
            final Op op = c.op();

            switch (op) {

                case SETi:
                case SETe:
                    setAppend(c, p);
                    return;
                case PROD:
                    productAppend(c, p);
                    return;
                case IMGi:
                case IMGe:
                    imageAppend(c, p);
                    return;
                //case INHERIT: inheritAppend(c, p, pretty); return;
                //case SIMILAR: similarAppend(c, p, pretty); return;

                case NEG:
                    //special case disjunction: (--,(&&,.....))
                    if (Terms.isDisjunction(c)) {
                        compoundAppend(Op.DISJ.toString(), ((Compound) c.term(0)).subterms(), $::neg, p);
                        return;
                    }
            }

            if (op.statement || c.size() == 2) {
                Term subj = c.term(0);

                //special case: functional form
                if (subj.op() == Op.PROD) {
                    Term pred = c.term(1);
                    Op pOp = pred.op();
                    if (pOp == ATOM) {
                        operationAppend((Compound) c.term(0), (Atomic) pred, p);
                        return;
                    }
                }

                statementAppend(c, p, op);

            } else {
                compoundAppend(c, p);
            }
        }

//        static void inheritAppend(@NotNull Compound c, @NotNull Appendable p) throws IOException {
//            Term a = Statement.subj(c);
//            Term b = Statement.pred(c);
//
//            p.append(Symbols.COMPOUND_TERM_OPENER);
//            b.append(p);
//            p.append(Symbols.INHERIT_SEPARATOR);
//            a.append(p);
//            p.append(Symbols.COMPOUND_TERM_CLOSER);
//        }
//        static void similarAppend(@NotNull Compound c, @NotNull Appendable p) throws IOException {
//            Term a = Statement.subj(c);
//            Term b = Statement.pred(c);
//
//            p.append(Symbols.COMPOUND_TERM_OPENER);
//            a.append(p);
//            p.append(Symbols.SIMILAR_SEPARATOR);
//            b.append(p);
//            p.append(Symbols.COMPOUND_TERM_CLOSER);
//        }

        static void statementAppend(@NotNull Compound c, @NotNull Appendable p, @NotNull Op op) throws IOException {
            Term a = Statement.subj(c);
            Term b = Statement.pred(c);

            int dt = c.dt();
            boolean reversedDT;
            if (dt < 0 && c.isCommutative() && dt!=DTERNAL && dt!=XTERNAL) {
                reversedDT = true;
                Term x = a;
                a = b;
                b = x;
            } else {
                reversedDT = false;
            }

            p.append(COMPOUND_TERM_OPENER);
            a.append(p);

            op.append(c, p, reversedDT);

            b.append(p);

            p.append(COMPOUND_TERM_CLOSER);
        }


        static void productAppend(@NotNull Compound product, @NotNull Appendable p) throws IOException {

            int s = product.size();
            p.append(COMPOUND_TERM_OPENER);
            for (int i = 0; i < s; i++) {
                product.term(i).append(p);
                if (i < s - 1) {
                    p.append(",");
                }
            }
            p.append(COMPOUND_TERM_CLOSER);
        }

        static void imageAppend(@NotNull Compound image, @NotNull Appendable p) throws IOException {

            int len = image.size();

            p.append(COMPOUND_TERM_OPENER);
            p.append(image.op().str);

            int relationIndex = image.dt();
            int i;
            for (i = 0; i < len; i++) {
                Term tt = image.term(i);

                p.append(ARGUMENT_SEPARATOR);
                //if (pretty) p.append(' ');

                if (i == relationIndex) {
                    p.append(Symbols.IMAGE_PLACE_HOLDER);
                    p.append(ARGUMENT_SEPARATOR);
                    //if (pretty) p.append(' ');
                }

                tt.append(p);
            }
            if (i == relationIndex) {
                p.append(ARGUMENT_SEPARATOR);
                //if (pretty) p.append(' ');
                p.append(Symbols.IMAGE_PLACE_HOLDER);
            }

            p.append(COMPOUND_TERM_CLOSER);

        }

        static void setAppend(@NotNull Compound set, @NotNull Appendable p) throws IOException {

            int len = set.size();

            //duplicated from above, dont want to store this as a field in the class
            char opener, closer;
            if (set.op() == Op.SETe) {
                opener = Op.SETe.ch;
                closer = Symbols.SET_EXT_CLOSER;
            } else {
                opener = Op.SETi.ch;
                closer = Symbols.SET_INT_CLOSER;
            }

            p.append(opener);
            for (int i = 0; i < len; i++) {
                Term tt = set.term(i);
                if (i != 0) p.append(Symbols.ARGUMENT_SEPARATOR);
                tt.append(p);
            }
            p.append(closer);
        }

        static void operationAppend(@NotNull Compound argsProduct, @NotNull Atomic operator, @NotNull Appendable p) throws IOException {

            //Term predTerm = operator.identifier(); //getOperatorTerm();
            //        if ((predTerm.volume() != 1) || (predTerm.hasVar())) {
            //            //if the predicate (operator) of this operation (inheritance) is not an atom, use Inheritance's append format
            //            appendSeparator(p, pretty);
            //            return;
            //        }


            Term[] xt = argsProduct.terms();

            p.append(operator.toString());

            p.append(COMPOUND_TERM_OPENER);

            int n = 0;
            for (Term t : xt) {
                if (n != 0) {
                    p.append(ARGUMENT_SEPARATOR);
                    /*if (pretty)
                        p.append(' ');*/
                }

                t.append(p);


                n++;
            }

            p.append(COMPOUND_TERM_CLOSER);

        }


        @NotNull
        static StringBuilder stringify(@NotNull Compound c) {
            StringBuilder sb = new StringBuilder(/* conservative estimate */ c.volume() * 2);
            try {
                c.append(sb);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return sb;
        }
    }

    /**
     * Writes a string to the specified DataOutput using
     * <a href="DataInput.html#modified-utf-8">modified UTF-8</a>
     * encoding in a machine-independent manner.
     * <p>
     * First, two bytes are written to out as if by the <code>writeShort</code>
     * method giving the number of bytes to follow. This value is the number of
     * bytes actually written out, not the length of the string. Following the
     * length, each character of the string is output, in sequence, using the
     * modified UTF-8 encoding for the character. If no exception is thrown, the
     * counter <code>written</code> is incremented by the total number of
     * bytes written to the output stream. This will be at least two
     * plus the length of <code>str</code>, and at most two plus
     * thrice the length of <code>str</code>.
     *
     * @param str a string to be written.
     * @param out destination to write to
     * @return The number of bytes written out.
     * @throws IOException if an I/O error occurs.
     */
    static void writeUTFWithoutLength(String str, DataOutput out) throws IOException {


        //int c, count = 0;

//        /* use charAt instead of copying String to char array */
//        for (int i = 0; i < strlen; i++) {
//            c = str.charAt(i);
//            if ((c >= 0x0001) && (c <= 0x007F)) {
//                utflen++;
//            } else if (c > 0x07FF) {
//                utflen += 3;
//            } else {
//                utflen += 2;
//            }
//        }
//
//        if (utflen > 65535)
//            throw new UTFDataFormatException(
//                    "encoded string too long: " + utflen + " bytes");

        //byte[] bytearr = null;
//        if (out instanceof DataOutputStream) {
//            DataOutputStream dos = (DataOutputStream)out;
//            if(dos.bytearr == null || (dos.bytearr.length < (utflen+2)))
//                dos.bytearr = new byte[(utflen*2) + 2];
//            bytearr = dos.bytearr;
//        } else {
        //bytearr = new byte[utflen];
//        }

        //Length information, not written
        //bytearr[count++] = (byte) ((utflen >>> 8) & 0xFF);
        //bytearr[count++] = (byte) ((utflen >>> 0) & 0xFF);

        int strlen = str.length();
        int i, c;
        for (i = 0; i < strlen; i++) {
            c = str.charAt(i);
            if (!((c >= 0x0001) && (c <= 0x007F))) break;
            out.writeByte((byte) c);
        }

        for (; i < strlen; i++) {
            c = str.charAt(i);
            if ((c >= 0x0001) && (c <= 0x007F)) {
                out.writeByte((byte) c);

            } else if (c > 0x07FF) {
                out.writeByte((byte) (0xE0 | ((c >> 12) & 0x0F)));
                out.writeByte((byte) (0x80 | ((c >> 6) & 0x3F)));
                out.writeByte((byte) (0x80 | ((c >> 0) & 0x3F)));
            } else {
                out.writeByte((byte) (0xC0 | ((c >> 6) & 0x1F)));
                out.writeByte((byte) (0x80 | ((c >> 0) & 0x3F)));
            }
        }
    }
}
