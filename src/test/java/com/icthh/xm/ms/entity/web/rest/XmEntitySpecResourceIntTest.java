package com.icthh.xm.ms.entity.web.rest;

import static com.icthh.xm.commons.lep.XmLepConstants.THREAD_CONTEXT_KEY_AUTH_CONTEXT;
import static com.icthh.xm.commons.lep.XmLepConstants.THREAD_CONTEXT_KEY_TENANT_CONTEXT;
import static com.icthh.xm.commons.tenant.TenantContextUtils.setTenant;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icthh.xm.commons.security.XmAuthenticationContext;
import com.icthh.xm.commons.security.XmAuthenticationContextHolder;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.lep.api.LepManager;
import com.icthh.xm.ms.entity.EntityApp;
import com.icthh.xm.ms.entity.config.LepConfiguration;
import com.icthh.xm.ms.entity.config.SecurityBeanOverrideConfiguration;
import com.icthh.xm.ms.entity.config.tenant.WebappTenantOverrideConfiguration;
import com.icthh.xm.ms.entity.security.access.DynamicPermissionCheckService;
import com.icthh.xm.ms.entity.service.XmEntityGeneratorService;
import com.icthh.xm.ms.entity.service.XmEntitySpecService;
import com.icthh.xm.ms.entity.service.impl.XmEntityServiceImpl;
import java.io.File;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Test class for the XmEntitySpecResource REST controller.
 *
 * @see XmEntitySpecResource
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = {
    EntityApp.class,
    SecurityBeanOverrideConfiguration.class,
    WebappTenantOverrideConfiguration.class,
    LepConfiguration.class
})
public class XmEntitySpecResourceIntTest {

    private static final String KEY1 = "ACCOUNT";

    private static final String KEY2 = "ACCOUNT.ADMIN";

    private static final String KEY3 = "ACCOUNT.USER";

    private static final String KEY4 = "PRODUCT-OFFERING";

    @Autowired
    private XmEntityServiceImpl xmEntityService;

    @Autowired
    private XmEntitySpecService xmEntitySpecService;

    @Autowired
    private DynamicPermissionCheckService dynamicPermissionCheckService;

    @Autowired
    private TenantContextHolder tenantContextHolder;

    @Mock
    private XmAuthenticationContextHolder authContextHolder;

    @Mock
    private XmAuthenticationContext context;

    @Autowired
    private LepManager lepManager;

    @Autowired
    private ObjectMapper objectMapper;

    private XmEntityGeneratorService xmEntityGeneratorService;

    private MockMvc restXmEntitySpecMockMvc;

    @Before
    @SneakyThrows
    public void setup() {
        MockitoAnnotations.initMocks(this);
        when(authContextHolder.getContext()).thenReturn(context);
        when(context.getRequiredUserKey()).thenReturn("userKey");

        setTenant(tenantContextHolder, "DEMO");
        lepManager.beginThreadContext(ctx -> {
            ctx.setValue(THREAD_CONTEXT_KEY_TENANT_CONTEXT, tenantContextHolder.getContext());
            ctx.setValue(THREAD_CONTEXT_KEY_AUTH_CONTEXT, authContextHolder.getContext());
        });

        xmEntityGeneratorService = new XmEntityGeneratorService(xmEntityService,
            xmEntitySpecService, authContextHolder, objectMapper);

        XmEntitySpecResource xmEntitySpecResource = new XmEntitySpecResource(xmEntitySpecService,
            xmEntityGeneratorService, dynamicPermissionCheckService);
        this.restXmEntitySpecMockMvc = MockMvcBuilders.standaloneSetup(xmEntitySpecResource).build();
    }

    @After
    @Override
    public void finalize() {
        lepManager.endThreadContext();
        tenantContextHolder.getPrivilegedContext().destroyCurrentContext();
    }

    @Test
    public void getAllXmEntityTypeSpecs() throws Exception {
        restXmEntitySpecMockMvc.perform(get("/api/xm-entity-specs"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.[*].key").value(hasItem(KEY1)))
            .andExpect(jsonPath("$.[*].key").value(hasItem(KEY2)))
            .andExpect(jsonPath("$.[*].key").value(hasItem(KEY3)));
    }

    @Test
    public void getAllAppXmEntityTypeSpecs() throws Exception {
        restXmEntitySpecMockMvc.perform(get("/api/xm-entity-specs?filter=APP"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.[*].key").value(hasItem(KEY1)))
            .andExpect(jsonPath("$.[*].key").value(hasItem(KEY4)));
    }

    @Test
    public void getAllNonAbstractXmEntityTypeSpecs() throws Exception {
        restXmEntitySpecMockMvc.perform(get("/api/xm-entity-specs?filter=NON_ABSTRACT"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.[*].key").value(hasItem(KEY2)))
            .andExpect(jsonPath("$.[*].key").value(hasItem(KEY3)));
    }

    @Test
    public void getXmEntityTypeSpec() throws Exception {
        restXmEntitySpecMockMvc.perform(get("/api/xm-entity-specs/" + KEY1))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.key").value(KEY1));
    }

    @Test
    public void getGenerateXmEntity() throws Exception {
        restXmEntitySpecMockMvc.perform(post("/api/xm-entity-specs/generate-xm-entity"))
            .andExpect(status().isCreated())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andDo(mvcResult -> System.out.println(mvcResult.getResponse().getContentAsString()))
            .andExpect(jsonPath("$.id").isNotEmpty());
    }

}
