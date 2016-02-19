package nars.op.sys.scheme;

import nars.op.sys.scheme.cons.Cons;
import nars.op.sys.scheme.expressions.*;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static nars.op.sys.scheme.expressions.ListExpression.list;
import static nars.op.sys.scheme.expressions.SymbolExpression.symbol;


public enum Evaluator {
    ;

    public static Expression evaluate(@NotNull Expression exp, SchemeClosure env) {
        return analyze(exp).apply(env);
    }

    @NotNull
    public static Function<SchemeClosure, Expression> analyze(@NotNull Expression exp) {
        if (isSelfEvaluating(exp)) {
            return e -> exp;
        }
        if (exp.isSymbol()) {
            return env -> env.get(exp.symbol());
        }
        if (isSpecialForm(exp)) {
            return analyzeSpecialForm(exp.list());
        }
        if (isFunctionCall(exp)) {
            return analyzeFunctionCall(exp.list());
        }

        if (exp instanceof SymbolicProcedureExpression) {
            return (c) -> exp;
        }


        throw new IllegalArgumentException(String.format("Unable to evaluate expression '%s'", exp + " (" + exp.getClass() + ')'));
    }

    @NotNull
    private static Function<SchemeClosure, Expression> analyzeSpecialForm(@NotNull ListExpression exp) {
        Cons<Expression> exps = exp.value;

        //TODO use an enum of these operators already decoded to byte[] so that symbols dont need to re-generate a String version
        switch (exps.car().symbol().toString()) {
            case "quote":
                return analyzeQuote(exps);
            case "set!":
                return analyzeSet(exps);
            case "define":
                return isVarDefinition(exps) ? analyzeVarDefinition(exps) : analyzeFunctionDefinition(exps);
            case "if":
                return analyzeIf(exps);
            case "lambda":
                return analyzeLambda(exps);
            case "begin":
                return analyzeBegin(exps);
            case "let":
                return analyzeLet(exps);
            case "cond":
                return analyzeCond(exps);
        }

        throw new IllegalArgumentException(String.format("Invalid special form expression '%s'", exp));

    }

    private static Function<SchemeClosure, Expression> analyzeQuote(@NotNull Cons<Expression> exps) {
        return env -> exps.cadr();
    }

    private static Function<SchemeClosure, Expression> analyzeLambda(@NotNull Cons<Expression> exps) {
        Cons<SymbolExpression> paramNames = exps.cadr().list().value.stream()
                .map(Expression::symbol)
                .collect(Cons.collector());
        return analyzeProcedure(paramNames, exps);
    }

    private static Function<SchemeClosure, Expression> analyzeSet(@NotNull Cons<Expression> exps) {
        SymbolExpression symbol = exps.cadr().symbol();
        Function<SchemeClosure, Expression> valueProc = analyze(exps.cdr().cadr());

        return env -> {
            env.set(symbol, valueProc.apply(env));
            return Expression.none();
        };
    }

    private static Function<SchemeClosure, Expression> analyzeFunctionDefinition(@NotNull Cons<Expression> exps) {
        SymbolExpression name = exps.cadr().list().value.car().symbol();
        Cons<SymbolExpression> paramNames = exps.cadr().list().value.cdr().stream()
                .map(Expression::symbol)
                .collect(Cons.collector());
        Function<SchemeClosure, Expression> lambda = analyzeProcedure(paramNames, exps);
        return env -> {
            env.define(name, lambda.apply(env));
            return Expression.none();
        };
    }

    private static Function<SchemeClosure, Expression> analyzeVarDefinition(@NotNull Cons<Expression> exps) {
        SymbolExpression symbol = exps.cadr().symbol();
        Function<SchemeClosure, Expression> valueProc = analyze(exps.cdr().cadr());
        return env -> {
            env.define(symbol, valueProc.apply(env));
            return Expression.none();
        };
    }

    private static boolean isVarDefinition(@NotNull Cons<Expression> exps) {
        return exps.cadr().isSymbol();
    }

    private static Function<SchemeClosure, Expression> analyzeFunctionCall(@NotNull ListExpression exp) {
        List<Function<SchemeClosure, Expression>> map = exp.value.stream()
                .map(Evaluator::analyze)
                .collect(Collectors.toList());

        return env -> {
            Cons<Expression> list = map.stream()
                    .map(e -> e.apply(env))
                    .collect(Cons.collector());
            return list.car().procedure().apply(list.cdr());
        };
    }

    private static Function<SchemeClosure, Expression> analyzeLet(@NotNull Cons<Expression> exps) {
        List<Function<SchemeClosure, Expression>> letBindingValues = letBindingValues(exps);
        Function<SchemeClosure, Expression> letBody = analyzeProcedure(letBindingSymbols(exps), exps);

        return env -> {
            Cons<Expression> letParams = letBindingValues.stream()
                    .map(a -> a.apply(env))
                    .collect(Cons.collector());
            return letBody.apply(env).procedure().apply(letParams);
        };
    }

    private static Cons<SymbolExpression> letBindingSymbols(@NotNull Cons<Expression> exps) {
        return exps.cadr().list().value.stream()
                .map(e -> e.list().value.car().symbol())
                .collect(Cons.collector());
    }

    private static List<Function<SchemeClosure, Expression>> letBindingValues(@NotNull Cons<Expression> exps) {
        return exps.cadr().list().value.stream()
                .map(e -> e.list().value.cadr())
                .map(Evaluator::analyze)
                .collect(Collectors.toList());
    }

    private static Function<SchemeClosure, Expression> analyzeBegin(@NotNull Cons<Expression> exps) {
        return analyzeSequence(exps.cdr());
    }

    private static Function<SchemeClosure, Expression> analyzeSequence(@NotNull Cons<Expression> exps) {
        List<Function<SchemeClosure, Expression>> seq = exps.stream()
                .map(Evaluator::analyze)
                .collect(Collectors.toList());

        return env -> seq.stream()
                .collect(Collectors.reducing(Expression.none(), a -> a.apply(env), (a, b) -> b));
    }

    private static Function<SchemeClosure, Expression> analyzeCond(@NotNull Cons<Expression> exps) {
        return condToIf(exps.cdr());
    }

    private static Function<SchemeClosure, Expression> condToIf(@NotNull Cons<Expression> exps) {
        if (exps.isEmpty()) {
            return e -> BooleanExpression.bool(false);
        } else if (exps.size() == 1) {
            Function<SchemeClosure, Expression> condition = analyze(exps.car().list().value.car());
            Function<SchemeClosure, Expression> consequent = analyze(exps.car().list().value.cadr());
            Optional<Function<SchemeClosure, Expression>> alternative = Optional.empty();

            return makeIf(condition, consequent, alternative);
        } else {
            if (exps.cadr().list().value.car().equals(symbol("else"))) {
                Function<SchemeClosure, Expression> condition = analyze(exps.car().list().value.car());
                Function<SchemeClosure, Expression> consequent = analyze(exps.car().list().value.cadr());
                Optional<Function<SchemeClosure, Expression>> alternative = Optional.of(analyze(exps.cadr().list().value.cadr()));
                return makeIf(condition, consequent, alternative);
            } else {
                Function<SchemeClosure, Expression> condition = analyze(exps.car().list().value.car());
                Function<SchemeClosure, Expression> consequent = analyze(exps.car().list().value.cadr());
                Optional<Function<SchemeClosure, Expression>> alternative = Optional.of(condToIf(exps.cdr()));

                return makeIf(condition, consequent, alternative);
            }
        }
    }

    private static Function<SchemeClosure, Expression> makeIf(@NotNull Function<SchemeClosure, Expression> condition, @NotNull Function<SchemeClosure, Expression> consequent, @NotNull Optional<Function<SchemeClosure, Expression>> alternative) {
        return env -> isTruthy(condition.apply(env)) ? consequent.apply(env) : alternative.map(a -> a.apply(env)).orElse(Expression.none());
    }

    private static Function<SchemeClosure, Expression> analyzeIf(@NotNull Cons<Expression> exps) {
        Function<SchemeClosure, Expression> condition = analyze(exps.cadr());
        Function<SchemeClosure, Expression> consequent = analyze(exps.cdr().cadr());
        Optional<Function<SchemeClosure, Expression>> alternative = exps.size() > 3 ? Optional.of(analyze(exps.cdr().cdr().cadr())) : Optional.empty();
        return makeIf(condition, consequent, alternative);
    }

    private static Function<SchemeClosure, Expression> analyzeProcedure(@NotNull Cons<SymbolExpression> names, @NotNull Cons<Expression> exps) {
        Function<SchemeClosure, Expression> body = analyzeSequence(exps.cdr().cdr());
        return env ->
                ProcedureExpression.procedure(names, exps, args ->
                        body.apply(env.extend(makeMap(names, args, new LinkedHashMap<>()))));
    }

    @NotNull
    private static Map<SymbolExpression, Expression> makeMap(@NotNull Cons<SymbolExpression> names, @NotNull Cons<Expression> args, @NotNull Map<SymbolExpression, Expression> map) {
        while (true) {
            if (names.isEmpty()) {
                return map;
            } else if (names.car().equals(symbol("."))) {
                map.put(names.cadr(), list(args));
                return map;
            } else {
                map.put(names.car(), args.car());
                names = names.cdr();
                args = args.cdr();
            }
        }
    }

    private static boolean isSpecialForm(@NotNull Expression exp) {
        return exp.isList() && exp.list().value.car().isSymbol()
                && SPECIAL_FORMS.contains(exp.list().value.car().symbol());
    }

    private static boolean isFunctionCall(@NotNull Expression exp) {
        return exp.isList() && !exp.list().value.isEmpty();
    }

    private static boolean isSelfEvaluating(@NotNull Expression exp) {
        return exp.isNumber() || exp.isBoolean() || exp.isString() || exp == Expression.none() || (exp.isList() && exp.list().value.isEmpty());
    }

    private static boolean isTruthy(@NotNull Expression exp) {
        return !BooleanExpression.bool(false).equals(exp);
    }

    private static final Set<SymbolExpression> SPECIAL_FORMS = Arrays.stream(new String[]{"quote", "set!", "define", "if", "lambda", "begin", "let", "cond"})
            .map(SymbolExpression::symbol)
            .collect(Collectors.toSet());
}
