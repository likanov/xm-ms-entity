package com.icthh.xm.ms.entity.repository;

import com.icthh.xm.ms.entity.domain.XmEntity;
import com.icthh.xm.ms.entity.projection.XmEntityIdKeyTypeKey;
import com.icthh.xm.ms.entity.projection.XmEntityStateProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Repository interface accessible by LEP scripts.
 */
public interface XmEntityRepository {

    Optional<XmEntity> findById(Long aLong);

    /**
     * For backward compatibility in LEPs.
     *
     * Deprecated: use findById(Long aLong) instead.
     */
    @Deprecated
    XmEntity findOne(Long aLong);

//    <S extends XmEntity> Optional<S> findOne(Example<S> example);

    XmEntity findOne(Specification<XmEntity> spec);

    XmEntity findOne(Long aLong, List<String> embed);

    XmEntity findOneById(Long id);

    XmEntity findOneByIdForUpdate(@Param("id") Long id);

//    List<XmEntity> findAll();
//
//    List<XmEntity> findAll(Sort sort);
//
//    Page<XmEntity> findAll(Pageable pageable);

    List<XmEntity> findAllById(Iterable<Long> longs);

    /**
     * For backward compatibility in LEPs.
     *
     * Deprecated: use findAllById(Iterable<Long> longs) instead.
     */
    @Deprecated
    List<XmEntity> findAll(Iterable<Long> longs);

//    <S extends XmEntity> List<S> findAll(Example<S> example);
//
//    <S extends XmEntity> List<S> findAll(Example<S> example, Sort sort);
//
//    <S extends XmEntity> Page<S> findAll(Example<S> example, Pageable pageable);

    List<XmEntity> findAll(Specification<XmEntity> spec);

    Page<XmEntity> findAll(Specification<XmEntity> spec, Pageable pageable);

    List<XmEntity> findAll(Specification<XmEntity> spec, Sort sort);

    List<XmEntity> findAll(String jpql, Map<String, Object> args, List<String> embed);

    Page<XmEntity> findAllByTypeKeyIn(Pageable pageable, Set<String> typeKeys);

    XmEntityIdKeyTypeKey findOneIdKeyTypeKeyByKey(String key);

    XmEntityIdKeyTypeKey findOneIdKeyTypeKeyById(Long id);

    XmEntityStateProjection findStateProjectionByKey(String key);

    XmEntityStateProjection findStateProjectionById(Long id);

    XmEntity getOne(Long aLong);

    <S extends XmEntity> S save(S entity);

    <S extends XmEntity> S saveAndFlush(S entity);

    <S extends XmEntity> List<S> saveAll(Iterable<S> entities);

    /**
     * For backward compatibility in LEPs.
     *
     * Deprecated: use saveAll(Iterable<? extends XmEntity> entities) instead.
     */
    @Deprecated
    <S extends XmEntity> List<S> save(Iterable<S> entities);

    boolean existsById(Long aLong);

    /**
     * For backward compatibility in LEPs.
     *
     * Deprecated: use existsById(Long aLong) instead.
     */
    @Deprecated
    boolean exists(Long aLong);



//    Optional<XmEntityVersion> findVersionById(Long id);
//
//    boolean existsByTypeKeyAndNameIgnoreCase(String typeKey, String name);
//
//
//    void flush();
//
//    void deleteInBatch(Iterable<XmEntity> entities);
//
//    void deleteAllInBatch();
//
//
//
//

//
//    long count();
//
    void deleteById(Long aLong);
//
//    /**
//     * For backward compatibility in LEPs.
//     *
//     * Deprecated: use deleteById(Long aLong) instead.
//     */
//    @Deprecated
//    void delete(Long aLong);
//
    void delete(XmEntity entity);
//
//    void deleteAll(Iterable<? extends XmEntity> entities);
//
//    /**
//     * For backward compatibility in LEPs.
//     *
//     * Deprecated: use deleteAll(Iterable<? extends XmEntity> entities) instead.
//     */
//    @Deprecated
//    void delete(Iterable<? extends XmEntity> entities);
//
//    void deleteAll();
//
//    <S extends XmEntity> long count(Example<S> example);
//
//    <S extends XmEntity> boolean exists(Example<S> example);
//
//    long count(Specification<XmEntity> spec);

}
