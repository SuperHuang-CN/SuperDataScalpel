package cn.superhuang.data.scalpel.engine.query;

import cn.superhuang.data.scalpel.dialect.query.StandardQuery;

record CompiledServiceRequest(int pageNo, int pageSize, StandardQuery query) {
}
