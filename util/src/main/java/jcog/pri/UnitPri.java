package jcog.pri;

import jcog.Util;

/** pri limited to 0..1.0 range */
public class UnitPri extends Pri {

    public UnitPri() {
        super();
    }

    public UnitPri(Priority x) {
        super(x);
    }

    public UnitPri(float x) {
        super(x);
    }

    @Override
    public float pri(float p) {
        return super.pri(Util.unitize(p));
    }



}
