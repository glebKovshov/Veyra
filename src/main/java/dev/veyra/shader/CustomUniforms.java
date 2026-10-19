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
 * <p>Scalar float/int/bool results are supported. Vector definitions, unknown inputs (for example
 * {@code biome}) and malformed expressions are kept as unavailable with a reason, so programs that declare
 * them fail with a clear diagnostic instead of receiving fake values.
 */
public final class CustomUniforms {
    public static final CustomUniforms EMPTY = new CustomUniforms(List.of(), Map.of());
    /** Built-in scalar inputs an expression may reference; vector inputs are exposed by component. */
    public static final Set<String> BUILT_IN_INPUTS = Set.of(
            "viewWidth", "viewHeight", "aspectRatio", "frameTimeCounter", "frameTime", "frameCounter",
            "sunAngle", "rainStrength", "wetness", "thunderStrength", "worldTime", "worldDay", "moonPhase",
            "bedrockLevel", "cloudHeight", "endFlashIntensity", "eyeAltitude", "screenBrightness", "blindness",
            "nightVision", "darknessFactor", "isEyeInWater", "isSpectator", "is_sneaking", "is_sprinting",
            "is_on_ground", "is_hurt", "is_invisible", "is_burning",
            "cameraPosition.x", "cameraPosition.y", "cameraPosition.z",
            "previousCameraPosition.x", "previousCameraPosition.y", "previousCameraPosition.z",
            "eyeBrightness.x", "eyeBrightness.y", "eyeBrightnessSmooth.x", "eyeBrightnessSmooth.y");
    private static final Pattern DEFINITION = Pattern.compile(
            "(uniform|variable)\\.(float|int|bool|vec2|vec3|vec4)\\.([A-Za-z_][A-Za-z0-9_]*)\\s*[=:]\\s*(.*)",
            Pattern.DOTALL);
    private static final int MAX_EXPRESSION_LENGTH = 16 * 1024;
    private static final int MAX_DEFINITIONS = 1024;
    private static volatile CustomUniforms active = EMPTY;

    public enum Type {
        FLOAT("float"), INT("int"), BOOL("bool");

        private final String glsl;

        Type(String glsl) {
            this.glsl = glsl;
        }

        public String glsl() {
            return glsl;
        }
    }

    /** A uniform exposed to shaders. */
    public record Uniform(String name, Type type) {
    }

    private record Definition(String name, Type type, boolean uniform, Node expression) {
    }

    private final List<Definition> definitions;
    private final Map<String, String> unavailable;
    private final Map<String, Double> values = new HashMap<>();

    private CustomUniforms(List<Definition> definitions, Map<String, String> unavailable) {
        this.definitions = definitions;
        this.unavailable = unavailable;
    }

    public static CustomUniforms active() {
        return active;
    }

    public static void activate(CustomUniforms uniforms) {
        active = uniforms == null ? EMPTY : uniforms;
    }

    /** Parses preprocessed shaders.properties text, keeping definition order. */
    public static CustomUniforms parse(String properties) {
        List<Definition> definitions = new ArrayList<>();
        Map<String, String> unavailable = new LinkedHashMap<>();
        Map<String, Type> known = new HashMap<>();
        for (String line : logicalLines(properties == null ? "" : properties)) {
            Matcher matcher = DEFINITION.matcher(line);
            if (!matcher.matches()) continue;
            boolean uniform = matcher.group(1).equals("uniform");
            String typeName = matcher.group(2);
            String name = matcher.group(3);
            String source = matcher.group(4).strip();
            known.remove(name);
            definitions.removeIf(definition -> definition.name().equals(name));
            unavailable.remove(name);
            if (BUILT_IN_INPUTS.contains(name)) {
                unavailable.put(name, "it would replace a built-in uniform");
                continue;
            }
            if (typeName.startsWith("vec")) {
                unavailable.put(name, typeName + " custom uniforms are not supported yet");
                continue;
            }
            if (source.length() > MAX_EXPRESSION_LENGTH || definitions.size() >= MAX_DEFINITIONS) {
                unavailable.put(name, "the expression exceeds Veyra's size limit");
                continue;
            }
            Node expression;
            try {
                expression = new Parser(source).parse();
                String missing = expression.missingInput(known.keySet());
                if (missing != null) {
                    unavailable.put(name, unavailable.containsKey(missing)
                            ? "it depends on unavailable '" + missing + "'"
                            : "input '" + missing + "' is not provided by Veyra");
                    continue;
                }
            } catch (IllegalArgumentException exception) {
                unavailable.put(name, "invalid expression: " + exception.getMessage());
                continue;
            }
            Type type = Type.valueOf(typeName.toUpperCase(java.util.Locale.ROOT));
            definitions.add(new Definition(name, type, uniform, expression));
            known.put(name, type);
        }
        return new CustomUniforms(List.copyOf(definitions), Collections.unmodifiableMap(unavailable));
    }

    /** Uniforms (not helper variables) that can be supplied to shaders, in definition order. */
    public List<Uniform> uniforms() {
        return definitions.stream().filter(Definition::uniform)
                .map(definition -> new Uniform(definition.name(), definition.type())).toList();
    }

    public Uniform uniform(String name) {
        for (Definition definition : definitions) {
            if (definition.uniform() && definition.name().equals(name)) {
                return new Uniform(definition.name(), definition.type());
            }
        }
        return null;
    }

    /** Explains why a declared custom uniform cannot be supplied, or returns null. */
    public String unavailableReason(String name) {
        return unavailable.get(name);
    }

    /** Re-evaluates all definitions. {@code inputs} returns null for unknown names. */
    public synchronized void evaluate(Function<String, Double> inputs, double frameTimeSeconds) {
        Map<String, Double> scope = new HashMap<>();
        Function<String, Double> resolver = name -> {
            Double local = scope.get(name);
            return local != null ? local : inputs.apply(name);
        };
        for (Definition definition : definitions) {
            double value;
            try {
                value = definition.expression().evaluate(resolver, frameTimeSeconds);
            } catch (ArithmeticException exception) {
                value = 0.0;
            }
            value = switch (definition.type()) {
                case FLOAT -> Double.isFinite(value) ? value : 0.0;
                case INT -> Double.isFinite(value) ? (double) (int) value : 0.0;
                case BOOL -> value != 0.0 ? 1.0 : 0.0;
            };
            scope.put(definition.name(), value);
        }
        values.clear();
        values.putAll(scope);
    }

    public synchronized float floatValue(String name) {
        return values.getOrDefault(name, 0.0).floatValue();
    }

    public synchronized int intValue(String name) {
        return values.getOrDefault(name, 0.0).intValue();
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

    private interface Node {
        double evaluate(Function<String, Double> inputs, double frameTime);

        /** Returns the first referenced name that is neither built in nor previously defined. */
        String missingInput(Set<String> defined);
    }

    private record Constant(double value) implements Node {
        public double evaluate(Function<String, Double> inputs, double frameTime) {
            return value;
        }

        public String missingInput(Set<String> defined) {
            return null;
        }
    }

    private record Reference(String name) implements Node {
        public double evaluate(Function<String, Double> inputs, double frameTime) {
            Double value = inputs.apply(name);
            return value == null ? 0.0 : value;
        }

        public String missingInput(Set<String> defined) {
            return defined.contains(name) || BUILT_IN_INPUTS.contains(name) ? null : name;
        }
    }

    private record Unary(char operator, Node operand) implements Node {
        public double evaluate(Function<String, Double> inputs, double frameTime) {
            double value = operand.evaluate(inputs, frameTime);
            return operator == '-' ? -value : value == 0.0 ? 1.0 : 0.0;
        }

        public String missingInput(Set<String> defined) {
            return operand.missingInput(defined);
        }
    }

    private record Binary(String operator, Node left, Node right) implements Node {
        public double evaluate(Function<String, Double> inputs, double frameTime) {
            double a = left.evaluate(inputs, frameTime);
            if (operator.equals("&&")) return a != 0.0 && right.evaluate(inputs, frameTime) != 0.0 ? 1.0 : 0.0;
            if (operator.equals("||")) return a != 0.0 || right.evaluate(inputs, frameTime) != 0.0 ? 1.0 : 0.0;
            double b = right.evaluate(inputs, frameTime);
            return switch (operator) {
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
            };
        }

        public String missingInput(Set<String> defined) {
            String missing = left.missingInput(defined);
            return missing != null ? missing : right.missingInput(defined);
        }
    }

    private static final class Call implements Node {
        private final String name;
        private final List<Node> arguments;
        private boolean smoothed;
        private double smoothValue;

        Call(String name, List<Node> arguments) {
            this.name = name;
            this.arguments = arguments;
            int count = arguments.size();
            boolean valid = switch (name) {
                case "sin", "cos", "tan", "asin", "acos", "abs", "floor", "ceil", "exp", "exp2", "log", "log2",
                     "frac", "fract", "sqrt", "signum", "sign", "round", "torad", "todeg", "radians", "degrees" -> count == 1;
                case "atan" -> count == 1 || count == 2;
                case "atan2", "pow", "fmod", "mod", "step" -> count == 2;
                case "min", "max" -> count >= 2;
                case "clamp", "between", "mix", "lerp", "smoothstep" -> count == 3;
                case "equals" -> count == 3;
                case "in" -> count >= 2;
                case "if" -> count >= 3 && count % 2 == 1;
                case "smooth" -> count >= 1 && count <= 4;
                default -> throw new IllegalArgumentException("unknown function '" + name + "'");
            };
            if (!valid) throw new IllegalArgumentException("wrong argument count for '" + name + "'");
        }

        private double arg(int index, Function<String, Double> inputs, double frameTime) {
            return arguments.get(index).evaluate(inputs, frameTime);
        }

        public double evaluate(Function<String, Double> inputs, double frameTime) {
            switch (name) {
                case "if": {
                    for (int i = 0; i + 1 < arguments.size(); i += 2) {
                        if (arg(i, inputs, frameTime) != 0.0) return arg(i + 1, inputs, frameTime);
                    }
                    return arg(arguments.size() - 1, inputs, frameTime);
                }
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
                case "log" -> Math.log(a);
                case "log2" -> Math.log(a) / Math.log(2.0);
                case "frac", "fract" -> a - Math.floor(a);
                case "sqrt" -> Math.sqrt(a);
                case "signum", "sign" -> Math.signum(a);
                case "round" -> Math.round(a);
                case "torad", "radians" -> Math.toRadians(a);
                case "todeg", "degrees" -> Math.toDegrees(a);
                case "pow" -> Math.pow(a, arg(1, inputs, frameTime));
                case "fmod" -> {
                    double b = arg(1, inputs, frameTime);
                    yield b == 0.0 ? 0.0 : a % b;
                }
                case "mod" -> {
                    double b = arg(1, inputs, frameTime);
                    yield b == 0.0 ? 0.0 : a - b * Math.floor(a / b);
                }
                case "step" -> arg(1, inputs, frameTime) < a ? 0.0 : 1.0;
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
         * OptiFine smooth([id,] value[, fadeUpSeconds[, fadeDownSeconds]]): exponential approach whose
         * fade time is the time to cover ~99% of the change (default one second). The id only names the
         * state in OptiFine; each call site keeps its own state here.
         */
        private double smooth(Function<String, Double> inputs, double frameTime) {
            int offset = arguments.size() == 4 ? 1 : 0;
            double target = arg(offset, inputs, frameTime);
            double fadeUp = arguments.size() > offset + 1 ? arg(offset + 1, inputs, frameTime) : 1.0;
            double fadeDown = arguments.size() > offset + 2 ? arg(offset + 2, inputs, frameTime) : fadeUp;
            if (!smoothed) {
                smoothed = true;
                smoothValue = target;
                return target;
            }
            double fade = target > smoothValue ? fadeUp : fadeDown;
            if (fade <= 0.0 || frameTime <= 0.0) {
                smoothValue = target;
            } else {
                double factor = Math.exp(-Math.log(100.0) * frameTime / fade);
                smoothValue = target + (smoothValue - target) * factor;
            }
            return smoothValue;
        }

        public String missingInput(Set<String> defined) {
            for (Node argument : arguments) {
                String missing = argument.missingInput(defined);
                if (missing != null) return missing;
            }
            return null;
        }
    }

    private record Ternary(Node condition, Node whenTrue, Node whenFalse) implements Node {
        public double evaluate(Function<String, Double> inputs, double frameTime) {
            return condition.evaluate(inputs, frameTime) != 0.0
                    ? whenTrue.evaluate(inputs, frameTime) : whenFalse.evaluate(inputs, frameTime);
        }

        public String missingInput(Set<String> defined) {
            String missing = condition.missingInput(defined);
            if (missing == null) missing = whenTrue.missingInput(defined);
            return missing != null ? missing : whenFalse.missingInput(defined);
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
                    return new Ternary(condition, whenTrue, ternary());
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
                left = new Binary(operator, left, binary(level + 1));
            }
        }

        private Node unary() {
            if (accept("-")) return new Unary('-', unary());
            if (accept("+")) return unary();
            if (peek("!") && !peek("!=")) {
                position++;
                return new Unary('!', unary());
            }
            return primary();
        }

        private Node primary() {
            skipWhitespace();
            if (accept("(")) {
                Node inner = ternary();
                expect(")");
                return inner;
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
                    return new Call(name, arguments);
                }
                return switch (name) {
                    case "true" -> new Constant(1.0);
                    case "false" -> new Constant(0.0);
                    case "pi", "PI" -> new Constant(Math.PI);
                    default -> new Reference(name);
                };
            }
            throw new IllegalArgumentException("unexpected '" + c + "' at " + position);
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
