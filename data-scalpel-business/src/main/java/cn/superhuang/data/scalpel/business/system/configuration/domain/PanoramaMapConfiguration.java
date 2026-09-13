package cn.superhuang.data.scalpel.business.system.configuration.domain;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.util.Set;
import io.swagger.v3.oas.annotations.media.Schema;

/** Typed value of the panorama.map STRING setting; URLs are fetched only by the user's map. */
@Schema(description = "panorama.map 字符串配置中保存的受控 JSON 结构。瓦片由用户浏览器直接向配置地址请求，DataScalpel 服务端不代理请求。")
public record PanoramaMapConfiguration(
        @Schema(description = "HTTP(S) XYZ 瓦片模板，最长 3000 个字符且必须包含 {z}、{x}、{y} 三个占位符；不得包含用户凭据或 URL fragment。空字符串表示不配置底图。") String url,
        @Schema(description = "展示给最终用户的地图提供方纯文本署名，去除首尾空白后最长 500 个字符。") String attribution,
        @Schema(description = "浏览器地图允许请求的最大缩放级别，闭区间 0 到 22。") int maxZoom) {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    public static PanoramaMapConfiguration parse(String raw) {
        try {
            JsonNode node = JSON.readTree(raw);
            if (!node.isObject() || node.size() != 3 || !node.has("url") || !node.has("attribution") || !node.has("maxZoom")
                    || !node.get("url").isString() || !node.get("attribution").isString() || !node.get("maxZoom").isIntegralNumber() || !node.get("maxZoom").canConvertToInt()) throw new IllegalArgumentException();
            String url = node.get("url").asString().trim(), attribution = node.get("attribution").asString().trim();
            int zoom = node.get("maxZoom").asInt(-1);
            if (zoom < 0 || zoom > 22 || attribution.length() > 500 || url.length() > 3000) throw new IllegalArgumentException();
            if (!url.isEmpty()) {
                if (!url.contains("{z}") || !url.contains("{x}") || !url.contains("{y}")) throw new IllegalArgumentException();
                URI uri = URI.create(url.replace("{z}", "0").replace("{x}", "0").replace("{y}", "0"));
                if (!Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) throw new IllegalArgumentException();
            }
            return new PanoramaMapConfiguration(url, attribution, zoom);
        } catch (RuntimeException e) { throw new IllegalArgumentException("全景地图配置无效：请填写 HTTP(S) XYZ 地址、纯文本署名和 0～22 的最大缩放级别"); }
    }
    public static String normalize(String raw) { return JSON.writeValueAsString(parse(raw)); }
}
