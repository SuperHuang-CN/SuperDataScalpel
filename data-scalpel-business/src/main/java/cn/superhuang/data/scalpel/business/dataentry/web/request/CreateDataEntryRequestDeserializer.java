package cn.superhuang.data.scalpel.business.dataentry.web.request;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

import java.util.LinkedHashMap;
import java.util.Map;

public final class CreateDataEntryRequestDeserializer extends ValueDeserializer<CreateDataEntryRequest> {

    @Override
    public CreateDataEntryRequest deserialize(JsonParser parser, DeserializationContext context) {
        require(parser.currentToken(), JsonToken.START_OBJECT, parser, "新增填报请求必须是 JSON 对象");
        Map<String, Object> values = null;
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();
            if ("values".equals(fieldName)) {
                values = readUniqueObject(parser, "values");
            } else {
                parser.skipChildren();
            }
        }
        return new CreateDataEntryRequest(values);
    }

    static Map<String, Object> readUniqueObject(JsonParser parser, String path) {
        require(parser.currentToken(), JsonToken.START_OBJECT, parser, path + " 必须是 JSON 对象");
        Map<String, Object> values = new LinkedHashMap<>();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String name = parser.currentName();
            if (values.containsKey(name)) {
                throw DatabindException.from(parser, path + " 包含重复字段：" + name);
            }
            parser.nextToken();
            values.put(name, parser.readValueAs(Object.class));
        }
        return values;
    }

    static void require(JsonToken actual, JsonToken expected, JsonParser parser, String message) {
        if (actual != expected) throw DatabindException.from(parser, message);
    }
}
