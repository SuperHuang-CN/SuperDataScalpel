package cn.superhuang.data.scalpel.business.dsh.service;
import org.springframework.http.HttpStatusCode;
import cn.superhuang.data.scalpel.web.error.CodedProblemException;
public final class DshProblems {
    private DshProblems() {}
    public static CodedProblemException error(int status, String code, String message) {
        return new CodedProblemException(HttpStatusCode.valueOf(status),code,message);
    }
}
