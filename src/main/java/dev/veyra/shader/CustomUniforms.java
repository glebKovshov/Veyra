package dev.veyra.shader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OptiFine/Iris {@code uniform.<type>.<name>} and {@code variable.<type>.<name>} definitions from
 * shaders.properties. Expressions are evaluated on the CPU once per frame from Veyra's built-in uniforms.
 *
 * <p>Scalar float/int/bool and float vec2/3/4 results are supported. Unknown inputs
 * and malformed expressions are kept as unavailable with a reason, so programs that declare
 * them fail with a clear diagnostic instead of receiving fake values.
 */
public final class CustomUniforms {
    public static final CustomUniforms EMPTY = new CustomUniforms(List.of(), Map.of(), Map.of(), List.of(), List.of());
    /** Built-in scalar inputs an expression may reference; vector inputs are exposed by component. */
    public static final Set<String> BUILT_IN_INPUTS = withIrisExtras(Set.of(
            "biome", "biome_precipitation", "playerMood", "darknessLightFactor", "shadowFade", "velocity", "near", "far", "heldBlockLightValue", "heldBlockLightValue2", "heldItemId", "heldItemId2",
            "viewWidth", "viewHeight", "aspectRatio", "frameTimeCounter", "frameTime", "frameCounter",
            "sunAngle", "rainStrength", "wetness", "thunderStrength", "worldTime", "worldDay", "moonPhase",
            "bedrockLevel", "cloudHeight", "endFlashIntensity", "eyeAltitude", "screenBrightness", "blindness",
            "nightVision", "darknessFactor", "isEyeInWater", "isSpectator", "is_sneaking", "is_sprinting",
            "is_on_ground", "is_hurt", "is_invisible", "is_burning",
            "cameraPosition.x", "cameraPosition.y", "cameraPosition.z",
            "previousCameraPosition.x", "previousCameraPosition.y", "previousCameraPosition.z",
            "eyeBrightness.x", "eyeBrightness.y", "eyeBrightnessSmooth.x", "eyeBrightnessSmooth.y"),
            IrisExtraUniforms.expressionInputs());
    /** Iris hardcoded custom floats and Veyra frame helpers that pack properties may override. */
    public static final Set<String> PACK_OVERRIDABLE = withIrisExtras(Set.of("shadowFade", "velocity"),
            IrisExtraUniforms.HARDCODED_CUSTOM);

    private static Set<String> withIrisExtras(Set<String> names, Set<String> extras) {
        Set<String> union = new java.util.HashSet<>(names);
        union.addAll(extras);
        return Set.copyOf(union);
    }
    private static final Pattern DEFINITION = Pattern.compile(
            "(uniform|variable)\\.(float|int|bool|vec2|vec3|vec4)\\.([A-Za-z_][A-Za-z0-9_]*)\\s*[=:]\\s*(.*)",
            Pattern.DOTALL);
    private static final int MAX_EXPRESSION_LENGTH = 16 * 1024;
    private static final int MAX_DEFINITIONS = 1024;
    private static final Map<String, Integer> BUILT_IN_WIDTHS = Map.copyOf(inputWidths());
    private static volatile CustomUniforms active = EMPTY;

    public enum Type {
        FLOAT("float"), INT("int"), BOOL("bool"), VEC2("vec2"), VEC3("vec3"), VEC4("vec4");

        private final String glsl;

        Type(String glsl) {
            this.glsl = glsl;
        }

        public String glsl() {
            return glsl;
        }

        public int components() { return glsl.startsWith("vec") ? glsl.charAt(3) - '0' : 1; }
    }

    /** A uniform exposed to shaders. */
    public record Uniform(String name, Type type) {
    }

    private record Definition(String name, Type type, boolean uniform, Node expression) {
    }

    private final List<Definition> definitions;
    private final Map<String, String> unavailable;
    private final Map<String, String> irisUnresolved;
    private final List<String> circularDefinitions;
    // Renderer suppliers look uniforms up on every uniform-buffer update.
    private final Map<String, Uniform> uniformIndex = new LinkedHashMap<>();
    private final Map<String, Value> values = new HashMap<>();

    private record Value(double scalar, double[] vector) {
        static Value scalar(double value) { return new Value(value, null); }
        static Value vector(double[] values) { return new Value(0, values); }
        int size() { return vector == null ? 1 : vector.length; }
        double component(int index) { return vector == null ? scalar : vector[index]; }
    }

    private static Map<String, Integer> inputWidths() {
        Map<String, Integer> widths = new HashMap<>();
        for (String name : BUILT_IN_INPUTS) {
            widths.put(name, 1);
            int dot = name.lastIndexOf('.');
            if (dot > 0 && name.length() == dot + 2) {
                int component = "xyzw".indexOf(name.charAt(dot + 1));
                if (component >= 0) widths.merge(name.substring(0, dot), component + 1, Math::max);
            }
        }
        return widths;
    }

    private CustomUniforms(List<Definition> definitions, Map<String, String> unavailable,
                           Map<String, String> irisUnresolved, List<Uniform> uniforms, List<String> circularDefinitions) {
        this.definitions = definitions;
        this.unavailable = unavailable;
        this.irisUnresolved = irisUnresolved;
        this.circularDefinitions = List.copyOf(circularDefinitions);
        for (Uniform uniform : uniforms) uniformIndex.put(uniform.name(), uniform);
    }

    public static CustomUniforms active() {
        return active;
    }

    public static void activate(CustomUniforms uniforms) {
        active = uniforms == null ? EMPTY : uniforms;
    }

    /** Parses all definitions before resolving their dependency order, as Iris does. */
    public static CustomUniforms parse(String properties) {
        List<Definition> definitions = new ArrayList<>();
        Map<String, String> unavailable = new LinkedHashMap<>();
        Map<String, String> irisUnresolved = new LinkedHashMap<>();
        Map<String, Definition> parsed = new LinkedHashMap<>();
        Set<String> seen = new java.util.HashSet<>();
        for (String line : logicalLines(properties == null ? "" : properties)) {
            Matcher matcher = DEFINITION.matcher(line);
            if (!matcher.matches()) continue;
            boolean uniform = matcher.group(1).equals("uniform");
            String typeName = matcher.group(2);
            String name = matcher.group(3);
            String source = matcher.group(4).strip();
            if (seen.contains(name)) continue;
            // BSL defines its own expression for shadowFade in shaders.properties. Keep such pack
            // overrides compatible while still exposing Iris' frame value when they are absent.
            if ((BUILT_IN_WIDTHS.containsKey(name) || IrisUnsetUniforms.IRIS_SET_UNIFORMS.contains(name))
                    && !PACK_OVERRIDABLE.contains(name)) {
                unavailable.put(name, "it would replace a built-in uniform");
                continue;
            }
            if (source.length() > MAX_EXPRESSION_LENGTH || seen.size() >= MAX_DEFINITIONS) {
                unavailable.put(name, "the expression exceeds Veyra's size limit");
                continue;
            }
            Node expression;
            try {
                expression = new Parser(source).parse();
            } catch (IllegalArgumentException exception) {
                unavailable.put(name, "invalid expression: " + exception.getMessage());
                continue;
            }
            Type type = Type.valueOf(typeName.toUpperCase(java.util.Locale.ROOT));
            if (PACK_OVERRIDABLE.contains(name)
                    && (type.components() > 1 || !IrisExtraUniforms.VEYRA_COMPATIBILITY_UNIFORMS.contains(name)
                    && type != Type.FLOAT)) {
                unavailable.put(name, "the built-in " + name + " uniform must remain a float");
                continue;
            }
            seen.add(name);
            unavailable.remove(name);
            parsed.put(name, new Definition(name, type, uniform, expression));
        }
        Map<String, Integer> widths = new HashMap<>(BUILT_IN_WIDTHS);
        parsed.forEach((name, definition) -> widths.put(name, definition.type().components()));
        for (Definition definition : parsed.values()) {
            try {
                int width = expressionWidth(definition.expression(), widths);
                if (width != definition.type().components()) throw new IllegalArgumentException(
                        "expression has " + width + " components but " + definition.type().glsl() + " requires "
                                + definition.type().components());
            } catch (IllegalArgumentException exception) {
                unavailable.put(definition.name(), "invalid expression: " + exception.getMessage());
            }
        }
        parsed.entrySet().removeIf(entry -> unavailable.containsKey(entry.getKey()));
        Map<String, Set<String>> inputs = new LinkedHashMap<>();
        Map<String, Integer> remaining = new HashMap<>();
        Map<String, List<String>> dependents = new HashMap<>();
        java.util.ArrayDeque<String> ready = new java.util.ArrayDeque<>();
        for (Definition definition : parsed.values()) {
            Set<String> names = new java.util.LinkedHashSet<>();
            definition.expression().collectInputs(names);
            Set<String> dependencies = new java.util.LinkedHashSet<>();
            for (String input : names) {
                int dot = input.lastIndexOf('.');
                String base = dot > 0 ? input.substring(0, dot) : input;
                dependencies.add(widths.getOrDefault(base, 1) > 1 ? base : input);
            }
            names = dependencies;
            inputs.put(definition.name(), names);
            int count = 0;
            for (String input : names) {
                // A pack override may read its own built-in fallback, e.g. velocity.
                if (!parsed.containsKey(input)
                        || input.equals(definition.name()) && BUILT_IN_INPUTS.contains(input)) continue;
                dependents.computeIfAbsent(input, key -> new ArrayList<>()).add(definition.name());
                count++;
            }
            remaining.put(definition.name(), count);
            if (count == 0) ready.addLast(definition.name());
        }
        while (!ready.isEmpty()) {
            String name = ready.removeFirst();
            Definition definition = parsed.get(name);
            String missing = inputs.get(name).stream().filter(input ->
                    irisUnresolved.containsKey(input)
                            || !BUILT_IN_WIDTHS.containsKey(input) && BiomeIds.constant(input) == null
                            && (!parsed.containsKey(input) || unavailable.containsKey(input)))
                    .findFirst().orElse(null);
            if (missing == null) {
                definitions.add(definition);
            } else if (irisUnresolved.containsKey(missing) || missing.startsWith("BIOME_")) {
                irisUnresolved.put(name, irisUnresolved.containsKey(missing)
                        ? "it depends on '" + missing + "', which Iris cannot resolve"
                        : "'" + missing + "' is not a registered vanilla biome");
                Node zero = definition.type().components() == 1 ? new Constant(0.0)
                        : new Call(definition.type().glsl(), Collections.nCopies(definition.type().components(), new Constant(0)));
                definitions.add(new Definition(name, definition.type(), definition.uniform(), zero));
            } else {
                unavailable.put(name, unavailable.containsKey(missing)
                        ? "it depends on unavailable '" + missing + "'"
                        : "input '" + missing + "' is not provided by Veyra");
            }
            for (String dependent : dependents.getOrDefault(name, List.of())) {
                if (remaining.merge(dependent, -1, Integer::sum) == 0) ready.addLast(dependent);
            }
        }
        remaining.forEach((name, count) -> {
            if (count > 0) unavailable.put(name, "circular custom uniform dependency involving '" + name + "'");
        });
        return new CustomUniforms(List.copyOf(definitions), Collections.unmodifiableMap(unavailable),
                Collections.unmodifiableMap(irisUnresolved), parsed.values().stream()
                        .filter(definition -> definition.uniform() && !unavailable.containsKey(definition.name()))
                        .map(definition -> new Uniform(definition.name(), definition.type())).toList(),
                parsed.keySet().stream().filter(name -> remaining.get(name) > 0).toList());
    }

    /** Cycles and their blocked dependents cannot be evaluated and must reject pack installation. */
    public List<String> circularDefinitions() { return circularDefinitions; }

    /**
     * Definitions that Iris itself cannot resolve, mapped to the reason. Like Iris they stay 0;
     * callers should log them the way Iris warns about them.
     */
    public Map<String, String> irisUnresolved() {
        return irisUnresolved;
    }

    /** Uniforms in declaration order; only evaluation uses dependency order. */
    public List<Uniform> uniforms() {
        return List.copyOf(uniformIndex.values());
    }

    public Uniform uniform(String name) {
        return uniformIndex.get(name);
    }

    /** Explains why a declared custom uniform cannot be supplied, or returns null. */
    public String unavailableReason(String name) {
        return unavailable.get(name);
    }

    /** Re-evaluates all definitions. {@code inputs} returns null for unknown names. */
    public synchronized void evaluate(Function<String, Double> inputs, double frameTimeSeconds) {
        Map<String, Value> scope = new HashMap<>();
        Map<String, Integer> builtIns = BUILT_IN_WIDTHS;
        Function<String, Value> resolver = name -> {
            Value local = scope.get(name);
            if (local != null) return local;
            int dot = name.lastIndexOf('.');
            if (dot > 0 && scope.containsKey(name.substring(0, dot))) {
                Value vector = scope.get(name.substring(0, dot));
                return Value.scalar(vector.component(componentIndex(name.substring(dot + 1))));
            }
            if (dot > 0 && builtIns.getOrDefault(name.substring(0, dot), 1) > 1) {
                String base = name.substring(0, dot);
                Double value = inputs.apply(base + "." + "xyzw".charAt(componentIndex(name.substring(dot + 1))));
                return Value.scalar(value == null ? 0 : value);
            }
            int width = builtIns.getOrDefault(name, 1);
            if (width > 1) {
                double[] vector = new double[width];
                for (int i = 0; i < width; i++) {
                    Double value = inputs.apply(name + "." + "xyzw".charAt(i));
                    vector[i] = value == null ? 0 : value;
                }
                return Value.vector(vector);
            }
            Double value = inputs.apply(name);
            return Value.scalar(value == null ? 0 : value);
        };
        for (Definition definition : definitions) {
            Value evaluated;
            try {
                evaluated = definition.expression().evaluate(resolver, frameTimeSeconds);
            } catch (ArithmeticException exception) {
                evaluated = definition.type().components() == 1 ? Value.scalar(0)
                        : Value.vector(new double[definition.type().components()]);
            }
            double value = evaluated.scalar();
            Value result = switch (definition.type()) {
                case VEC2, VEC3, VEC4 -> {
                    double[] vector = evaluated.vector().clone();
                    for (int i = 0; i < vector.length; i++) vector[i] = Float.isFinite((float) vector[i]) ? (float) vector[i] : 0;
                    yield Value.vector(vector);
                }
                case FLOAT -> Value.scalar(Double.isFinite(value) ? value : 0.0);
                case INT -> Value.scalar(Double.isFinite(value) ? (double) (int) value : 0.0);
                case BOOL -> Value.scalar(value != 0.0 ? 1.0 : 0.0);
            };
            scope.put(definition.name(), result);
        }
        values.clear();
        values.putAll(scope);
    }

    public synchronized float floatValue(String name) {
        return (float) values.getOrDefault(name, Value.scalar(0)).scalar();
    }

    public synchronized int intValue(String name) {
        return (int) values.getOrDefault(name, Value.scalar(0)).scalar();
    }

    /** Copies one frame's vector atomically; missing names clear old GPU supplier values after a pack switch. */
    public synchronized void copyVector(String name, float[] destination) {
        java.util.Arrays.fill(destination, 0);
        Value value = values.get(name);
        if (value == null || value.vector() == null) return;
        if (destination.length < value.size()) throw new IllegalArgumentException("Vector destination is too small");
        for (int i = 0; i < value.size(); i++) destination[i] = (float) value.component(i);
    }

    private static List<String> logicalLines(String text) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String raw : text.split("\\r?\\n", -1)) {
            String line = current.isEmpty() ? raw.stripLeading() : raw.strip();
            if (line.endsWith("\\")) {
                current.append(line, 0, line.length() - 1).append(' ');
                continue;
            }
            current.append(line);
            String complete = current.toString().strip();
            current.setLength(0);
            if (!complete.isEmpty() && !complete.startsWith("#") && !complete.startsWith("!")) {
                lines.add(complete);
            }
        }
        if (!current.isEmpty()) lines.add(current.toString().strip());
        return lines;
    }

    private static int componentIndex(String accessor) {
        return switch (accessor) {
            case "x", "r", "s", "0" -> 0;
            case "y", "g", "t", "1" -> 1;
            case "z", "b", "p", "2" -> 2;
            case "w", "a", "q", "3" -> 3;
            default -> throw new IllegalArgumentException("unsupported vector accessor '" + accessor + "'");
        };
    }

    private static int sameWidth(int a, int b) {
        if (a != b) throw new IllegalArgumentException("vector dimensions do not match: " + a + " and " + b);
        return a;
    }

    private static void scalarWidth(int width) {
        if (width != 1) throw new IllegalArgumentException("this operation requires a scalar, got vec" + width);
    }

    /** Validate dimensions before any frame evaluation; unsupported overloads never silently read zero. */
    private static int expressionWidth(Node node, Map<String, Integer> widths) {
        if (node instanceof Constant) return 1;
        if (node instanceof Reference reference) {
            Integer width = widths.get(reference.name());
            if (width != null) return width;
            int dot = reference.name().lastIndexOf('.');
            if (dot > 0 && widths.containsKey(reference.name().substring(0, dot))) {
                int size = widths.get(reference.name().substring(0, dot));
                int index = componentIndex(reference.name().substring(dot + 1));
                if (size == 1 || index >= size) throw new IllegalArgumentException("component outside vec" + size);
            }
            return 1;
        }
        if (node instanceof Access access) {
            int width = expressionWidth(access.vector(), widths);
            if (width == 1 || access.index() >= width) throw new IllegalArgumentException("component outside vec" + width);
            return 1;
        }
        if (node instanceof Unary unary) {
            int width = expressionWidth(unary.operand(), widths);
            if (unary.operator() == '!') scalarWidth(width);
            return width;
        }
        if (node instanceof Binary binary) {
            int width = sameWidth(expressionWidth(binary.left(), widths), expressionWidth(binary.right(), widths));
            if (Set.of("==", "!=").contains(binary.operator())) return 1;
            if (!Set.of("+", "-", "*", "/").contains(binary.operator())) scalarWidth(width);
            return width;
        }
        if (node instanceof Ternary ternary) {
            scalarWidth(expressionWidth(ternary.condition(), widths));
            return sameWidth(expressionWidth(ternary.whenTrue(), widths), expressionWidth(ternary.whenFalse(), widths));
        }
        Call call = (Call) node;
        List<Integer> args = call.arguments.stream().map(argument -> expressionWidth(argument, widths)).toList();
        if (call.name.startsWith("vec")) {
            args.forEach(CustomUniforms::scalarWidth);
            return call.resultWidth;
        }
        if (call.name.equals("if")) {
            int width = args.getLast();
            for (int i = 0; i + 1 < args.size(); i += 2) {
                scalarWidth(args.get(i));
                sameWidth(width, args.get(i + 1));
            }
            if (width > 1 && args.size() != 3) throw new IllegalArgumentException("vector if requires three arguments");
            return call.resultWidth = width;
        }
        int width = args.isEmpty() ? 1 : args.getFirst();
        if (args.stream().anyMatch(size -> size > 1)) {
            if (!Set.of("abs", "floor", "ceil", "min", "max", "clamp").contains(call.name)
                    || Set.of("min", "max").contains(call.name) && args.size() != 2) {
                throw new IllegalArgumentException("no float vector overload for '" + call.name + "'");
            }
            for (int size : args) sameWidth(width, size);
        }
        return call.resultWidth = width;
    }

    private interface Node {
        Value evaluate(Function<String, Value> inputs, double frameTime);

        void collectInputs(Set<String> inputs);
    }

    private record Constant(double value) implements Node {
        public Value evaluate(Function<String, Value> inputs, double frameTime) {
            return Value.scalar(value);
        }

        public void collectInputs(Set<String> inputs) { }
    }

    private record Reference(String name) implements Node {
        public Value evaluate(Function<String, Value> inputs, double frameTime) {
            Integer constant = BiomeIds.constant(name);
            if (constant != null) return Value.scalar(constant);
            return inputs.apply(name);
        }

        public void collectInputs(Set<String> inputs) { inputs.add(name); }
    }

    private record Access(Node vector, int index) implements Node {
        public Value evaluate(Function<String, Value> inputs, double frameTime) {
            return Value.scalar(vector.evaluate(inputs, frameTime).component(index));
        }
        public void collectInputs(Set<String> inputs) { vector.collectInputs(inputs); }
    }

    private record Unary(char operator, Node operand) implements Node {
        public Value evaluate(Function<String, Value> inputs, double frameTime) {
            Value value = operand.evaluate(inputs, frameTime);
            if (value.size() == 1) return Value.scalar(operator == '-' ? -value.scalar() : value.scalar() == 0 ? 1 : 0);
            double[] result = value.vector().clone();
            for (int i = 0; i < result.length; i++) result[i] = -result[i];
            return Value.vector(result);
        }

        public void collectInputs(Set<String> inputs) { operand.collectInputs(inputs); }
    }

    private record Binary(String operator, Node left, Node right) implements Node {
        public Value evaluate(Function<String, Value> inputs, double frameTime) {
            Value first = left.evaluate(inputs, frameTime);
            double a = first.scalar();
            if (operator.equals("&&")) return Value.scalar(a != 0 && right.evaluate(inputs, frameTime).scalar() != 0 ? 1 : 0);
            if (operator.equals("||")) return Value.scalar(a != 0 || right.evaluate(inputs, frameTime).scalar() != 0 ? 1 : 0);
            Value second = right.evaluate(inputs, frameTime);
            if (first.size() > 1) {
                if (operator.equals("==") || operator.equals("!=")) {
                    boolean equal = java.util.Arrays.equals(first.vector(), second.vector());
                    return Value.scalar(equal == operator.equals("==") ? 1 : 0);
                }
                double[] result = new double[first.size()];
                for (int i = 0; i < result.length; i++) {
                    double x = first.component(i), y = second.component(i);
                    result[i] = switch (operator) {
                        case "+" -> x + y;
                        case "-" -> x - y;
                        case "*" -> x * y;
                        case "/" -> y == 0 ? 0 : x / y;
                        default -> throw new IllegalStateException(operator);
                    };
                    result[i] = (float) result[i];
                }
                return Value.vector(result);
            }
            double b = second.scalar();
            return Value.scalar(switch (operator) {
                case "+" -> a + b;
                case "-" -> a - b;
                case "*" -> a * b;
                case "/" -> b == 0.0 ? 0.0 : a / b;
                case "%" -> b == 0.0 ? 0.0 : a % b;
                case "<" -> a < b ? 1.0 : 0.0;
                case "<=" -> a <= b ? 1.0 : 0.0;
                case ">" -> a > b ? 1.0 : 0.0;
                case ">=" -> a >= b ? 1.0 : 0.0;
                case "==" -> a == b ? 1.0 : 0.0;
                case "!=" -> a != b ? 1.0 : 0.0;
                default -> throw new IllegalStateException(operator);
            });
        }

        public void collectInputs(Set<String> inputs) {
            left.collectInputs(inputs);
            right.collectInputs(inputs);
        }
    }

    private static final class Call implements Node {
        private final String name;
        private final List<Node> arguments;
        private final IrisSmoothedFloat smoother = new IrisSmoothedFloat(1.0, 1.0);
        private int resultWidth = 1;

        Call(String name, List<Node> arguments) {
            this.name = name;
            this.arguments = arguments;
            int count = arguments.size();
            boolean valid = switch (name) {
                case "vec2", "vec3", "vec4" -> count == name.charAt(3) - '0';
                case "sin", "cos", "tan", "asin", "acos", "abs", "floor", "ceil", "exp", "exp2", "exp10", "log2",
                     "log10", "frac", "fract", "sqrt", "signum", "sign", "round", "torad", "todeg", "radians",
                     "degrees" -> count == 1;
                case "atan", "log" -> count == 1 || count == 2;
                case "atan2", "pow", "fmod", "mod", "step", "edge" -> count == 2;
                case "random" -> count == 0 || count == 2;
                case "randomInt" -> count <= 2;
                case "min", "max" -> count >= 2;
                case "clamp", "between", "mix", "lerp", "smoothstep" -> count == 3;
                case "equals" -> count == 3;
                case "in" -> count >= 2;
                case "if" -> count >= 3 && count % 2 == 1;
                case "smooth" -> count >= 1 && count <= 4;
                default -> throw new IllegalArgumentException("unknown function '" + name + "'");
            };
            if (!valid) throw new IllegalArgumentException("wrong argument count for '" + name + "'");
            if (name.startsWith("vec")) resultWidth = name.charAt(3) - '0';
        }

        private double arg(int index, Function<String, Value> inputs, double frameTime) {
            return arguments.get(index).evaluate(inputs, frameTime).scalar();
        }

        public Value evaluate(Function<String, Value> inputs, double frameTime) {
            if (name.startsWith("vec")) {
                double[] result = new double[resultWidth];
                for (int i = 0; i < result.length; i++) result[i] = (float) arg(i, inputs, frameTime);
                return Value.vector(result);
            }
            if (name.equals("if")) {
                for (int i = 0; i + 1 < arguments.size(); i += 2) {
                    if (arg(i, inputs, frameTime) != 0) return arguments.get(i + 1).evaluate(inputs, frameTime);
                }
                return arguments.getLast().evaluate(inputs, frameTime);
            }
            if (resultWidth > 1) {
                List<Value> args = arguments.stream().map(argument -> argument.evaluate(inputs, frameTime)).toList();
                double[] result = new double[resultWidth];
                for (int i = 0; i < result.length; i++) {
                    double a = args.getFirst().component(i);
                    result[i] = switch (name) {
                        case "abs" -> Math.abs(a);
                        case "floor" -> Math.floor(a);
                        case "ceil" -> Math.ceil(a);
                        case "min" -> Math.min(a, args.get(1).component(i));
                        case "max" -> Math.max(a, args.get(1).component(i));
                        case "clamp" -> Math.max(args.get(1).component(i), Math.min(args.get(2).component(i), a));
                        default -> throw new IllegalStateException(name);
                    };
                }
                return Value.vector(result);
            }
            return Value.scalar(evaluateScalar(inputs, frameTime));
        }

        private double evaluateScalar(Function<String, Value> inputs, double frameTime) {
            switch (name) {
                case "in": {
                    double value = arg(0, inputs, frameTime);
                    for (int i = 1; i < arguments.size(); i++) {
                        if (arg(i, inputs, frameTime) == value) return 1.0;
                    }
                    return 0.0;
                }
                case "min", "max": {
                    double result = arg(0, inputs, frameTime);
                    for (int i = 1; i < arguments.size(); i++) {
                        double value = arg(i, inputs, frameTime);
                        result = name.equals("min") ? Math.min(result, value) : Math.max(result, value);
                    }
                    return result;
                }
                case "smooth":
                    return smooth(inputs, frameTime);
                case "random": {
                    double unit = java.util.concurrent.ThreadLocalRandom.current().nextDouble();
                    if (arguments.isEmpty()) return unit;
                    double min = arg(0, inputs, frameTime);
                    return min + unit * (arg(1, inputs, frameTime) - min);
                }
                case "randomInt": {
                    java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();
                    if (arguments.isEmpty()) return random.nextInt();
                    int first = (int) arg(0, inputs, frameTime);
                    if (arguments.size() == 1) return first > 0 ? random.nextInt(first) : 0;
                    int second = (int) arg(1, inputs, frameTime);
                    return second > first ? random.nextInt(first, second) : first;
                }
                default:
                    break;
            }
            double a = arg(0, inputs, frameTime);
            return switch (name) {
                case "sin" -> Math.sin(a);
                case "cos" -> Math.cos(a);
                case "tan" -> Math.tan(a);
                case "asin" -> Math.asin(a);
                case "acos" -> Math.acos(a);
                case "atan" -> arguments.size() == 1 ? Math.atan(a) : Math.atan2(a, arg(1, inputs, frameTime));
                case "atan2" -> Math.atan2(a, arg(1, inputs, frameTime));
                case "abs" -> Math.abs(a);
                case "floor" -> Math.floor(a);
                case "ceil" -> Math.ceil(a);
                case "exp" -> Math.exp(a);
                case "exp2" -> Math.pow(2.0, a);
                case "exp10" -> Math.pow(10.0, a);
                // Iris: log(x) is natural, log(base, x) uses the given base.
                case "log" -> arguments.size() == 1 ? Math.log(a) : Math.log(arg(1, inputs, frameTime)) / Math.log(a);
                case "log2" -> Math.log(a) / Math.log(2.0);
                case "log10" -> Math.log10(a);
                case "frac", "fract" -> a - Math.floor(a);
                case "sqrt" -> Math.sqrt(a);
                case "signum", "sign" -> Math.signum(a);
                case "round" -> Math.round(a);
                case "torad", "radians" -> Math.toRadians(a);
                case "todeg", "degrees" -> Math.toDegrees(a);
                case "pow" -> Math.pow(a, arg(1, inputs, frameTime));
                // Iris fmod is a floored modulo: (a % b + b) % b, Math.floorMod for integers.
                case "fmod", "mod" -> {
                    double b = arg(1, inputs, frameTime);
                    yield b == 0.0 ? 0.0 : a - b * Math.floor(a / b);
                }
                // GLSL step(edge, x) and Iris edge(edge, x) are both 0 below the edge.
                case "step", "edge" -> arg(1, inputs, frameTime) < a ? 0.0 : 1.0;
                case "clamp" -> Math.max(arg(1, inputs, frameTime), Math.min(arg(2, inputs, frameTime), a));
                case "between" -> a >= arg(1, inputs, frameTime) && a <= arg(2, inputs, frameTime) ? 1.0 : 0.0;
                case "equals" -> Math.abs(a - arg(1, inputs, frameTime)) <= arg(2, inputs, frameTime) ? 1.0 : 0.0;
                case "mix", "lerp" -> {
                    double b = arg(1, inputs, frameTime);
                    yield a + (b - a) * arg(2, inputs, frameTime);
                }
                case "smoothstep" -> {
                    double edge0 = a;
                    double edge1 = arg(1, inputs, frameTime);
                    double t = edge1 == edge0 ? 0.0
                            : Math.max(0.0, Math.min(1.0, (arg(2, inputs, frameTime) - edge0) / (edge1 - edge0)));
                    yield t * t * (3.0 - 2.0 * t);
                }
                default -> throw new IllegalStateException(name);
            };
        }

        /**
         * smooth([id,] value[, fadeUp[, fadeDown]]) as implemented by Iris' SmoothFloat: exponential
         * smoothing whose fade arguments are half-lives in tenths of a second (default 1), and a zero
         * fade follows the value immediately. A constant first argument is OptiFine's state id; each call
         * site keeps its own state here, so the id is not needed to separate them.
         */
        private double smooth(Function<String, Value> inputs, double frameTime) {
            int offset = arguments.size() >= 2 && arguments.getFirst() instanceof Constant ? 1 : 0;
            double target = arg(offset, inputs, frameTime);
            int fades = arguments.size() - offset - 1;
            double fadeUp = fades >= 1 ? arg(offset + 1, inputs, frameTime) : 1.0;
            double fadeDown = fades >= 2 ? arg(offset + 2, inputs, frameTime) : fadeUp;
            return smoother.update(target, frameTime, fadeUp, fadeDown);
        }

        public void collectInputs(Set<String> inputs) {
            for (Node argument : arguments) argument.collectInputs(inputs);
        }
    }

    private record Ternary(Node condition, Node whenTrue, Node whenFalse) implements Node {
        public Value evaluate(Function<String, Value> inputs, double frameTime) {
            return condition.evaluate(inputs, frameTime).scalar() != 0.0
                    ? whenTrue.evaluate(inputs, frameTime) : whenFalse.evaluate(inputs, frameTime);
        }

        public void collectInputs(Set<String> inputs) {
            condition.collectInputs(inputs);
            whenTrue.collectInputs(inputs);
            whenFalse.collectInputs(inputs);
        }
    }

    /** Recursive-descent parser with C operator precedence. */
    private static final class Parser {
        private static final int MAX_DEPTH = 64;
        private final String source;
        private int position;
        private int depth;

        Parser(String source) {
            this.source = source;
        }

        Node parse() {
            Node node = ternary();
            skipWhitespace();
            if (position != source.length()) {
                throw new IllegalArgumentException("unexpected '" + source.charAt(position) + "' at " + position);
            }
            return node;
        }

        private Node ternary() {
            if (++depth > MAX_DEPTH) throw new IllegalArgumentException("expression is nested too deeply");
            try {
                Node condition = binary(0);
                if (accept("?")) {
                    Node whenTrue = ternary();
                    expect(":");
                    return bounded(new Ternary(condition, whenTrue, ternary()));
                }
                return condition;
            } finally {
                depth--;
            }
        }

        private static final List<List<String>> LEVELS = List.of(
                List.of("||"), List.of("&&"), List.of("==", "!="), List.of("<=", ">=", "<", ">"),
                List.of("+", "-"), List.of("*", "/", "%"));

        private Node binary(int level) {
            if (level == LEVELS.size()) return unary();
            Node left = binary(level + 1);
            while (true) {
                String operator = null;
                for (String candidate : LEVELS.get(level)) {
                    if (peek(candidate)) {
                        operator = candidate;
                        break;
                    }
                }
                if (operator == null) return left;
                position += operator.length();
                left = bounded(new Binary(operator, left, binary(level + 1)));
            }
        }

        private Node unary() {
            if (++depth > MAX_DEPTH) throw new IllegalArgumentException("expression is nested too deeply");
            try {
                if (accept("-")) return bounded(new Unary('-', unary()));
                if (accept("+")) return unary();
                if (peek("!") && !peek("!=")) {
                    position++;
                    return bounded(new Unary('!', unary()));
                }
                return primary();
            } finally {
                depth--;
            }
        }

        private Node bounded(Node node) {
            if (treeDepth(node) > MAX_DEPTH) throw new IllegalArgumentException("expression is nested too deeply");
            return node;
        }

        private static int treeDepth(Node node) {
            if (node instanceof Access access) return 1 + treeDepth(access.vector());
            if (node instanceof Unary unary) return 1 + treeDepth(unary.operand());
            if (node instanceof Binary binary) return 1 + Math.max(treeDepth(binary.left()), treeDepth(binary.right()));
            if (node instanceof Ternary ternary) return 1 + Math.max(treeDepth(ternary.condition()),
                    Math.max(treeDepth(ternary.whenTrue()), treeDepth(ternary.whenFalse())));
            if (node instanceof Call call) return 1 + call.arguments.stream().mapToInt(Parser::treeDepth).max().orElse(0);
            return 1;
        }

        private Node primary() {
            skipWhitespace();
            if (accept("(")) {
                Node inner = ternary();
                expect(")");
                return postfix(inner);
            }
            if (position >= source.length()) throw new IllegalArgumentException("unexpected end of expression");
            char c = source.charAt(position);
            if (Character.isDigit(c) || c == '.') return number();
            if (Character.isLetter(c) || c == '_') {
                int start = position;
                while (position < source.length()
                        && (Character.isLetterOrDigit(source.charAt(position)) || source.charAt(position) == '_'
                        || source.charAt(position) == '.')) {
                    position++;
                }
                String name = source.substring(start, position);
                if (accept("(")) {
                    List<Node> arguments = new ArrayList<>();
                    if (!accept(")")) {
                        do {
                            arguments.add(ternary());
                        } while (accept(","));
                        expect(")");
                    }
                    return postfix(bounded(new Call(name, arguments)));
                }
                return postfix(switch (name) {
                    case "true" -> new Constant(1.0);
                    case "false" -> new Constant(0.0);
                    case "pi", "PI" -> new Constant(Math.PI);
                    default -> new Reference(name);
                });
            }
            throw new IllegalArgumentException("unexpected '" + c + "' at " + position);
        }

        private Node postfix(Node node) {
            while (accept(".")) {
                skipWhitespace();
                int start = position;
                while (position < source.length() && Character.isLetterOrDigit(source.charAt(position))) position++;
                node = bounded(new Access(node, componentIndex(source.substring(start, position))));
            }
            return node;
        }

        private Node number() {
            int start = position;
            while (position < source.length()
                    && (Character.isDigit(source.charAt(position)) || source.charAt(position) == '.')) {
                position++;
            }
            if (position < source.length() && (source.charAt(position) == 'e' || source.charAt(position) == 'E')) {
                position++;
                if (position < source.length() && (source.charAt(position) == '+' || source.charAt(position) == '-')) {
                    position++;
                }
                while (position < source.length() && Character.isDigit(source.charAt(position))) position++;
            }
            String text = source.substring(start, position);
            if (position < source.length() && (source.charAt(position) == 'f' || source.charAt(position) == 'F')) {
                position++;
            }
            try {
                return new Constant(Double.parseDouble(text));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("invalid number '" + text + "'");
            }
        }

        private boolean peek(String token) {
            skipWhitespace();
            return source.startsWith(token, position);
        }

        private boolean accept(String token) {
            if (!peek(token)) return false;
            position += token.length();
            return true;
        }

        private void expect(String token) {
            if (!accept(token)) {
                throw new IllegalArgumentException("expected '" + token + "' at " + position);
            }
        }

        private void skipWhitespace() {
            while (position < source.length() && Character.isWhitespace(source.charAt(position))) position++;
        }
    }
}
