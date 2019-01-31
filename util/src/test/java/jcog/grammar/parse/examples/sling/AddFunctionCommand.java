package jcog.grammar.parse.examples.sling;

import jcog.grammar.parse.examples.imperative.Command;

/**
 * This command, when executed, evaluates a renderable 
 * function and adds it to a renderable collection.
 * 
 * @author Steven J. Metsker
 * 
 * @version 1.0 
 */
public class AddFunctionCommand extends Command {
	private RenderableCollection renderables;
	private SlingFunction f;
	private Variable nLine;

	/**
	 * Construct a command to addAt the supplied function to the
	 * supplied function collection.
	 *
	 * @param   RenderableCollection   the collection
	 *
	 * @param   SlingFunction   the function to evaluate and addAt
	 *                          at execution time
	 *
	 * @param   nLine   a varialbe representing the number of
	 *                  lines to plot when rendering the
	 *                  function
	 */
	public AddFunctionCommand(RenderableCollection renderables, SlingFunction f, Variable nLine) {

		this.renderables = renderables;
		this.f = f;
		this.nLine = nLine;
	}

	/**
	 * Evaluate the function and addAt it to the collection.
	 */
	public void execute() {
		renderables.add(new Renderable(f.eval(), nLine.eval()));
	}

	/**
	 * Returns a string description of this command.
	 *
	 * @return   a string description of this command
	 */
	public String toString() {
		return "addAt(" + f + ", " + renderables + ")";
	}
}
