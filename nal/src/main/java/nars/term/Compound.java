/*
 * CompoundTerm.java
 *
 * Copyright (C) 2008  Pei Wang
 *
 * This file is part of Open-NARS.
 *
 * Open-NARS is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * Open-NARS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Open-NARS.  If not, see <http:
 */
package nars.term;

import com.google.common.io.ByteArrayDataOutput;
import jcog.data.sexpression.IPair;
import jcog.data.sexpression.Pair;
import nars.IO;
import nars.Op;
import nars.subterm.Subterms;
import nars.subterm.util.TermList;
import nars.term.anon.Anon;
import nars.unify.Unify;
import nars.util.term.transform.Retemporalize;
import nars.util.term.transform.TermTransform;
import org.eclipse.collections.api.block.function.primitive.IntObjectToIntFunction;
import org.eclipse.collections.api.block.predicate.primitive.LongObjectPredicate;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static nars.Op.*;
import static nars.time.Tense.*;

/**
 * a compound term
 * TODO make this an interface extending Subterms
 */
public interface Compound extends Term, IPair, Subterms {


    static boolean equals(/*@NotNull*/ Compound a, @Nullable Term bb) {
        assert (a != bb) : "instance check should have already been performed before calling this";

        return
                (a.opX() == bb.opX())
                        &&
                        (a.dt() == bb.dt())
                        &&
                        (a.subterms().equals(bb.subterms()))
                ;
    }

    static String toString(Compound c) {
        return toStringBuilder(c).toString();
    }

    static StringBuilder toStringBuilder(Compound c) {
        StringBuilder sb = new StringBuilder(/* conservative estimate */ c.volume() * 2);
        try {
            c.append(sb);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return sb;
    }

    /**
     * whether any subterms (recursively) have
     * non-DTernal temporal relation
     */
    @Override
    default boolean isTemporal() {
        return (dt() != DTERNAL && op().temporal)
                ||
                (subterms().isTemporal());
    }

    @Override
    default boolean containsRecursively(Term t, boolean root, Predicate<Term> inSubtermsOf) {
        return !impossibleSubTerm(t) && inSubtermsOf.test(this) && subterms().containsRecursively(t, root, inSubtermsOf);
    }

    @Override
    Subterms subterms();

    @Override
    default int hashCodeSubterms() {
        return subterms().hashCode();
    }


    @Override
    default int opX() {
        return Term.opX(op(), (short) volume());
    }

    @Override
    default void recurseTerms(Consumer<Term> v) {
        v.accept(this);
        subterms().recurseTerms(v);
    }


    
















    @Override
    default Term anon() {
        return new Anon(2).put(this);
    }

    @Override
    default boolean recurseTerms(Predicate<Term> aSuperCompoundMust, Predicate<Term> whileTrue, @Nullable Term parent) {
        return (!aSuperCompoundMust.test(this)) || (subterms().recurseTerms(aSuperCompoundMust, whileTrue, this));
    }


    @Override
    default boolean ORrecurse(Predicate<Term> p) {
        return p.test(this) || subterms().ORrecurse(p);
    }

    @Override
    default boolean ANDrecurse(Predicate<Term> p) {
        return p.test(this) && subterms().ANDrecurse(p);
    }











    default void append(ByteArrayDataOutput out) {

        Op o = op();
        out.writeByte(o.id);
        subterms().append(out);
        if (o.temporal)
            out.writeInt(dt());

    }


    







    /**
     * unification matching entry point (default implementation)
     *
     * @param y compound to match against (the instance executing this method is considered 'x')
     * @param u the substitution context holding the match state
     * @return whether match was successful or not, possibly having modified subst regardless
     */
    @Override
    default boolean unify(/*@NotNull*/ Term y, /*@NotNull*/ Unify u) {
        return equals(y)
                ||
                (op() == y.op() && unifySubterms(y, u))
                ||
                y.unifyReverse(this, u);
    }

    default boolean unifySubterms(Term ty, Unify u) {
        if (!Terms.commonStructureTest(this, ty, u))
            return false;

        Subterms xsubs = subterms();
        Subterms ysubs = ty.subterms();

        int xs, ys;
        if ((xs = xsubs.subs()) != (ys = ysubs.subs()))
            return false;

        if (op().temporal) {
            
            int xdt = this.dt();
            int ydt = ty.dt();
            if (xdt!=ydt) {
                boolean xOrY;
                if (xdt == XTERNAL && ydt != XTERNAL) {
                    xOrY = false; 
                } else if (xdt != XTERNAL && ydt == XTERNAL) {
                    xOrY = true;
                } else {
                    if (xdt == DTERNAL && ydt != DTERNAL) {
                        xOrY = false;
                    } else if (xdt != DTERNAL && ydt == DTERNAL) {
                        xOrY = true;
                    } else {
                        return false; 
                    }
                }
            }

            if (xsubs.equals(ysubs))
                return true;
            
        }

        if (xs > 1 && isCommutative()) {
            
            
            
            
            boolean yCommutive = ty.isCommutative() && (ty.dt() != XTERNAL || ys != 2);
            return xsubs.unifyCommute(ysubs, yCommutive, u);
        } else {
            if (xs == 1) {
                return sub(0).unify(ysubs.sub(0), u);
            } else {
                return xsubs.unifyLinear(ysubs, u);
            }
        }
    }


    @Override
    default void append(/*@NotNull*/ Appendable p) throws IOException {
        IO.Printer.append(this, p);
    }













    @Override
    default Term sub(int i, Term ifOutOfBounds) {
        return subterms().sub(i, ifOutOfBounds);
    }


    @Nullable
    @Override
    default Object _car() {
        
        return sub(0);
    }

    /**
     * cdr or 'rest' function for s-expression interface when arity > 1
     */
    @Nullable
    @Override
    default Object _cdr() {
        int len = subs();
        switch (len) {
            case 1:
                throw new RuntimeException("Pair fault");
            case 2:
                return sub(1);
            case 3:
                return new Pair(sub(1), sub(2));
            case 4:
                return new Pair(sub(1), new Pair(sub(2), sub(3)));
        }

        
        Pair p = null;
        for (int i = len - 2; i >= 0; i--) {
            p = new Pair(sub(i), p == null ? sub(i + 1) : p);
        }
        return p;
    }


    /*@NotNull*/
    @Override
    default Object setFirst(Object first) {
        throw new UnsupportedOperationException();
    }

    /*@NotNull*/
    @Override
    default Object setRest(Object rest) {
        throw new UnsupportedOperationException();
    }


    @Override
    default int varDep() {
        return subterms().varDep();
    }

    @Override
    default int varIndep() {
        return subterms().varIndep();
    }


    @Override
    default int intifyRecurse(IntObjectToIntFunction<Term> reduce, int v) {
        return subterms().intifyRecurse(reduce, Term.super.intifyRecurse(reduce, v));
    }

    @Override
    default int intifyShallow(IntObjectToIntFunction<Term> reduce, int v) {
        return subterms().intifyShallow(reduce, v);
    }

    @Override
    default int varQuery() {
        return subterms().varQuery();
    }

    @Override
    default int varPattern() {
        return subterms().varPattern();
    }

    @Override
    default int vars() {
        return subterms().vars();
    }


    /*@NotNull*/
    @Override
    default Term sub(int i) {
        return subterms().sub(i);
    }

    @Override
    default boolean contains(Term t) {
        return subterms().contains(t);
    }

    @Override
    default boolean containsNeg(Term x) {
        return subterms().containsNeg(x);
    }

    @Override
    default boolean containsRoot(Term x) {
        if (!impossibleSubTerm(x)) {
            Term xr = x.root();
            return (OR(y -> y.root().equals(xr)));
        }
        return false;
    }

    @Override
    default boolean OR(/*@NotNull*/ Predicate<Term> p) {
        return subterms().OR(p);
    }

    @Override
    default boolean AND(/*@NotNull*/ Predicate<Term> p) {
        return subterms().AND(p);
    }

    /*@NotNull*/
    @Override
    default Term[] arrayClone() {
        return subterms().arrayClone();
    }

    @Override
    default Term[] arrayShared() {
        return subterms().arrayShared();
    }


    @Override
    default void forEach(/*@NotNull*/ Consumer<? super Term> c) {
        subterms().forEach(c);
    }


    @Override
    default int structure() {
        return subterms().structure() | op().bit;
    }


    @Override
    default int subs() {
        return subterms().subs();
    }

    @Override
    default int complexity() {
        return subterms().complexity(); 
    }

    @Override
    default int volume() {
        return subterms().volume();  
    }

    @Override
    default boolean impossibleSubTermVolume(int otherTermVolume) {
        return subterms().impossibleSubTermVolume(otherTermVolume);
    }


    @Override
    default boolean isCommutative() {
        Op op = op();
        if (!op.commutative)
            return false;

        if (op == CONJ) {
            int dt = dt();
            switch (dt) {
                case 0:
                case DTERNAL:
                case XTERNAL:
                    return true;
                
                default:
                    return false;
            }
        } else
            return subs() > 1;
    }


    @Override
    default void forEach(/*@NotNull*/ Consumer<? super Term> action, int start, int stop) {
        subterms().forEach(action, start, stop);
    }


    @Override
    default Iterator<Term> iterator() {
        return subterms().iterator();
    }

    @Override
    default void copyInto(/*@NotNull*/ Collection<Term> set) {
        subterms().copyInto(set);
    }










    @Override
    default boolean isNormalized() {
        return subterms().isNormalized();
    }













    /**
     * gets temporal relation value
     */
    @Override
    int dt();

    @Override
    default int eventCount() {
        return this.dt() != DTERNAL && op() == CONJ ? subterms().sum(Term::eventCount) : 1;
    }

    default Term replace(Term from, Term to) {
        if (!from.equals(to)) {
            if (this.equals(from))
                return to;

            Subterms oldSubs = subterms();
            Subterms newSubs = oldSubs.replaceSubs(from, to);
            if (newSubs == null)
                return Null;
            if (!newSubs.equals(oldSubs)) {
                return op().the(dt(), (TermList) newSubs);
            }
        }
        return this;
    }


    /**
     * TODO do shuffled search to return different repeated results wherever they may appear
     */
    @Override
    default int subTimeSafe(Term x, int after) {
        if (equals(x))
            return 0;

        Op op = op();
        if (op != CONJ)
            return DTERNAL;

        int dt = dt();
        if (dt == XTERNAL) 
            return DTERNAL;

        if (impossibleSubTerm(x))
            return DTERNAL;

        /*@NotNull*/
        Subterms yy = subterms();























        /*} else */

        /* HACK apply to other cases too */
        if (after >= dt) {
            Term yy1 = yy.sub(1);
            if (yy.sub(0).equals(yy1)) {
                
                
                if (x.equals(yy1))
                    return dt;
            }
        }

        boolean reverse;
        int idt;
        if (dt == DTERNAL || dt == 0) {
            idt = 0; 
            reverse = false;
        } else {
            idt = dt;
            if (idt < 0) {
                idt = -idt;
                reverse = true;
            } else {
                reverse = false;
            }
        }

        int ys = yy.subs();
        int offset = 0;
        for (int yi = 0; yi < ys; yi++) {
            Term yyy = yy.sub(reverse ? ((ys - 1) - yi) : yi);
            int sdt = yyy.subTimeSafe(x, after - offset);
            if (sdt != DTERNAL)
                return sdt + offset;
            offset += idt + yyy.dtRange();
        }

        return DTERNAL;
    }


    @Override
    default Term dt(int nextDT) {
        return nextDT != dt() ? Op.dt(this, nextDT) : this;
    }










































































    /* collects any contained events within a conjunction*/
    @Override
    default boolean eventsWhile(LongObjectPredicate<Term> events, long offset, boolean decomposeConjParallel, boolean decomposeConjDTernal, boolean decomposeXternal, int level) {
        Op o = op();
        if (o == CONJ) {
            int dt = dt();

            if ((decomposeConjDTernal || dt != DTERNAL) && (decomposeConjParallel || dt != 0) && (decomposeXternal || dt != XTERNAL)) {

                if (dt == DTERNAL)
                    dt = 0;
                else if (dt == XTERNAL) 
                    dt = 0;

                Subterms tt = subterms();
                int s = tt.subs();
                long t = offset;


                boolean changeDT = t != ETERNAL && t != TIMELESS;

                level++;

                if (dt >= 0) {
                    
                    for (int i = 0; i < s; i++) {
                        Term st = tt.sub(i);
                        if (!st.eventsWhile(events, t,
                                decomposeConjParallel, decomposeConjDTernal, decomposeXternal,
                                level)) 
                            return false;

                        if (changeDT)
                            t += dt + st.dtRange();
                    }
                } else {
                    
                    for (int i = s - 1; i >= 0; i--) {
                        Term st = tt.sub(i);
                        if (!st.eventsWhile(events, t,
                                decomposeConjParallel, decomposeConjDTernal, decomposeXternal,
                                level)) 
                            return false;

                        if (changeDT)
                            t += -dt + st.dtRange();
                    }

                }

                return true;
            }

        }

        return events.accept(offset, this);
    }














    @Override
    default boolean hasXternal() {
        return dt() == XTERNAL ||
                subterms().hasXternal();
    }

    @Override
    default Term unneg() {
        if (op() == NEG) {

            Term u = sub(0);


            return u;

        } else {
            return this;
        }
    }



    @Override
    @Nullable
    default Term normalize(byte varOffset) {
        if (varOffset == 0 && this.isNormalized())
            return this;






        Term y = transform(
                new nars.util.term.transform.CompoundNormalization(this, varOffset)
        );

        if (varOffset == 0 && y instanceof Compound) {
            
                y.subterms().setNormalized();
            
        }

        return y;
    }


    @Override
    @Nullable
    default Term transform(TermTransform t) {
        Termed y = t.transformCompound(this);
        if (y == this)
            return this; 
        else if (y != null)
            return y.term();
        else
            return null;
    }

    @Override
    default int dtRange() {
        Op o = op();
        switch (o) {





            case CONJ:

                Subterms tt = subterms();
                int l = tt.subs();
                if (l == 2) {
                    int dt = dt();

                    switch (dt) {
                        case DTERNAL:
                        case XTERNAL:
                        case 0:
                            dt = 0;
                            break;
                        default:
                            dt = Math.abs(dt);
                            break;
                    }

                    return tt.sub(0).dtRange() + (dt) + tt.sub(1).dtRange();

                } else {
                    int s = 0;


                    for (int i = 0; i < l; i++) {
                        s = Math.max(s, tt.sub(i).dtRange());
                    }

                    return s;
                }

            default:
                return 0;
        }

    }

    @Override
    @Nullable
    default Term temporalize(Retemporalize r) {
        return r.transformCompound(this);
    }

    /*@NotNull*/
    @Override
    default Term root() {
        return temporalize(Retemporalize.root);
    }


    @Override
    default Term concept() {

        Term term = unneg().root(); 

        Op op = term.op();
        assert (op != NEG): this + " concept() to NEG: " + unneg().root();
        if (!op.conceptualizable)
            return Null;


        Term term2 = term.normalize();
        if (term2 != term) {
            if (term2 == null)
                return Null;

            assert (term2.op() == op);



            term = term2;
        }


        return term;
    }

    @Override
    default boolean equalsRoot(Term x) {
        if (this.equals(x))
            return true;

        
        if (
                opX() == x.opX()
                        &&
                        structure() == x.structure()
        ) {

            Term root = root();
            return (root != this && root.equals(x)) || root.equals(x.root());
        }

        return false;
    }


    

































    



















}























































































    /*
    @Override
    public boolean equals(final Object that) {
        return (that instanceof Term) && (compareTo((Term) that) == 0);
    }
    */









































































































































































    /* UNTESTED
    public Compound clone(VariableTransform t) {
        if (!hasVar())
            throw new RuntimeException("this VariableTransform clone should not have been necessary");

        Compound result = cloneVariablesDeep();
        if (result == null)
            throw new RuntimeException("unable to clone: " + this);

        result.transformVariableTermsDeep(t);

        result.invalidate();

        return result;
    } */


















































/**
 * override in subclasses to avoid unnecessary reinit
 */
    /*public CompoundTerm _clone(final Term[] replaced) {
        if (Terms.equals(term, replaced)) {
            return this;
        }
        return clone(replaced);
    }*/





















































    /*static void shuffle(final Term[] list, final Random randomNumber) {
        if (list.length < 2)  {
            return;
        }


        int n = list.length;
        for (int i = 0; i < n; i++) {
            
            int r = i + (randomNumber.nextInt() % (n-i));
            Term tmp = list[i];    
            list[i] = list[r];
            list[r] = tmp;
        }
    }*/

/*        public static void shuffle(final Term[] ar,final Random rnd)
        {
            if (ar.length < 2)
                return;



          for (int i = ar.length - 1; i > 0; i--)
          {
            int index = randomNumber.nextInt(i + 1);
            
            Term a = ar[index];
            ar[index] = ar[i];
            ar[i] = a;
          }

        }*/















































































































































































