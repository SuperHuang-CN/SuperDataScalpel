package cn.superhuang.data.scalpel.business.dataentry.web.request;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class DeleteDataEntryBatchRequestDeserializer extends ValueDeserializer<DeleteDataEntryBatchRequest> {

    @Override
    public DeleteDataEntryBatchRequest deserialize(JsonParser parser, DeserializationContext context) {
        CreateDataEntryRequestDeserializer.require(
                parser.currentToken(), JsonToken.START_OBJECT, parser, "批量删除请求必须是 JSON 对象"
        );
        List<Map<String, Object>> keys = null;
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();
            if ("keys".equals(fieldName)) {
                if (parser.currentToken() != JsonToken.START_ARRAY) {
                    throw DatabindException.from(parser, "keys 必须是 JSON 数组");
                }
                keys = new ArrayList<>();
                int index = 0;
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    keys.add(CreateDataEntryRequestDeserializer.readUniqueObject(parser, "keys[" + index + "]"));
                    index++;
                }
            } else {
                parser.skipChildren();
            }
        }
        return new DeleteDataEntryBatchRequest(keys);
    }
}
