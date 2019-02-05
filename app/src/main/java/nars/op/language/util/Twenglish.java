/*
 * Copyright (C) 2014 tc
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http:
 */
package nars.op.language.util;

import jcog.io.Twokenize;
import jcog.io.Twokenize.Span;
import nars.$;
import nars.NAR;
import nars.task.TaskBuilder;
import nars.term.Term;
import nars.term.atom.Atomic;
import org.eclipse.collections.api.set.ImmutableSet;
import org.eclipse.collections.impl.factory.Sets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static nars.Op.BELIEF;


/**
 * Twitter English - english with additional tags for twitter-like content
 */
public class Twenglish {
    public static final ImmutableSet<String> prepositions = Sets.immutable.of(("aboard\n" +
        "about\n" +
        "above\n" +
        "across\n" +
        "after\n" +
        "against\n" +
        "along\n" +
        "amid\n" +
        "among\n" +
        "anti\n" +
        "around\n" +
        "as\n" +
        "at\n" +
        "before\n" +
        "behind\n" +
        "below\n" +
        "beneath\n" +
        "beside\n" +
        "besides\n" +
        "between\n" +
        "beyond\n" +
        "but\n" +
        "by\n" +
        "concerning\n" +
        "considering\n" +
        "despite\n" +
        "down\n" +
        "during\n" +
        "except\n" +
        "excepting\n" +
        "excluding\n" +
        "following\n" +
        "for\n" +
        "from\n" +
        "in\n" +
        "inside\n" +
        "into\n" +
        "like\n" +
        "minus\n" +
        "near\n" +
        "of\n" +
        "off\n" +
        "on\n" +
        "onto\n" +
        "opposite\n" +
        "outside\n" +
        "over\n" +
        "past\n" +
        "per\n" +
        "plus\n" +
        "regarding\n" +
        "round\n" +
        "save\n" +
        "since\n" +
        "than\n" +
        "through\n" +
        "to\n" +
        "toward\n" +
        "towards\n" +
        "under\n" +
        "underneath\n" +
        "unlike\n" +
        "until\n" +
        "up\n" +
        "upon\n" +
        "versus\n" +
        "via\n" +
        "with\n" +
        "within\n" +
        "without").split("\\r?\\n"));
    /**
     * http:
     */
    public static final ImmutableSet<String> personalPronouns = Sets.immutable.of("i,you,he,she,it,we,they,me,him,her,us,them".split(","));






    

    /**
     * substitutions
     */
    public final Map<String, String> sub = new HashMap<String, String>();


    
    boolean inputProduct = true;


    public static final Map<String, String> POS = new HashMap<String, String>() {{
        

        put("i", "pronoun");
        put("it", "pronoun");
        put("them", "pronoun");
        put("they", "pronoun");
        put("we", "pronoun");
        put("you", "pronoun");
        put("he", "pronoun");
        put("she", "pronoun");
        put("some", "pronoun");
        put("all", "pronoun");
        put("this", "pronoun");
        put("that", "pronoun");
        put("these", "pronoun");
        put("those", "pronoun");

        put("is", "verb");

        put("who", "qpronoun");
        put("what", "qpronoun");
        put("where", "qpronoun");
        put("when", "qpronoun");
        put("why", "qpronoun");
        put("which", "qpronoun");

        put("to", "prepos");
        put("at", "prepos");
        put("before", "prepos");
        put("after", "prepos");
        put("on", "prepos");
        put("but", "prepos");

        put("and", "conjunc");
        put("but", "conjunc");
        put("or", "conjunc");
        put("if", "conjunc");
        put("while", "conjunct");

    }};

    public Twenglish() {
        
        sub.put("go to", "goto");
        
    }


    @NotNull
    protected Collection<TaskBuilder> parseSentence(String source, @NotNull NAR n, @NotNull List<Span> s) {

        LinkedList<Term> t = new LinkedList();
        Span last = null;
        for (Span c : s) {
            t.add(spanToTerm(c));
            last = c;
        }
        if (t.isEmpty()) return Collections.emptyList();














        List<TaskBuilder> tt = new ArrayList();

        
        if (inputProduct) {

            Term tokens =
                    $.p(t.toArray(new Term[t.size()]));







            Term q = $.func("hear", Atomic.the(source), tokens);

            TaskBuilder newtask = new TaskBuilder(q, BELIEF, 1f, n).present(n); 
            tt.add(newtask); 


        }

        















        return tt;

    }


    @Nullable
    public static Term spanToTerm(@NotNull Span c) {
        return spanToTerm(c, false);
    }

    
    public static final Atomic EXCLAMATION = $.quote("!");
    public static final Atomic PERIOD = $.quote(".");
    public static final Atomic QUESTION_MARK = $.quote("?");
    public static final Atomic COMMA = $.quote(",");

    @Nullable
    public static Term spanToTerm(@NotNull Span c, boolean includeWordPOS) {
        switch (c.pattern) {
            case "word":
                
                if (!includeWordPOS) {
                    return lexToTerm(c.content);
                } else {
                    String pos = POS.get(c.content.toLowerCase());
                    if (pos != null) {
                        return $.prop(lexToTerm(c.content), tagToTerm(pos));
                    }
                }
                break;
            case "punct":
                switch (c.content) {
                    case "!":
                        return EXCLAMATION;
                    case ".":
                        return PERIOD;
                    case "?":
                        return QUESTION_MARK;
                    case ",":
                        return COMMA;
                }
                break;
        }

        return $.prop(lexToTerm(c.content), tagToTerm(c.pattern));
    }

    public static Term lexToTerm(String c) {
        
        return $.quote(c);
        

        















        
    }

    @NotNull
    public static Term tagToTerm(String c) {
        c = c.toLowerCase();
        if ("word".equals(c)) return $.quote(" ");
        return Atomic.the(c);
    }


    /**
     * returns a list of all tasks that it was able to parse for the input
     */
    @NotNull
    public List<TaskBuilder> parse(String source, @NotNull NAR n, String s) {


        List<TaskBuilder> results = $.newArrayList();

        List<Span> tokens = Twokenize.twokenize(s);

        List<List<Span>> sentences = $.newArrayList();

        List<Span> currentSentence = $.newArrayList(tokens.size());
        for (Span p : tokens) {

            currentSentence.add(p);

            if ("punct".equals(p.pattern)) {
                switch (p.content) {
                    case ".":
                    case "?":
                    case "!":
                        if (!currentSentence.isEmpty()) {
                            sentences.add(currentSentence);
                            currentSentence = $.newArrayList();
                            break;
                        }
                }
            }
        }

        if (!currentSentence.isEmpty())
            sentences.add(currentSentence);

        for (List<Span> x : sentences) {
            Collection<TaskBuilder> ss = parseSentence(source, n, x);
            if (ss != null)
                results.addAll(ss);
        }

        if (!results.isEmpty()) {











        }

        return results;
    }


}
