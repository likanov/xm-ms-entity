package com.icthh.xm.ms.entity.web.rest;

import static com.icthh.xm.commons.lep.XmLepConstants.THREAD_CONTEXT_KEY_AUTH_CONTEXT;
import static com.icthh.xm.commons.lep.XmLepConstants.THREAD_CONTEXT_KEY_TENANT_CONTEXT;
import static com.icthh.xm.commons.tenant.TenantContextUtils.setTenant;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.icthh.xm.commons.i18n.error.web.ExceptionTranslator;
import com.icthh.xm.commons.lep.XmLepScriptConfigServerResourceLoader;
import com.icthh.xm.commons.security.XmAuthenticationContext;
import com.icthh.xm.commons.security.XmAuthenticationContextHolder;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.lep.api.LepManager;
import com.icthh.xm.ms.entity.EntityApp;
import com.icthh.xm.ms.entity.config.LepConfiguration;
import com.icthh.xm.ms.entity.config.SecurityBeanOverrideConfiguration;
import com.icthh.xm.ms.entity.config.tenant.WebappTenantOverrideConfiguration;
import com.icthh.xm.ms.entity.domain.XmEntity;
import com.icthh.xm.ms.entity.repository.XmEntityRepository;
import com.icthh.xm.ms.entity.service.impl.XmEntityServiceImpl;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.Validator;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 * Test class for the XmEntitySpecResource REST controller.
 *
 * @see XmEntitySpecResource
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(classes = {
    EntityApp.class,
    SecurityBeanOverrideConfiguration.class,
    WebappTenantOverrideConfiguration.class,
    LepConfiguration.class
})
public class XmEntitySearchIntTest {

    private static final String KEY1 = "ACCOUNT";

    private static final String KEY2 = "ACCOUNT-ADMIN";

    private static final String STATE_KEY1 = "TEST-STATE-KEY-1";

    private static final String STATE_KEY2 = "TEST-STATE-KEY-2";

    @Autowired
    private XmEntityServiceImpl xmEntityService;

    @Autowired
    private TenantContextHolder tenantContextHolder;

    @Mock
    private XmAuthenticationContextHolder authContextHolder;

    @Mock
    private XmAuthenticationContext context;

    @Autowired
    private LepManager lepManager;

    @Autowired
    private PageableHandlerMethodArgumentResolver pageableArgumentResolver;

    @Autowired
    private Validator validator;

    @Autowired
    private ExceptionTranslator exceptionTranslator;

    @Autowired
    private ElasticsearchTemplate elasticsearchTemplate;

    @Autowired
    private XmEntityRepository xmEntityRepository;

    private MockMvc restXmEntityMockMvc;

    @Autowired
    private XmLepScriptConfigServerResourceLoader leps;

    @Before
    @SneakyThrows
    public void setup() {
        MockitoAnnotations.initMocks(this);
        when(authContextHolder.getContext()).thenReturn(context);
        when(context.getRequiredUserKey()).thenReturn("userKey");

        setTenant(tenantContextHolder, "DEMO");

        elasticsearchTemplate.deleteIndex(XmEntity.class);
        elasticsearchTemplate.createIndex(XmEntity.class);
        elasticsearchTemplate.putMapping(XmEntity.class);

        lepManager.beginThreadContext(ctx -> {
            ctx.setValue(THREAD_CONTEXT_KEY_TENANT_CONTEXT, tenantContextHolder.getContext());
            ctx.setValue(THREAD_CONTEXT_KEY_AUTH_CONTEXT, authContextHolder.getContext());
        });


        this.restXmEntityMockMvc = MockMvcBuilders.standaloneSetup(new XmEntityResource(xmEntityService, null, null, null, null, null))
            .setValidator(validator).setControllerAdvice(exceptionTranslator).setCustomArgumentResolvers(pageableArgumentResolver).build();

    }

    void initLeps() {
        leps.onRefresh("/config/tenants/DEMO/entity/lep/service/entity/Save$$ACCOUNT$$around.groovy", loadFile("config/testlep/Save$$ACCOUNT$$around.groovy"));
    }

    void destroyLeps() {
        leps.onRefresh("/config/tenants/DEMO/entity/lep/service/entity/Save$$ACCOUNT$$around.groovy", null);
    }

    @SneakyThrows
    public static String loadFile(String path) {
        return IOUtils.toString(new ClassPathResource(path).getInputStream(), UTF_8);
    }

    public static XmEntity createEntity(String typeKey, String key, String stateKey) {
        val data = new HashMap<String, Object>();
        val entity = new XmEntity()
            .key(key == null ? UUID.randomUUID().toString() : key)
            .stateKey(stateKey)
            .typeKey(typeKey)
            .startDate(Instant.now())
            .updateDate(Instant.now())
            .name("DEFAULT_NAME")
            .description("DEFAULT_DESCRIPTION");
        entity.setData(data);
        return entity;
    }

    public static XmEntity createEntity(String typeKey) {
        return createEntity(typeKey, null, null);
    }

    @After
    @Override
    public void finalize() {
        lepManager.endThreadContext();
        tenantContextHolder.getPrivilegedContext().destroyCurrentContext();
    }

    @Test
    @SneakyThrows
    @WithMockUser(authorities = "SUPER-ADMIN")
    public void testRollbackedTransactionNotLeaveEntitiesInElastic() throws Exception {
        initLeps();

        XmEntity account = createEntity("ACCOUNT");

        restXmEntityMockMvc.perform(post("/api/xm-entities")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(account)))
            .andDo(print())
            .andExpect(status().isBadRequest())
        ;

        String contentAsString = restXmEntityMockMvc
            .perform(get("/api/_search/xm-entities?query=DEFAULT_NAME&page=0&size=10")
                .contentType(TestUtil.APPLICATION_JSON_UTF8))
            .andDo(print())
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        List<XmEntity> readValue = buildObjectMapper().readValue(contentAsString,
            new TypeReference<List<XmEntity>>() {
            });

        assertTrue(readValue.isEmpty());

        destroyLeps();
    }

    @Test
    @SneakyThrows
    @WithMockUser(authorities = "SUPER-ADMIN")
    public void testSearchByKeyInElastic() throws Exception {
        XmEntity account = createEntity("ACCOUNT", KEY2, null);
        xmEntityRepository.save(account);
        assertEquals(xmEntityRepository.findAll().size(), 1);

        //partial match
        List<XmEntity> partialMatchResult = searchEntityByKey(KEY1);
        assertEquals(0, partialMatchResult.size());

        //full match
        List<XmEntity> fullMatchResult = searchEntityByKey(KEY2);
        assertEquals(1, fullMatchResult.size());
    }

    @Test
    @SneakyThrows
    @WithMockUser(authorities = "SUPER-ADMIN")
    public void testSearchByStateKeyInElastic() throws Exception {
        XmEntity account = createEntity("ACCOUNT", KEY2, STATE_KEY2);
        xmEntityRepository.save(account);
        assertEquals(xmEntityRepository.findAll().size(), 1);

        //partial match
        List<XmEntity> partialMatchResult = searchEntityByStateKey(STATE_KEY1);
        assertEquals(0, partialMatchResult.size());

        //full match
        List<XmEntity> fullMatchResult = searchEntityByStateKey(STATE_KEY2);
        assertEquals(1, fullMatchResult.size());
    }

    @SneakyThrows
    private List<XmEntity> searchEntityByKey(String key) {
        String contentAsString = restXmEntityMockMvc
            .perform(get("/api/_search/xm-entities?query=key:" + key)
                .contentType(TestUtil.APPLICATION_JSON_UTF8))
            .andDo(print())
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        return buildObjectMapper().readValue(contentAsString,
            new TypeReference<List<XmEntity>>() {
            });
    }

    @SneakyThrows
    private List<XmEntity> searchEntityByStateKey(String stateKey) {
        String contentAsString = restXmEntityMockMvc
            .perform(get("/api/_search/xm-entities?query=stateKey:" + stateKey)
                .contentType(TestUtil.APPLICATION_JSON_UTF8))
            .andDo(print())
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        return buildObjectMapper().readValue(contentAsString,
            new TypeReference<List<XmEntity>>() {
            });
    }

    private ObjectMapper buildObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return objectMapper;
    }
}
