package io.github.ulisse1996.jaorm.entity.relationship;

import io.github.ulisse1996.jaorm.BaseDao;
import io.github.ulisse1996.jaorm.entity.Result;
import io.github.ulisse1996.jaorm.spi.QueriesService;
import io.github.ulisse1996.jaorm.spi.RelationshipService;

import java.util.Objects;
import java.util.function.BiConsumer;

public abstract class PreApplyEvent implements EntityEvent {

    @SuppressWarnings("unchecked")
    protected  <T> void doPreApply(T entity, BiConsumer<BaseDao<Object>, Object> consumer) {
        Class<T> klass = (Class<T>) entity.getClass();
        if (isDelegate(entity)) {
            klass = (Class<T>) getRealClass(klass);
        }
        Relationship<T> tree = RelationshipService.getInstance().getRelationships(klass);
        for (Relationship.Node<T> node : tree.getNodeSet()) {
            if (node.isCollection()) {
                node.getAsCollection(entity).forEach(i -> {
                    Objects.requireNonNull(i, "Collection can't contains null values !");
                    BaseDao<Object> baseDao = (BaseDao<Object>) QueriesService.getInstance().getBaseDao(i.getClass());
                    consumer.accept(baseDao, i);
                });
            } else if (node.isOpt()) {
                Result<Object> optional = node.getAsOpt(entity);
                if (optional.isPresent()) {
                    Object i = optional.get();
                    BaseDao<Object> baseDao = (BaseDao<Object>) QueriesService.getInstance().getBaseDao(i.getClass());
                    consumer.accept(baseDao, i);
                }
            } else {
                Object i = node.get(entity);
                BaseDao<Object> baseDao = (BaseDao<Object>) QueriesService.getInstance().getBaseDao(i.getClass());
                consumer.accept(baseDao, i);
            }
        }
    }
}