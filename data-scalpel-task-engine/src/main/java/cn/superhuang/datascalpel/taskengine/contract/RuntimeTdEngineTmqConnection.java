package cn.superhuang.datascalpel.taskengine.contract;

public record RuntimeTdEngineTmqConnection(
        String bootstrapServers,
        String username,
        String password,
        boolean useSsl
) {
    @Override
    public String toString() {
        return "RuntimeTdEngineTmqConnection["
                + "bootstrapServers=" + bootstrapServers
                + ", usernameConfigured=" + (username != null && !username.isBlank())
                + ", passwordConfigured=" + (password != null && !password.isBlank())
                + ", useSsl=" + useSsl
                + ']';
    }
}
