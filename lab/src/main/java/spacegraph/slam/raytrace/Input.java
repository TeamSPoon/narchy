package spacegraph.slam.raytrace;

import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

public final class Input extends ComponentAdapter implements KeyListener, MouseListener, MouseMotionListener {
    public Dimension newSize;

    private boolean forward = false;
    private boolean backward = false;
    private boolean left = false;
    private boolean right = false;
    private boolean shift = false;
    private int currentMouseX;
    private int currentMouseY;
    private int lastMouseX;
    private int lastMouseY;
    private final List<Runnable> onInputInterruptables = new ArrayList<>();

    public void waitForInput() {
        if (!moving()) {
            try {
                synchronized (this) {
                    wait();
                }
            } catch (InterruptedException e) {
            }
        }
    }

    public void addInterruptable(Runnable i) {
        onInputInterruptables.add(i);
    }

    private void interrupt() {
        for (Runnable i : onInputInterruptables) {
            i.run();
        }
    }

    public int getDeltaMouseX() {
        int dx = currentMouseX - lastMouseX;
        lastMouseX = currentMouseX;
        return dx;
    }

    public int getDeltaMouseY() {
        int dy = currentMouseY - lastMouseY;
        lastMouseY = currentMouseY;
        return dy;
    }

    public Vector3 getKeyboardVector() {
        Vector3 kbVector = new Vector3(0, 0, 0);

        if (forward) {
            kbVector = kbVector.plus(new Vector3(0, 1, 0));
        }
        if (backward) {
            kbVector = kbVector.plus(new Vector3(0, -1, 0));
        }
        if (left) {
            kbVector = kbVector.plus(new Vector3(-1, 0, 0));
        }
        if (right) {
            kbVector = kbVector.plus(new Vector3(1, 0, 0));
        }

        kbVector = kbVector.normalize().scale(0.3);

        if (shift) {
            kbVector = kbVector.scale(0.1);
        }

        return kbVector;
    }

    public boolean moving() {
        return forward || backward || left || right;
    }

    @Override
    public void componentResized(ComponentEvent e) {
        newSize = e.getComponent().getSize();
        interrupt();
        synchronized (this) {
            notifyAll();
        }
    }

    @Override
    public void keyPressed(KeyEvent e) {
        int keyCode = e.getKeyCode();
        if (keyCode ==  KeyEvent.VK_A) {
            left = true;
        } else if (keyCode ==  KeyEvent.VK_D) {
            right = true;
        } else if (keyCode ==  KeyEvent.VK_W) {
            forward = true;
        } else if (keyCode ==  KeyEvent.VK_S) {
            backward = true;
        } else if (keyCode == KeyEvent.VK_SHIFT) {
            shift = true;
        }
        if (moving()) {
            interrupt();
            synchronized (this) {
                notifyAll();
            }
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        int keyCode = e.getKeyCode();
        boolean oldMoving = moving();
        if (keyCode == KeyEvent.VK_A) {
            left = false;
        } else if (keyCode == KeyEvent.VK_D) {
            right = false;
        } else if (keyCode == KeyEvent.VK_W) {
            forward = false;
        } else if (keyCode == KeyEvent.VK_S) {
            backward = false;
        } else if (keyCode == KeyEvent.VK_SHIFT) {
            shift = false;
        }
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }


    @Override
    public void mouseDragged(MouseEvent e) {
        currentMouseX = e.getX();
        currentMouseY = e.getY();
        interrupt();
        synchronized (this) {
            notifyAll();
        }
    }

    @Override
    public void mouseMoved(MouseEvent e) {
    }

    @Override
    public void mouseClicked(MouseEvent e) {
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }

    @Override
    public void mousePressed(MouseEvent e) {
        lastMouseX = e.getX();
        lastMouseY = e.getY();
    }

    @Override
    public void mouseReleased(MouseEvent e) {
    }
}
