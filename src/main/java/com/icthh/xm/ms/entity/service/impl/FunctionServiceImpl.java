package com.icthh.xm.ms.entity.service.impl;

import com.icthh.xm.commons.exceptions.EntityNotFoundException;
import com.icthh.xm.ms.entity.domain.FunctionContext;
import com.icthh.xm.ms.entity.domain.XmEntity;
import com.icthh.xm.ms.entity.domain.ext.IdOrKey;
import com.icthh.xm.ms.entity.domain.spec.FunctionSpec;
import com.icthh.xm.ms.entity.projection.XmEntityIdKeyTypeKey;
import com.icthh.xm.ms.entity.projection.XmEntityStateProjection;
import com.icthh.xm.ms.entity.security.access.DynamicPermissionCheckService;
import com.icthh.xm.ms.entity.service.FunctionContextService;
import com.icthh.xm.ms.entity.service.FunctionExecutorService;
import com.icthh.xm.ms.entity.service.FunctionService;
import com.icthh.xm.ms.entity.service.XmEntityService;
import com.icthh.xm.ms.entity.service.XmEntitySpecService;
import com.icthh.xm.ms.entity.util.CustomCollectionUtils;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The {@link FunctionServiceImpl} class.
 */
@Slf4j
@Transactional
@Service("functionService")
public class FunctionServiceImpl implements FunctionService {

    private final XmEntitySpecService xmEntitySpecService;
    private final XmEntityService xmEntityService;
    private final FunctionExecutorService functionExecutorService;
    private final FunctionContextService functionContextService;
    private final DynamicPermissionCheckService dynamicPermissionCheckService;

    public FunctionServiceImpl(XmEntitySpecService xmEntitySpecService,
                               XmEntityService xmEntityService,
                               FunctionExecutorService functionExecutorService,
                               FunctionContextService functionContextService, DynamicPermissionCheckService dynamicPermissionCheckService) {
        this.xmEntitySpecService = xmEntitySpecService;
        this.xmEntityService = xmEntityService;
        this.functionExecutorService = functionExecutorService;
        this.functionContextService = functionContextService;
        this.dynamicPermissionCheckService = dynamicPermissionCheckService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FunctionContext execute(final String functionKey, final Map<String, Object> functionInput) {

        Objects.requireNonNull(functionKey, "functionKey can't be null");
        Map<String, Object> vInput = CustomCollectionUtils.emptyIfNull(functionInput);

        dynamicPermissionCheckService.checkContextPermission(DynamicPermissionCheckService.FeatureContext.FUNCTION,
            "FUNCTION.CALL", functionKey);

        FunctionSpec functionSpec = findFunctionSpec(functionKey, null);

        // execute function
        Map<String, Object> data = functionExecutorService.execute(functionKey, vInput);

        return processFunctionResult(functionKey,  data, functionSpec);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FunctionContext execute(final String functionKey,
                                   final IdOrKey idOrKey,
                                   final Map<String, Object> functionInput) {
        Objects.requireNonNull(functionKey, "functionKey can't be null");
        Objects.requireNonNull(idOrKey, "idOrKey can't be null");

        Map<String, Object> vInput = CustomCollectionUtils.emptyIfNull(functionInput);

        dynamicPermissionCheckService.checkContextPermission(DynamicPermissionCheckService.FeatureContext.FUNCTION,
            "XMENTITY.FUNCTION.EXECUTE", functionKey);

        // get type key
        XmEntityStateProjection projection = xmEntityService.findStateProjectionById(idOrKey).orElseThrow(
            () -> new EntityNotFoundException("XmEntity with idOrKey [" + idOrKey + "] not found")
        );

        // validate that current XmEntity has function
        FunctionSpec functionSpec = findFunctionSpec(functionKey, projection);

        //TODO check that Entity.State compliant with functionSpec.getAllowedStateKeys()!!!! in any provided

        // execute function
        Map<String, Object> data = functionExecutorService.execute(functionKey, idOrKey, projection.getTypeKey(), vInput);

        return processFunctionResult(functionKey, idOrKey, data, functionSpec);

    }

    private FunctionSpec findFunctionSpec(String functionKey, XmEntityIdKeyTypeKey projection) {
        if (projection == null) {
            return xmEntitySpecService.findFunction(functionKey).orElseThrow(
                () -> new IllegalArgumentException("Function not found, function key: " + functionKey));
        }
        return xmEntitySpecService.findFunction(projection.getTypeKey(), functionKey).orElseThrow(
            () -> new IllegalArgumentException("Function not found for entity type key " + projection.getTypeKey()
                + " and function key: " + functionKey)
        );
    }

    private FunctionContext processFunctionResult(String functionKey,
                                                  Map<String, Object> data,
                                                  FunctionSpec functionSpec) {
        return processFunctionResult(functionKey, null, data, functionSpec);
    }

    private FunctionContext processFunctionResult(String functionKey,
                                  IdOrKey idOrKey,
                                  Map<String, Object> data,
                                  FunctionSpec functionSpec) {
        FunctionContext functionResult = toFunctionContext(functionKey, idOrKey, data, functionSpec);
        if (functionSpec.getSaveFunctionContext()) {
            return functionContextService.save(functionResult);
        }
        return functionResult;
    }

    private FunctionContext toFunctionContext(String functionKey, IdOrKey idOrKey,
                                              Map<String, Object> data,
                                              FunctionSpec functionSpec) {

        FunctionContext functionResult = new FunctionContext();
        // TODO review key & typeKey ...
        functionResult.setKey(functionKey + "-" + UUID.randomUUID().toString());
        functionResult.setTypeKey(functionKey);
        functionResult.setData(data);
        functionResult.setStartDate(Instant.now());
        functionResult.setUpdateDate(functionResult.getStartDate());
        if (functionSpec.getSaveFunctionContext()) {
            XmEntity xmEntity = (idOrKey != null) ? xmEntityService.findOne(idOrKey) : null;
            functionResult.setXmEntity(xmEntity);
        }
        functionResult.setOnlyData(functionSpec.getOnlyData());
        return functionResult;
    }

}
