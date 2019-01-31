package jcog.grammar.parse.examples.tokens;

import jcog.grammar.parse.tokens.Token;
import jcog.grammar.parse.tokens.Tokenizer;

import java.io.IOException;

/**
 * This class shows how to addAt a new multi-character symbol.
 * 
 * @author Steven J. Metsker
 *
 * @version 1.0 
 */
public class ShowNewSymbol {
	/**
	 * Demonstrate how to addAt a multi-character symbol.
	 */
	public static void main(String args[]) throws IOException {
		Tokenizer t = new Tokenizer("42.001 =~= 42");

		t.symbolState().add("=~=");

		while (true) {
			Token tok = t.nextToken();
			if (tok.equals(Token.EOF)) {
				break;
			}
			System.out.println(tok);
		}
	}
}
