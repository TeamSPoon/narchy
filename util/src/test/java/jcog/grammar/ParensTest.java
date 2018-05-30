













package jcog.grammar;


import jcog.grammar.synthesize.GrammarFuzzer;
import jcog.grammar.synthesize.GrammarSynthesis;
import jcog.grammar.synthesize.util.GrammarUtils;
import jcog.grammar.synthesize.util.Log;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Stack;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class ParensTest {

	@Test
	public void test1() {

		

		
		Predicate<String> oracle = query -> {
			Stack<Character> stack = new Stack<>();
			for (int i = 0; i < query.length(); i++) {
				char c = query.charAt(i);
				if (c == '(' || c == '[' || c == '{') {
					
					stack.push(c);
				} else if (c == ')' || c == ']' || c == '}') {
					
					if (stack.isEmpty()) {
						return false;
					}
					char d = stack.pop();
					if ((d == '(' && c != ')') || (d == '[' && c != ']') || (d == '{' && c != '}')) {
						return false;
					}
				} else {
					return false;
				}
			}
			return stack.isEmpty();
		};

		
		List<String> examples = Arrays.asList("{([][])([][])}{[()()][()()]}");

		
		GrammarUtils.Grammar grammar = GrammarSynthesis.learn(examples, oracle);

		
		Iterable<String> samples = new GrammarFuzzer.GrammarMutationSampler(grammar, new GrammarFuzzer.SampleParameters(new double[]{
				0.2, 0.2, 0.2, 0.4}, 
				0.8,                                          
				0.1,                                          
				100),
                1000, 20, new Random(0));

		int pass = 0;
		int count = 0;
        int numSamples = 10;
		for(String sample : samples) {
			Log.info("SAMPLE: " + sample);
			if(oracle.test(sample)) {
				Log.info("PASS");
				pass++;
			} else {
				Log.info("FAIL");
			}
			Log.info("");
			count++;
			if(count >= numSamples) {
				break;
			}
		}
		Log.info("PASS RATE: " + (float)pass/numSamples);

		assertEquals(pass, numSamples);
	}
}