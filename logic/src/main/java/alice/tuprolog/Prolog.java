/*
 * tuProlog - Copyright (C) 2001-2002  aliCE team at deis.unibo.it
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package alice.tuprolog;

import java.lang.*;
import alice.tuprolog.event.*;
import alice.tuprolog.lib.BasicLibrary;
import alice.tuprolog.lib.IOLibrary;
import alice.tuprolog.lib.ISOLibrary;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;


/**
 * Prolog engine
 */
public class Prolog {


    public final Theories theories;

    public final PrologPrimitives prims;

    public final PrologOperators ops;

    public final Flags flags;

    public final PrologLibraries libs;

    public final PrologRun run = new PrologRun(0);


    /* listeners registrated for virtual machine output events */
    final List<OutputListener> onOut = new CopyOnWriteArrayList<>();
    /* listeners registrated for virtual machine internal events */
    private final List<SpyListener> onSpy = new CopyOnWriteArrayList<>();
    /* listeners registrated for virtual machine state exception events */
    private final List<ExceptionListener> onException = new CopyOnWriteArrayList<>();
    /* listeners to theory events */
    private final List<TheoryListener> onTheory = new CopyOnWriteArrayList<>();

    /* listeners registrated for virtual machine state change events */
    /* listeners to library events */
    private final List<LibraryListener> onLibrary = new CopyOnWriteArrayList<>();
    /* listeners to query events */
    private final List<Consumer<QueryEvent>> onQuery = new CopyOnWriteArrayList<>();
    /*  spying activated ?  */
    private boolean spy;
    /* exception activated ? */
    private boolean exception;
    private boolean warning;
    /* path history for including documents */
    private List<String> absolutePathList;

    public final static Logger logger = LoggerFactory.getLogger(Prolog.class);

    public Prolog() {
        this(new MutableClauseIndex());
    }

    /**
     * Builds a prolog engine with default libraries loaded.
     * <p>
     * The default libraries are BasicLibrary, ISOLibrary,
     * IOLibrary, and  JavaLibrary
     */
    public Prolog(ClauseIndex dynamics) {
        this(false, dynamics);

        try {
            addLibrary(BasicLibrary.class);
            addLibrary(ISOLibrary.class);
            addLibrary(IOLibrary.class);
        } catch (InvalidLibraryException ex) {
            throw new RuntimeException(ex);
        }
    }


    /**
     * Builds a tuProlog engine with loaded
     * the specified libraries
     *
     * @param libs the (class) name of the libraries to be loaded
     */
    public Prolog(String... libs) {
        this(false, new MutableClauseIndex());
        if (libs != null) {
            for (String lib : libs) {
                try {
                    addLibrary(lib);
                } catch (InvalidLibraryException e) {
                    logger.error("loading {}: {}", lib, e);
                }
            }
        }
    }


    /**
     * Initialize basic engine structures.
     *
     * @param spy spying activated
     */
    private Prolog(boolean spy, ClauseIndex dynamics) {
        super();


        
        this.spy = spy;

        
        exception = true;
        

        absolutePathList = new CopyOnWriteArrayList<>();
        flags = new Flags();

        libs = new PrologLibraries();
        ops = new PrologOperators();
        prims = new PrologPrimitives();
        theories = new Theories(this, dynamics);

        libs.start(this);
        prims.start(this);

        run.initialize(this);

    }

    public static void warn(String s) {
        logger.warn(s);
    }

    /**
     * Gets the last Element of the path list
     */
    public String getCurrentDirectory() {
        String directory = "";
        if (absolutePathList.isEmpty()) {
            directory = /*this.lastPath != null ? this.lastPath : */System.getProperty("user.dir");
        } else {
            directory = absolutePathList.get(absolutePathList.size() - 1);
        }

        return directory;
    }

    /**
     * Adds (appends) a theory
     *
     * @param th is the theory to be added
     * @throws InvalidTheoryException if the new theory is not valid
     * @see Theory
     */
    public Prolog input(Theory th) throws InvalidTheoryException {

        Consumer<Theory> ifSuccessful;
        if (!onTheory.isEmpty()) {
            Theory oldTh = getTheory();
            ifSuccessful = (newTheory) -> {
                for (TheoryListener tl : onTheory) {
                    tl.theoryChanged(new TheoryEvent(this, oldTh, newTheory));
                }
            };
        } else {
            ifSuccessful = null;
        }

        theories.consult(th, true, null);
        theories.solveTheoryGoal();
        Theory newTh = getTheory();

        if (ifSuccessful != null)
            ifSuccessful.accept(newTh);

        return this;
    }

    /**
     * Gets current theory
     *
     * @return current(dynamic) theory
     */
    public Theory getTheory() {
        try {
            return new Theory(theories.getTheory(true));
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Sets a new theory
     *
     * @param th is the new theory
     * @throws InvalidTheoryException if the new theory is not valid
     * @see Theory
     */
    public Prolog setTheory(Theory th) throws InvalidTheoryException {
        theories.clear();
        input(th);
        return this;
    }

    /**
     * Clears current theory
     */
    public void clearTheory() {
        try {
            setTheory(Theory.Null);
        } catch (InvalidTheoryException e) {

        }
    }

    /**
     * Loads a library.
     * <p>
     * If a library with the same name is already present,
     * a warning event is notified and the request is ignored.
     *
     * @param className name of the Java class containing the library to be loaded
     * @return the reference to the Library just loaded
     * @throws InvalidLibraryException if name is not a valid library
     */
    public PrologLib addLibrary(String className) throws InvalidLibraryException {
        return libs.loadClass(className);
    }

    /**
     * Loads a library.
     * <p>
     * If a library with the same name is already present,
     * a warning event is notified and the request is ignored.
     *
     * @param className name of the Java class containing the library to be loaded
     * @param paths     The path where is contained the library.
     * @return the reference to the Library just loaded
     * @throws InvalidLibraryException if name is not a valid library
     */
    public PrologLib addLibrary(String className, String... paths) throws InvalidLibraryException {
        return libs.loadClass(className, paths);
    }

    /**
     * Loads a specific instance of a library
     * <p>
     * If a library with the same name is already present,
     * a warning event is notified
     *
     * @param lib the (Java class) name of the library to be loaded
     * @throws InvalidLibraryException if name is not a valid library
     */
    public void addLibrary(PrologLib lib) throws InvalidLibraryException {
        libs.load(lib);
    }

    public void addLibrary(Class<? extends PrologLib> lib) throws InvalidLibraryException {
        try {
            addLibrary(lib.getConstructor().newInstance());
        } catch (InvocationTargetException | NoSuchMethodException | InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Unloads a previously loaded library
     *
     * @param name of the library to be unloaded
     * @throws InvalidLibraryException if name is not a valid loaded library
     */
    public void removeLibrary(String name) throws InvalidLibraryException {
        libs.unload(name);
    }

    /**
     * Gets the reference to a loaded library
     *
     * @param name the name of the library already loaded
     * @return the reference to the library loaded, null if the library is
     * not found
     */
    public PrologLib library(String name) {
        return libs.getLibrary(name);
    }

    /**
     * Gets the list of the operators currently defined
     *
     * @return the list of the operators
     */
    public Iterable<PrologOp> operators() {
        return ops.operators();
    }

    /**
     * Solves a query
     *
     * @param g the term representing the goal to be demonstrated
     * @return the result of the demonstration
     * @see Solution
     **/
    public Solution solve(Term g) {

        this.clearSinfoSetOf();

        Solution sinfo = run.solve(g);

        solution(this, sinfo);

        return sinfo;
    }

    public final Prolog solve(String g, Consumer<Solution> eachSolution) {
        return solve(term(g), eachSolution);
    }

    public final Prolog solveWhile(String g, Predicate<Solution> eachSolution) {
        return solveWhile(term(g), eachSolution);
    }

    public Prolog solve(Term g, Consumer<Solution> eachSolution) {
        return solveWhile(g, (x) -> {
            eachSolution.accept(x);
            return true;
        });
    }

    public Prolog solveWhile(Term g, Predicate<Solution> eachSolution) {
        return solveWhile(g, eachSolution, -1);
    }

    public Prolog solveWhile(Term g, Predicate<Solution> eachSolution, long timeoutMS) {

        Solution next = null;
        do {
            if (next == null) {
                next = solve(g);
                if (next == null)
                    break;
            } else {
                try {
                    next = solveNext( /* TODO subdivide input time */);
                } catch (NoMoreSolutionException e) {
                    e.printStackTrace();
                }
            }

            solution(this, next);

        } while (eachSolution.test(next) && hasOpenAlternatives());


        return this;
    }

    /**
     * Solves a query
     *
     * @param st the string representing the goal to be demonstrated
     * @return the result of the demonstration
     * @see Solution
     **/
    public Solution solve(String st) throws MalformedGoalException {
        try {
            return solve(term(st));
        } catch (InvalidTermException ex) {
            throw new MalformedGoalException();
        }
    }

    public Term term(String toParse) throws InvalidTermException {
        return new Parser(toParse, ops).nextTerm(true);
    }

    /**
     * Gets next solution
     *
     * @return the result of the demonstration
     * @throws NoMoreSolutionException if no more solutions are present
     * @see Solution
     **/
    public Solution solveNext() throws NoMoreSolutionException {
        if (hasOpenAlternatives()) {
            Solution result;
            synchronized (run) {
                result = run.solveNext();
            }
            Solution sinfo = result;
            solution(this, sinfo);
            return sinfo;
        } else
            throw new NoMoreSolutionException();
    }

    /**
     * Halts current solve computation
     */
    public void solveHalt() {
        run.solveHalt();
    }

    /**
     * Accepts current solution
     */
    public void solveEnd() {
        run.solveEnd();
    }

    /**
     * Checks if the demonstration process was stopped by an halt command.
     *
     * @return true if the demonstration was stopped
     */
    public boolean isHalted() {
        return run.isHalted();
    }

    /**
     * Gets a term from a string, using the operators currently
     * defined by the engine
     *
     * @param st the string representing a term
     * @return the term parsed from the string
     * @throws InvalidTermException if the string does not represent a valid term
     */
    public Term toTerm(String st) throws InvalidTermException {
        return Parser.parseSingleTerm(st, ops);
    }

    /**
     * Gets the string representation of a term, using operators
     * currently defined by engine
     *
     * @param term the term to be represented as a string
     * @return the string representing the term
     */
    public String toString(Term term) {
        return (term.toStringAsArgY(ops, PrologOperators.OP_HIGH));
    }

    /**
     * Checks the spy state of the engine
     *
     * @return true if the engine emits spy information
     */
    public boolean isSpy() {
        return spy;
    }

    /**
     * Switches on/off the notification of spy information events
     *
     * @param state - true for enabling the notification of spy event
     */
    public void setSpy(boolean state) {
        spy = state;
    }

    /**
     * Notifies a spy information event
     */
    protected void spy(String s) {
        if (spy && !onSpy.isEmpty()) {
            notifySpy(new SpyEvent(this, s));
        }
    }

    /**
     * Notifies a spy information event
     *
     * @param s TODO
     */
    protected void spy(State s, PrologSolve e) {

        if (spy) {
            PrologContext ctx = e.currentContext;
            if (ctx != null) {
                int i = 0;
                String g = "-";
                if (ctx.fatherCtx != null) {
                    i = ctx.depth - 1;
                    g = ctx.fatherCtx.currentGoal.toString();
                }
                notifySpy(new SpyEvent(this, e, "spy: " + i + "  " + s + "  " + g));
            }
        }
    }

    /**
     * Notifies a exception information event
     *
     * @param m the exception message
     */
    public void exception(Throwable e) {
        if (exception && !onException.isEmpty()) {
            ExceptionEvent e1 = new ExceptionEvent(this, e);

            for (ExceptionListener exceptionListener : onException)
                exceptionListener.onException(e1);

            logger.error("{} {}", e1.getSource(), e1.getException());
        }
    }

    /**
     * Produces an output information event
     *
     * @param m the output string
     */
    public void output(String m) {

        int outputListenersSize = onOut.size();
        if (outputListenersSize > 0) {
            OutputEvent e = new OutputEvent(this, m);
            for (OutputListener outputListener : onOut) {
                outputListener.onOutput(e);
            }
        }

    }

    /**
     * Adds a listener to ouput events
     *
     * @param l the listener
     */
    public void addOutputListener(OutputListener l) {
        onOut.add(l);
    }

    /**
     * Adds a listener to theory events
     *
     * @param l the listener
     */
    public void addTheoryListener(TheoryListener l) {
        onTheory.add(l);
    }

    /**
     * Adds a listener to library events
     *
     * @param l the listener
     */
    public void addLibraryListener(LibraryListener l) {
        onLibrary.add(l);
    }

    /**
     * Adds a listener to theory events
     *
     * @param l the listener
     */
    public void addQueryListener(Consumer<QueryEvent> l) {
        onQuery.add(l);
    }

    /**
     * Adds a listener to spy events
     *
     * @param l the listener
     */
    public void addSpyListener(SpyListener l) {
        spy = true;
        onSpy.add(l);
    }
    
    /**
     * Adds a listener to exception events
     *
     * @param l the listener
     */
    public void addExceptionListener(ExceptionListener l) {
        onException.add(l);
    }

    /**
     * Removes a listener to ouput events
     *
     * @param l the listener
     */
    public void removeOutputListener(OutputListener l) {
        onOut.remove(l);
    }

    /**
     * Removes all output event listeners
     */
    public void removeAllOutputListeners() {
        onOut.clear();
    }

    /**
     * Removes a listener to theory events
     *
     * @param l the listener
     */
    public void removeTheoryListener(TheoryListener l) {
        onTheory.remove(l);
    }

    /**
     * Removes a listener to library events
     *
     * @param l the listener
     */
    public void removeLibraryListener(LibraryListener l) {
        onLibrary.remove(l);
    }

    /**
     * Removes a listener to query events
     *
     * @param l the listener
     */
    public void removeQueryListener(Consumer<QueryEvent> l) {
        onQuery.remove(l);

    }

    /**
     * Removes a listener to spy events
     *
     * @param l the listener
     */
    public synchronized void removeSpyListener(SpyListener l) {
        onSpy.remove(l);
        spy = !(onSpy.isEmpty());
    }

















    /* Castagna 06/2011*/

    /**
     * Removes all spy event listeners
     */
    public synchronized void removeAllSpyListeners() {
        spy = false;
        onSpy.clear();
    }
    
    /**
     * Removes a listener to exception events
     *
     * @param l the listener
     */
    public void removeExceptionListener(ExceptionListener l) {
        onException.remove(l);
    }
    
    /**
     * Removes all exception event listeners
     */
    public void removeAllExceptionListeners() {
        onException.clear();
    }

    /**
     * Notifies a spy information event
     *
     * @param e the event
     */
    private void notifySpy(SpyEvent e) {
        for (SpyListener spyListener : onSpy) {
            spyListener.onSpy(e);
        }
    }

    /**
     * Notifies a library loaded event
     *
     * @param e the event
     */
    protected void notifyLoadedLibrary(/* TODO Supplier< */ LibraryEvent e) {
        for (LibraryListener ll : onLibrary) {
            ll.libraryLoaded(e);
        }
    }

    /**
     * Notifies a library unloaded event
     *
     * @param e the event
     */
    protected void notifyUnloadedLibrary(LibraryEvent e) {
        for (LibraryListener ll : onLibrary) {
            ll.libraryUnloaded(e);
        }
    }

    /**
     * Notifies a library loaded event
     *
     * @param e the event
     */
    protected void solution(Prolog source, Solution info) {

        int qls = onQuery.size();
        if (qls > 0) {
            QueryEvent e = new QueryEvent(source, info);
            for (int i = 0, queryListenersSize = qls; i < queryListenersSize; i++) {
                onQuery.get(i).accept(e);
            }
        }


    }

    /**
     * Append a new path to directory list
     */
    public void pushDirectoryToList(String path) {
        absolutePathList.add(path);
    }

    /**
     * Retract an element from directory list
     */
    public void popDirectoryFromList() {
        if (!absolutePathList.isEmpty()) {
            absolutePathList.remove(absolutePathList.size() - 1);
        }
    }

//    public Term termSolve(String st) {
//        try {
//            Parser p = new Parser(st, ops);
//            return p.nextTerm(true);
//        } catch (InvalidTermException e) {
//
//            return Term.term("null");
//        }
//    }

    /**
     * Reset directory list
     */
    public void resetDirectoryList(String path) {
        absolutePathList = new ArrayList<>();
        absolutePathList.add(path);
    }

    public boolean isTrue(String s) {
        return isTrue(term(s));
    }

    public boolean isTrue(Term s) {
        Solution r = solve(s);
        return r.isSuccess();
    }

    public boolean isWarning() {
        return warning;
    }

    public void setWarning(boolean b) {
        this.warning = b;
    }

    public void notifyWarning(WarningEvent warningEvent) {
        if (warning)
            logger.warn("warning {}", warningEvent);
    }

    final void cut() {
        run.cut();
    }

    public boolean hasOpenAlternatives() {
        return run.hasOpenAlternatives();
    }

    void pushSubGoal(SubGoalTree goals) {
        run.pushSubGoal(goals);
    }

    PrologSolve getEnv() {
        return run.solve;
    }

    public void identify(Term t) {
        run.identify(t);
    }

    public boolean relinkVar() {
        return run.getRelinkVar();
    }

    public void relinkVar(boolean b) {
        run.setRelinkVar(b);
    }

    public List<Term> getBagOFres() {
        return run.getBagOFres();
    }

    public void setBagOFres(List<Term> l) {
        run.setBagOFres(l);
    }

    public List<String> getBagOFresString() {
        return run.getBagOFresString();
    }

    public void setBagOFresString(List<String> l) {
        run.setBagOFresString(l);
    }

    public Term getBagOFvarSet() {
        return run.getBagOFvarSet();
    }

    public void setBagOFvarSet(Term l) {
        run.setBagOFvarSet(l);
    }

    public Term getBagOFgoal() {
        return run.getBagOFgoal();
    }

    public void setBagOFgoal(Term l) {
        run.setBagOFgoal(l);
    }

    public Term getBagOFbag() {
        return run.getBagOFBag();
    }

    public void setBagOFbag(Term l) {
        run.setBagOFBag(l);
    }

    public void setSetOfSolution(String s) {
        run.setSetOfSolution(s);
    }

    public void clearSinfoSetOf() {
        run.clearSinfoSetOf();
    }

    public void endFalse(String s) {
        setSetOfSolution(s);
        relinkVar(false);
        setBagOFres(null);
        setBagOFgoal(null);
        setBagOFvarSet(null);
        setBagOFbag(null);
    }

    public Solution run(String goal) {
        return run(null, goal);
    }

    /**
     * if called directly, Starts agent execution in current thread
     */
    public Solution run(@Nullable String theoryText, String goalText){
        try {

            if (theoryText!=null) {
                setTheory(
                        new Theory(theoryText)
                );
            }

            if (goalText!=null){
                return solve(goalText);
            }
        } catch (Exception ex){
            System.err.println("invalid theory or goal.");
            ex.printStackTrace();
        }
        return null;
    }

    public List<Term> solutionList(String goal) {
        return Lists.newArrayList( solutionIterator(goal) );
    }

    public Iterator<Term> solutionIterator(String goal) {


        try {

            return new Iterator<Term>() { // @source level 1.8 (Anonymous Classes cannot use <>_

                final Solution s = run(goal);

                public Term next = s.getSolutionOrNull();

                @Override
                public boolean hasNext() {

                    return hasOpenAlternatives();
                    //return next != null;
                }

                @Override
                public Term next() {
                    Term next = this.next;

                    try {
                        this.next = solveNext().getSolutionOrNull();
                    } catch (NoMoreSolutionException e) {
                        this.next = null;
                    }
                    return next;
                }
            };
        } catch (Exception e) {
            return Collections.emptyIterator();
        }
    }






}