/*
 * tuProlog - Copyright (C) 2001-2007  aliCE team at deis.unibo.it
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

import alice.util.OneWayList;
import jcog.data.set.ArrayHashSet;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Term class is the root abstract class for prolog data type
 *
 * @see Struct
 * @see Var
 * @see NumberTerm
 */
public abstract class Term implements Serializable, SubTree {

    protected static final Term[] EmptyTermArray = new Term[0];
    public static final Term TRUE = new Struct("true");
    public static final Term FALSE = new Struct("false");
    public static final String HALT = "halt.";
    public static final String NO = "no.";
    public static final String YES = "yes.";



    /**
     * is this term a null term?
     */
    public abstract boolean isEmptyList();


    /**
     * is this term a constant prolog term?
     */
    public abstract boolean isAtom();

    /**
     * is this term a prolog compound term?
     */
    public abstract boolean isCompound();

    /**
     * is this term a prolog (alphanumeric) atom?
     */
    public abstract boolean isAtomic();

    /**
     * is this term a prolog list?
     */
    public abstract boolean isList();

    /**
     * is this term a ground term?
     */
    public abstract boolean isGround();

    /**
     * Tests for the equality of two object terms
     * <p>
     * The comparison follows the same semantic of
     * the isEqual method.
     */
    public final boolean equals(Object t) {
        return this == t || ((t instanceof Term) && isEqual((Term) t));
    }


    /**
     * is term greater than term t?
     */
    public abstract boolean isGreater(Term t);

    public abstract boolean isGreaterRelink(Term t, ArrayList<String> vorder);

    /**
     * Tests if this term is (logically) equal to another
     */
    public abstract boolean isEqual(Term t);

    /**
     * Gets the actual term referred by this Term. if the Term is a bound variable, the method gets the Term linked to the variable
     */
    public Term term() {
        return this;
    }


    private final static AtomicLong tick = new AtomicLong(0);

    public static long now() {

        return tick.getAndIncrement();
    }

    /**
     * Resolves variables inside the term, starting from a specific time count.
     * <p>
     * If the variables has been already resolved, no renaming is done.
     *
     * @param count new starting time count for resolving process
     * @return the new time count, after resolving process
     */
    void resolveTerm(long count) {

    }


    /**
     * Resolves variables inside the term
     * <p>
     * If the variables has been already resolved, no renaming is done.
     */
    public void resolveTerm() {

    }


    /**
     * gets a engine's copy of this term.
     *
     * @param idExecCtx Execution Context identified
     */
    public Term copyGoal(AbstractMap<Var, Var> vars, int idExecCtx) {
        return copy(vars, idExecCtx);
    }


    /**
     * gets a copy of this term for the output
     */
    public Term copyResult(Collection<Var> goalVars, List<Var> resultVars) {
        int s = goalVars.size();
        IdentityHashMap<Var, Var> originals = new IdentityHashMap<>(s);
        if (s > 0) {
            for (Var key : goalVars) {
                Var clone;
                if (!key.isAnonymous())
                    clone = new Var(key.getOriginalName());
                else
                    clone = new Var();
                originals.put(key, clone);
                resultVars.add(clone);
            }
        }
        return copy(originals, null /*new IdentityHashMap<>()*/);
    }


    /**
     * gets a copy (with renamed variables) of the term.
     * <p>
     * The list argument passed contains the list of variables to be renamed
     * (if empty list then no renaming)
     *
     * @param vMap
     * @param idExecCtx Execution Context identifier
     */
    Term copy(Map<Var, Var> vMap, int idExecCtx) {
        return this;
    }


    /**
     * gets a copy for result.
     *
     * @param vMap
     * @param substMap
     */
    abstract Term copy(Map<Var, Var> vMap, Map<Term, Var> substMap);

    public final boolean unify(Prolog p, Term y) {
        return term()._unify(p, y.term());
    }

    /**
     * Try to unify two terms
     *
     * @param p  have the reference of EngineManager
     * @param y the term to unify
     * @return true if the term is unifiable with this one
     */
    public final boolean _unify(Prolog p, Term y) {

        if (this ==y)
            return true;

        if ((this instanceof Struct != y instanceof Struct) && !(this instanceof Var) && !(y instanceof Var))
            return false; //two differing types

        if (this.isGround() && y.isGround())
            return equals(y);



        EngineManager engine = p.engine;

        long now = Term.now();
        resolveTerm(now);
        y.resolveTerm(now);

        Collection<Var> v1 =
                new ArrayHashSet();
                //new HashSet();
                //new LinkedHashSet();
                //new UnifiedSet();
        Collection<Var> v2 =
                new ArrayHashSet();
                //new HashSet();
                //new LinkedHashSet();
                //new UnifiedSet();

        boolean ok = unify(v1, v2, y);

        if (ok) {
            ExecutionContext ec = engine.getCurrentContext();
            if (ec != null) {
                ec.trailingVars = new OneWayList<>(v1, ec.trailingVars);
            }

            int count = 0;
            Engine env = engine.getEnv();
            int id = (env == null) ? Var.PROGRESSIVE : env.nDemoSteps;
            for (Var v : v1) {
                v.rename(id, count);
                if (id >= 0) {
                    id++;
                } else {
                    count++;
                }
            }
            for (Var v : v2) {
                v.rename(id, count);
                if (id >= 0) {
                    id++;
                } else {
                    count++;
                }
            }


            return true;
        }
        Var.free(v1);
        Var.free(v2);

        return false;
    }


    /**
     * Tests if this term is unifiable with an other term.
     * No unification is done.
     * <p>
     * The test is done outside any demonstration context
     *
     * @param y the term to checked
     * @return true if the term is unifiable with this one
     */
    boolean unifiable(Term y) {

        long now = Term.now();
        resolveTerm(now);
        y.resolveTerm(now);
        Collection<Var> v1 =
                new ArrayHashSet();
                //new HashSet();
                //new LinkedHashSet<Var>();
                //new UnifiedSet(); //<- fails, why? unordered?

        Collection<Var> v2 =
                new ArrayHashSet();
                //new HashSet();
                //new LinkedHashSet<Var>();
                //new UnifiedSet(); //<- fails, why

        boolean ok = unify(v1, v2, y);
        Var.free(v1);
        Var.free(v2);
        return ok;
    }


    /**
     * Tries to unify two terms, given a demonstration context
     * identified by the mark integer.
     * <p>
     * Try the unification among the term and the term specified
     *
     * @param varsUnifiedArg1 Vars unified in myself
     * @param varsUnifiedArg2 Vars unified in term t
     */
    abstract boolean unify(Collection<Var> varsUnifiedArg1, Collection<Var> varsUnifiedArg2, Term t);


    /**
     * Static service to create a Term from a string.
     *
     * @param st the string representation of the term
     * @return the term represented by the string
     * @throws InvalidTermException if the string does not represent a valid term
     */
    public static Term term(String st) {
        return Parser.parseSingleTerm(st);
    }


    /**
     * Static service to create a Term from a string, providing an
     * external operator manager.
     *
     * @param st the string representation of the term
     * @param op the operator manager used to builder the term
     * @return the term represented by the string
     * @throws InvalidTermException if the string does not represent a valid term
     */
    public static Term term(String st, OperatorManager op) {
        return Parser.parseSingleTerm(st, op);
    }


    /**
     * Gets an iterator providing
     * a term stream from a source text
     */
    public static java.util.Iterator<Term> getIterator(String text) {
        return new Parser(text).iterator();
    }


    /**
     * Gets the string representation of this term
     * as an X argument of an operator, considering the associative property.
     */
    String toStringAsArgX(OperatorManager op, int prio) {
        return toStringAsArg(op, prio, true);
    }

    /**
     * Gets the string representation of this term
     * as an Y argument of an operator, considering the associative property.
     */
    String toStringAsArgY(OperatorManager op, int prio) {
        return toStringAsArg(op, prio, false);
    }

    /**
     * Gets the string representation of this term
     * as an argument of an operator, considering the associative property.
     * <p>
     * If the boolean argument is true, then the term must be considered
     * as X arg, otherwise as Y arg (referring to prolog associative rules)
     */
    String toStringAsArg(OperatorManager op, int prio, boolean x) {
        return toString();
    }


    /**
     * The iterated-goal term G of a term T is a term defined
     * recursively as follows:
     * <ul>
     * <li>if T unifies with ^(_, Goal) then G is the iterated-goal
     * term of Goal</li>
     * <li>else G is T</li>
     * </ul>
     */
    public Term iteratedGoalTerm() {
        return this;
    }

    /*Castagna 06/2011*/
    /**/


    @Override
    public final boolean isLeaf() {
        return true;
    }


}