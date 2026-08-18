package cn.superhuang.datascalpel.taskengine.tdengine.tmq;

import com.taosdata.jdbc.tmq.ConsumerRecords;
import com.taosdata.jdbc.tmq.TaosConsumer;
import com.taosdata.jdbc.tmq.TopicPartition;

import java.io.Serializable;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

interface TdEngineTmqConsumer extends AutoCloseable {
    void subscribe(Collection<String> topics) throws SQLException;

    void unsubscribe() throws SQLException;

    ConsumerRecords<Map<String, Object>> poll(Duration timeout) throws SQLException;

    void seek(TopicPartition partition, long offset) throws SQLException;

    long position(TopicPartition partition) throws SQLException;

    Map<TopicPartition, Long> beginningOffsets(String topic) throws SQLException;

    Map<TopicPartition, Long> endOffsets(String topic) throws SQLException;

    Set<TopicPartition> assignment() throws SQLException;

    @Override
    void close() throws SQLException;
}

@FunctionalInterface
interface TdEngineTmqConsumerFactory extends Serializable {
    TdEngineTmqConsumer create(TdEngineTmqOptions options, String groupId, String clientId)
            throws SQLException;
}

enum DriverTdEngineTmqConsumerFactory implements TdEngineTmqConsumerFactory {
    INSTANCE;

    @Override
    public TdEngineTmqConsumer create(TdEngineTmqOptions options, String groupId, String clientId)
            throws SQLException {
        return new DriverTdEngineTmqConsumer(options.createDriverConsumer(groupId, clientId));
    }
}

final class DriverTdEngineTmqConsumer implements TdEngineTmqConsumer {
    private final TaosConsumer<Map<String, Object>> delegate;

    DriverTdEngineTmqConsumer(TaosConsumer<Map<String, Object>> delegate) {
        this.delegate = delegate;
    }

    @Override
    public void subscribe(Collection<String> topics) throws SQLException {
        delegate.subscribe(topics);
    }

    @Override
    public void unsubscribe() throws SQLException {
        delegate.unsubscribe();
    }

    @Override
    public ConsumerRecords<Map<String, Object>> poll(Duration timeout) throws SQLException {
        return delegate.poll(timeout);
    }

    @Override
    public void seek(TopicPartition partition, long offset) throws SQLException {
        delegate.seek(partition, offset);
    }

    @Override
    public long position(TopicPartition partition) throws SQLException {
        return delegate.position(partition);
    }

    @Override
    public Map<TopicPartition, Long> beginningOffsets(String topic) throws SQLException {
        return delegate.beginningOffsets(topic);
    }

    @Override
    public Map<TopicPartition, Long> endOffsets(String topic) throws SQLException {
        return delegate.endOffsets(topic);
    }

    @Override
    public Set<TopicPartition> assignment() throws SQLException {
        return delegate.assignment();
    }

    @Override
    public void close() throws SQLException {
        delegate.close();
    }
}
