package dev.ivchenko.webview.testing.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ivchenko.webview.foreign.NativeLibraries;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.foreign.AddressLayout;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * The reachability metadata shipped in a backend jar must list exactly the stubs its bindings create.
 *
 * <p>A native image can only perform a downcall, or accept an upcall, whose signature was registered when the image was
 * built. A missing entry does not fail {@code nativeCompile}; it fails at run time, inside the first window operation
 * that reaches the unregistered stub. This test turns that into a build failure: it initialises every binding class -
 * which opens the libraries but touches no display - and compares what {@link NativeLibraries} recorded with the JSON
 * the tracing agent produced. Extra entries fail too, so the file cannot rot.</p>
 */
public abstract class NativeImageMetadataContractTest {
    /** Classpath location of the module's {@code reachability-metadata.json}. */
    protected abstract String metadataPath();

    /** Every class whose static initialiser binds native functions or callbacks. */
    protected abstract List<Class<?>> bindingClasses();

    @Test
    void metadataListsExactlyTheBoundStubs() throws Exception {
        String metadataPath = this.metadataPath();
        // Static initialisers bind everything; no window, no toolkit initialisation.
        this.bindingClasses()
                .forEach(type -> {
                    try {
                        Class.forName(type.getName(), true, type.getClassLoader());
                    } catch (ClassNotFoundException e) {
                        throw new IllegalStateException(e);
                    }
                });

        JsonNode foreign = readMetadata(metadataPath).path("foreign");

        Set<String> registeredDowncalls = stream(foreign.path("downcalls"))
                .map(NativeImageMetadataContractTest::signature)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> boundDowncalls = NativeLibraries.downcalls().stream()
                .map(NativeImageMetadataContractTest::signature)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Assertions.assertEquals(boundDowncalls, registeredDowncalls,
                "foreign.downcalls in " + metadataPath + " is out of sync with the bindings");

        Set<String> registeredUpcalls = stream(foreign.path("directUpcalls"))
                .map(node -> node.path("class").asText() + "#" + node.path("method").asText()
                        + " " + signature(node))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> boundUpcalls = NativeLibraries.upcalls().stream()
                .map(target -> target.owner().getName() + "#" + target.method()
                        + " " + signature(target.descriptor()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Assertions.assertEquals(boundUpcalls, registeredUpcalls,
                "foreign.directUpcalls in " + metadataPath + " is out of sync with the bindings");
    }

    private static JsonNode readMetadata(String metadataPath) throws Exception {
        ClassLoader loader = NativeImageMetadataContractTest.class.getClassLoader();
        try (InputStream stream = loader.getResourceAsStream(metadataPath)) {
            Assertions.assertNotNull(stream, "Missing " + metadataPath);
            return new ObjectMapper().readTree(stream);
        }
    }

    /** {@code returnType(param, param, ...)} in the agent's own type vocabulary. */
    private static String signature(JsonNode node) {
        return node.path("returnType").asText() + "("
                + stream(node.path("parameterTypes")).map(JsonNode::asText).collect(Collectors.joining(", "))
                + ")";
    }

    private static String signature(FunctionDescriptor descriptor) {
        return descriptor.returnLayout().map(NativeImageMetadataContractTest::typeName).orElse("void") + "("
                + descriptor.argumentLayouts().stream()
                .map(NativeImageMetadataContractTest::typeName)
                .collect(Collectors.joining(", "))
                + ")";
    }

    /** The names {@code native-image-agent} writes: {@code jint}, {@code jlong}, {@code void*}. */
    private static String typeName(MemoryLayout layout) {
        return switch (layout) {
            case AddressLayout _ -> "void*";
            case ValueLayout.OfInt _ -> "jint";
            case ValueLayout.OfLong _ -> "jlong";
            case ValueLayout.OfByte _ -> "jbyte";
            case ValueLayout.OfShort _ -> "jshort";
            case ValueLayout.OfChar _ -> "jchar";
            case ValueLayout.OfBoolean _ -> "jboolean";
            case ValueLayout.OfFloat _ -> "jfloat";
            case ValueLayout.OfDouble _ -> "jdouble";
            case StructLayout struct -> struct.memberLayouts().stream()
                    .map(NativeImageMetadataContractTest::typeName)
                    .collect(Collectors.joining(",", "struct(", ")"));
            default -> throw new IllegalArgumentException("Unmapped layout: " + layout);
        };
    }

    private static Stream<JsonNode> stream(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false);
    }
}
