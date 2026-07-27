package cn.superhuang.datascalpel.taskengine.canvas;

public interface CanvasNodeIssueSink {

    void error(String code, String message, String path);

    void warning(String code, String message, String path);

    boolean hasErrors();
}
