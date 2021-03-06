package jcog.learn.markov;

import com.google.common.collect.Iterables;
import jcog.io.Twokenize;

import java.util.HashMap;
import java.util.List;

/**
 * Adds functionality to the MarkovChain class which
 * tokenizes a String argument for use in the Markov graph.
 *
 * @author OEP
 */
public class MarkovSentence extends MarkovChain<String> {



    /**
     * Buffer to use when parsing our input source
     */
    



    public MarkovSentence(int tupleLength) {
        super(new HashMap<>(), tupleLength);
    }


















    /**
     * Stream-safe method to parse an InputStream.
     *
     * @param is InputStream to parse
     */
    public void parseSentence(String sentence)  {
        List<Twokenize.Span> phrase = Twokenize.twokenize(sentence);
        learn(Iterables.transform(phrase, Twokenize.Span::toString));
    }


    public String generateSentence() {
        return generateSentence(-1);
    }

    /**
     * Make our generated Markov phrase into a String
     * object that is more versatile.
     *
     * @return String of our Markov phrase
     */
    public String generateSentence(int len) {
        
        List<String> phrase = sample().generate(len);

        
        StringBuilder sb = new StringBuilder();
        int sz = phrase.size();

        
        for (int i = 0; i < sz; i++) {
            
            String word = phrase.get(i);

            
            if (i == 0) word = word.substring(0, 1).toUpperCase() + word.substring(1);

            
            if (i != sz - 1) word = word + ' ';

            sb.append(word);
        }

        return sb.toString();
    }




















}