package nars.test.analyze;

import jcog.event.Ons;
import jcog.event.Topic;
import jcog.meter.event.HitMeter;
import nars.$;
import nars.NAR;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
* Created by me on 2/10/15.
*/
public class EventCount {

    @NotNull
    public final Map<Object, HitMeter> eventMeters;
    @Nullable
    private Ons sub;

    public EventCount(@NotNull NAR nar) {

        Map<Object, HitMeter> eventMeters
                = this.eventMeters = $.newHashMap();

        Topic.each(NAR.class, (field) -> {
            String nn = field.getName();
            eventMeters.put(nn, new HitMeter(nn));
        });

        sub = Topic.all(nar, (event, value) ->
            eventMeters.get(event).hit());
    }

    public void off() {
        if (sub!=null) {
            sub.off();
            sub = null;
        }
    }


    public long numTaskProcesses() {
        return eventMeters.get("eventTaskProcess").count(); }
    public long numOutputs() { return eventMeters.get("eventDerived").count(); }
    public long numInputs() { return eventMeters.get("eventInput").count(); }
    public long numExecutions() { return eventMeters.get("eventExecute").count(); }
    public long numErrors() { return eventMeters.get("eventError").count(); }
    public long numAnswers() { return eventMeters.get("eventAnswer").count(); }


    public void reset() {
        eventMeters.values().forEach(HitMeter::reset);
    }
}
