package alice.tuprolog;


import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 
 * @author <a href="mailto:giulio.piancastelli@unibo.it">Giulio Piancastelli</a>
 */
public class TermIteratorTestCase {
	
	@Test
	public void testEmptyIterator() {
		String theory = "";
		Iterator<Term> i = Term.getIterator(theory);
		assertFalse(i.hasNext());
		try {
			i.next();
			fail("");
		} catch (NoSuchElementException expected) {}
	}
	
	@Test public void testIterationCount() {
		String theory = "q(1)." + "\n" +
		                "q(2)." + "\n" +
		                "q(3)." + "\n" +
		                "q(5)." + "\n" +
		                "q(7).";
		Iterator<Term> i = Term.getIterator(theory);
		int count = 0;
		for (; i.hasNext(); count++)
			i.next();
		assertEquals(5, count);
		assertFalse(i.hasNext());
	}
	
	@Test public void testMultipleHasNext() {
		String theory = "p. q. r.";
		Iterator<Term> i = Term.getIterator(theory);
		assertTrue(i.hasNext());
		assertTrue(i.hasNext());
		assertTrue(i.hasNext());
		assertEquals(new Struct("p"), i.next());
	}
	
	@Test public void testMultipleNext() {
		String theory = "p(X):-q(X),X>1." + "\n" +
		                "q(1)." + "\n" +
						"q(2)." + "\n" +
						"q(3)." + "\n" +
						"q(5)." + "\n" +
						"q(7).";
		Iterator<Term> i = Term.getIterator(theory);
		assertTrue(i.hasNext());
		i.next(); 
		assertEquals(new Struct("q", new NumberTerm.Int(1)), i.next());
		assertEquals(new Struct("q", new NumberTerm.Int(2)), i.next());
		assertEquals(new Struct("q", new NumberTerm.Int(3)), i.next());
		assertEquals(new Struct("q", new NumberTerm.Int(5)), i.next());
		assertEquals(new Struct("q", new NumberTerm.Int(7)), i.next());
		
		assertFalse(i.hasNext());
		try {
			i.next();
			fail("");
		} catch (NoSuchElementException expected) {}
	}
	
	@Test public void testIteratorOnInvalidTerm() {
		String t = "q(1)"; 
		try {
			Term.getIterator(t);
			fail("");
		} catch (InvalidTermException expected) {}
	}
	
	@Test public void testIterationOnInvalidTheory() {
		String theory = "q(1)." + "\n" +
		                "q(2)." + "\n" +
						"q(3) " + "\n" + 
						"q(5)." + "\n" +
						"q(7).";
		Struct firstTerm = new Struct("q", new NumberTerm.Int(1));
		Struct secondTerm = new Struct("q", new NumberTerm.Int(2));
		Iterator<Term> i1 = Term.getIterator(theory);
		assertTrue(i1.hasNext());
		assertEquals(firstTerm, i1.next());
		assertTrue(i1.hasNext());
		assertEquals(secondTerm, i1.next());
		try {
			i1.hasNext();
			fail("");
		} catch (InvalidTermException expected) {}
		Iterator<Term> i2 = Term.getIterator(theory);
		assertEquals(firstTerm, i2.next());
		assertEquals(secondTerm, i2.next());
		try {
			i2.next();
			fail("");
		} catch (InvalidTermException expected) {}
	}
	
	@Test public void testRemoveOperationNotSupported() {
		String theory = "p(1).";
		Iterator<Term> i = Term.getIterator(theory);
		assertNotNull(i.next());
		try {
			i.remove();
			fail("");
		} catch (UnsupportedOperationException expected) {}
	}

}
