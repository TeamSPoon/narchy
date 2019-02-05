package nars;

import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TerminalTextUtils;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.SimpleTheme;
import com.googlecode.lanterna.graphics.ThemeDefinition;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.ExtendedTerminal;
import com.googlecode.lanterna.terminal.MouseCaptureMode;
import com.googlecode.lanterna.terminal.Terminal;
import com.googlecode.lanterna.terminal.ansi.TelnetTerminal;
import com.googlecode.lanterna.terminal.ansi.TelnetTerminalServer;
import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal;
import com.googlecode.lanterna.terminal.virtual.VirtualTerminal;
import com.googlecode.lanterna.terminal.virtual.VirtualTerminalListener;
import jcog.event.Off;
import jcog.math.FloatRange;
import jcog.math.MutableInteger;
import jcog.pri.PLink;
import jcog.pri.PriReference;
import jcog.pri.Prioritized;
import jcog.pri.bag.impl.ArrayBag;
import jcog.pri.bag.impl.PLinkArrayBag;
import jcog.pri.op.PriMerge;
import nars.control.DurService;
import nars.control.NARService;
import nars.link.Activate;
import nars.op.language.NARHear;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static com.googlecode.lanterna.gui2.BorderLayout.Location.*;
import static com.googlecode.lanterna.gui2.Window.Hint.NO_POST_RENDERING;

/**
 * https:
 * https:
 */
public class TextUI {

    final static org.slf4j.Logger logger = LoggerFactory.getLogger(TextUI.class);
    private final NAR nar;

    final Set<TextGUI> sessions = Sets.newConcurrentHashSet();

    /** HACK because Lanterna's Component.onRemove doesnt get called reliably */
    private final Set<DurService> updaters = Sets.newConcurrentHashSet();

    public TextUI(NAR n) {
        this.nar = n;
    }

    public DefaultVirtualTerminal session(float fps) {
        DefaultVirtualTerminal vt = new DefaultVirtualTerminal(new TerminalSize(80, 25));
        TextGUI session = new TextGUI(nar, vt, fps);
        sessions.add(session);
        return vt;
    }

    public TextUI(NAR n, Terminal t, float fps) {
        this.nar = n;
        TextGUI session = new TextGUI(n, t, fps);
        sessions.add(session);
    }

    public TextUI(NAR n, int telnetPort) throws IOException {
        this(n);
        TelnetTerminalServer server = new TelnetTerminalServer(telnetPort, Charset.forName("utf-8"));
        logger.info("telnet listen on port={}", telnetPort);
        while (true) {
            TelnetTerminal conn = server.acceptConnection();
            if (conn != null) {
                logger.info("connect from {}", conn.getRemoteSocketAddress());
                TextGUI session = new TextGUI(n, conn, 10f);
                
                sessions.add(session);
            }
        }
    }


    private class TextGUI extends NARService implements Runnable {


        public final FloatRange guiUpdateFPS;
        private final Terminal terminal;
        private TerminalScreen screen;
        private Thread thread;
        private MultiWindowTextGUI tui;

        public TextGUI(NAR nar, Terminal terminal, float fps) {
            super(nar);
            this.terminal = terminal;
            this.guiUpdateFPS = new FloatRange(fps, 0.01f, 20f);
            sessions.add(this);
        }

        @Override
        protected void starting(NAR nar) {
            super.starting(nar);
            thread = new Thread(this);
            thread.setUncaughtExceptionHandler((t, e) -> {
                off();
            });
            thread.start();
        }

        @Override
        public void run() {


            try {
                if (terminal instanceof ExtendedTerminal)
                    ((ExtendedTerminal) terminal).setMouseCaptureMode(MouseCaptureMode.CLICK);

                if (terminal instanceof VirtualTerminal)
                    ((VirtualTerminal) terminal).addVirtualTerminalListener(new VirtualTerminalListener() {
                        @Override
                        public void onFlush() {

                        }

                        @Override
                        public void onBell() {

                        }

                        @Override
                        public void onClose() {
                            off();
                        }

                        @Override
                        public void onResized(Terminal terminal, TerminalSize terminalSize) {

                        }
                    });

                screen = new TerminalScreen(terminal);
                screen.startScreen();
            } catch (IOException e) {
                logger.warn("{} {}", this, e.getMessage());
                off();
                return;
            }


            tui = new MultiWindowTextGUI(screen);
            
            tui.setEOFWhenNoWindows(true);


            TextColor.Indexed limegreen = TextColor.ANSI.Indexed.fromRGB(127, 255, 0);
            TextColor.Indexed orange = TextColor.ANSI.Indexed.fromRGB(255, 127, 0);
            TextColor.Indexed darkblue = TextColor.ANSI.Indexed.fromRGB(5, 5, 80);
            TextColor.Indexed darkgreen = TextColor.ANSI.Indexed.fromRGB(5, 80, 5);

            TextColor.Indexed white = TextColor.ANSI.Indexed.fromRGB(255, 255, 255);
            SimpleTheme st = SimpleTheme.makeTheme(
                                /*SimpleTheme makeTheme(
                                    boolean activeIsBold,
                                     TextColor baseForeground,            TextColor baseBackground,
                                            TextColor editableForeground,            TextColor editableBackground,
                                                       TextColor selectedForeground,            TextColor selectedBackground,
                                                               TextColor guiBackground) {*/
                    true,
                    white, TextColor.ANSI.BLACK,
                    orange, darkgreen,
                    white, darkblue,
                    TextColor.ANSI.BLACK
            );

            st.setWindowPostRenderer(null);

            tui.setTheme(st);


            final BasicWindow window = new BasicWindow();
            window.setHints(List.of(Window.Hint.FULL_SCREEN, NO_POST_RENDERING, Window.Hint.NO_DECORATIONS));
            window.setEnableDirectionBasedMovements(true);


            

            
            

            















            Panel center = new Panel(new BorderLayout());







            Supplier<Component> defaultMenu;

            ComboBox menu = new ComboBox();
            menu.addItem(new View("Tasks", defaultMenu = () -> {
                return new TaskListBox(64);
            }));
            menu.addItem(new View("Concepts", () -> {
                return new BagListBox<Activate>(64) {
                    @Override
                    public void update() {
                        NAR n = TextGUI.this.nar;
                        if (n == null)
                            return;
                        n.conceptsActive().forEach(this::add);
                        super.update();
                    }
                };
            }));
            menu.addItem(new View("Stats", () -> {
                return new EmotionDashboard();
            }));
            menu.addListener(new ComboBox.Listener() {
                View e = null;

                @Override
                public void onSelectionChanged(int selectedIndex, int previousSelection) {
                    View v = ((View) menu.getSelectedItem());
                    if (v == e) return;
                    center.addComponent(v.builder.get(), CENTER);
                    this.e = v;
                }
            });

            center.addComponent(defaultMenu.get(), CENTER);

            final InputTextBox input = new InputTextBox();

            Panel root = new Panel(new BorderLayout());

            Panel top = new Panel(new BorderLayout());
            {
                top.addComponent(Panels.horizontal(menu, new Separator(Direction.VERTICAL)), LEFT);
                top.addComponent(input, CENTER);
            }

            root.addComponent(center, CENTER);
            root.addComponent(top, TOP);
            window.setComponent(root);

            tui.getGUIThread().invokeLater(input::takeFocus);

            tui.addWindowAndWait(window);
        }

        @Override
        protected void stopping(NAR nar) {
            updaters.forEach(DurService::off);
            updaters.clear();

            Collection<Window> w = tui.getWindows();









            w.forEach(Window::close);
            w.forEach(tui::removeWindow);

            sessions.remove(this);

            if (screen != null) {
                try {
                    screen.stopScreen();
                } catch (IOException e) {
                    
                }
            }

            synchronized (terminal) {
                if (thread != null) {
                    thread.interrupt();
                    thread = null;
                }
                TextGUIThread tt = tui.getGUIThread();
                if (tt!=null) {
                    Thread ttt = tt.getThread();
                    if (ttt!=null)
                        ttt.interrupt();
                }
            }

        }




        private class InputTextBox extends TextBox {
            public InputTextBox() {
                super(new TerminalSize(20, 1));
                setHorizontalFocusSwitching(true);
                setVerticalFocusSwitching(true);
            }

            @Override
            public synchronized Result handleKeyStroke(KeyStroke keyStroke) {
                Result r = super.handleKeyStroke(keyStroke);
                if (keyStroke.getKeyType() == KeyType.Enter) {
                    String t = getText().trim();
                    setText("");
                    setCaretPosition(0);


                    if (!t.isEmpty()) {

                        NARHear.hear(nar, t, "console");

                    }
                    runOnGUIThreadIfExistsOtherwiseRunDirect(InputTextBox.this::takeFocus);
                }
                return r;
            }
            











        }


        DurService newGUIUpdate(Runnable r) {
            DurService u = DurService.on(nar, r);
            updaters.add(u);
            return u;




        }

        private class TaskListRenderer extends AbstractListBox.ListItemRenderer {

            final ThemeDefinition themeDefinition;

            private TaskListRenderer(AbstractListBox table) {
                this.themeDefinition = table.getTheme().getDefinition(AbstractListBox.class);
            }

            @Override
            public void drawItem(TextGUIGraphics graphics, AbstractListBox table, int index, Object item, boolean selected, boolean focused) {

                Task t = (Task) item; 


                if (selected && focused) {
                    graphics.applyThemeStyle(themeDefinition.getSelected());
                } else {
                    
                    int c = (int) (t.priElseZero() * 175 + 75);
                    int r, g, b;
                    if (t.isBelief()) {
                        r = g = b = c / 2;
                    } else if (t.isGoal()) {
                        g = c;
                        r = b = 5;
                    } else if (t.isQuestionOrQuest()) {
                        b = c;
                        r = g = 5;
                    } else {
                        r = g = b = 255; 
                    }

                    graphics.setForegroundColor(
                            TextColor.Indexed.fromRGB(r, g, b)
                    );
                }

                String label = t.toString();
                
                int cols = graphics.getSize().getColumns() - 1;
                label = TerminalTextUtils.fitString(label, cols);
                int w = TerminalTextUtils.getColumnWidth(label);
                graphics.putString(0, 0, Strings.padEnd(label, cols - w, ' '));
            }
        }

        private class EmotionDashboard extends Panel {
            private final TextBox stats;
            private final DurService on;
            private final AtomicBoolean busy = new AtomicBoolean(false);

            public EmotionDashboard() {
                super(new BorderLayout());


                stats = new TextBox(" \n \n");
                stats.setReadOnly(true);
                stats.setHorizontalFocusSwitching(true);
                stats.setVerticalFocusSwitching(true);


                addComponent(stats, CENTER);

                on = newGUIUpdate(this::update);
            }

            @Override
            public synchronized void onRemoved(Container container) {
                on.off();
                updaters.remove(on);
                super.onRemoved(container);
                removeAllComponents();
            }

            private final StringBuilder sb = new StringBuilder(1024);

            protected void update() {
                com.googlecode.lanterna.gui2.TextGUI gui = getTextGUI();
                if (gui != null && busy.compareAndSet(false, true)) {
                    nar.stats(sb);

                    String s = sb.toString();
                    sb.setLength(0);
                    s.replace('\t', ' ');


                    gui.getGUIThread().invokeLater(() -> {
                        stats.setText(s);
                        busy.set(false);
                    });

                }
            }


        }

        class BagListBox<X extends Prioritized> extends AbstractListBox {
            protected final ArrayBag<X, PriReference<X>> bag;


            protected final AtomicBoolean paused = new AtomicBoolean(false);
            protected final AtomicBoolean changed = new AtomicBoolean(false);
            protected final MutableInteger visible = new MutableInteger();

            protected final DurService update;
            float priInfluenceRate = 1f;
            private boolean autoupdate = true;

            public BagListBox(int capacity) {
                this(capacity, true);
            }

            public BagListBox(int capacity, boolean autoupdate) {

                this.autoupdate = autoupdate;
                visible.set(capacity);

                bag = new PLinkArrayBag<X>(PriMerge.replace, capacity * 2) {
                    @Override
                    protected boolean cleanIfFull() {
                        return true;
                    }
                };
                






                
                update = newGUIUpdate(this::update);

            }

            public void add(X x) {
                add(new PLink<>(x, priInfluenceRate * x.priElseZero()));
            }

            public void add(PLink<X> p) {
                if (bag.put(p) != null) {
                    changed.set(true); 
                }
            }

            public void update() {

                if (autoupdate || changed.compareAndSet(true, false)) {
                    com.googlecode.lanterna.gui2.TextGUI gui = getTextGUI();
                    if (gui != null) {
                        TextGUIThread guiThread = gui.getGUIThread();
                        if (guiThread != null) {

                            next.clear();
                            bag.commit();
                            bag.forEach(visible.intValue(), (t) -> next.add(t.get()));

                            guiThread.invokeLater(this::render);
                        }
                    }
                }
            }

            @Override
            public synchronized void onRemoved(Container container) {
                update.off();
                updaters.remove(update);
                super.onRemoved(container);
                clearItems();
            }

            final List<X> next = $.newArrayList();

            protected void render() {

                clearItems();
                next.forEach(this::addItem);


                
                
                
                
                


            }

        }

        class TaskListBox extends BagListBox<Task> {

            private final Off onTask;


            @Override
            public synchronized void onRemoved(Container container) {
                onTask.off();
                updaters.remove(onTask);
                super.onRemoved(container);
                clearItems();
            }

            public TaskListBox(int capacity) {
                super(capacity, false);

                setListItemRenderer(new TaskListRenderer(this));

                onTask = nar.eventTask.on(t -> {
                    add(t);
                    changed.set(true);
                });

            }


        }

    }


    private class View {
        private final String label;
        private final Supplier<Component> builder;

        public View(String label, Supplier<Component> builder) {
            this.label = label;
            this.builder = builder;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}























































































