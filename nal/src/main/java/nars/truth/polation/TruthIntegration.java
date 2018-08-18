package nars.truth.polation;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import jcog.math.Longerval;
import nars.Task;
import nars.task.signal.TruthletTask;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

import static nars.time.Tense.ETERNAL;

public class TruthIntegration {

    public static float eviAvg(Task t, int dur) {
        return eviAvg(t, t.start(), t.end(), dur);
    }

    public static float eviAvg(Task t, long start, long end, int dur) {
        long range = start==ETERNAL ? 1 : 1 + (end - start);
        return eviInteg(t, start, end, dur) / range;
    }

    public static float eviInteg(Task t, long dur) {
        return eviInteg(t, t.start(), t.end(), dur);
    }

    /**
     * convenience method for selecting evidence integration strategy
     * interval is: [qStart, qEnd], ie: qStart: inclusive qEnd: inclusive
     * if qStart==qEnd then it is a point sample
     */
    public static float eviInteg(Task t, long qStart, long qEnd, long dur) {
        if (qStart == ETERNAL) {
            return t.isEternal() ? t.evi() : t.eviEternalized();
        } else if (qStart == qEnd) {

            return t.evi(qStart, dur);
        } else {
            long tStart = t.start();
            long range = (qEnd - qStart + 1);
            if (tStart == ETERNAL)
                return t.evi() * range;







            long tEnd = t.end();



            long[] points;
            if (qStart<tStart || qEnd>tEnd) {

                @Nullable Longerval qt = Longerval.intersection(qStart, qEnd, tStart, tEnd);
                LongArrayList pp = new LongArrayList(qt ==null ? 3 : 5);

                pp.add(qStart);

                if (qStart+1!=qEnd)
                    pp.add((qStart+qEnd)/2L); //mid

                if (qt != null) {
                    //inner points
                    long qta = qt.a;
                    if (qta > qStart && qta < qEnd) //quick test to avoid set add
                        pp.add(qta);

                    long qtb = qt.b;
                    if (qta != qtb && qtb > qStart && qtb < qEnd)  //quick test to avoid set add
                        pp.add(qtb);
                }


                pp.add(qEnd);

                points = pp.toLongArray();

                if (qt!=null)
                    Arrays.sort(points); //sort if the internal points were tracked. otherwise the sort order is correct

            } else {
                if (t instanceof TruthletTask) {
                    //quick points array determination
                    long d = qEnd - qStart;
                    long qMid = (qStart + qEnd) / 2L;
                    if (d == 0) {
                        throw new UnsupportedOperationException(); //points = new long[] { qStart };
                    } else if (d < dur || (qMid == qStart) || (qMid == qEnd)) {
                        //points = new long[] { qStart, qEnd };
                        return t.evi(qStart, dur) + t.evi(qEnd, dur); //2 point samples summed
//                } else if (d <= dur) {
//                    points = new long[] { qStart, (qStart + qEnd)/2L, qEnd };
                    } else {
                        //with midpoint supersample
                        points = new long[]{qStart, qMid, qEnd};
                    }
                } else {
                    assert(qStart >= tStart && qEnd <= tEnd);
                    //for internal point of rectangular truth, simply use the point sample
                    return t.evi(qStart, dur) * range;
                }
            }





            //return x.eviIntegRectMid(dur, points);
            return t.eviIntegTrapezoidal(dur, points);


        }
    }

}
