package nars;


import com.google.common.primitives.Longs;
import jcog.Texts;
import jcog.Util;
import jcog.data.byt.DynBytes;
import jcog.data.iterator.ArrayIterator;
import jcog.data.list.FasterList;
import jcog.event.ByteTopic;
import jcog.event.ListTopic;
import jcog.event.Off;
import jcog.event.Topic;
import jcog.exe.Cycled;
import jcog.math.MutableInteger;
import jcog.pri.Prioritized;
import jcog.service.Service;
import jcog.service.Services;
import jcog.util.TriConsumer;
import nars.Narsese.NarseseException;
import nars.attention.Attention;
import nars.concept.Concept;
import nars.concept.Operator;
import nars.concept.PermanentConcept;
import nars.concept.TaskConcept;
import nars.concept.util.ConceptBuilder;
import nars.control.Cause;
import nars.control.MetaGoal;
import nars.control.NARService;
import nars.control.channel.CauseChannel;
import nars.control.op.Remember;
import nars.eval.Evaluator;
import nars.eval.Facts;
import nars.exe.Exec;
import nars.exe.NARLoop;
import nars.index.concept.ConceptIndex;
import nars.link.Activate;
import nars.subterm.Subterms;
import nars.table.BeliefTable;
import nars.task.ITask;
import nars.task.NALTask;
import nars.task.util.TaskBuffer;
import nars.task.util.TaskException;
import nars.term.Functor;
import nars.term.Term;
import nars.term.Termed;
import nars.term.atom.Atom;
import nars.term.atom.Atomic;
import nars.time.Tense;
import nars.time.Time;
import nars.truth.PreciseTruth;
import nars.truth.Truth;
import nars.util.Timed;
import org.HdrHistogram.Histogram;
import org.eclipse.collections.api.block.function.primitive.ShortToObjectFunction;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.api.tuple.Twin;
import org.eclipse.collections.impl.bag.mutable.HashBag;
import org.fusesource.jansi.Ansi;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;
import java.util.Set;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static nars.$.$;
import static nars.$.$$;
import static nars.Op.*;
import static nars.term.Functor.f;
import static nars.time.Tense.ETERNAL;
import static nars.truth.func.TruthFunctions.c2w;
import static org.fusesource.jansi.Ansi.ansi;


/**
 * Non-Axiomatic Reasoner
 * <p>
 * Instances of this represent a reasoner connected to a Memory, and set of Input and Output channels.
 * <p>
 * All state is contained within   A NAR is responsible for managing I/O channels and executing
 * memory operations.  It executes a series sof cycles in two possible modes:
 * * step mode - controlled by an outside system, such as during debugging or testing
 * * thread mode - runs in a pausable closed-loop at a specific maximum framerate.
 */
public class NAR extends Param implements Consumer<ITask>, NARIn, NAROut, Cycled, Timed {

    static final String VERSION = "NARchy v?.?";
    private static final Set<String> loggedEvents = java.util.Set.of("eventTask");
    public final Exec exe;
    public final Topic<NAR> eventClear = new ListTopic<>();
    public final Topic<NAR> eventCycle = new ListTopic<>();
    public final ByteTopic<Task> eventTask = new ByteTopic<>(Op.Punctuation);
    public final Services<NAR, Term> services;
    public final Time time;
    public final ConceptIndex concepts;
    public final NARLoop loop;
    public final Emotion emotion;
    public final Memory memory = new Memory(this);
    /**
     * cause->value table
     */
    public final FasterList<Cause> causes = new FasterList<Cause>(512) {
        @Override
        protected Cause[] newArray(int newCapacity) {
            return new Cause[newCapacity];
        }
    };

    protected final Supplier<Random> random;

    /**
     * atomic for thread-safe schizophrenia
     */
    private final AtomicReference<Term> self = new AtomicReference<>(null);
    public final ConceptBuilder conceptBuilder;

    /**
     * default attention; other attentions can be attached as services
     */
    public final Attention attn;

    public Logger logger;

    public final Evaluator evaluator = new Evaluator(this::functor);

    public final TaskBuffer input;


    public NAR(ConceptIndex concepts, Exec exe, Attention attn, Time time, Supplier<Random> rng, ConceptBuilder conceptBuilder) {

        this.random = rng;

        this.concepts = concepts;

        (this.time = time).reset();

        named(Param.randomSelf());

        this.attn = attn;

        this.exe = exe;



        services = new Services<>(this, exe);

        this.conceptBuilder = conceptBuilder;

        concepts.start(this);
        Builtin.init(this);

        this.emotion = new Emotion(this);

        on(this.attn);



        input =
            //new TaskBuffer.DirectTaskBuffer(exe::input);
            new TaskBuffer.BagTaskBuffer(512, 0.5f);
            //new DerivedTasks.DerivedTasksMap(4096);
            //new TaskBuffer.BagPuncTasksBuffer(1024, 0.1f);
        onCycle(n-> input.commit(n.time(), exe));

        this.loop = new NARLoop(this);
        exe.start(this);
    }

    static void outputEvent(Appendable out, String previou, String chan, Object v) throws IOException {


        if (!chan.equals(previou)) {
            out

                    .append(chan)

                    .append(": ");

        } else {

            int n = chan.length() + 2;
            for (int i = 0; i < n; i++)
                out.append(' ');
        }

        if (v instanceof Object[]) {
            v = Arrays.toString((Object[]) v);
        } else if (v instanceof Task) {
            Task tv = ((Task) v);
            float tvp = tv.priElseZero();
            v = ansi()

                    .a(tvp >= 0.25f ?
                            Ansi.Attribute.INTENSITY_BOLD :
                            Ansi.Attribute.INTENSITY_FAINT)
                    .a(tvp > 0.75f ? Ansi.Attribute.NEGATIVE_ON : Ansi.Attribute.NEGATIVE_OFF)
                    .fg(Prioritized.budgetSummaryColor(tv))
                    .a(
                            tv.toString(true)
                    )
                    .reset()
                    .toString();
        }

        out.append(v.toString()).append('\n');
    }

    /**
     * resolves functor by its target
     */
    public final Functor functor(Atom term) {
        Termed x = concept(term);
        return x instanceof Functor ? (Functor) x : null;
    }

    /**
     * creates a snapshot statistics object
     * TODO extract a Method Object holding the snapshot stats with the instances created below as its fields
     */
    public SortedMap<String, Object> stats() {

        LongSummaryStatistics beliefs = new LongSummaryStatistics();
        LongSummaryStatistics goals = new LongSummaryStatistics();
        LongSummaryStatistics questions = new LongSummaryStatistics();
        LongSummaryStatistics quests = new LongSummaryStatistics();


        HashBag clazz = new HashBag();
        HashBag rootOp = new HashBag();

        Histogram volume = new Histogram(1, Param.COMPOUND_VOLUME_MAX, 3);


        SortedMap<String, Object> x = new TreeMap();


        {

            concepts().filter(xx -> !(xx instanceof Functor)).forEach(c -> {

                Term ct = c.term();
                volume.recordValue(ct.volume());
                rootOp.add(ct.op());
                clazz.add(ct.getClass().toString());

                beliefs.accept(c.beliefs().size());
                goals.accept(c.goals().size());
                questions.accept(c.questions().size());
                quests.accept(c.quests().size());
            });


            if (loop.isRunning()) {
                loop.stats("loop", x);
            }

            x.put("time", time());


            x.put("concept count", concepts.size());
        }

        x.put("belief count", ((double) beliefs.getSum()));
        x.put("goal count", ((double) goals.getSum()));

        Util.toMap(rootOp, "concept op", x::put);

        Texts.histogramDecode(volume, "concept volume", 4, x::put);

        Util.toMap(clazz, "concept class", x::put);

        emotion.getter(() -> x).run();

        return x;

    }

    /**
     * Reset the system with an empty memory and reset clock.  Event handlers
     * will remain attached but enabled plugins will have been deactivated and
     * reactivated, a signal for them to empty their state (if necessary).
     */
    public void reset() {

        synchronized (exe) {

            boolean running = loop.isRunning();
            float fps = running ? loop.getFPS() : -1;

            stop();

            clear();

            input.clear();

            time.clear(this);
            time.reset();

            exe.start(this);

            if (running)
                loop.setFPS(fps);
        }

        logger.info("reset");

    }

    /**
     * the clear event is a signal indicating that any active memory or processes
     * which would interfere with attention should be stopped and emptied.
     * <p>
     * this does not indicate the NAR has stopped or reset itself.
     */
    public void clear() {
        synchronized (exe) {

            eventClear.emit(this);

            logger.info("cleared");
        }
    }

    public final NAR named(String self) {
        return named(Atomic.the(self));
    }

    public final NAR named(Term self) {
        Logger nextLogger = LoggerFactory.getLogger("NAR:" + self);
        this.self.updateAndGet((prevSelf) -> {
            logger = nextLogger;
            return self;
        });
        return this;
    }

    /**
     * parses one and only task
     */

    public <T extends Task> T inputTask(String taskText) throws Narsese.NarseseException {
        return (T) inputTask(Narsese.task(taskText, (this)));
    }

    public List<Task> input(String text) throws NarseseException, TaskException {
        List<Task> l = Narsese.tasks(text, this);
        switch (l.size()) {
            case 0:
                return List.of();
            case 1:
                input(l.get(0));
                return l;
            default:
                input(l);
                return l;
        }
    }

    /**
     * gets a concept if it exists, or returns null if it does not
     */
    @Nullable
    public final Concept conceptualize(String conceptTerm) throws NarseseException {
        return conceptualize($(conceptTerm));
    }

    /**
     * ask question
     */
    public Task question(String termString) {

        return question($$(termString));
    }

    /**
     * ask question
     */
    public Task question(Term c) {
        return que(c, QUESTION);
    }

    public Task quest(Term c) {
        return que(c, QUEST);
    }

    @Nullable
    public Task want(String goalTermString, Tense tense, float freq, float conf) throws NarseseException {
        return want($(goalTermString), tense, freq, conf);
    }

    public Task want(Term goal, Tense tense, float freq) {
        return want(goal, tense, freq, confDefault(GOAL));
    }

    /**
     * desire goal
     */
    @Nullable
    public Task want(Term goalTerm, Tense tense, float freq, float conf) {
        long now = time(tense);
        return want(
                priDefault(GOAL),
                goalTerm, now, now, freq, conf);
    }

    public Task believe(Term term, Tense tense, float freq, float conf) {
        return believe(term, time(tense), freq, conf);
    }

    public Task believe(Term term, long when, float freq, float conf) {
        return believe(priDefault(BELIEF), term, when, freq, conf);
    }

    public Task believe(Term term, Tense tense, float freq) {
        return believe(term, tense, freq, confDefault(BELIEF));
    }

    public Task believe(Term term, long when, float freq) {
        return believe(term, when, freq, confDefault(BELIEF));
    }

    public Task believe(Term term, float freq, float conf) {
        return believe(term, Tense.Eternal, freq, conf);
    }

    public Task want(Term term, float freq, float conf) {
        return want(term, Tense.Eternal, freq, conf);
    }

    public NAR believe(String term, Tense tense, float freq, float conf) {
        try {
            believe(priDefault(BELIEF), $(term), time(tense), freq, conf);
        } catch (NarseseException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    public long time(Tense tense) {
        return Tense.getRelativeOccurrence(tense, this);
    }

    public NAR believe(String termString, float freq, float conf) throws NarseseException {
        return believe(termString, freq, conf, ETERNAL, ETERNAL);
    }

    public NAR believe(String termString, float freq, float conf, long start, long end) throws NarseseException {
        believe($(termString), start, end, freq, conf);
        return this;
    }

    public Task want(String termString) {
        try {
            return want($(termString), true);
        } catch (NarseseException e) {
            throw new RuntimeException(e);
        }
    }

    public NAR believe(String... tt) throws NarseseException {

        for (String b : tt)
            believe(b, true);

        return this;
    }

    public NAR believe(String termString, boolean isTrue) throws NarseseException {
        believe($(termString), isTrue);
        return this;
    }

    public Task want(String termString, boolean isTrue) throws NarseseException {
        return want($(termString), isTrue);
    }

    public Task believe(Term term) {
        return believe(term, true);
    }

    public Task believe(Term term, boolean trueOrFalse) {
        return believe(term, trueOrFalse, confDefault(BELIEF));
    }

    public Task want(Term term) {
        return want(term, true);
    }

    public Task want(Term term, boolean trueOrFalse) {
        return want(term, trueOrFalse, confDefault(BELIEF));
    }

    public Task believe(Term term, boolean trueOrFalse, float conf) {
        return believe(term, trueOrFalse ? 1.0f : 0f, conf);
    }

    public Task want(Term term, boolean trueOrFalse, float conf) {
        return want(term, trueOrFalse ? 1.0f : 0f, conf);
    }

    public NAR believe(Term term, long occurrenceTime) throws TaskException {
        return believe(term, occurrenceTime, occurrenceTime);
    }

    public NAR believe(Term term, long start, long end) throws TaskException {
        input(priDefault(BELIEF), term, BELIEF, start, end, 1f, confDefault(BELIEF));
        return this;
    }

    public Task believe(Term term, long start, long end, float freq, float conf) throws TaskException {
        return believe(priDefault(BELIEF), term, start, end, freq, conf);
    }

    public Task believe(float pri, Term term, long occurrenceTime, float freq, float conf) throws TaskException {
        return believe(pri, term, occurrenceTime, occurrenceTime, freq, conf);
    }

    public Task believe(float pri, Term term, long start, long end, float freq, float conf) throws TaskException {
        return input(pri, term, BELIEF, start, end, freq, conf);
    }


    public Task want(float pri, Term goal, long start, long end, float freq, float conf) throws TaskException {
        return input(pri, goal, GOAL, start, end, freq, conf);
    }

    public Task input(float pri, Term term, byte punc, long occurrenceTime, float freq, float conf) throws TaskException {
        return input(pri, term, punc, occurrenceTime, occurrenceTime, freq, conf);
    }

    public Task input(float pri, Term term, byte punc, long start, long end, float freq, float conf) throws TaskException {

        PreciseTruth tr = Truth.theDithered(freq, c2w(conf), this);
        @Nullable Task z = Task.tryTask(term, punc, tr, (c, truth) -> {
            Task y = new NALTask(c, punc, truth, time(), start, end, evidence());
            y.pri(pri);
            return y;
        }, false);

        input(z);

        return z;
    }

    /**
     * ¿qué?  que-stion or que-st
     */
    public Task que(Term term, byte questionOrQuest) {
        return que(term, questionOrQuest, ETERNAL);
    }

    /**
     * ¿qué?  que-stion or que-st
     */
    public Task que(Term term, byte punc, long when) {
        return que(term, punc, when, when);
    }

    public Task que(Term term, byte punc, long start, long end) {
        assert ((punc == QUESTION) || (punc == QUEST));

        return inputTask(
                new NALTask(term.unneg(), punc, null,
                        time(), start, end,
                        new long[]{time.nextStamp()}
                ).budget(this)
        );
    }

    /**
     * logs tasks and other budgeted items with a summary exceeding a threshold
     */
    public NAR logPriMin(Appendable out, float priThresh) {
        return log(out, v -> {
            Prioritized b = null;
            if (v instanceof Prioritized) {
                b = ((Prioritized) v);
            } else if (v instanceof Twin) {
                if (((Pair) v).getOne() instanceof Prioritized) {
                    b = (Prioritized) ((Pair) v).getOne();
                }
            }
            return b != null && b.priElseZero() > priThresh;
        });
    }

    public NAR logWhen(Appendable out, boolean past, boolean present, boolean future) {

        if (past && present && future)
            return log(out);

        return log(out, v -> {
            if (v instanceof Task) {
                Task t = (Task) v;
                long now = time();
                return
                        (past && t.endsBefore(now)) ||
                                (present && t.startsAfter(now)) ||
                                (future && t.isDuring(now));
            }
            return false;
        });
    }

    @Override
    public final void input(ITask t) {
        exe.input(t);
    }

    @Override
    public final void input(ITask... t) {

        switch (t.length) {
            case 0:
                break;
            case 1:
                input(t[0]);
                break;
            default:
                exe.input((Iterator) new ArrayIterator<>(t));
                break;
        }
    }

    @Override
    public final void accept(ITask task) {
        input(task);
    }

    /**
     * asynchronously adds the service
     */
    public final void on(NARService s) {
        services.add(s.term(), s);
    }

    public final void off(NARService s) {
        services.remove(s.term(), s);
    }

    /**
     * simplified wrapper for use cases where only the arguments of an operation task, and not the task itself matter
     */
    public final void onOpN(String atom, BiConsumer<Subterms, NAR> exe) {
        onOp(atom, (task, nar) -> {
            exe.accept(task.term().sub(0).subterms(), nar);
            return null;
        });
    }

    public final Operator onOp1(String atom, BiConsumer<Term, NAR> exe) {
        return onOp1((Atom) Atomic.the(atom), exe);
    }

    public final Operator onOp1(Atom atom, BiConsumer<Term, NAR> exe) {
        return onOp(atom, (task, nar) -> {

            Subterms ss = task.term().sub(0).subterms();
            if (ss.subs() == 1)
                exe.accept(ss.sub(0), nar);
            return null;
        });
    }

    public final void onOp2(String atom, TriConsumer<Term, Term, NAR> exe) {
        onOp(atom, (task, nar) -> {

            Subterms ss = task.term().sub(0).subterms();
            if (ss.subs() == 2)
                exe.accept(ss.sub(0), ss.sub(1), nar);
            return null;
        });
    }

    /**
     * registers an operator
     */
    public final void onOp(String a, BiConsumer<Task, NAR> exe) {
        onOp(a, (task, nar) -> {

            exe.accept(task, nar);
            return null;
        });
    }

    /**
     * registers an operator
     */
    public final Operator onOp(String a, BiFunction<Task, NAR, Task> exe) {
        return onOp((Atom) $.the(a), exe);
    }

    /**
     * registers an operator
     */
    public final Operator onOp(Atom name, BiFunction<Task, NAR, Task> exe) {
        Operator op = Operator.simple(name, exe);
        concepts.set(op);
        return op;
    }

    @Override
    public final int dur() {
        return time.dur();
    }

    /**
     * provides a Random number generator
     */
    @Override
    public final Random random() {
        return random.get();
    }

    @Nullable
    public final Truth truth(Termed concept, byte punc, long when) {
        return truth(concept, punc, when, when);
    }

    @Nullable
    public final BeliefTable truths(Termed concept, byte punc) {
        assert (punc == BELIEF || punc == GOAL);
        @Nullable Concept c = conceptualizeDynamic(concept);
        if (c == null)
            return null;
        return (BeliefTable) c.table(punc);
    }

    /**
     * returns concept belief/goal truth evaluated at a given time
     */
    @Nullable
    public final Truth truth(Termed concept, byte punc, long start, long end) {
        @Nullable BeliefTable table = truths(concept, punc);
        return table != null ? table.truth(start, end, concept instanceof Term ? ((Term)concept) : null, this) : null;
    }

    @Nullable
    public final Truth beliefTruth(String concept, long when) throws NarseseException {
        return truth($(concept), BELIEF, when);
    }

    @Nullable
    public final Truth goalTruth(String concept, long when) throws NarseseException {
        return truth($(concept), GOAL, when);
    }

    @Nullable
    public final Truth beliefTruth(Termed concept, long when) {
        return truth(concept, BELIEF, when);
    }

    @Nullable
    public final Truth beliefTruth(Termed concept, long start, long end) {
        return truth(concept, BELIEF, start, end);
    }

    @Nullable
    public final Truth goalTruth(Termed concept, long when) {
        return truth(concept, GOAL, when);
    }

    @Nullable
    public final Truth goalTruth(Termed concept, long start, long end) {
        return truth(concept, GOAL, start, end);
    }

    /**
     * Exits an iteration loop if running
     */
    public NAR stop() {

        synchronized (exe) {

            pause();

            services.stop();

            exe.stop();

        }

        return this;
    }

    /* Print all statically known events (discovered via reflection)
     *  for this reasoner to a stream
     * */

    /**
     * steps 1 frame forward. cyclesPerFrame determines how many cycles this frame consists of
     */
    @Override
    public final void run() {
        loop.run();
    }

    public NAR trace(Appendable out, Predicate<String> includeKey) {
        return trace(out, includeKey, null);
    }

    public NAR trace(Appendable out, Predicate<String> includeKey, @Nullable Predicate includeValue) {


        String[] previous = {null};

        eventTask.on((v) -> {

            if (includeValue != null && !includeValue.test(v))
                return;

            try {
                outputEvent(out, previous[0], "task", v);
            } catch (IOException e) {
                logger.error("outputEvent: {}", e.toString());
            }
            previous[0] = "task";
        });
        Topic.all(this, (k, v) -> {

            if (includeValue != null && !includeValue.test(v))
                return;

            try {
                outputEvent(out, previous[0], k, v);
            } catch (IOException e) {
                logger.error("outputEvent: {}", e.toString());
            }
            previous[0] = k;
        }, includeKey);

        return this;
    }

    public NAR trace(Appendable out) {
        return trace(out, k -> true);
    }

    public NAR log() {
        return log(System.out);
    }

    public NAR log(Appendable out) {
        return log(out, null);
    }

    public NAR log(Appendable out, Predicate includeValue) {
        return trace(out, NAR.loggedEvents::contains, includeValue);
    }

    /**
     * Runs until stopped, at a given delay period between frames (0= no delay). Main loop
     *
     * @param initialDelayMS in milliseconds
     */
    @Override
    public final NARLoop startPeriodMS(int initialDelayMS) {
        loop.setPeriodMS(initialDelayMS);
        return loop;
    }

    /**
     * executes task. if executor is concurrent it will be async, otherwise it will be executed synch (current thread)
     */
    public final void run(Consumer<NAR> t) {
        exe.input(t);
    }

    /**
     * executes task. if executor is concurrent it will be async, otherwise it will be executed synch (current thread)
     */
    public final void run(Runnable t) {
        exe.execute(t);
    }

//    /**
//     * executes task either synchronously or asynchronously according to the executor's mode.
//     * this is different from input(ITask) because it will force async if available whereas input will
//     * execute AbstractTask inline by defaul
//     */
//    public final void run(ITask t) {
//        //HACK
//        if (exe.concurrent())
//        input(t)
//        exe.execute(t);
//    }

    /**
     * adds a task to the queue of task which will be executed in batch
     * after the end of the current frame before the next frame.
     */
    public final void runLater(Runnable t) {
        time.runAt(time(), t);
    }



    @Override
    public String toString() {
        return self() + ":" + getClass().getSimpleName();
    }


    public NAR input(String... ss) throws NarseseException {
        for (String s : ss)
            input(s);
        return this;
    }

    public NAR inputNarsese(URL url) throws IOException, NarseseException {
        return inputNarsese(url.openStream());
    }

    public NAR inputNarsese(InputStream inputStream) throws IOException, NarseseException {
        String x = new String(inputStream.readAllBytes());
        input(x);
        return this;
    }

    /**
     * TODO this needs refactoring to use a central scheduler
     */
    public NAR inputAt(long time, String... tt) {

        assert (tt.length > 0);

        runAt(time, () -> {
            List<Task> yy = $.newArrayList(tt.length);
            for (String s : tt) {
                try {
                    yy.addAll(Narsese.tasks(s, this));
                } catch (NarseseException e) {
                    logger.error("{} for: {}", e, s);
                    e.printStackTrace();
                }
            }


            int size = yy.size();
            if (size > 0)
                input(yy.toArray(new Task[size]));

        });

        return this;
    }

    /**
     * TODO use a scheduling using r-tree
     */
    public void inputAt(long when, ITask... x) {
        long now = time();
        if (when <= now) {

            input(x);
        } else {
            runAt(when, () -> input(x));
        }
    }

    /**
     * schedule a task to be executed no sooner than a given NAR time
     */
    public final void runAt(long whenOrAfter, Runnable then) {
//        if (whenOrAfter <= time())
//            run(then);
//        else
        time.runAt(whenOrAfter, then);
    }



    /**
     * tasks in concepts
     */

    public Stream<Task> tasks(boolean includeConceptBeliefs, boolean includeConceptQuestions,
                              boolean includeConceptGoals, boolean includeConceptQuests) {
        return concepts().flatMap(c ->
                c.tasks(includeConceptBeliefs, includeConceptQuestions, includeConceptGoals, includeConceptQuests));
    }

    public void tasks(boolean includeConceptBeliefs, boolean includeConceptQuestions, boolean includeConceptGoals,
                      boolean includeConceptQuests, BiConsumer<Concept, Task> each) {
        concepts().forEach(c ->

                c.tasks(includeConceptBeliefs,
                        includeConceptQuestions,
                        includeConceptGoals,
                        includeConceptQuests).forEach(t -> each.accept(c, t))
        );
    }

    public Stream<Task> tasks() {
        return tasks(true, true, true, true);
    }

    /**
     * resolves a target or concept to its currrent Concept
     */
    @Nullable
    public final Concept concept(Termed x) {
        return concept(x, false);
    }

    @Nullable
    public final Concept concept(String term) {
        return concept($$(term), false);
    }

    /**
     * resolves a target to its Concept; if it doesnt exist, its construction will be attempted
     */
    @Nullable
    public final Concept conceptualize(/**/ Termed termed) {
        return concept(termed, true);
    }

    @Nullable
    public final Concept concept(/**/Termed x, boolean createIfMissing) {
//        if (Param.DEBUG) {
//            int v = x.target().volume();
//            int max = termVolumeMax.intValue();
//            if (v > max) {
//                //return null; //too complex
//                logger.warn("too complex for conceptualization ({}/{}): " + x, v, max);
//            }
//        }

        return concepts.concept(x, createIfMissing);
    }

    @Deprecated public final Stream<Activate> conceptsActive() {
        return concepts.active().map(x-> x.source()).distinct()
                .map(x->concept(x))
                .filter(Objects::nonNull).map(c -> new Activate(c,1)); //HACK
        //return Stream.empty();
        //return concepts.active();
    }

    public final Stream<Concept> concepts() {
        return concepts.stream()/*.filter(Concept.class::isInstance)*/.map(Concept.class::cast);
    }

    /**
     * warning: the condition will be tested each cycle so it may affect performance
     */
    public NAR stopIf(BooleanSupplier stopCondition) {
        onCycle(n -> {
            if (stopCondition.getAsBoolean())
                stop();
        });
        return this;
    }

    /**
     * a frame batches a burst of multiple cycles, for coordinating with external systems in which multiple cycles
     * must be run per control frame.
     */
    public final Off onCycle(Consumer<NAR> each) {
        return eventCycle.on(each);
    }

    public final Off onCycle(Runnable each) {
        return onCycle((ignored) -> each.run());
    }

    /**
     * avoid using lambdas with this, instead use an interface implementation of the class that is expected to be garbage collected
     */
    public final Off onCycleWeak(Consumer<NAR> each) {
        return eventCycle.onWeak(each);
    }

    public final Off onTask(Consumer<Task> listener) {
        return eventTask.on(listener);
    }

    public final Off onTask(Consumer<Task> listener, byte... punctuations) {
        return eventTask.on(listener, punctuations);
    }


    public NAR trace() {
        trace(System.out);
        return this;
    }

    /**
     * if this is an Iterable<Task> , it can be more efficient to use the inputTasks method to bypass certain non-NALTask conditions
     */
    public void input(Iterable<? extends ITask> tasks) {
        //if (tasks == null) return;
        exe.input(tasks);
    }

    public final void input(Stream<? extends ITask> tasks) {

        exe.input(tasks.filter(Objects::nonNull));
    }

    @Override
    public final boolean equals(Object obj) {

        return this == obj;
    }

    public NAR believe(Term c, Tense tense) {
        believe(c, tense, 1f);
        return this;
    }

    /**
     * activate/"turn-ON"/install a concept in the index and activates it, used for setup of custom concept implementations
     * implementations should apply active concept capacity policy
     */
    public final Concept on(PermanentConcept c) {

        Termed existing = concepts.remove(c.term());
        if ((existing != null)) {
            if (existing != c) {

                if (!(c instanceof PermanentConcept)) {
                    throw new RuntimeException("concept already indexed for target: " + c.term());
                }
            }
        }

        concepts.set(c);

        conceptBuilder.start(c);

//        if (c instanceof Conceptor) {
//            conceptBuilder.on((Conceptor) c);
//        }

        return c;
    }

    /**
     * registers a target rewrite functor
     */

    public final Functor.LambdaFunctor on(String termAtom, Function<Subterms, Term> f) {
        return (Functor.LambdaFunctor) on(f(termAtom, f));
    }


    public final Concept on1(String termAtom, Function<Term, Term> f) {
        return on(termAtom, (Subterms s) -> s.subs() == 1 ? f.apply(s.sub(0)) : null);
    }

    @Override
    public final long time() {
        return time.now();
    }

    public NAR inputBinary(File input) throws IOException {
        return inputBinary(new GZIPInputStream(new BufferedInputStream(new FileInputStream(input), IO.STREAM_BUFFER_SIZE), IO.GZIP_BUFFER_SIZE));
    }

    public NAR outputBinary(File f) throws IOException {
        return outputBinary(f, false);
    }

    public final NAR outputBinary(File f, boolean append) throws IOException {
        return outputBinary(f, append, ((Task t) -> t));
    }

    public final NAR outputBinary(File f, boolean append, Predicate<Task> each) throws IOException {
        return outputBinary(f, append, (Task t) -> each.test(t) ? t : null);
    }

    public NAR outputBinary(File f, boolean append, Function<Task, Task> each) throws IOException {
        FileOutputStream f1 = new FileOutputStream(f, append);
        OutputStream ff = new GZIPOutputStream(f1, IO.GZIP_BUFFER_SIZE);
        outputBinary(ff, each);
        return this;
    }

    public final NAR outputBinary(OutputStream o) {
        return outputBinary(o, (Task x) -> x);
    }

    public final NAR outputBinary(OutputStream o, Predicate<Task> filter) {
        return outputBinary(o, (Task x) -> filter.test(x) ? x : null);
    }

    /**
     * byte codec output of matching concept tasks (blocking)
     * <p>
     * the each function allows transforming each task to an optional output form.
     * if this function returns null it will not output that task (use as a filter).
     */
    public NAR outputBinary(OutputStream o, Function<Task, Task> each) {

        Util.time(logger, "outputBinary", () -> {

            DataOutputStream oo = new DataOutputStream(o);

            MutableInteger total = new MutableInteger(0), wrote = new MutableInteger(0);

            DynBytes d = new DynBytes(128);

            tasks().map(each).filter(Objects::nonNull).distinct().forEach(x -> {

                total.increment();

                try {
                    byte[] b = IO.taskToBytes(x, d);
                    oo.write( b );
                    wrote.increment();
                } catch (IOException e) {
                    if (Param.DEBUG)
                        throw new RuntimeException(e);
                    else
                        logger.warn("{}", e);
                }

            });

            logger.info("{} output {}/{} tasks ({} bytes uncompressed)", o, wrote, total, oo.size());


            try {
                oo.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        return this;
    }

    public NAR outputText(OutputStream o, Function<Task, Task> each) {


        PrintStream ps = new PrintStream(o);

        MutableInteger total = new MutableInteger(0);

        StringBuilder sb = new StringBuilder();
        tasks().map(each).filter(Objects::nonNull).forEach(x -> {

            total.increment();

            if (x.truth() != null && x.conf() < confMin.floatValue())
                return;

            sb.setLength(0);
            ps.println(x.appendTo(sb, true));
        });

        try {
            o.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return this;
    }


    final static private int outputBufferSize = 128 * 1024;

    public NAR output(File o, boolean binary) throws FileNotFoundException {
        return output(new BufferedOutputStream(new FileOutputStream(o), outputBufferSize), binary);
    }

    public NAR output(File o, Function<Task, Task> f) throws FileNotFoundException {
        return outputBinary(new BufferedOutputStream(new FileOutputStream(o), outputBufferSize), f);
    }

    public NAR output(OutputStream o, boolean binary) {
        if (binary) {
            return outputBinary(o, (Task x) -> x.isDeleted() ? null : x);
        } else {
            return outputText(o, x -> x.isDeleted() ? null : x);
        }
    }

    /**
     * byte codec input stream of tasks, to be input after decode
     * TODO use input(Stream<Task>..</Task>
     */
    public NAR inputBinary(InputStream i) throws IOException {

        int count = IO.readTasks(i, this::input);


        logger.info("input {} tasks from {}", count, i);


        return this;
    }

    /**
     * The id/name of the reasoner
     */
    public final Term self() {
        return self.get();
    }

    /**
     * strongest matching belief for the target time
     */
    @Nullable
    public Task belief(Termed c, long start, long end) {
        return answer(c, BELIEF, start, end);
    }

    public final Task belief(String c, long when) throws NarseseException {
        return belief($(c), when);
    }

    public final Task belief(Termed c, long when) {
        return belief(c, when, when);
    }

    public final Task belief(Term c) {
        return belief(c, time());
    }



    @Nullable
    public final Task answer(Term c, byte punc, long when) {
        return answer(c, punc, when, when);
    }

    @Nullable
    public Task answer(Termed t, byte punc, long start, long end) {
        assert (punc == BELIEF || punc == GOAL);
        Concept concept =
                conceptualizeDynamic(t);


        if (!(concept instanceof TaskConcept))
            return null;

        Task answer = concept.table(punc).answer(start, end,
                t.term(), null, this);
//        if (answer != null && !answer.isDeleted()) {
//            input(answer);
//        }
        return answer;
    }

    public SortedMap<String, Object> stats(Appendable out) {

        SortedMap<String, Object> stat = stats();
        stat.forEach((k, v) -> {
            try {
                out.append(k.replace(" ", "/")).append(" \t ").append(v.toString()).append('\n');
            } catch (IOException e) {
            }
        });
        try {
            out.append('\n');
        } catch (IOException e) {
        }

        return stat;
    }

    /**
     * deletes any task with a stamp containing the component
     */
    public void retract(long stampComponent) {
        tasks().filter(x -> Longs.contains(x.stamp(), stampComponent)).forEach(Task::delete);
    }

    /**
     * computes an evaluation amplifier factor, in range 0..2.0.
     * VALUE -> AMP
     * -Infinity -> amp=0
     * 0     -> amp=1
     * +Infinity -> amp=2
     */
    public float amp(short[] effect) {
        return 1f + Util.tanhFast(value(effect));
    }

    public final float amp(Task task) {
        return amp(task.cause());
    }

    public float value(short[] effect) {
        return MetaGoal.privaluate(causes, effect);
    }


    /**
     * creates a new evidence stamp (1-element array)
     */
    public final long[] evidence() {
        return new long[]{time.nextStamp()};
    }

    public Cause newCause(Object name) {
        return newCause((id) -> new Cause(id, name));
    }

    /**
     * automatically adds the cause id to each input
     */
    public CauseChannel<ITask> newChannel(Object id) {
        return new TaskChannel(newCause(id));
    }

    public <C extends Cause> C newCause(ShortToObjectFunction<C> idToChannel) {
        synchronized (causes) {
            short next = (short) (causes.size());
            C c = idToChannel.valueOf(next);
            causes.add(c);
            return c;
        }
    }
//    @Nullable
//    public final Concept activate(Termed t, float activationApplied) {
//        return activate(t, activationApplied, false);
//    }
//    @Nullable
//    public final Concept activate(Termed t, float activationApplied, boolean forceConceptualize) {
//
//        /** conceptualize regardless */
//        Concept c = forceConceptualize ? conceptualize(t) : concept(t);
//        if (c!=null && activationApplied >= 0) {
//            concepts.activate(c, activationApplied);
//        }
//
//        return c;
//
//    }

    public Stream<Service<NAR>> services() {
        return services.stream();
    }

    public void conceptualize(Term term, Consumer<Concept> with) {


        Concept x = conceptualize(term);
        if (x != null) {
            with.accept(x);
        } else {

        }

    }


    /**
     * invokes any pending tasks without advancing the clock
     */
    public final NAR synch() {
        time.synch(this);
        return this;
    }

    public final boolean pause() {
        if (loop.stop()) {
            synch();
            return true;
        }
        return false;
    }

    /**
     * conceptualize a target if dynamic truth is possible; otherwise return concept if exists
     */
    public final Concept conceptualizeDynamic(Termed concept) {

        Concept x = concept(concept);
        if (x == null) {
            Term ct = concept.term();

            if (ct.volume() > termVolumeMax.intValue())
                return null; //too complex

            if (ConceptBuilder.dynamicModel(ct) != null) {
                //try conceptualizing the dynamic
                //return conceptualize(concept);

                //create temporary dynamic concept
                Concept c = conceptBuilder.construct(ct);
                if (c!=null) {
                    //flyweight start deleted and unallocated (in-capacit-ated) since it isnt actually in memory
                    c.delete(this);
                }
                return c;
            }
        }
        return x;
    }

    public final Task answerBelief(Term x, long when) {
        return answerBelief(x, when, when);
    }

    public final Task answerBelief(Term x, long start, long end) {
        return answer(x, BELIEF, start, end);
    }

    public final Task answerGoal(Term x, long when) {
        return answerGoal(x, when, when);
    }

    public final Task answerGoal(Term x, long start, long end) {
        return answer(x, GOAL, start, end);
    }

    /**
     * creates a view for resolving unifiable terms with 'x'
     * above provided absolute expectation.  negative terms can be returned negative
     */
    public Function<Term, Stream<Term>> facts(float expMin, boolean beliefsOrGoals) {
        return new Facts(this, expMin, beliefsOrGoals);
    }

    public final Task is(Term content, long start, long end) {
        return answer(content, BELIEF, start, end);
    }

    public final Task wants(Term content, long start, long end) {
        return answer(content, GOAL, start, end);
    }

    private class TaskChannel extends CauseChannel<ITask> {

        private final short ci;
        final short[] uniqueCause;

        TaskChannel(Cause cause) {
            super(cause);
            this.ci = cause.id;
            uniqueCause = new short[]{ci};
        }

        @Override
        public void input(ITask x) {
            if (process(x))
                NAR.this.input(x);
        }

        protected boolean process(Object x) {
            if (x instanceof NALTask) {
                NALTask t = (NALTask) x;
                short[] currentCause = t.cause();
                int tcl = currentCause.length;
                switch (tcl) {
                    case 0:
                        //shared one-element cause
                        //assert (uniqueCause[0] == ci);
                        t.cause(uniqueCause);
                        break;
                    case 1:
                        if (currentCause == uniqueCause) {
                            /* same instance */
                        } else if (currentCause[0] == ci) {
                            //replace with shared instance
                            t.cause(uniqueCause);
                        }
                        break;
                    default:

                        int cc = Param.causeCapacity.intValue();
                        short[] tc = Arrays.copyOf(currentCause, Math.min(cc, tcl + 1));
                        if (tc.length == cc) {
                            //shift
                            System.arraycopy(tc, 1, tc, 0, tc.length - 1);
                            tc[tc.length - 1] = ci;
                        } else {
                            tc[tcl] = ci;
                        }
                        t.cause(tc);
                        break;
                }
            } else if (x instanceof Remember) {
                return process(((Remember) x).input);
            } else
                return x != null;

            return true;
        }

    }

}
