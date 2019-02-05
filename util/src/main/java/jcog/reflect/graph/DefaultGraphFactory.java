/*
 * The MIT License
 *
 * Copyright 2014 Kamnev Georgiy (nt.gocha@gmail.com).
 *
 * Данная лицензия разрешает, безвозмездно, лицам, получившим копию данного программного
 * обеспечения и сопутствующей документации (в дальнейшем именуемыми "Программное Обеспечение"),
 * использовать Программное Обеспечение без ограничений, включая неограниченное право на
 * использование, копирование, изменение, объединение, публикацию, распространение, сублицензирование
 * и/или продажу копий Программного Обеспечения, также как и лицам, которым предоставляется
 * данное Программное Обеспечение, при соблюдении следующих условий:
 *
 * Вышеупомянутый копирайт и данные условия должны быть включены во все копии
 * или значимые части данного Программного Обеспечения.
 *
 * ДАННОЕ ПРОГРАММНОЕ ОБЕСПЕЧЕНИЕ ПРЕДОСТАВЛЯЕТСЯ «КАК ЕСТЬ», БЕЗ ЛЮБОГО ВИДА ГАРАНТИЙ,
 * ЯВНО ВЫРАЖЕННЫХ ИЛИ ПОДРАЗУМЕВАЕМЫХ, ВКЛЮЧАЯ, НО НЕ ОГРАНИЧИВАЯСЬ ГАРАНТИЯМИ ТОВАРНОЙ ПРИГОДНОСТИ,
 * СООТВЕТСТВИЯ ПО ЕГО КОНКРЕТНОМУ НАЗНАЧЕНИЮ И НЕНАРУШЕНИЯ ПРАВ. НИ В КАКОМ СЛУЧАЕ АВТОРЫ
 * ИЛИ ПРАВООБЛАДАТЕЛИ НЕ НЕСУТ ОТВЕТСТВЕННОСТИ ПО ИСКАМ О ВОЗМЕЩЕНИИ УЩЕРБА, УБЫТКОВ
 * ИЛИ ДРУГИХ ТРЕБОВАНИЙ ПО ДЕЙСТВУЮЩИМ КОНТРАКТАМ, ДЕЛИКТАМ ИЛИ ИНОМУ, ВОЗНИКШИМ ИЗ, ИМЕЮЩИМ
 * ПРИЧИНОЙ ИЛИ СВЯЗАННЫМ С ПРОГРАММНЫМ ОБЕСПЕЧЕНИЕМ ИЛИ ИСПОЛЬЗОВАНИЕМ ПРОГРАММНОГО ОБЕСПЕЧЕНИЯ
 * ИЛИ ИНЫМИ ДЕЙСТВИЯМИ С ПРОГРАММНЫМ ОБЕСПЕЧЕНИЕМ.
 */
package jcog.reflect.graph;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Фабрика классов графа
 *
 * @param <N> Тип вершины
 * @param <E> Тип ребра
 * @author GoCha
 */
public class DefaultGraphFactory<N, E> implements GraphFactory<N, E> {
    /* (non-Javadoc)
     * @see org.gocha.collection.graph.GraphFactory#CreateEdgeSet
     */
    @Override
    public Edge<N, E> createEdge(N a, N b, E e) {
        if (a == null) {
            throw new IllegalArgumentException("a == null");
        }
        if (b == null) {
            throw new IllegalArgumentException("b == null");
        }
        if (e == null) {
            throw new IllegalArgumentException("e == null");
        }

        final N f_a = a;
        final N f_b = b;
        final E f_e = e;

        return new Edge<N,E>() {

            @Override
            public N getNodeA() {
                return f_a;
            }

            @Override
            public N getNodeB() {
                return f_b;
            }

            @Override
            public E getEdge() {
                return f_e;
            }
        };
    }

    /* (non-Javadoc)
     * @see org.gocha.collection.graph.GraphFactory#CreateEdgeSetList
     */
    @Override
    public List<Edge<N, E>> createEdgePairs() {
        return new ArrayList<>();
    }

    /* (non-Javadoc)
     * @see org.gocha.collection.graph.GraphFactory#CreateNodeList
     */
    @Override
    public List<N> createNodes() {
        return new ArrayList<>();
    }

    /* (non-Javadoc)
     * @see org.gocha.collection.graph.GraphFactory#CreateEdgeList
     */
    @Override
    public List<E> createEdges() {
        return new ArrayList<>();
    }

    /**
     * Дуга
     *
     * @param <N> Тип вершины
     * @param <E> Тип ребра
     */
    public static class MutableEdge<N, E> implements Edge<N, E>, Serializable {
        private N a = null;
        private N b = null;
        private E e = null;
        public MutableEdge() {
        }
        public MutableEdge(N a, N b, E e) {
            this.a = a;
            this.b = b;
            this.e = e;
        }

        @Override
        public N getNodeA() {
            return a;
        }

        public void setNodeA(N a) {
            this.a = a;
        }

        @Override
        public N getNodeB() {
            return b;
        }

        public void setNodeB(N b) {
            this.b = b;
        }

        @Override
        public E getEdge() {
            return e;
        }

        public void setEdge(E e) {
            this.e = e;
        }
    }
}
