package io.jaorm.dsl.common;

import io.jaorm.entity.SqlColumn;

public interface EndSelect<T> extends EndJoin<T> {

    Order<T> orderBy(OrderType type, SqlColumn<T, ?> column);
}
