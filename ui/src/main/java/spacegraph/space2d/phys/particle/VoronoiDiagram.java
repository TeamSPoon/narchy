package spacegraph.space2d.phys.particle;

import jcog.math.v2;
import spacegraph.space2d.phys.common.MathUtils;
import spacegraph.space2d.phys.pooling.IDynamicStack;
import spacegraph.space2d.phys.pooling.normal.MutableStack;

public class VoronoiDiagram {

    /** the extended v2 point represents the center */
    static class Generator extends v2 {
        int tag;




    }

    static class VoronoiDiagramTask {
        float m_x, m_y;
        int m_i;
        Generator m_generator;

        VoronoiDiagramTask() {
        }








        VoronoiDiagramTask set(float x, float y, int i, Generator g) {
            m_x = x;
            m_y = y;
            m_i = i;
            m_generator = g;
            return this;
        }
    }

    public interface VoronoiDiagramCallback {
        void callback(int aTag, int bTag, int cTag);
    }

    private final Generator[] m_generatorBuffer;
    private int m_generatorCount;
    private int m_countX, m_countY;
    
    private Generator[] m_diagram;

    public VoronoiDiagram(int generatorCapacity) {
        m_generatorBuffer = new Generator[generatorCapacity];
        for (int i = 0; i < generatorCapacity; i++) {
            m_generatorBuffer[i] = new Generator();
        }
        m_generatorCount = 0;
        m_countX = 0;
        m_countY = 0;
        m_diagram = null;
    }

    public void getNodes(VoronoiDiagramCallback callback) {
        for (int y = 0; y < m_countY - 1; y++) {
            for (int x = 0; x < m_countX - 1; x++) {
                int i = x + y * m_countX;
                Generator a = m_diagram[i];
                Generator b = m_diagram[i + 1];
                Generator c = m_diagram[i + m_countX];
                Generator d = m_diagram[i + 1 + m_countX];
                if (b != c) {
                    if (a != b && a != c) {
                        callback.callback(a.tag, b.tag, c.tag);
                    }
                    if (d != b && d != c) {
                        callback.callback(b.tag, d.tag, c.tag);
                    }
                }
            }
        }
    }

    public void addGenerator(v2 center, int tag) {
        Generator g = m_generatorBuffer[m_generatorCount++];
        g.x = center.x;
        g.y = center.y;
        g.tag = tag;
    }

    private final v2 lower = new v2();
    private final v2 upper = new v2();

    private final IDynamicStack<VoronoiDiagramTask> taskPool =
            new MutableStack<VoronoiDiagramTask>(50) {
                @Override
                protected VoronoiDiagramTask newInstance() {
                    return new VoronoiDiagramTask();
                }

                @Override
                protected VoronoiDiagramTask[] newArray(int size) {
                    return new VoronoiDiagramTask[size];
                }
            };

    private final StackQueue<VoronoiDiagramTask> queue = new StackQueue<>();

    public void generate(float radius) {
        assert (m_diagram == null);
        float inverseRadius = 1 / radius;
        lower.x = Float.MAX_VALUE;
        lower.y = Float.MAX_VALUE;
        upper.x = -Float.MAX_VALUE;
        upper.y = -Float.MAX_VALUE;
        for (int k = 0; k < m_generatorCount; k++) {
            v2 g = m_generatorBuffer[k];
            v2.minToOut(lower, g, lower);
            v2.maxToOut(upper, g, upper);
        }
        m_countX = 1 + (int) (inverseRadius * (upper.x - lower.x));
        m_countY = 1 + (int) (inverseRadius * (upper.y - lower.y));
        m_diagram = new Generator[m_countX * m_countY];
        queue.reset(new VoronoiDiagramTask[4 * m_countX * m_countX]);
        for (int k = 0; k < m_generatorCount; k++) {
            Generator g = m_generatorBuffer[k];
            g.x = inverseRadius * (g.x - lower.x);
            g.y = inverseRadius * (g.y - lower.y);
            int x = MathUtils.max(0, MathUtils.min((int)g.x, m_countX - 1));
            int y = MathUtils.max(0, MathUtils.min((int)g.y, m_countY - 1));
            queue.push(taskPool.pop().set(x, y, x + y * m_countX, g));
        }
        while (!queue.empty()) {
            VoronoiDiagramTask front = queue.pop();
            float x = front.m_x;
            float y = front.m_y;
            int i = front.m_i;
            Generator g = front.m_generator;
            if (m_diagram[i] == null) {
                m_diagram[i] = g;
                if (x > 0) {
                    queue.push(taskPool.pop().set(x - 1, y, i - 1, g));
                }
                if (y > 0) {
                    queue.push(taskPool.pop().set(x, y - 1, i - m_countX, g));
                }
                if (x < m_countX - 1) {
                    queue.push(taskPool.pop().set(x + 1, y, i + 1, g));
                }
                if (y < m_countY - 1) {
                    queue.push(taskPool.pop().set(x, y + 1, i + m_countX, g));
                }
            }
            taskPool.push(front);
        }
        int maxIteration = m_countX + m_countY;
        for (int iteration = 0; iteration < maxIteration; iteration++) {
            for (int y = 0; y < m_countY; y++) {
                for (int x = 0; x < m_countX - 1; x++) {
                    int i = x + y * m_countX;
                    Generator a = m_diagram[i];
                    Generator b = m_diagram[i + 1];
                    if (a != b) {
                        queue.push(taskPool.pop().set(x, y, i, b));
                        queue.push(taskPool.pop().set(x + 1, y, i + 1, a));
                    }
                }
            }
            for (int y = 0; y < m_countY - 1; y++) {
                for (int x = 0; x < m_countX; x++) {
                    int i = x + y * m_countX;
                    Generator a = m_diagram[i];
                    Generator b = m_diagram[i + m_countX];
                    if (a != b) {
                        queue.push(taskPool.pop().set(x, y, i, b));
                        queue.push(taskPool.pop().set(x, y + 1, i + m_countX, a));
                    }
                }
            }
            boolean updated = false;
            while (!queue.empty()) {
                VoronoiDiagramTask front = queue.pop();
                float x = front.m_x;
                float y = front.m_y;
                int i = front.m_i;
                Generator k = front.m_generator;
                v2 a = m_diagram[i];
                Generator b = k;
                if (a != b) {
                    float ax = a.x - x;
                    float ay = a.y - y;
                    float bx = b.x - x;
                    float by = b.y - y;
                    float a2 = ax * ax + ay * ay;
                    float b2 = bx * bx + by * by;
                    if (a2 > b2) {
                        m_diagram[i] = b;
                        if (x > 0) {
                            queue.push(taskPool.pop().set(x - 1, y, i - 1, b));
                        }
                        if (y > 0) {
                            queue.push(taskPool.pop().set(x, y - 1, i - m_countX, b));
                        }
                        if (x < m_countX - 1) {
                            queue.push(taskPool.pop().set(x + 1, y, i + 1, b));
                        }
                        if (y < m_countY - 1) {
                            queue.push(taskPool.pop().set(x, y + 1, i + m_countX, b));
                        }
                        updated = true;
                    }
                }
                taskPool.push(front);
            }
            if (!updated) {
                break;
            }
        }
    }
}
