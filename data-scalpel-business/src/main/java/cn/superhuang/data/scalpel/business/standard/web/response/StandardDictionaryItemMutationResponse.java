package cn.superhuang.data.scalpel.business.standard.web.response;

public record StandardDictionaryItemMutationResponse(
        int dictionaryVersion,
        StandardDictionaryItemResponse item
) {
}
