package jcog.signal.meter.event;

import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import jcog.Util;
import jcog.data.iterator.ArrayIterator;

import java.io.*;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

/**
 * dead simple CSV logging
 */
public class CSVOutput extends PrintStream {

    

    public CSVOutput(File out, String... headers) throws FileNotFoundException {
        this(new FileOutputStream(out), headers);
    }

    public CSVOutput(OutputStream out, String... headers) {
        super(out);
        println(Joiner.on(',').join(Iterables.transform(
                new ArrayIterator<>(headers),
                col -> '"' + col + '"'
            )
        ));
    }

    public void out(double... row) {
        println(DoubleStream.of(row).mapToObj(String::valueOf).collect(Collectors.joining(",")));
    }

    public void out(float... row) {
        out(Util.toDouble(row));
    }

}
