package cn.superhuang.datascalpel.taskengine.contract;



public record HealthResponse(
        String status,
        String sparkVersion,
        String sparkApplicationId,
        String master
) {
    public static HealthResponse live() {
        return new HealthResponse("UP", null, null, null);
    }
}
