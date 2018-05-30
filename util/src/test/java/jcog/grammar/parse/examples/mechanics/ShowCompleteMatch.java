package jcog.grammar.parse.examples.mechanics;

import jcog.grammar.parse.Parser;
import jcog.grammar.parse.examples.arithmetic.ArithmeticParser;
import jcog.grammar.parse.tokens.TokenAssembly;

/**
 * This class shows that Parser.completeMatch() returns
 * a complete match, or null.
 * 
 * @author Steven J. Metsker
 *
 * @version 1.0 
 */
public class ShowCompleteMatch {
	/**
	 * Show that </code>Parser.completeMatch()</code>
	 * returns a complete match, or null.
	 */
	public static void main(String[] args) throws ArithmeticException {

		Parser p = ArithmeticParser.start();

		TokenAssembly ta = new TokenAssembly("3 * 4 + 5 and more");

		System.out.println(p.bestMatch(ta));
		System.out.println(p.completeMatch(ta));
	}
}
