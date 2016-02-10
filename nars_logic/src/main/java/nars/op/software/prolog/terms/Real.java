package nars.op.software.prolog.terms;

/**
 * Part of the Term hierarchy, implementing double float point numbers.
 * 
 * @see Term
 * @see Nonvar
 */
public class Real extends Num {

	public final double val;

	public Real(double i) {
		super(String.valueOf(i));
		val = i;
	}

	boolean bind_to(Term that, Trail trail) {
		return super.bind_to(that, trail) && val == ((Real) that).val;
	}

	public final int arity() {
		return Term.REAL;
	}

	public final double getValue() {
		return val;
	}
}
