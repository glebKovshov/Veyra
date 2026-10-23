package dev.veyra.shader;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Identifies unsupported Iris depth texture inputs without rejecting helper parameter names. */
final class DepthSamplerSupport {
    private static final Pattern UNSUPPORTED = Pattern.compile("\\b(depthtex(?:[3-9]|[1-9][0-9]+))\\b");

    private DepthSamplerSupport() { }

    static Optional<String> firstUnsupported(String source) {
        Matcher matcher = UNSUPPORTED.matcher(ShaderSource.maskBlockAndLineComments(source));
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }
}
