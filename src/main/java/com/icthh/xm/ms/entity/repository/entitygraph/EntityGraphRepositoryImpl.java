package com.icthh.xm.ms.entity.repository.entitygraph;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;

import javax.persistence.EntityGraph;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.Subgraph;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

import static org.hibernate.jpa.QueryHints.HINT_LOADGRAPH;
import static org.springframework.data.jpa.repository.query.QueryUtils.toOrders;

@Slf4j
public class EntityGraphRepositoryImpl<T, I extends Serializable>
    extends SimpleJpaRepository<T, I> implements EntityGraphRepository<T, I> {

    private static final String GRAPH_DELIMETER = ".";
    private final EntityManager entityManager;

    private final Class<T> domainClass;

    public EntityGraphRepositoryImpl(JpaEntityInformation<T, I> entityInformation, EntityManager entityManager) {
        super(entityInformation, entityManager);

        this.entityManager = entityManager;
        this.domainClass = entityInformation.getJavaType();
    }

    public EntityGraphRepositoryImpl(Class<T> domainClass, EntityManager entityManager) {
        super(domainClass, entityManager);

        this.entityManager = entityManager;
        this.domainClass = domainClass;
    }

    @Override
    public T findOne(I id, List<String> embed) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> criteriaQuery = builder.createQuery(domainClass);
        Root<T> root = criteriaQuery.from(domainClass);
        criteriaQuery.where(builder.equal(root.get("id"), id));

        TypedQuery<T> query = entityManager
            .createQuery(criteriaQuery)
            .setHint(HINT_LOADGRAPH, createEntityGraph(embed));

        List<T> resultList = query.getResultList();
        if (CollectionUtils.isEmpty(resultList)) {
            return null;
        }
        return resultList.get(0);
    }

    @Override
    public List<T> findAll(String jpql, Map<String, Object> args, List<String> embed) {
        Query query = entityManager.createQuery(jpql);
        args.forEach(query::setParameter);
        query.setHint(HINT_LOADGRAPH, createEntityGraph(embed));
        return query.getResultList();
    }

    @Override
    public List<?> findAll(String jpql, Map<String, Object> args) {
        Query query = entityManager.createQuery(jpql);
        args.forEach(query::setParameter);
        return query.getResultList();
    }

    @Override
    public List<?> findAll(String jpql, Map<String, Object> args, Pageable pageable) {
        Query query = entityManager.createQuery(jpql);
        args.forEach(query::setParameter);
        if (pageable.isPaged()) {
            query.setFirstResult((int) pageable.getOffset());
            query.setMaxResults(pageable.getPageSize());
        }
        return query.getResultList();
    }

    private EntityGraph<T> createEntityGraph(List<String> embed) {
        EntityGraph<T> graph = entityManager.createEntityGraph(domainClass);
        if (CollectionUtils.isNotEmpty(embed)) {
            embed.forEach(f -> addAttributeNodes(f, graph));
        }
        return graph;
    }

    private static void addAttributeNodes(String fieldName, EntityGraph<?> graph) {
        int pos = fieldName.indexOf(GRAPH_DELIMETER);
        if (pos < 0) {
            graph.addAttributeNodes(fieldName);
            return;
        }

        String subgraphName = fieldName.substring(0, pos);
        Subgraph<?> subGraph = graph.addSubgraph(subgraphName);
        String nextFieldName = fieldName.substring(pos + 1);
        pos = nextFieldName.indexOf(GRAPH_DELIMETER);

        while (pos > 0) {
            subgraphName = nextFieldName.substring(0, pos);
            subGraph = subGraph.addSubgraph(subgraphName);
            nextFieldName = nextFieldName.substring(pos + 1);
            pos = nextFieldName.indexOf(GRAPH_DELIMETER);
        }

        subGraph.addAttributeNodes(nextFieldName);
    }

}
