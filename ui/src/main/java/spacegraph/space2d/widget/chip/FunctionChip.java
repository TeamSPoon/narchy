package spacegraph.space2d.widget.chip;

import java.util.function.Function;

public class FunctionChip<X,Y> extends AbstractFunctionChip<X,Y> {

    private final Function<X, Y> f;

    public FunctionChip(Class<? super X> cx, Class<? super Y> cy, Function<X,Y> f) {
        super(cx, cy);
        this.f = f;
    }

    @Override protected Function<X,Y> f() {
        return f;
    }

    /** TODO atomic */
    public FunctionChip<X,Y> buffered() {
        return new FunctionChip<>(this.in.type, this.out.type, new Function<X,Y>() {
            X last = null;
            Y lastY = null;

            @Override
            public Y apply(X xx) {
                if (last == xx)
                    return lastY;
                return lastY = f.apply(last = xx);
            }
        });
    }
}
