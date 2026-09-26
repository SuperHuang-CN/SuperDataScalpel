import com.sun.source.doctree.*;
import com.sun.source.util.DocTrees;
import jdk.javadoc.doclet.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.util.ElementFilter;
import javax.tools.ToolProvider;
import javax.tools.Diagnostic;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

/** Build-only JDK doclet. No runtime dependency and no API method allowlist. */
public class SdkApiDocGenerator {
    private static Path output;
    private static String version;

    public static void main(String[] args) throws Exception {
        output = Path.of(args[1]); version = args[2];
        Files.deleteIfExists(output); // Never leave a stale document after a failed build.
        var tool = ToolProvider.getSystemDocumentationTool();
        try (var files = tool.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8);
             var sources = Files.walk(Path.of(args[0]))) {
            var units = files.getJavaFileObjectsFromPaths(sources.filter(p -> p.toString().endsWith(".java")).sorted().toList());
            boolean success = tool.getTask(null, files, null, ApiDoclet.class,
                    List.of("-quiet", "-public", "-encoding", "UTF-8", "-classpath", args[3]), units).call();
            if (!success) throw new IllegalStateException("SDK API documentation is incomplete; see Javadoc diagnostics");
        }
    }

    public static class ApiDoclet implements Doclet {
        private Reporter reporter;
        private DocTrees trees;
        private boolean valid = true;
        public void init(Locale locale, Reporter reporter) { this.reporter = reporter; }
        public String getName() { return "DataScalpel SDK API"; }
        public Set<? extends Option> getSupportedOptions() { return Set.of(); }
        public SourceVersion getSupportedSourceVersion() { return SourceVersion.RELEASE_21; }
        public boolean run(DocletEnvironment environment) {
            trees = environment.getDocTrees();
            var types = new TreeMap<String, TypeElement>();
            for (Element element : environment.getIncludedElements()) collect(element, types);
            var result = new ArrayList<Object>();
            for (TypeElement type : types.values().stream().sorted(Comparator.comparingInt(this::order)
                    .thenComparing(t -> t.getQualifiedName().toString())).toList()) {
                var doc = required(type);
                String group = tag(type, "apiGroup");
                String mode = tag(type, "apiMode");
                if (group.isBlank()) error(type, "Missing @apiGroup");
                if (mode.isBlank()) mode = "BOTH";
                if (!Set.of("BOTH", "BATCH", "STREAMING").contains(mode)) error(type, "Invalid @apiMode");
                var members = new ArrayList<Object>();
                for (Element member : type.getEnclosedElements()) {
                    if (!member.getModifiers().contains(Modifier.PUBLIC)) continue;
                    if (member instanceof ExecutableElement method && trees.getTree(member) != null
                            && method.getKind() == ElementKind.METHOD && !objectMethod(method)) {
                        var md = required(method);
                        var parameters = new ArrayList<Object>();
                        for (VariableElement param : method.getParameters()) {
                            String description = paramDoc(md, param.getSimpleName().toString());
                            if (description.isBlank()) error(method, "Missing @param " + param.getSimpleName());
                            parameters.add(map("name", param.getSimpleName().toString(), "type", parameterType(method, param), "description", description));
                        }
                        String signature = method.getSimpleName() + "(" + method.getParameters().stream()
                                .map(p -> parameterType(method, p) + " " + p.getSimpleName()).collect(Collectors.joining(", ")) + ")";
                        members.add(map("name", method.getSimpleName().toString(), "signature", signature,
                                "summary", body(md), "returnType", simple(method.getReturnType().toString()),
                                "returns", returns(md), "parameters", parameters,
                                "example", tag(method, "apiExample"), "note", tag(method, "apiNote"),
                                "deprecated", environment.getElementUtils().isDeprecated(method)));
                    } else if (member.getKind() == ElementKind.ENUM_CONSTANT || member.getKind() == ElementKind.FIELD) {
                        members.add(map("name", member.getSimpleName().toString(), "signature", member.getSimpleName().toString(),
                                "summary", body(required(member)), "returnType", simple(member.asType().toString()),
                                "returns", "", "parameters", List.of(), "example", "", "note", "", "deprecated", environment.getElementUtils().isDeprecated(member)));
                    }
                }
                // Records describe their generated accessors once, via the component @param documentation.
                for (var component : type.getRecordComponents()) {
                    String description = paramDoc(doc, component.getSimpleName().toString());
                    if (description.isBlank()) error(type, "Missing record @param " + component.getSimpleName());
                    members.add(map("name", component.getSimpleName().toString(), "signature", component.getSimpleName() + "()",
                            "summary", description, "returnType", simple(component.asType().toString()), "returns", description,
                            "parameters", List.of(), "example", "", "note", "", "deprecated", false));
                }
                result.add(map("name", type.getQualifiedName().toString(), "simpleName", simple(type.getQualifiedName().toString()),
                        "group", group, "mode", mode, "summary", body(doc), "example", tag(type, "apiExample"), "note", tag(type, "apiNote"),
                        "parents", type.getInterfaces().stream().map(Object::toString).toList(), "members", members));
            }
            if (!valid) return false;
            try {
                String content = json(result);
                String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((version + content).getBytes(StandardCharsets.UTF_8)));
                Files.createDirectories(output.getParent());
                Files.writeString(output, json(map("version", version, "fingerprint", fingerprint, "types", result)), StandardCharsets.UTF_8);
                return true;
            } catch (Exception e) { throw new IllegalStateException("Cannot generate SDK documentation", e); }
        }
        private void collect(Element element, Map<String, TypeElement> types) {
            if (!(element instanceof TypeElement type) || !type.getModifiers().contains(Modifier.PUBLIC)) return;
            types.put(type.getQualifiedName().toString(), type);
            ElementFilter.typesIn(type.getEnclosedElements()).forEach(t -> collect(t, types));
        }
        private boolean objectMethod(ExecutableElement method) {
            return (method.getSimpleName().contentEquals("equals") && method.getParameters().size() == 1 && method.getParameters().getFirst().asType().toString().equals("java.lang.Object"))
                    || (Set.of("hashCode", "toString").contains(method.getSimpleName().toString()) && method.getParameters().isEmpty());
        }
        private String parameterType(ExecutableElement method, VariableElement param) {
            String name = simple(param.asType().toString());
            return method.isVarArgs() && param.equals(method.getParameters().getLast()) ? name.replaceFirst("\\[\\]$", "...") : name;
        }
        private DocCommentTree required(Element element) {
            var doc = trees.getDocCommentTree(element);
            if (body(doc).isBlank()) error(element, "Missing SDK API description");
            return doc;
        }
        private void error(Element element, String message) { valid = false; reporter.print(Diagnostic.Kind.ERROR, element, message); }
        private int order(TypeElement type) {
            String value = tag(type, "apiOrder");
            if (value.isBlank()) return 1000;
            try { return Integer.parseInt(value); }
            catch (NumberFormatException e) { error(type, "Invalid @apiOrder"); return 1000; }
        }
        private String tag(Element element, String name) {
            var doc = trees.getDocCommentTree(element);
            if (doc != null) for (DocTree tree : doc.getBlockTags())
                if (tree instanceof UnknownBlockTagTree tag && tag.getTagName().equals(name)) return text(tag.getContent()).strip();
            return (name.equals("apiGroup") || name.equals("apiMode")) && element.getEnclosingElement() instanceof TypeElement parent ? tag(parent, name) : "";
        }
        private String body(DocCommentTree doc) { return doc == null ? "" : text(doc.getFullBody()).strip(); }
        private String paramDoc(DocCommentTree doc, String name) {
            if (doc != null) for (DocTree tree : doc.getBlockTags())
                if (tree instanceof ParamTree param && param.getName().toString().equals(name)) return text(param.getDescription()).strip();
            return "";
        }
        private String returns(DocCommentTree doc) {
            if (doc != null) for (DocTree tree : doc.getBlockTags()) if (tree instanceof ReturnTree ret) return text(ret.getDescription()).strip();
            return "";
        }
        private String text(List<? extends DocTree> trees) {
            return trees.stream().map(tree -> tree instanceof TextTree text ? text.getBody()
                    : tree instanceof LiteralTree literal ? literal.getBody().getBody()
                    : tree instanceof LinkTree link ? (link.getLabel().isEmpty() ? link.getReference().getSignature() : text(link.getLabel()))
                    : tree.toString()).collect(Collectors.joining()).replaceAll("</?p>", "\n");
        }
    }
    private static String simple(String name) { return name.replaceAll("\\b(?:[a-z_][\\w$]*\\.)+", ""); }
    private static Map<String, Object> map(Object... pairs) {
        var map = new LinkedHashMap<String, Object>();
        for (int i = 0; i < pairs.length; i += 2) map.put((String) pairs[i], pairs[i + 1]);
        return map;
    }
    private static String json(Object value) {
        if (value instanceof Map<?, ?> map) return map.entrySet().stream().map(e -> json(e.getKey()) + ":" + json(e.getValue())).collect(Collectors.joining(",", "{", "}"));
        if (value instanceof List<?> list) return list.stream().map(SdkApiDocGenerator::json).collect(Collectors.joining(",", "[", "]"));
        if (value instanceof Boolean) return value.toString();
        String text = Objects.toString(value, "");
        var out = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            if (c == '"' || c == '\\') out.append('\\').append(c);
            else if (c < 32) out.append(String.format("\\u%04x", (int) c));
            else out.append(c);
        }
        return out.append('"').toString();
    }
}
