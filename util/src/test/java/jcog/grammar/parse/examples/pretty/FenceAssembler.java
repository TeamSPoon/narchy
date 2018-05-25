package jcog.grammar.parse.examples.pretty;

import jcog.grammar.parse.Assembly;
import jcog.grammar.parse.IAssembler;

/**
 * Places a given "fence" or marker object on an assembly's
 * stack. 
 * 
 * @author Steven J. Metsker
 *
 * @version 1.0 
 */
public class FenceAssembler implements IAssembler {
	protected Object fence;

	/**
	 * Construct an assembler that will place the given object
	 * on an assembly's stack.
	 */
	public FenceAssembler(Object fence) {
		this.fence = fence;
	}

	/**
	 * Place the fence object on the assembly's stack.
	 *
	 * @param  Assembly  the assembly to work on
	 */
	public void accept(Assembly a) {
		a.push(fence);
	}
}