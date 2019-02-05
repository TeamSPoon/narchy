package alice.tuprologx.spyframe;

import alice.tuprolog.NumberTerm;
import alice.tuprolog.Struct;
import alice.tuprolog.Term;
import alice.tuprolog.Var;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/** GUI-Window containing a TermComponent that displays a prolog term.
 * Displaying should be a side effect of a corresponding prolog predicate
 * termframe(Term) that evaluates to constant true. Closing the window has
 * no consequences to the prolog process.
 * The windows also has an input field that shows the term. Changing this
 * will change the display without consequence to the prolog process.
 *
 * @author franz.beslmeisl at googlemail.com
 */
public class TermFrame extends JFrame implements ActionListener{
  
	private static final long serialVersionUID = 1L;

/**Transforms prolog terms into trees.*/ 
  public static final ToTree<Term> term2tree= new ToTree<Term>() {
    @Override
    public Node makeTreeFrom(Term term) {
        Node node = new Node(String.valueOf(term));
        node.textcolor = node.bordercolor = Color.BLACK;
        
        if (term instanceof Var) {
            Var var = (Var) term;
            node.text = var.name();
            node.textcolor = node.bordercolor = Color.BLUE;
            if (var.isBound()) {
                node.kids = new Node[1];
                node.kids[0] = makeTreeFrom(var.term());
            }
        } else if (term instanceof NumberTerm) {
            node.textcolor = node.bordercolor = Color.MAGENTA;
        } else if (term instanceof Struct) {
            Struct struct = (Struct) term;
            node.text = struct.name();
            int n = struct.subs();
            node.kids = new Node[n];
            for (int i = 0; i < n; i++)
                node.kids[i] = makeTreeFrom(struct.sub(i));
        }
        return node;
    }
};

  final JTextField input;
  final Tree<Term> ptt;

  /** Constructs a new TermFrame.
   *  @param term the prolog term to be displayed.
   */
  public TermFrame(Term term){
    super("termframe");
    setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    Container c=getContentPane();
    ptt= new Tree<>(term2tree, term);
    c.add(new JScrollPane(ptt));
    input=new JTextField();
    c.add(input, BorderLayout.SOUTH);
    input.setText(String.valueOf(term));
    pack();
    setVisible(true);
    input.addActionListener(this);
  }

  @Override
  public void actionPerformed(ActionEvent e){setTerm(input.getText());}

  /**Sets a new prolog term.
   * @param term to be displayed.
   */
  public void setTerm(Term term){
    ptt.setStructure(term);
    input.setText(String.valueOf(term));
    validate();
  }

  /**Sets a new prolog term.
   * @param sterm to be displayed.
   */
  public void setTerm(String sterm){
    Term term;
    try{term=Term.term(sterm);}
    catch(Exception ex){
      term=Term.term("'>illegal prolog term<'");
    }
    setTerm(term);
  }

  /** Displays a prolog term generated out of a string.
   * @param args array of length one containing the string.
   */
  public static void main(String... args){
    if(args.length!=1)
      System.out.println("Pass exactly one prolog term!");
    else{
      TermFrame tf=new TermFrame(Term.term(args[0]));
      tf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }
  }
}