
package nars.op.language;

import nars.*;
import nars.bag.leak.TaskLeak;
import nars.op.language.util.IRC;
import nars.term.Term;
import nars.truth.Truth;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pircbotx.exception.IrcException;
import org.pircbotx.hooks.events.MessageEvent;
import org.pircbotx.hooks.events.PrivateMessageEvent;
import org.pircbotx.hooks.types.GenericMessageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static nars.Op.INH;
import static nars.Op.PROD;
import static nars.time.Tense.ETERNAL;

/**
 * http:
 * <p>
 * $0.9;0.9;0.99$
 * <p>
 * $0.9;0.9;0.99$ (hear(?someone, $something) ==>+1 hear(I,$something)).
 * $0.9;0.9;0.99$ (((hear(#someone,#someThing) &&+1 hear(#someone,$nextThing)) && hear(I, #someThing)) ==>+1 hear(I, $nextThing)).
 * $0.9;0.9;0.99$ (((hear($someone,$someThing) &&+1 hear($someone,$nextThing)) <=> hear($someone, ($someThing,$nextThing)))).
 * $0.9;0.9;0.99$ (((I<->#someone) && hear(#someone, $something)) ==>+1 hear(I, $something)).
 * $0.9;0.9;0.99$ hear(I, #something)!
 * hear(I,?x)?
 * <p>
 * $0.9$ (($x,"the") <-> ($x,"a")).
 * ((($x --> (/,hear,#c,_)) &&+1 ($y --> (/,hear,#c,_))) ==> bigram($x,$y)).
 */
public class IRCNLP extends IRC {
    private static final Logger logger = LoggerFactory.getLogger(IRCNLP.class);

    
    private final NAR nar;
    

    private final boolean hearTwenglish = true;

    
    private final String[] channels;
    private final MyLeakOut outleak;
    final Vocalization speech;

    boolean trace;




    public IRCNLP(NAR nar, String nick, String server, String... channels) {
        super(nick, server, channels);

        this.nar = nar;
        this.channels = channels;
        this.speech = new Vocalization(nar, 2f, this::send);


















        outleak = new MyLeakOut(nar, channels);


        /*
        $0.9;0.9;0.99$ (hear(?someone, $something) ==>+1 hear(I,$something)).
 $0.9;0.9;0.99$ (((hear(#someone,#someThing) &&+1 hear(#someone,$nextThing)) && hear(I, #someThing)) ==>+1 hear(I, $nextThing)).
 $0.9;0.9;0.99$ (((hear($someone,$someThing) &&+1 hear($someone,$nextThing)) <=> hear($someone, ($someThing,$nextThing)))).
 $0.9;0.9;0.99$ (((I<->#someone) && hear(#someone, $something)) ==>+1 hear(I, $something)).
 $0.9;0.9;0.99$ hear(I, #something)!
 hear(I,?x)?

 $0.9$ (($x,"the") <-> ($x,"a")).
         */



























    }

    /**
     * identical with IRCAgent, TODO share them
     */
    private class MyLeakOut extends TaskLeak {
        public final String[] channels;

        public MyLeakOut(NAR nar, String... channels) {
            super(8, 0.05f, nar);
            this.channels = channels;
        }

        @Override
        public float value() {
            return 1;
        }

        @Override
        protected float leak(Task next) {
            boolean cmd = next.isCommand();
            if (cmd || (trace && !next.isDeleted())) {
                String s = (!cmd) ? next.toString() : next.term().toString();
                Runnable r = IRCNLP.this.send(channels, s);
                if (r != null) {
                    nar.runLater(r);
                    if (Param.DEBUG && !next.isCommand())
                        logger.info("{}\n{}", next, next.proof());
                } else {
                    
                }
                return cmd ? 0 : 1; 
            }
            return 0;
        }

        @Override
        public boolean preFilter(@NotNull Task next) {
            if (trace || next.isCommand())
                return super.preFilter(next);
            return false;
        }
    }

    public void setTrace(boolean trace) {
        this.trace = trace;
    }

    
















































    void hear(String text, String src) {

        NARHear.hearIfNotNarsese(nar, text, src, (t) -> {
            return new NARHear(nar, NARHear.tokenize(t.toLowerCase()), src, 200);



        });
    }

    @Override
    public void onPrivateMessage(PrivateMessageEvent event) {
        
    }

    @Override
    public void onGenericMessage(GenericMessageEvent event) {

        if (event instanceof MessageEvent) {
            MessageEvent pevent = (MessageEvent) event;

            if (pevent.getUser().equals(irc.getUserBot())) {
                return; 
            }

            String msg = pevent.getMessage().trim();

            String src = pevent.getUser().getNick(); 
            String channel = pevent.getChannel().getName();

            try {

                hear(msg, src);

            } catch (Exception e) {
                pevent.respond(e.toString());
            }


            
            
        }


    }


    public static void main(String[] args) {

        

        float durFPS = 20f;
        NAR n = NARS.realtime(durFPS).get();

        n.activateConceptRate.set(0.2f);
        n.forgetRate.set(1f);

        n.freqResolution.set(0.2f);
        n.confResolution.set(0.05f);

        n.termVolumeMax.set(48);

        /*@NotNull Default n = new Default(new Default.DefaultTermIndex(4096),
            new RealTime.DS(true),
            new TaskExecutor(256, 0.25f));*/



        


        

        new Thread(() -> {
            try {
                new TextUI(n, 1024);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();


        IRCNLP bot = new IRCNLP(n,
                
                "nar" + Math.round(64 * 1024 * Math.random()),
                "irc.freenode.net",
                "#123xyz"
                
        );


        Term HEAR = $.the("hear");
        

        n.onTask(t -> {
            
            
            Term tt = t.term();
            long start = t.start();
            if (start != ETERNAL) {
                if (t.isBeliefOrGoal() /* BOTH */) {
                    long now = n.time();
                    int dur = n.dur();
                    if (start >= now - dur) {
                        if (tt.op()==INH && HEAR.equals(tt.sub(1))) {
                            if (tt.subIs(0, PROD) && tt.sub(0).subIs(0, Op.ATOM)) {
                                bot.speak(tt.sub(0).sub(0), start, t.truth());
                            }
                        }
                    }
                }
            }
        });


        









        
























        NARHear.readURL(n);
        n.logPriMin(System.out, 0.9f);

        n.start();

        try {
            bot.start();
        } catch (IOException | IrcException e) {
            e.printStackTrace();
        }























































































































    }


    private void speak(Term word, long when, @Nullable Truth truth) {
        speech.speak(word, when, truth);
    }


    String s = "";
    int minSendLength = 24;

    protected float send(Term o) {
        Runnable r = null;
        synchronized (channels) {
            String w = $.unquote(o);
            boolean punctuation = w.equals(".") || w.equals("!") || w.equals("?");
            this.s += w;
            if (!punctuation)
                s += " ";
            if ((!s.isEmpty() && punctuation) || this.s.length() > minSendLength) {
                
                r = IRCNLP.this.send(channels, this.s.trim());
                this.s = "";
            }
        }


        if (r != null) {
            r.run();
            
        }

        return 1;
    }

}
