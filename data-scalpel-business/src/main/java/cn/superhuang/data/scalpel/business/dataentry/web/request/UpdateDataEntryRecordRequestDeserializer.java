package cn.superhuang.data.scalpel.business.dataentry.web.request;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class UpdateDataEntryRecordRequestDeserializer extends ValueDeserializer<UpdateDataEntryRecordRequest> {
    @Override
    public UpdateDataEntryRecordRequest deserialize(JsonParser parser, DeserializationContext context) {
        CreateDataEntryRequestDeserializer.require(parser.currentToken(), JsonToken.START_OBJECT, parser,
                "编辑填报请求必须是 JSON 对象");
        Map<String, Object> key = null;
        Map<String, Object> values = null;
        Set<String> seen = new HashSet<>();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String name = parser.currentName();
            if (!seen.add(name)) throw DatabindException.from(parser, "编辑填报请求包含重复字段：" + name);
            parser.nextToken();
            if ("key".equals(name)) key = CreateDataEntryRequestDeserializer.readUniqueObject(parser, "key");
            else if ("values".equals(name)) values = CreateDataEntryRequestDeserializer.readUniqueObject(parser, "values");
            else throw DatabindException.from(parser, "编辑填报请求包含未知字段：" + name);
        }
        return new UpdateDataEntryRecordRequest(key, values);
    }
}
