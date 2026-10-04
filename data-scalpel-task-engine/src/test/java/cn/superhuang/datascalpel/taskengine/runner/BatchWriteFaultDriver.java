package cn.superhuang.datascalpel.taskengine.runner;

import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/** Delegates to a real DB; injects only acknowledgement and cleanup faults, never mocks writes. */
public final class BatchWriteFaultDriver implements Driver {
    static volatile boolean loseCommitAcknowledgement;
    static volatile boolean failCleanup;
    static volatile boolean interruptAfterDelete;
    static final Set<String> ownedTables = ConcurrentHashMap.newKeySet();
    static { try { DriverManager.registerDriver(new BatchWriteFaultDriver()); } catch (SQLException e) { throw new ExceptionInInitializerError(e); } }
    public Connection connect(String url, Properties properties) throws SQLException {
        if (!acceptsURL(url)) return null;
        Connection real=DriverManager.getConnection(url.substring("jdbc:write-fault:".length()),properties);
        boolean[] targetTouched={false};
        return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(proxy,method,args)-> {
            if (method.getName().equals("commit") && targetTouched[0] && loseCommitAcknowledgement) {
                real.commit();
                throw new SQLException("intentional lost commit acknowledgement","08006");
            }
            Object result=invoke(real,method,args);
            if (result instanceof Statement statement) {
                String prepared=args!=null && args.length>0 && args[0] instanceof String text?text:null;
                Class<?> api=statement instanceof PreparedStatement?PreparedStatement.class:Statement.class;
                return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{api},(p,m,a)-> {
                    String sql=prepared!=null?prepared:a!=null && a.length>0 && a[0] instanceof String text?text:null;
                    if (m.getName().startsWith("execute") && sql!=null) {
                        if (sql.startsWith("DROP TABLE ") && failCleanup) throw new SQLException("intentional cleanup failure","08006");
                        if ((sql.startsWith("INSERT INTO ") || sql.startsWith("DELETE FROM ")) && sql.contains("\"dswr_")) targetTouched[0]=true;
                        Object executed=invoke(statement,m,a);
                        if (interruptAfterDelete && sql.startsWith("DELETE FROM ") && sql.contains("\"dswr_")) Thread.currentThread().interrupt();
                        if (sql.startsWith("CREATE TABLE ")) ownedTables.add(sql.substring(13,sql.indexOf(" ",13)));
                        if (sql.startsWith("DROP TABLE ")) ownedTables.remove(sql.substring(11));
                        return executed;
                    }
                    return invoke(statement,m,a);
                });
            }
            return result;
        });
    }
    private static Object invoke(Object instance,Method method,Object[] args)throws Throwable {
        try { return method.invoke(instance,args); } catch(InvocationTargetException e){throw e.getCause();}
    }
    public boolean acceptsURL(String url){return url!=null && url.startsWith("jdbc:write-fault:");}
    public DriverPropertyInfo[] getPropertyInfo(String url,Properties properties){return new DriverPropertyInfo[0];}
    public int getMajorVersion(){return 1;}
    public int getMinorVersion(){return 0;}
    public boolean jdbcCompliant(){return false;}
    public Logger getParentLogger(){return Logger.getGlobal();}
}
