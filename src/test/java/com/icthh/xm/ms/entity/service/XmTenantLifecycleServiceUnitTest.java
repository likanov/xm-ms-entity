package com.icthh.xm.ms.entity.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.icthh.xm.commons.gen.model.Tenant;
import com.icthh.xm.commons.tenant.TenantContext;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.ms.entity.AbstractUnitTest;
import com.icthh.xm.ms.entity.config.ApplicationProperties;
import com.icthh.xm.ms.entity.domain.EntityState;
import com.icthh.xm.ms.entity.domain.XmEntity;
import com.icthh.xm.ms.entity.lep.LepXmEntityMsConstants;
import com.icthh.xm.ms.entity.service.XmTenantLifecycleService.ServiceInfo;
import com.icthh.xm.ms.entity.web.client.tenant.TenantClient;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class XmTenantLifecycleServiceUnitTest extends AbstractUnitTest {

    private static final String ENTITY_TYPE_KEY = "RESOURCE.XM-TENANT";
    private static final String SERVICE_NAME = "test";
    private static final String EXEC_ERROR = "Service call failed";
    private static final String SUCCESS_NAME = "success";

    private XmTenantLifecycleService xmTenantLifecycleService;
    private List<TenantClient> tenantClients = new ArrayList<>();
    private Map<String, Object> context = new HashMap<>();

    @Mock
    private ApplicationProperties applicationProperties;

    @Mock
    private com.icthh.xm.commons.tenant.Tenant tenant;

    @Mock
    private TenantContext tenantContext;

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);

        TenantContextHolder tenantContextHolder = mock(TenantContextHolder.class);

        when(tenantContextHolder.getContext()).thenReturn(tenantContext);
        when(tenantContext.getTenant()).thenReturn(Optional.of(tenant));
        when(tenant.isSuper()).thenReturn(true);

        xmTenantLifecycleService = new XmTenantLifecycleService(tenantClients,
            applicationProperties, tenantContextHolder);
    }

    @Test
    public void testNoTenant() throws Exception {
        when(tenantContext.getTenant()).thenReturn(Optional.empty());

        IllegalArgumentException exception = null;
        try {
            xmTenantLifecycleService.changeState(getEntity(), EntityState.ACTIVE.name(), context);
        } catch (IllegalArgumentException e) {
            exception = e;
        }

        assertNotNull(exception);
        assertEquals("Tenant not supplied", exception.getMessage());
    }

    @Test
    public void testNotSuperTenant() throws Exception {
        when(tenant.isSuper()).thenReturn(false);

        IllegalArgumentException exception = null;
        try {
            xmTenantLifecycleService.changeState(getEntity(), EntityState.ACTIVE.name(), context);
        } catch (IllegalArgumentException e) {
            exception = e;
        }

        assertNotNull(exception);
        assertEquals("Creating new tenants allowed only from super tenant", exception.getMessage());
    }

    @Test
    public void testNoContext() {
        XmEntity xmEntity = getEntity();

        xmTenantLifecycleService.changeState(xmEntity, EntityState.ACTIVE.name(), context);

        assertEquals(0, xmEntity.getData().size());
    }

    @Test
    public void testNoService() {
        XmEntity xmEntity = getEntity();
        context.put(LepXmEntityMsConstants.BINDING_KEY_SERVICES, Collections.singletonList(SERVICE_NAME));

        xmTenantLifecycleService.changeState(xmEntity, EntityState.ACTIVE.name(), context);

        assertEquals(1, xmEntity.getData().size());
        assertEquals(false, ((ServiceInfo) ((Map) xmEntity.getData().get(SERVICE_NAME)).get("create")).isSuccess());
        assertEquals("Service client not found: test",
            ((ServiceInfo) ((Map) xmEntity.getData().get(SERVICE_NAME)).get("create")).getErrorMessage());
    }

    @Test
    public void testServiceCallFail() {
        XmEntity xmEntity = getEntity();
        context.put(LepXmEntityMsConstants.BINDING_KEY_SERVICES, Collections.singletonList(SERVICE_NAME));
        TenantClient client = new FailClient();
        tenantClients.add(client);

        xmTenantLifecycleService.changeState(xmEntity, EntityState.ACTIVE.name(), context);

        assertEquals(1, xmEntity.getData().size());
        assertEquals(false, ((ServiceInfo) ((Map) xmEntity.getData().get(SERVICE_NAME)).get("create")).isSuccess());
        assertEquals(EXEC_ERROR, ((ServiceInfo) ((Map) xmEntity.getData().get(SERVICE_NAME)).get("create")).getErrorMessage());
    }

    @Test
    public void testServiceCallPass() {
        XmEntity xmEntity = getEntity();
        Map<String, Object> serviceInfo = new HashMap<>();
        Map<String, Object> action = new HashMap<>();
        serviceInfo.put("create", action);
        action.put(SUCCESS_NAME, true);
        xmEntity.getData().put(SERVICE_NAME, serviceInfo);
        context.put(LepXmEntityMsConstants.BINDING_KEY_SERVICES, Collections.singletonList(SERVICE_NAME));
        TenantClient client = new FailClient();
        tenantClients.add(client);

        xmTenantLifecycleService.changeState(xmEntity, EntityState.ACTIVE.name(), context);

        assertEquals(1, xmEntity.getData().size());
        assertEquals(true, ((Map) ((Map) xmEntity.getData().get(SERVICE_NAME)).get("create")).get(SUCCESS_NAME));
    }

    @Test
    public void testServiceCallSuccess() {
        XmEntity xmEntity = getEntity();
        context.put(LepXmEntityMsConstants.BINDING_KEY_SERVICES, Collections.singletonList(SERVICE_NAME));
        TenantClient client = new SuccessClient();
        tenantClients.add(client);

        xmTenantLifecycleService.changeState(xmEntity, EntityState.ACTIVE.name(), context);

        assertEquals(1, xmEntity.getData().size());
        assertEquals(true, ((ServiceInfo) ((Map) xmEntity.getData().get(SERVICE_NAME)).get("create")).isSuccess());
    }

    @Test
    public void testServiceCallSuccessWithNullContext() {

        when(applicationProperties.getTenantCreateServiceList()).thenReturn(Collections.singletonList(SERVICE_NAME));

        XmEntity xmEntity = getEntity();
        TenantClient client = new SuccessClient();
        tenantClients.add(client);

        xmTenantLifecycleService.changeState(xmEntity, EntityState.ACTIVE.name(), null);

        assertEquals(1, xmEntity.getData().size());
        assertEquals(true, ((ServiceInfo) ((Map) xmEntity.getData().get(SERVICE_NAME)).get("create")).isSuccess());
    }

    @Test
    @SneakyThrows
    public void testServiceInfoSerializable(){
        val serviceInfo = new XmTenantLifecycleService.ServiceInfo(false, "error message");
        new ObjectOutputStream(System.out).writeObject(serviceInfo);
    }

    private XmEntity getEntity() {
        return new XmEntity().typeKey(ENTITY_TYPE_KEY).stateKey(EntityState.NEW.name());
    }

    private static class FailClient implements TenantClient {

        @Override
        public String getName() {
            return SERVICE_NAME;
        }

        @Override
        public void addTenant(Tenant tenant) {
            throw new RuntimeException(EXEC_ERROR);
        }

        public void deleteTenant(String var1) {

        }

        public List<Tenant> getAllTenantInfo() {
            return null;
        }

        public Tenant getTenant(String var1) {
            return null;
        }

        public void manageTenant(String var1, String var2){

        }
    }

    private static class SuccessClient implements TenantClient {

        @Override
        public String getName() {
            return SERVICE_NAME;
        }

        @Override
        public void addTenant(Tenant tenant) {
        }

        public void deleteTenant(String var1) {

        }

        public List<Tenant> getAllTenantInfo() {
            return null;
        }

        public Tenant getTenant(String var1) {
            return null;
        }

        public void manageTenant(String var1, String var2) {

        }
    }

}
