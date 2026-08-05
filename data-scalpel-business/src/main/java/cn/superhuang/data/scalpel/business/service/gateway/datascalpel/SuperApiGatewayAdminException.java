package cn.superhuang.data.scalpel.business.service.gateway.datascalpel;

final class SuperApiGatewayAdminException extends RuntimeException {

    private final int status;
    private final String problemCode;

    SuperApiGatewayAdminException(int status, String problemCode, String message) {
        super(message);
        this.status = status;
        this.problemCode = problemCode;
    }

    int status() {
        return status;
    }

    String problemCode() {
        return problemCode;
    }

    boolean isNotFound() {
        return status == 404;
    }

    boolean isConflict() {
        return status == 409;
    }
}
