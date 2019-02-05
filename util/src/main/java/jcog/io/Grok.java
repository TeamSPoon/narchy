/*
 * Copyright 2016 ruckc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http:
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jcog.io;

import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.String.format;


/**
 * {@code Grok} parse arbitrary text and structure it.<br>
 * <p>
 * {@code Grok} is simple API that allows you to easily parse logs
 * and other files (single line). With {@code Grok},
 * you can turn unstructured log and event data into structured data (JSON).
 * <br>
 * example:<br>
 * <pre>
 *  Grok grok = Grok.create("patterns/patterns");
 *  grok.compile("%{USER}");
 *  Match gm = grok.match("root");
 *  gm.captures();
 * </pre>
 *
 * @author anthonycorbacho
 * @since 0.0.1
 *
 * see also:
 *      http:
 *      https:
 */
public class Grok implements Serializable {

    public static void main(String[] args) throws FileNotFoundException {


        Grok g = Grok.withThe("patterns", "linux-syslog");
        BufferedReader br = new BufferedReader(new FileReader(
                
                "/var/log/alternatives.log"
        ));
        br.lines().forEach(line -> {
            System.out.println(line);

            String data = g.discover(line);

            System.out.println(data);






        });

    }

    
    /**
     * Named regex of the originalGrokPattern.
     */
    private String namedRegex;
    /**
     * Map of the named regex of the originalGrokPattern
     * with id = namedregexid and value = namedregex.
     */
    private final Map<String, String> namedRegexCollection;
    /**
     * Original {@code Grok} pattern (expl: %{IP}).
     */
    private String originalGrokPattern;
    /**
     * Pattern of the namedRegex.
     */
    private Pattern compiledNamedRegex;

    /**
     * {@code Grok} patterns definition.
     */
    private final Map<String, String> grokPatternDefinition;


    /**
     * automatic conversion of values
     */
    private boolean automaticConversionEnabled = true;

    /**
     * Create Empty {@code Grok}.
     */
    static final Grok EMPTY = new Grok();

    /**
     * Create a new <i>empty</i>{@code Grok} object.
     */
    Grok() {
        this(new TreeMap<>());
    }

    String savedPattern;

    private Grok(Map<String, String> patterns) {
        originalGrokPattern = "";
        namedRegex = "";
        compiledNamedRegex = null;
        grokPatternDefinition = patterns;
        namedRegexCollection = new TreeMap<>();
        savedPattern = "";
    }


    /**
     * Create a {@code Grok} instance with the given patterns file and
     * a {@code Grok} pattern.
     *
     * @param grokPatternPath Path to the pattern file
     * @param grokExpression  - <b>OPTIONAL</b> - Grok pattern to compile ex: %{APACHELOG}
     * @return {@code Grok} instance
     * @throws RuntimeException runtime expt
     */
    public static Grok withFile(String grokPatternPath/*, String grokExpression*/)
            throws RuntimeException, FileNotFoundException {
        Grok g = new Grok();
        g.addPatternFrom(grokPatternPath);
        return g;
    }

    public static Grok withReader(Reader reader)
            throws RuntimeException {
        Grok g = new Grok();
        g.addPatterns(reader);
        return g;
    }

    /**
     * Add custom pattern to grok in the runtime.
     *
     * @param name    : Pattern Name
     * @param pattern : Regular expression Or {@code Grok} pattern
     * @throws RuntimeException runtime expt
     **/
    private void addPattern(String name, String pattern) throws RuntimeException {
        if (isBlank(name)) {
            throw new RuntimeException("Invalid Pattern name");
        }
        if (isBlank(pattern)) {
            throw new RuntimeException("Invalid Pattern");
        }
        grokPatternDefinition.put(name, pattern);
    }

    /**
     * Copy the given Map of patterns (pattern name, regular expression) to {@code Grok},
     * duplicate element will be override.
     *
     * @param cpy : Map to copy
     * @throws RuntimeException runtime expt
     **/
    void addPatterns(Map<String, String> cpy) throws RuntimeException {
        if (cpy == null) {
            throw new RuntimeException("Invalid Patterns");
        }

        if (cpy.isEmpty()) {
            throw new RuntimeException("Invalid Patterns");
        }
        for (Map.Entry<String, String> entry : cpy.entrySet()) {
            grokPatternDefinition.put(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Get the current map of {@code Grok} pattern.
     *
     * @return Patterns (name, regular expression)
     */
    Map<String, String> patterns() {
        return grokPatternDefinition;
    }

    /**
     * Get the named regex from the {@code Grok} pattern. <br>
     * See {@link #compile(String)} for more detail.
     *
     * @return named regex
     */
    String namedRegex() {
        return namedRegex;
    }

    /**
     * Add patterns to {@code Grok} from the given file.
     *
     * @param file : Path of the grok pattern
     * @throws RuntimeException runtime expt
     */
    private Grok addPatternFrom(String file) throws RuntimeException, FileNotFoundException {
        return addPatterns(new FileReader(file));
    }

    static final Pattern gp = Pattern.compile("^([A-z0-9_]+)\\s+(.*)$");
    /**
     * Add patterns to {@code Grok} from a Reader.
     *
     * @param r : Reader with {@code Grok} patterns
     * @throws RuntimeException runtime expt
     */
    private Grok addPatterns(Reader r) throws RuntimeException {
        BufferedReader br = new BufferedReader(r);
        

        try {
            String line;
            while ((line = br.readLine()) != null) {
                Matcher m = gp.matcher(line);
                if (m.matches()) {
                    this.addPattern(m.group(1), m.group(2));
                }
            }
            br.close();
        } catch (IOException | RuntimeException e) {
            throw new RuntimeException(e.getMessage());
        }
        return this;
    }

    /**
     * Disable automatic conversion of values
     */
    public void disableAutomaticConversion() {
        this.automaticConversionEnabled = false;
    }

    public boolean isAutomaticConversionEnabled() {
        return automaticConversionEnabled;
    }

    /**
     * Match the given <tt>text</tt> with the named regex.
     *
     * @param text : log to match
     * */
    public Match capture(String text) {
        Match match = match(text);
        match.captures(/*flattened*/);
        return match;
    }
    public Match capture(String text, String pattern) {
        Match match = match(text, pattern);
        match.captures(/*flattened*/);
        return match;
    }


    /**
     * Match the given <tt>text</tt> with the named regex
     * {@code Grok} will extract data from the string and get an extence of {@link Match}.
     *
     * @param text : Single line of log
     * @return Grok Match
     */
    Match match(String text) {
        if (compiledNamedRegex == null || isBlank(text)) {
            return Match.EMPTY;
        }

        Matcher m = compiledNamedRegex.matcher(text);
        Match match = new Match();
        if (m.find()) {
            match.setSubject(text);
            match.setGrok(this);
            match.setMatch(m);
            match.setStart(m.start(0));
            match.setEnd(m.end(0));
        }
        return match;
    }
   Match match(String text, String pattern) {
        if (isBlank(text)) {
            return Match.EMPTY;
        }

        Matcher m = Pattern.compile(compiled(patterns().get(pattern), false)).matcher(text);
        Match match = new Match();
        if (m.find()) {
            match.setSubject(text);
            match.setGrok(this);
            match.setMatch(m);
            match.setStart(m.start(0));
            match.setEnd(m.end(0));
        }
        return match;
    }
    /**
     * Compile the {@code Grok} pattern to named regex pattern.
     *
     * @param pattern : Grok pattern (ex: %{IP})
     * @throws RuntimeException runtime expt
     */
    void compile(String pattern) throws RuntimeException {
        compile(pattern, false);
    }

    private static boolean isBlank(String str) {
        int strLen;
        if (str != null && (strLen = str.length()) != 0) {
            for (int i = 0; i < strLen; ++i) {
                if (!Character.isWhitespace(str.charAt(i))) {
                    return false;
                }
            }

            return true;
        } else {
            return true;
        }
    }

    private static int countMatches(String str, String sub) {
        if (!str.isEmpty()) {
            int sl = sub.length();
            if (sl > 0) {
                int count = 0;


                for (int idx = 0; (idx = str.indexOf(sub, idx)) != -1; idx += sl) {
                    ++count;
                }

                return count;
            }
        }
        return 0;
    }

    /**
     * Compile the {@code Grok} pattern to named regex pattern.
     *
     * @param pattern   : Grok pattern (ex: %{IP})
     * @param namedOnly : Whether to capture named expressions only or not (i.e. %{IP:ip} but not ${IP})
     * @throws RuntimeException runtime expt
     */
    private void compile(String pattern, boolean namedOnly) throws RuntimeException {

        String namedRegex = compiled(pattern, namedOnly);

        
        this.namedRegex = namedRegex;
        compiledNamedRegex = Pattern.compile(namedRegex);
    }

    @NotNull
    private String compiled(String pattern, boolean namedOnly) {
        if (isBlank(pattern)) {
            throw new RuntimeException("{pattern} should not be empty or null");
        }

        String namedRegex = pattern;
        originalGrokPattern = pattern;
        int index = 0;
        /** flag for infinite recurtion */
        int iterationLeft = 1000;
        Boolean continueIteration = true;

        
        
        while (continueIteration) {
            continueIteration = false;
            if (iterationLeft <= 0) {
                throw new RuntimeException("Deep recursion pattern compilation of " + originalGrokPattern);
            }
            iterationLeft--;

            Matcher m = GROK_PATTERN.matcher(namedRegex);
            
            
            if (m.find()) {
                continueIteration = true;
                Map<String, String> group = namedGroups(m, m.group());
                String gdef = group.get("definition");
                String gname = group.get("name");
                String gpat = group.get("pattern");
                if (gdef != null) {
                    try {
                        addPattern(gpat, gdef);
                        group.put("name", gname + "=" + gdef);
                    } catch (RuntimeException e) {
                        throw new RuntimeException(e);
                    }
                }
                int count = countMatches(namedRegex, "%{" + gname + "}");
                for (int i = 0; i < count; i++) {
                    String definitionOfPattern = grokPatternDefinition.get(gpat);
                    if (definitionOfPattern == null) {
                        throw new RuntimeException(format("No definition for key '%s' found, aborting",
                                gpat));
                    }
                    String replacement = String.format("(?<name%d>%s)", index, definitionOfPattern);
                    String gsub = group.get("subname");
                    if (namedOnly && gsub == null) {
                        replacement = String.format("(?:%s)", definitionOfPattern);
                    }
                    namedRegexCollection.put("name" + index,
                            (gsub != null ? gsub : gname));
                    namedRegex =
                            replace(namedRegex, "%{" + gname + "}", replacement, 1);
                    
                    index++;
                }
            }
        }

        if (namedRegex.isEmpty()) {
            throw new RuntimeException("Pattern not fount");
        }
        return namedRegex;
    }


    private static String replace(String text, String searchString, String replacement) {
        return replace(text, searchString, replacement, Integer.MAX_VALUE);
    }

    private static String replace(String text, String searchString, String replacement, int max) {
        if (max > 0 && !text.isEmpty()) {
            int replLength = searchString.length();
            if (replLength > 0) {

                int start = 0;
                int end = text.indexOf(searchString, start);
                if (end == -1) {
                    return text;
                } else {
                    int increase = replacement.length() - replLength;
                    increase = increase < 0 ? 0 : increase;
                    increase *= max > 64 ? 64 : max;

                    StringBuilder buf;
                    for (buf = new StringBuilder(text.length() + increase); end != -1; end = text.indexOf(searchString, start)) {
                        buf.append(text, start, end).append(replacement);
                        start = end + replLength;
                        --max;
                        if (max == 0) {
                            break;
                        }
                    }

                    return buf.append(text, start, text.length()).toString();
                }

            }
        }
        return text;

    }

    /**
     * {@code Grok} will try to find the best expression that will match your input.
     * {@link Discovery}
     *
     * @param input : Single line of log
     * @return the Grok pattern
     */
    String discover(String input) {
        return new Discovery(this).discover(input);
    }

    /**
     * Original grok pattern used to compile to the named regex.
     *
     * @return String Original Grok pattern
     */
    public String getOriginalGrokPattern() {
        return originalGrokPattern;
    }

    /**
     * Get the named regex from the given id.
     *
     * @param id : named regex id
     * @return String of the named regex
     */
    private String getNamedRegexCollectionById(String id) {
        return namedRegexCollection.get(id);
    }










    public static Grok withThe(String... patternLibs) {
        Grok g = new Grok();
        for (String s : patternLibs) {

            try (InputStream nn = Grok.class.getClassLoader().getResourceAsStream("patterns/" + s)) {

                g.addPatterns(new InputStreamReader(nn));

            } catch (IOException e) {

                e.printStackTrace();

            }

        }
        return g;
    }
    public static Grok all() {
        Grok g = new Grok();
        try {
            File f = new File(Grok.class.getClassLoader().getResource("patterns").toURI());
            for (File e : f.listFiles()) {
                g.addPatterns(new FileReader(e));
            }
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        return g;
    }


    private final static Pattern complexity = Pattern.compile("\\Q" + "|" + "\\E");
    private final static Pattern wordBoundary = Pattern.compile(".\\b.");

    /**
     * {@code Discovery} try to find the best pattern for the given string.
     *
     * @author anthonycorbacho
     * @since 0.0.2
     */
    static class Discovery extends Grok {

        private final Grok grok;

        /**
         * Create a new {@code Discovery} object.
         *
         * @param grok instance of grok
         */
        Discovery(Grok grok) {
            this.grok = grok;
        }

        /**
         * Sort by regex complexity.
         *
         * @param groks Map of the pattern name and grok instance
         * @return the map sorted by grok pattern complexity
         */
        private Map<String, Grok> sort(Map<String, Grok> groks) {

            Collection<Grok> n = groks.values();
            List<Grok> groky = new ArrayList<>(n);
            Map<String, Grok> mGrok = new LinkedHashMap(n.size());
            groky.sort(MyGrokComparator);

            for (Grok g : groky) {
                mGrok.put(g.savedPattern, g);
            }
            return mGrok;

        }

        /**
         * @param expandedPattern regex string
         * @return the complexity of the regex
         */
        private int complexity(String expandedPattern) {
            int score = 0;

            score += complexity.split(expandedPattern, -1).length - 1;
            score += expandedPattern.length();

            return score;
        }


        /**
         * Find a pattern from a log.
         *
         * @param text     witch is the representation of your single
         * @param patterns
         * @return Grok pattern %{Foo}...
         */
        @Override
        public String discover(String text) {
            if (text == null) {
                return "";
            }

            Map<String, Grok> groks = new TreeMap<>();
            Map<String, String> gPatterns = grok.patterns();
            
            String texte = text;

            
            for (Map.Entry<String, String> pairs : gPatterns.entrySet()) {
                String key = pairs.getKey();
                Grok g = new Grok();

                
                try {
                    g.addPatterns(gPatterns);
                    g.savedPattern = key;
                    g.compile("%{" + key + "}");
                    groks.put(key, g);
                } catch (RuntimeException e) {
                    
                }

            }

            
            Map<String, Grok> patterns = this.sort(groks);

            
            
            for (Map.Entry<String, Grok> pairs : patterns.entrySet()) {
                String key = pairs.getKey();
                Grok value = pairs.getValue();

                
                
                if (this.complexity(value.namedRegex()) < 20) {
                    continue;
                }

                Match m = value.match(text);
                if (m.isNull()) {
                    continue;
                }
                
                String part = getPart(m, text);

                
                Matcher ma = wordBoundary.matcher(part);
                if (!ma.find()) {
                    continue;
                }

                
                Pattern pattern2 = Pattern.compile("%\\{[^}+]\\}");
                Matcher ma2 = pattern2.matcher(part);

                if (ma2.find()) {
                    continue;
                }
                texte = replace(texte, part, "%{" + key + "}");
            }
            

            return texte;
        }

        /**
         * Get the substring that match with the text.
         *
         * @param m    Grok Match
         * @param text text
         * @return string
         */
        private String getPart(Match m, String text) {

            if (m == null || text == null) {
                return "";
            }

            return text.substring(m.getStart(), m.getEnd());
        }

        static final Comparator<Grok> MyGrokComparator = new Comparator<Grok>() {

            @Override
            public int compare(Grok g1, Grok g2) {
                return (this.complexity(g1.namedRegex()) < this.complexity(g2.namedRegex())) ? 1
                        : 0;
            }

            private int complexity(String expandedPattern) {
                int score = 0;
                score += complexity.split(expandedPattern, -1).length - 1;
                score += expandedPattern.length();
                return score;
            }
        };
    }

    /**
     * Convert String argument to the right type.
     *
     * @author anthonyc
     */
    static class Converter {

        static final Map<String, IConverter<?>> converters = new HashMap<>();
        static final Locale locale = Locale.ENGLISH;

        static {
            converters.put("byte", new ByteConverter());
            converters.put("boolean", new BooleanConverter());
            converters.put("short", new ShortConverter());
            converters.put("int", new IntegerConverter());
            converters.put("long", new LongConverter());
            converters.put("float", new FloatConverter());
            converters.put("double", new DoubleConverter());
            converters.put("date", new DateConverter());
            converters.put("datetime", new DateConverter());
            converters.put("string", new StringConverter());

        }

        private static IConverter getConverter(String key) throws Exception {
            IConverter converter = converters.get(key);
            if (converter == null) {
                throw new Exception("Invalid data type :" + key);
            }
            return converter;
        }

        static KeyValue convert(String key, Object value) {
            String[] spec = key.split(";|:", 3);
            try {
                if (spec.length == 1) {
                    return new KeyValue(spec[0], value);
                } else if (spec.length == 2) {
                    return new KeyValue(spec[0], getConverter(spec[1]).convert(String.valueOf(value)));
                } else if (spec.length == 3) {
                    return new KeyValue(spec[0], getConverter(spec[1]).convert(String.valueOf(value), spec[2]));
                } else {
                    return new KeyValue(spec[0], value, "Unsupported spec :" + key);
                }
            } catch (Exception e) {
                return new KeyValue(spec[0], value, e.toString());
            }
        }
    }






    static class KeyValue {

        private String key;
        private Object value;
        private String grokFailure;

        KeyValue(String key, Object value) {
            this.key = key;
            this.value = value;
        }

        KeyValue(String key, Object value, String grokFailure) {
            this.key = key;
            this.value = value;
            this.grokFailure = grokFailure;
        }

        public boolean hasGrokFailure() {
            return grokFailure != null;
        }

        public String getGrokFailure() {
            return this.grokFailure;
        }

        String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        Object getValue() {
            return value;
        }

        public void setValue(Object value) {
            this.value = value;
        }
    }


    


    @FunctionalInterface
    interface IConverter<T> {

        default T convert(String value, String informat) throws ParseException {
            return null;
        }

        T convert(String value) throws Exception;
    }


    static class ByteConverter implements IConverter<Byte> {
        @Override
        public Byte convert(String value) {
            return Byte.parseByte(value);
        }
    }


    static class BooleanConverter implements IConverter<Boolean> {
        @Override
        public Boolean convert(String value) {
            return Boolean.parseBoolean(value);
        }
    }


    static class ShortConverter implements IConverter<Short> {
        @Override
        public Short convert(String value) {
            return Short.parseShort(value);
        }
    }


    static class IntegerConverter implements IConverter<Integer> {
        @Override
        public Integer convert(String value) {
            return Integer.parseInt(value);
        }
    }


    static class LongConverter implements IConverter<Long> {
        @Override
        public Long convert(String value) {
            return Long.parseLong(value);
        }
    }


    static class FloatConverter implements IConverter<Float> {
        @Override
        public Float convert(String value) {
            return Float.parseFloat(value);
        }
    }


    static class DoubleConverter implements IConverter<Double> {
        @Override
        public Double convert(String value) {
            return Double.parseDouble(value);
        }
    }


    static class StringConverter implements IConverter<String> {
        @Override
        public String convert(String value) {
            return value;
        }
    }


    static class DateConverter implements IConverter<Date> {
        @Override
        public Date convert(String value) throws java.text.ParseException {
            return DateFormat.getDateTimeInstance(DateFormat.SHORT,
                    DateFormat.SHORT,
                    Converter.locale).parse(value);
        }

        @Override
        public Date convert(String value, String informat) throws ParseException {
            SimpleDateFormat formatter = new SimpleDateFormat(informat, Converter.locale);
            return formatter.parse(value);
        }

    }

    public static class Match {

        private String subject; 
        private final Map<String, Object> capture;
        private final Garbage garbage;
        private Grok grok;
        private Matcher match;
        private int start;
        private int end;

        /**
         * For thread safety.
         */
        private static final ThreadLocal<Match> matchHolder = ThreadLocal.withInitial(Match::new);

        /**
         * Create a new {@code Match} object.
         */
        Match() {
            subject = "Nothing";
            grok = null;
            match = null;
            capture = new TreeMap<>();
            garbage = new Garbage();
            start = 0;
            end = 0;
        }

        /**
         * Create Empty grok matcher.
         */
        static final Match EMPTY = new Match();

        void setGrok(Grok grok) {
            if (grok != null) {
                this.grok = grok;
            }
        }

        public Matcher getMatch() {
            return match;
        }

        void setMatch(Matcher match) {
            this.match = match;
        }

        int getStart() {
            return start;
        }

        void setStart(int start) {
            this.start = start;
        }

        int getEnd() {
            return end;
        }

        void setEnd(int end) {
            this.end = end;
        }

        /**
         * Singleton.
         *
         * @return instance of Match
         */
        public static Match getInstance() {
            return matchHolder.get();
        }

        /**
         * Set the single line of log to parse.
         *
         * @param text : single line of log
         */
        void setSubject(String text) {
            if (text == null) {
                return;
            }
            if (text.isEmpty()) {
                return;
            }
            subject = text;
        }

        /**
         * Retrurn the single line of log.
         *
         * @return the single line of log
         */
        public String getSubject() {
            return subject;
        }



        @SuppressWarnings("unchecked")
        private void captures(/*boolean flattened*/) {
            if (match == null) {
                return;
            }
            capture.clear();
            boolean automaticConversionEnabled = true; 


            
            

            Map<String, String> mappedw = namedGroups(this.match, this.subject);
            Iterator<Map.Entry<String, String>> it = mappedw.entrySet().iterator();
            while (it.hasNext()) {

                @SuppressWarnings("rawtypes")
                Map.Entry<String,String> pairs = it.next();
                String key = null;
                String nr = this.grok.getNamedRegexCollectionById(pairs.getKey());
                if (nr == null) {
                    key = pairs.getKey();
                } else if (!nr.isEmpty()) {
                    key = nr;
                }
                Object value = null;
                if (pairs.getValue() != null) {
                    value = pairs.getValue();


                    if (automaticConversionEnabled) {
                        KeyValue keyValue = Converter.convert(key, value);

                        
                        key = keyValue.getKey();

                        
                        value = keyValue.getValue() instanceof String ? cleanString((String) keyValue.getValue()) : keyValue.getValue();





                    }
                }

                Object currentValue = capture.get(key);
                if (currentValue!=null) {

                    /*if (flattened) {
                        if (currentValue == null && value != null) {
                            capture.put(key, value);
                        }
                        if (currentValue != null && value != null) {
                            throw new RuntimeException(
                                    format("key '%s' has multiple non-null values, this is not allowed in flattened mode, values:'%s', '%s'",
                                            key,
                                            currentValue,
                                            value));
                        }
                    } else*/ {
                        if (currentValue instanceof List) {
                            ((List<Object>) currentValue).add(value);
                        } else {
                            capture.put(key, List.of(currentValue, value));
                        }
                    }
                } else {
                    capture.put(key, value);
                }

                it.remove(); 
            }
        }

        /**
         * remove from the string the quote and double quote.
         *
         * @param value string to pure: "my/text"
         * @return unquoted string: my/text
         */
        private String cleanString(String value) {
            if (value == null) {
                return null;
            }
            if (value.isEmpty()) {
                return value;
            }
            char[] tmp = value.toCharArray();
            char t0 = tmp[0];
            if (tmp.length == 1 && (t0 == '"' || t0 == '\'')) {
                return "";
            } else {
                int vlen = value.length();
                char tl = tmp[vlen - 1];
                if ((t0 == '"' && tl == '"')
                        || (t0 == '\'' && tl == '\'')) {
                    return value.substring(1, vlen - 1);
                } else {
                    return value;
                }
            }

        }









































        /**
         * Get the map representation of the matched element in the text.
         *
         * @return map object from the matched element in the text
         */
        public Map<String, Object> toMap() {
            this.cleanMap();
            return capture;
        }

        /**
         * Remove and rename the unwanted elelents in the matched map.
         */
        private void cleanMap() {
            garbage.rename(capture);
            garbage.remove(capture);
        }

        /**
         * Util fct.
         *
         * @return boolean
         */
        public boolean isNull() {
            return this.match == null;
        }

        @Override
        public String toString() {
            return "Match{" +
                    "subject='" + subject + '\'' +
                    ", capture=" + capture +
                    ", garbage=" + garbage +
                    ", grok=" + grok +
                    ", match=" + match +
                    ", start=" + start +
                    ", end=" + end +
                    '}';
        }
    }

    /**
     * The Leon the professional of {@code Grok}.<br>
     * Garbage is use by grok to remove or rename elements before getting the final output
     *
     * @author anthonycorbacho
     * @since 0.0.2
     */
    static class Garbage {

        private final List<String> toRemove;
        private final Map<String, Object> toRename;

        /**
         * Create a new {@code Garbage} object.
         */
        Garbage() {

            toRemove = new ArrayList<>();
            toRename = new TreeMap<>();
            /** this is a default value to remove */
            toRemove.add("UNWANTED");
        }

        /**
         * Set a new name to be change when exporting the final output.
         *
         * @param origin : original field name
         * @param value  : New field name to apply
         */
        public void addToRename(String origin, Object value) {
            if (origin == null || value == null) {
                return;
            }

            if (!origin.isEmpty() && !value.toString().isEmpty()) {
                toRename.put(origin, value);
            }
        }

        /**
         * Set a field to be remove when exporting the final output.
         *
         * @param name of the field to remove
         */
        public void addToRemove(String name) {
            if (name == null) {
                return;
            }

            if (!name.isEmpty()) {
                toRemove.add(name);
            }
        }

        /**
         * Set a list of field name to be remove when exporting the final output.
         *
         * @param lst list of elem to remove
         */
        public void addToRemove(List<String> lst) {
            if (lst == null) {
                return;
            }

            if (!lst.isEmpty()) {
                toRemove.addAll(lst);
            }
        }

        /**
         * Remove from the map the unwilling items.
         *
         * @param map to clean
         * @return nb of deleted item
         */
        int remove(Map<String, Object> map) {
            int item = 0;

            if (map == null) {
                return item;
            }

            if (map.isEmpty()) {
                return item;
            }

            for (Iterator<Map.Entry<String, Object>> it = map.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, Object> entry = it.next();
                for (int i = 0; i < toRemove.size(); i++) {
                    if (entry.getKey().equals(toRemove.get(i))) {
                        it.remove();
                        item++;
                    }
                }
            }
            return item;
        }

        /**
         * Rename the item from the map.
         *
         * @param map elem to rename
         * @return nb of renamed items
         */
        int rename(Map<String, Object> map) {
            int item = 0;

            if (map == null) {
                return item;
            }

            if (map.isEmpty() || toRename.isEmpty()) {
                return item;
            }

            for (Iterator<Map.Entry<String, Object>> it = toRename.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, Object> entry = it.next();
                if (map.containsKey(entry.getKey())) {
                    Object obj = map.remove(entry.getKey());
                    map.put(entry.getValue().toString(), obj);
                    item++;
                }
            }
            return item;
        }

    }

    /**
     * Extract Grok patter like %{FOO} to FOO, Also Grok pattern with semantic.
     */
    private static final java.util.regex.Pattern GROK_PATTERN = java.util.regex.Pattern.compile(
            "%\\{" +
                    "(?<name>" +
                    "(?<pattern>[A-z0-9]+)" +
                    "(?::(?<subname>[A-z0-9_:;\\/\\s\\.]+))?" +
                    ")" +
                    "(?:=(?<definition>" +
                    "(?:" +
                    "(?:[^{}]+|\\.+)+" +
                    ")+" +
                    ")" +
                    ")?" +
                    "\\}");

    private static final java.util.regex.Pattern NAMED_REGEX = java.util.regex.Pattern
            .compile("\\(\\?<([a-zA-Z][a-zA-Z0-9]*)>");

    private static Set<String> getNameGroups(String regex) {
        Set<String> namedGroups = new LinkedHashSet<>();
        Matcher m = NAMED_REGEX.matcher(regex);
        while (m.find()) {
            namedGroups.add(m.group(1));
        }
        return namedGroups;
    }

    private static Map<String, String> namedGroups(Matcher matcher,
                                                   String namedRegex) {
        Set<String> groupNames = getNameGroups(matcher.pattern().pattern());
        Matcher localMatcher = matcher.pattern().matcher(namedRegex);
        Map<String, String> namedGroups = new LinkedHashMap<>();
        if (localMatcher.find()) {
            for (String groupName : groupNames) {
                String groupValue = localMatcher.group(groupName);
                namedGroups.put(groupName, groupValue);
            }
        }
        return namedGroups;
    }
}