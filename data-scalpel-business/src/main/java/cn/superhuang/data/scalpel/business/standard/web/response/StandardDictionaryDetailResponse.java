package cn.superhuang.data.scalpel.business.standard.web.response;

public record StandardDictionaryDetailResponse(
        StandardDictionaryResponse dictionary,
        long itemCount,
        long fieldReferenceCount,
        long templateFieldReferenceCount
) {
}
