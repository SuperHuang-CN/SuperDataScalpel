package cn.superhuang.data.scalpel.admin.config;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.PropertyCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.util.ClassUtils;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.stream.Collectors;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    private static final String BEARER_AUTH = "bearerAuth";
    private static final String SCHEMA_DESCRIPTION = "$schema";
    private static final Map<String, String> SEARCH_PARAMETER_DESCRIPTIONS = Map.of(
            "search", "可选实体 Search DSL；支持比较、字符串匹配以及 AND/OR/NOT，省略或空白表示不增加客户端条件。具体可查询字段由当前接口返回实体决定。",
            "page", "从 0 开始的页码；省略时为 0。",
            "size", "每页数量；省略时为 20，最大 500。",
            "sort", "逗号分隔的标量字段排序；字段名前加 - 表示倒序，省略时按 id 倒序。"
    );

    static {
        // Jackson 3 JsonNode represents an arbitrary JSON value.  Treating it as a Java bean
        // exposes implementation predicates such as isNumber/isArray as fictitious API fields.
        SpringDocUtils.getConfig().replaceWithSchema(JsonNode.class,
                new io.swagger.v3.oas.models.media.Schema<>()
                        .description("任意 JSON 值；具体结构由所属接口或字段说明约束。"));
    }

    @Bean
    OpenAPI dataScalpelOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("DataScalpel 管理与数据治理 API")
                        .description("DataScalpel 管理端公开 REST 契约；接口说明、参数 Schema 和响应 Schema 同时供 Swagger UI 与系统 MCP 接口目录使用。")
                        .version("v1"))
                .components(new Components().addSecuritySchemes(BEARER_AUTH,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }

    /**
     * Contracts deliberately do not depend on Swagger annotations.  Surface their Jackson
     * property descriptions in the generated OpenAPI document so Swagger UI and System MCP
     * expose the same field semantics as Business DTOs.
     */
    @Bean
    PropertyCustomizer jsonPropertyDescriptionCustomizer() {
        return (schema, type) -> {
            if (schema.getDescription() != null && !schema.getDescription().isBlank()) {
                return schema;
            }
            if (type.getCtxAnnotations() == null) {
                return schema;
            }
            for (var annotation : type.getCtxAnnotations()) {
                if (annotation instanceof JsonPropertyDescription description
                        && !description.value().isBlank()) {
                    schema.setDescription(description.value());
                    break;
                }
            }
            return schema;
        };
    }

    @Bean
    ModelConverter jsonClassDescriptionModelConverter() {
        return (annotatedType, context, chain) -> {
            Class<?> javaType = rawClass(annotatedType.getType());
            if (javaType != null
                    && javaType.getEnclosingClass() != null
                    && javaType.getName().startsWith("cn.superhuang.data.scalpel.")) {
                annotatedType.name(nestedSchemaName(javaType));
            }
            var schema = chain.hasNext() ? chain.next().resolve(annotatedType, context, chain) : null;
            if (schema != null && javaType != null) {
                constrainDiscriminator(javaType, schema, context);
            }
            if (schema == null || (schema.getDescription() != null && !schema.getDescription().isBlank())
                    || javaType == null) {
                return schema;
            }
            JsonClassDescription description = javaType.getAnnotation(JsonClassDescription.class);
            if (description != null && !description.value().isBlank()) {
                schema.setDescription(description.value());
            }
            return schema;
        };
    }

    private static Class<?> rawClass(java.lang.reflect.Type type) {
        if (type instanceof Class<?> javaType) {
            return javaType;
        }
        if (type instanceof com.fasterxml.jackson.databind.JavaType jacksonType) {
            return jacksonType.getRawClass();
        }
        if (type instanceof java.lang.reflect.ParameterizedType parameterizedType
                && parameterizedType.getRawType() instanceof Class<?> javaType) {
            return javaType;
        }
        return null;
    }

    /** JSON Schema validators do not interpret OpenAPI discriminator metadata as a constraint. */
    private static void constrainDiscriminator(Class<?> type, io.swagger.v3.oas.models.media.Schema<?> schema,
                                               io.swagger.v3.core.converter.ModelConverterContext context) {
        if (schema.get$ref() != null && schema.get$ref().startsWith("#/components/schemas/")) {
            schema = context.getDefinedModels().get(schema.get$ref().substring("#/components/schemas/".length()));
        }
        if (schema == null) return;
        for (Class<?> parent : type.getInterfaces()) {
            JsonTypeInfo info = parent.getAnnotation(JsonTypeInfo.class);
            JsonSubTypes subtypes = parent.getAnnotation(JsonSubTypes.class);
            if (info == null || subtypes == null || info.use() != JsonTypeInfo.Id.NAME
                    || info.property().isBlank()
                    || !(info.include() == JsonTypeInfo.As.PROPERTY || info.include() == JsonTypeInfo.As.EXISTING_PROPERTY)) continue;
            var names = Arrays.stream(subtypes.value()).filter(subtype -> subtype.value() == type)
                    .flatMap(subtype -> java.util.stream.Stream.concat(java.util.stream.Stream.of(subtype.name()),
                            Arrays.stream(subtype.names())))
                    .filter(name -> !name.isBlank()).distinct().toList();
            if (names.isEmpty()) continue;
            schema.addProperty(info.property(), new io.swagger.v3.oas.models.media.StringSchema()._enum(names));
            if (schema.getRequired() == null || !schema.getRequired().contains(info.property())) {
                schema.addRequiredItem(info.property());
            }
        }
    }

    /**
     * {@code @ParameterObject} expands SearchRequest into four query parameters after property
     * customizers run, so copy the shared contract descriptions onto the final parameters.
     */
    @Bean
    OpenApiCustomizer searchRequestParameterDescriptions() {
        Map<String, Map<String, String>> declaredDescriptions = discoverDeclaredPropertyDescriptions();
        return openApi -> {
            openApi.getPaths().values().forEach(pathItem -> pathItem.readOperations().forEach(operation -> {
            if (operation.getParameters() == null) {
                return;
            }
            var queryParameters = operation.getParameters().stream()
                    .filter(parameter -> "query".equals(parameter.getIn()))
                    .filter(parameter -> SEARCH_PARAMETER_DESCRIPTIONS.containsKey(parameter.getName()))
                    .toList();
            if (queryParameters.size() != SEARCH_PARAMETER_DESCRIPTIONS.size()) {
                return;
            }
            queryParameters.forEach(parameter -> {
                if (parameter.getDescription() == null || parameter.getDescription().isBlank()) {
                    parameter.setDescription(SEARCH_PARAMETER_DESCRIPTIONS.get(parameter.getName()));
                }
            });
            }));
            if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
                return;
            }
            openApi.getComponents().getSchemas().forEach((schemaName, schema) -> {
                Map<String, String> descriptions = declaredDescriptions.get(schemaName);
                if (descriptions != null) {
                    String schemaDescription = descriptions.get(SCHEMA_DESCRIPTION);
                    if (schemaDescription != null
                            && (schema.getDescription() == null || schema.getDescription().isBlank())) {
                        schema.setDescription(schemaDescription);
                    }
                }
                if (schema.getDescription() == null || schema.getDescription().isBlank()) {
                    if (schemaName.startsWith("PageResponse")) {
                        schema.setDescription("实体列表的分页查询结果；包含当前页数据、匹配总数、总页数和实际分页位置。");
                    } else if (schema.getDiscriminator() != null) {
                        schema.setDescription("多态契约；必须通过判别字段选择一种具体结构，并同时满足对应子类型的字段要求。");
                    } else if ("StreamingResponseBody".equals(schemaName)) {
                        schema.setDescription("Spring MVC 流式响应体；实际媒体类型、文件名和字节内容由所属下载接口定义。");
                    }
                }
                if (descriptions != null) {
                    restorePropertyDescriptions(schema, descriptions,
                            Collections.newSetFromMap(new IdentityHashMap<>()));
                }
            });
        };
    }

    /**
     * Polymorphic schemas often place the concrete record properties in an inline allOf schema.
     * Walk only the inline schema tree owned by the current component; following references could
     * incorrectly apply this component's descriptions to another component.
     */
    private static void restorePropertyDescriptions(
            io.swagger.v3.oas.models.media.Schema<?> schema,
            Map<String, String> descriptions,
            Set<io.swagger.v3.oas.models.media.Schema<?>> visited
    ) {
        if (schema == null || !visited.add(schema)) {
            return;
        }
        if (schema.getProperties() != null) {
            descriptions.forEach((propertyName, description) -> {
                var property = (io.swagger.v3.oas.models.media.Schema<?>) schema.getProperties().get(propertyName);
                if (property != null && (property.getDescription() == null || property.getDescription().isBlank())) {
                    property.setDescription(description);
                }
            });
        }
        restorePropertyDescriptions(schema.getItems(), descriptions, visited);
        restorePropertyDescriptions(schema.getAllOf(), descriptions, visited);
        restorePropertyDescriptions(schema.getOneOf(), descriptions, visited);
        restorePropertyDescriptions(schema.getAnyOf(), descriptions, visited);
    }

    private static void restorePropertyDescriptions(
            java.util.List<io.swagger.v3.oas.models.media.Schema> schemas,
            Map<String, String> descriptions,
            Set<io.swagger.v3.oas.models.media.Schema<?>> visited
    ) {
        if (schemas != null) {
            schemas.forEach(schema -> restorePropertyDescriptions(schema, descriptions, visited));
        }
    }

    /**
     * Swagger's polymorphic converter can replace a property schema after PropertyCustomizer
     * has copied its annotation, losing the description on oneOf properties.  Read the same
     * source annotations once and restore only descriptions that are still absent in the final
     * OpenAPI tree.  Ambiguous duplicate class/property names are deliberately ignored.
     */
    private static Map<String, Map<String, String>> discoverDeclaredPropertyDescriptions() {
        var resolver = new PathMatchingResourcePatternResolver(OpenApiConfiguration.class.getClassLoader());
        var metadataReaders = new CachingMetadataReaderFactory(resolver);
        Map<String, Map<String, Set<String>>> candidates = new HashMap<>();
        try {
            for (var resource : resolver.getResources("classpath*:cn/superhuang/data/scalpel/**/*.class")) {
                String className = metadataReaders.getMetadataReader(resource).getClassMetadata().getClassName();
                if (className.endsWith("package-info") || className.endsWith("module-info")) {
                    continue;
                }
                Class<?> type = ClassUtils.forName(className, OpenApiConfiguration.class.getClassLoader());
                collectDeclaredDescriptions(candidates, type);
            }
        } catch (IOException | ClassNotFoundException exception) {
            throw new IllegalStateException("无法读取 OpenAPI 字段说明", exception);
        }
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        candidates.forEach((schemaName, properties) -> properties.forEach((propertyName, descriptions) -> {
            if (descriptions.size() == 1) {
                result.computeIfAbsent(schemaName, ignored -> new LinkedHashMap<>())
                        .put(propertyName, descriptions.iterator().next());
            }
        }));
        return result;
    }

    private static void collectDeclaredDescriptions(
            Map<String, Map<String, Set<String>>> candidates,
            Class<?> type
    ) {
        Set<String> schemaNames = new LinkedHashSet<>();
        schemaNames.add(type.getSimpleName());
        if (type.getEnclosingClass() != null) {
            schemaNames.add(nestedSchemaName(type));
        }
        for (String schemaName : schemaNames) {
            collectSchemaDescription(candidates, schemaName, type);
            for (var field : type.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    collectDescription(candidates, schemaName, field.getName(), field);
                }
            }
            for (Method method : type.getDeclaredMethods()) {
                if (!Modifier.isStatic(method.getModifiers()) && method.getParameterCount() == 0) {
                    collectDescription(candidates, schemaName, propertyName(method), method);
                }
            }
            collectDiscriminatorDescription(candidates, schemaName, type);
        }
    }

    private static String nestedSchemaName(Class<?> type) {
        StringBuilder result = new StringBuilder(type.getSimpleName());
        for (Class<?> enclosing = type.getEnclosingClass(); enclosing != null; enclosing = enclosing.getEnclosingClass()) {
            result.insert(0, enclosing.getSimpleName());
        }
        return result.toString();
    }

    private static void collectSchemaDescription(
            Map<String, Map<String, Set<String>>> candidates,
            String schemaName,
            Class<?> type
    ) {
        String description = null;
        JsonClassDescription jackson = type.getAnnotation(JsonClassDescription.class);
        if (jackson != null && !jackson.value().isBlank()) {
            description = jackson.value();
        }
        io.swagger.v3.oas.annotations.media.Schema swagger =
                type.getAnnotation(io.swagger.v3.oas.annotations.media.Schema.class);
        if (swagger != null && !swagger.description().isBlank()) {
            description = swagger.description();
        }
        if (description != null) {
            candidates.computeIfAbsent(schemaName, ignored -> new HashMap<>())
                    .computeIfAbsent(SCHEMA_DESCRIPTION, ignored -> new LinkedHashSet<>())
                    .add(description);
        }
    }

    private static void collectDiscriminatorDescription(
            Map<String, Map<String, Set<String>>> candidates,
            String schemaName,
            Class<?> type
    ) {
        JsonTypeInfo typeInfo = type.getAnnotation(JsonTypeInfo.class);
        JsonSubTypes subTypes = type.getAnnotation(JsonSubTypes.class);
        if (typeInfo == null || subTypes == null || typeInfo.property().isBlank()) {
            return;
        }
        String values = Arrays.stream(subTypes.value())
                .map(JsonSubTypes.Type::name)
                .filter(name -> !name.isBlank())
                .distinct()
                .collect(Collectors.joining("、"));
        if (values.isBlank()) {
            return;
        }
        String description = "多态结构判别字段；决定其余字段采用的具体结构。可选值：" + values + "。";
        Map<String, Set<String>> properties = candidates.computeIfAbsent(schemaName, ignored -> new HashMap<>());
        properties.computeIfAbsent(typeInfo.property(), ignored -> new LinkedHashSet<>());
        if (properties.get(typeInfo.property()).isEmpty()) {
            properties.get(typeInfo.property()).add(description);
        }
    }

    private static void collectDescription(
            Map<String, Map<String, Set<String>>> candidates,
            String schemaName,
            String propertyName,
            AnnotatedElement element
    ) {
        String description = null;
        JsonPropertyDescription jackson = element.getAnnotation(JsonPropertyDescription.class);
        if (jackson != null && !jackson.value().isBlank()) {
            description = jackson.value();
        }
        io.swagger.v3.oas.annotations.media.Schema swagger =
                element.getAnnotation(io.swagger.v3.oas.annotations.media.Schema.class);
        if (swagger != null && !swagger.description().isBlank()) {
            description = swagger.description();
        }
        if (description != null) {
            candidates.computeIfAbsent(schemaName, ignored -> new HashMap<>())
                    .computeIfAbsent(propertyName, ignored -> new LinkedHashSet<>())
                    .add(description);
        }
    }

    private static String propertyName(Method method) {
        String name = method.getName();
        if (name.startsWith("get") && name.length() > 3) {
            return Character.toLowerCase(name.charAt(3)) + name.substring(4);
        }
        if (name.startsWith("is") && name.length() > 2
                && (method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class)) {
            return Character.toLowerCase(name.charAt(2)) + name.substring(3);
        }
        return name;
    }
}
