package com.icthh.xm.ms.entity.web.rest;

import static com.icthh.xm.commons.lep.XmLepConstants.THREAD_CONTEXT_KEY_AUTH_CONTEXT;
import static com.icthh.xm.commons.lep.XmLepConstants.THREAD_CONTEXT_KEY_TENANT_CONTEXT;
import static com.icthh.xm.commons.tenant.TenantContextUtils.getRequiredTenantKeyValue;
import static com.icthh.xm.ms.entity.config.TenantConfigMockConfiguration.getXmEntityTemplatesSpec;
import static com.icthh.xm.ms.entity.web.rest.TestUtil.sameInstant;
import static java.util.Collections.emptyMap;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hibernate.validator.internal.util.CollectionHelper.asSet;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.collect.ImmutableMap;
import com.icthh.xm.lep.api.LepManager;
import com.icthh.xm.commons.exceptions.spring.web.ExceptionTranslator;
import com.icthh.xm.commons.security.XmAuthenticationContext;
import com.icthh.xm.commons.security.XmAuthenticationContextHolder;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.commons.tenant.TenantContextUtils;
import com.icthh.xm.ms.entity.EntityApp;
import com.icthh.xm.ms.entity.config.ApplicationProperties;
import com.icthh.xm.ms.entity.config.Constants;
import com.icthh.xm.ms.entity.config.LepConfiguration;
import com.icthh.xm.ms.entity.config.SecurityBeanOverrideConfiguration;
import com.icthh.xm.ms.entity.config.tenant.WebappTenantOverrideConfiguration;
import com.icthh.xm.ms.entity.domain.Attachment;
import com.icthh.xm.ms.entity.domain.Calendar;
import com.icthh.xm.ms.entity.domain.Content;
import com.icthh.xm.ms.entity.domain.Event;
import com.icthh.xm.ms.entity.domain.Link;
import com.icthh.xm.ms.entity.domain.Location;
import com.icthh.xm.ms.entity.domain.Tag;
import com.icthh.xm.ms.entity.domain.XmEntity;
import com.icthh.xm.ms.entity.domain.ext.IdOrKey;
import com.icthh.xm.ms.entity.repository.XmEntityPermittedRepository;
import com.icthh.xm.ms.entity.repository.XmEntityRepository;
import com.icthh.xm.ms.entity.repository.kafka.ProfileEventProducer;
import com.icthh.xm.ms.entity.repository.search.XmEntityPermittedSearchRepository;
import com.icthh.xm.ms.entity.repository.search.XmEntitySearchRepository;
import com.icthh.xm.ms.entity.service.*;
import com.icthh.xm.ms.entity.service.impl.StartUpdateDateGenerationStrategy;
import com.icthh.xm.ms.entity.service.impl.XmEntityServiceImpl;
import com.jayway.jsonpath.JsonPath;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.transaction.BeforeTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.Validator;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.persistence.EntityManager;

/**
 * Extension Test class for the XmEntityResource REST controller. Contains additional test apart from Jhipster generated
 * logic
 *
 * @see XmEntityResource
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(classes = {
    EntityApp.class,
    SecurityBeanOverrideConfiguration.class,
    WebappTenantOverrideConfiguration.class,
    LepConfiguration.class
})
public class XmEntityResourceExtendedIntTest {

    private static final String DEFAULT_KEY = "AAAAAAAAAA";

    private static final String DEFAULT_TYPE_KEY = "ACCOUNT.ADMIN";
    private static final String UPDATED_TYPE_KEY = "ACCOUNT.USER";

    private static final String DEFAULT_STATE_KEY = "STATE2";

    private static final String DEFAULT_NAME = "AAAAAAAAAA";

    private static final Instant DEFAULT_START_DATE = Instant.ofEpochMilli(0L);

    private static final Instant DEFAULT_UPDATE_DATE = Instant.ofEpochMilli(0L);

    private static final Instant DEFAULT_END_DATE = Instant.ofEpochMilli(0L);

    private static final String DEFAULT_AVATAR_URL = "http://hello.rgw.icthh.test/aaaaa.jpg";

    private static final String DEFAULT_DESCRIPTION = "AAAAAAAAAA";

    private static final Boolean DEFAULT_REMOVED = false;

    private static final Map<String, Object> DEFAULT_DATA = ImmutableMap.<String, Object>builder()
        .put("AAAAAAAAAA", "BBBBBBBBBB").build();

    private static final String LARGE_VALUE = StringUtils.repeat("LongStr ", 1024);
    private static final Map<String, Object> LARGE_DATA = ImmutableMap.<String, Object>builder()
        .put("AAAAAAAAAA", LARGE_VALUE).build();

    private static final String DEFAULT_TAG_NAME = "Test";
    private static final String DEFAULT_TAG_KEY = "FAVORIT";
    private static final String SEARCH_TEST_KEY = "ACCOUNTING";
    private static final String UNIQ_DESCRIPTION = "fd47b4dd-9ff9-4248-8d0b-1174350ba06c";
    private static final String NOT_PRESENT_UNIQ_DESCRIPTION = "b3163aab-d6e7-45c3-a0c3-96ac67ad7570";

    private static final String DEFAULT_ATTACHMENT_KEY = "AAAAAAAAAA";
    private static final String DEFAULT_ATTACHMENT_NAME = "Attachment1";
    private static final Instant DEFAULT_ATTACHMENT_START_DATE = Instant.now();
    private static final Instant DEFAULT_ATTACHMENT_END_DATE = Instant.now();

    private static final Instant MOCKED_START_DATE = Instant.ofEpochMilli(42L);
    private static final Instant MOCKED_UPDATE_DATE = Instant.ofEpochMilli(84L);

    private static final String DEFAULT_ATTACHMENT_CONTENT_TYPE = "my-content";
    private static final String DEFAULT_ATTACHMENT_CONTENT_VALUE = "THIS IS CONTENT";
    private static final String DEFAULT_ATTACHMENT_URL = "content url";
    private static final String DEFAULT_ATTACHMENT_DESCRIPTION = "Attachment description";
    private static final Integer DEFAULT_ATTACHMENT_CONTENT_SIZE = DEFAULT_ATTACHMENT_CONTENT_VALUE.length();

    private static final String DEFAULT_LOCATION_KEY = "AAAAAAAAAA";
    private static final String DEFAULT_LOCATION_NAME = "My location";
    private static final String DEFAULT_LOCATION_COUNTRY_KEY = "UA";

    private static final String DEFAULT_LN_TARGET_KEY = "LINK.TARGET";
    private static final String DEFAULT_LN_TARGET_NAME = "My target link";
    private static final Instant DEFAULT_LN_TARGET_START_DATE = Instant.now();

    @Autowired
    private ApplicationProperties applicationProperties;

    @Autowired
    private CalendarResource calendarResource;

    @Autowired
    private EventResource eventResource;

    @Autowired
    private XmEntityResource xmEntityResource;

    @Autowired
    private XmEntityRepository xmEntityRepository;

    @Autowired
    private ProfileEventProducer profileEventProducer;

    private XmEntityService xmEntityService;

    @Autowired
    private ProfileService profileService;

    @Autowired
    private CalendarService calendarService;

    @Autowired
    private EventService eventService;

    @Autowired
    private XmEntitySearchRepository xmEntitySearchRepository;

    @Autowired
    private MappingJackson2HttpMessageConverter jacksonMessageConverter;

    @Autowired
    private PageableHandlerMethodArgumentResolver pageableArgumentResolver;

    @Autowired
    private FunctionService functionService;

    @Autowired
    private ExceptionTranslator exceptionTranslator;

    @Autowired
    private EntityManager em;

    @Autowired
    private LepManager lepManager;

    @Autowired
    private Validator validator;

    @Autowired
    private TenantContextHolder tenantContextHolder;

    @Autowired
    XmEntitySpecService xmEntitySpecService;

    @Autowired
    XmEntityTemplatesSpecService xmEntityTemplatesSpecService;

    @Autowired
    LifecycleLepStrategyFactory lifeCycleService;

    @Autowired
    XmEntityPermittedRepository xmEntityPermittedRepository;

    @Autowired
    LinkService linkService;

    @Autowired
    StorageService storageService;

    @Autowired
    AttachmentService attachmentService;

    @Autowired
    XmEntityPermittedSearchRepository xmEntityPermittedSearchRepository;

    @Mock
    private XmAuthenticationContextHolder authContextHolder;

    @Autowired
    private TenantService tenantService;

    @Mock
    private XmAuthenticationContext context;

    @Spy
    private StartUpdateDateGenerationStrategy startUpdateDateGenerationStrategy;

    private MockMvc restXmEntityMockMvc;

    private XmEntity xmEntityIncoming;

    @BeforeTransaction
    public void beforeTransaction() {
        TenantContextUtils.setTenant(tenantContextHolder, "RESINTTEST");
    }

    @Before
    public void setup() {
        log.info("Init setup");

        //xmEntitySearchRepository.deleteAll();

        MockitoAnnotations.initMocks(this);

        when(authContextHolder.getContext()).thenReturn(context);
        when(context.getUserKey()).thenReturn(Optional.of("userKey"));

        when(startUpdateDateGenerationStrategy.generateStartDate()).thenReturn(MOCKED_START_DATE);
        when(startUpdateDateGenerationStrategy.generateUpdateDate()).thenReturn(MOCKED_UPDATE_DATE);

        String tenantName = getRequiredTenantKeyValue(tenantContextHolder);
        String config = getXmEntityTemplatesSpec(tenantName);
        String key = applicationProperties.getSpecificationTemplatesPathPattern().replace("{tenantName}", tenantName);
        xmEntityTemplatesSpecService.onRefresh(key, config);

        XmEntityServiceImpl xmEntityService = new XmEntityServiceImpl(xmEntitySpecService,
                                                   xmEntityTemplatesSpecService,
                                                   xmEntityRepository,
                                                   xmEntitySearchRepository,
                                                   lifeCycleService,
                                                   xmEntityPermittedRepository,
                                                   profileService,
                                                   linkService,
                                                   storageService,
                                                   attachmentService,
                                                   xmEntityPermittedSearchRepository,
                                                   startUpdateDateGenerationStrategy,
                                                   authContextHolder);

        xmEntityService.setSelf(xmEntityService);
        this.xmEntityService = xmEntityService;


        lepManager.beginThreadContext(ctx -> {
            ctx.setValue(THREAD_CONTEXT_KEY_TENANT_CONTEXT, tenantContextHolder.getContext());
            ctx.setValue(THREAD_CONTEXT_KEY_AUTH_CONTEXT, authContextHolder.getContext());
        });

        EventResource eventResourceMock = new EventResource(eventService, eventResource);

        XmEntityResource xmEntityResourceMock = new XmEntityResource(xmEntityService,
            profileService,
            profileEventProducer,
            functionService,
            tenantService,
            xmEntityResource
            );
        this.restXmEntityMockMvc = MockMvcBuilders.standaloneSetup(
            xmEntityResourceMock, new CalendarResource(calendarService, calendarResource), eventResourceMock)
            .setCustomArgumentResolvers(pageableArgumentResolver)
            .setControllerAdvice(exceptionTranslator)
            .setValidator(validator)
            .setMessageConverters(jacksonMessageConverter).build();

        xmEntityIncoming = createEntityComplexIncoming(em);

    }

    @After
    @Override
    public void finalize() {
        lepManager.endThreadContext();
        tenantContextHolder.getPrivilegedContext().destroyCurrentContext();
    }

    /**
     * Create an entity for this test.
     * <p>
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static XmEntity createEntity(EntityManager em) {
        XmEntity xmEntity = new XmEntity()
            .key(DEFAULT_KEY)
            .typeKey(DEFAULT_TYPE_KEY)
            .stateKey(DEFAULT_STATE_KEY)
            .name(DEFAULT_NAME)
            .startDate(DEFAULT_START_DATE)
            .updateDate(DEFAULT_UPDATE_DATE)
            .endDate(DEFAULT_END_DATE)
            .avatarUrl(DEFAULT_AVATAR_URL)
            .description(DEFAULT_DESCRIPTION)
            .data(DEFAULT_DATA)
            .removed(DEFAULT_REMOVED);

        return xmEntity;
    }

    /**
     * Creates incoming Entity as from HTTP request.
     * Potentially cam be moved to DTO
     *
     * @param em - Entity manager
     * @return XmEntity for incoming request
     */
    public static XmEntity createEntityComplexIncoming(EntityManager em) {
        XmEntity entity = createEntity(em);

        entity.getTags().add(new Tag()
            .typeKey(DEFAULT_TAG_KEY)
            .startDate(DEFAULT_START_DATE)
            .name(DEFAULT_TAG_NAME));
        entity.getAttachments().add(new Attachment()
            .typeKey(DEFAULT_ATTACHMENT_KEY)
            .name(DEFAULT_ATTACHMENT_NAME)
            .startDate(DEFAULT_ATTACHMENT_START_DATE)
            .endDate(DEFAULT_ATTACHMENT_END_DATE)
            .valueContentType(DEFAULT_ATTACHMENT_CONTENT_TYPE)
            .content(new Content().value(DEFAULT_ATTACHMENT_CONTENT_VALUE.getBytes()))
            .contentUrl(DEFAULT_ATTACHMENT_URL)
            .description(DEFAULT_ATTACHMENT_DESCRIPTION)
        );
        entity.getLocations().add(new Location()
            .typeKey(DEFAULT_LOCATION_KEY)
            .name(DEFAULT_LOCATION_NAME)
            .countryKey(DEFAULT_LOCATION_COUNTRY_KEY)
        );

        return entity;
    }

    /**
     * Creates XmEntiy with tags and persist to DB.
     * Emulates existing XM entity manipulation
     *
     * @param em - Entity manager
     * @return XmEntity persisted in DB
     */
    public static XmEntity createEntityComplexPersisted(EntityManager em) {

        XmEntity entity = createEntity(em);
        em.persist(entity);
        em.flush();

        entity.addTags(new Tag()
            .typeKey(DEFAULT_TAG_KEY)
            .startDate(DEFAULT_START_DATE)
            .name(DEFAULT_TAG_NAME));
        em.persist(entity);
        em.flush();
        return entity;
    }

    /**
     * Performs REST result to log.
     */
    private void printMvcResult(MvcResult result) throws UnsupportedEncodingException {
        log.info("MVC result: {}", result.getResponse().getContentAsString());
    }

    /**
     * Performs HTTP GET.
     *
     * @param url    URL template
     * @param params Temaplte params
     */
    private ResultActions performGet(String url, Object... params) throws Exception {
        return restXmEntityMockMvc.perform(get(url, params))
            .andDo(this::printMvcResult);
    }

    /**
     * Performs HTTP PUT.
     *
     * @param url     URL template
     * @param content Content object
     */
    private ResultActions performPut(String url, Object content) throws Exception {

        String json = new String(convertObjectToJsonBytesByFields(content));
        log.info("perform PUT: {} with content: {}", url, json);
        return restXmEntityMockMvc.perform(put(url)
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(json))
            .andDo(this::printMvcResult);
    }

    /**
     * Performs HTTP POST.
     *
     * @param url     URL template
     * @param content content object
     */
    private ResultActions performPost(String url, Object content) throws Exception {
        String json = new String(convertObjectToJsonBytesByFields(content));
        log.info("perform POST: {} with content: {}", url, json);
        return restXmEntityMockMvc.perform(post(url)
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(json))
            .andDo(this::printMvcResult);
    }

    /**
     * Validates expected entites size in DB.
     *
     * @param expectedSize - expected size
     */
    private List<XmEntity> validateEntityInDB(final int expectedSize) {
        List<XmEntity> xmEntityList = xmEntityRepository.findAll();
        assertThat(xmEntityList).hasSize(expectedSize);
        return xmEntityList;
    }

    /**
     * Convert an object to JSON byte array.
     *
     * @param object the object to convert
     * @return the JSON byte array
     */
    public static byte[] convertObjectToJsonBytesByFields(Object object)
        throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
            .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

        JavaTimeModule module = new JavaTimeModule();
        mapper.registerModule(module);

        return mapper.writeValueAsBytes(object);
    }

    @Test
    @Transactional
    public void createXmEntityWithTagsAttachmentsLocations() throws Exception {
        int databaseSizeBeforeCreate = xmEntityRepository.findAll().size();

        // Create the XmEntity with tag, attachment and location
        MvcResult result = performPost("/api/xm-entities", xmEntityIncoming)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.key").value(DEFAULT_KEY))
            .andReturn();

        Integer id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

        List<XmEntity> xmEntityList = validateEntityInDB(databaseSizeBeforeCreate + 1);
        XmEntity testXmEntity = xmEntityList.get(xmEntityList.size() - 1);

        // Get the xmEntityPersisted by ID
        performGet("/api/xm-entities/{id}", id)
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.id").value(id))
            .andExpect(jsonPath("$.key").value(DEFAULT_KEY))
            .andExpect(jsonPath("$.typeKey").value(DEFAULT_TYPE_KEY))
            .andExpect(jsonPath("$.stateKey").value(DEFAULT_STATE_KEY))
            .andExpect(jsonPath("$.name").value(DEFAULT_NAME))
            .andExpect(jsonPath("$.startDate").value(sameInstant(MOCKED_START_DATE)))
            .andExpect(jsonPath("$.updateDate").value(sameInstant(MOCKED_UPDATE_DATE)))
            .andExpect(jsonPath("$.endDate").value(sameInstant(DEFAULT_END_DATE)))
            .andExpect(jsonPath("$.avatarUrl").value(containsString("aaaaa.jpg")))
            .andExpect(jsonPath("$.description").value(DEFAULT_DESCRIPTION))
            .andExpect(jsonPath("$.data.AAAAAAAAAA").value("BBBBBBBBBB"))

            .andExpect(jsonPath("$.tags[0].id").value(notNullValue()))
            .andExpect(jsonPath("$.tags[0].name").value(DEFAULT_TAG_NAME))
            .andExpect(jsonPath("$.tags[0].typeKey").value(DEFAULT_TAG_KEY))
            .andExpect(jsonPath("$.tags[0].xmEntity").value(id))

            .andExpect(jsonPath("$.attachments[0].id").value(notNullValue()))
            .andExpect(jsonPath("$.attachments[0].typeKey").value(DEFAULT_ATTACHMENT_KEY))
            .andExpect(jsonPath("$.attachments[0].name").value(DEFAULT_ATTACHMENT_NAME))
            .andExpect(jsonPath("$.attachments[0].contentUrl").value(DEFAULT_ATTACHMENT_URL))
            .andExpect(jsonPath("$.attachments[0].description").value(DEFAULT_ATTACHMENT_DESCRIPTION))
            .andExpect(jsonPath("$.attachments[0].startDate").value(DEFAULT_ATTACHMENT_START_DATE.toString()))
            .andExpect(jsonPath("$.attachments[0].endDate").value(DEFAULT_ATTACHMENT_END_DATE.toString()))
            .andExpect(jsonPath("$.attachments[0].valueContentType").value(DEFAULT_ATTACHMENT_CONTENT_TYPE))
            .andExpect(jsonPath("$.attachments[0].valueContentSize").value(DEFAULT_ATTACHMENT_CONTENT_SIZE))
            .andExpect(jsonPath("$.attachments[0].xmEntity").value(id))

            .andExpect(jsonPath("$.locations[0].id").value(notNullValue()))
            .andExpect(jsonPath("$.locations[0].typeKey").value(DEFAULT_LOCATION_KEY))
            .andExpect(jsonPath("$.locations[0].name").value(DEFAULT_LOCATION_NAME))
            .andExpect(jsonPath("$.locations[0].countryKey").value(DEFAULT_LOCATION_COUNTRY_KEY))
            .andExpect(jsonPath("$.locations[0].xmEntity").value(id))

        // TODO - check not implemented XmEntity references. Replace with correct assertions after implementation
        //.andExpect(jsonPath("$.calendars.length()").value(0))

        // FIXME - content is returned in TEST mode besides LAZY fetchtype. Need to investigate.
        // .andExpect(jsonPath("$.attachments[0].content.id").value(notNullValue()))

        ;

        // Validate the XmEntity in Elasticsearch
        XmEntity xmEntityEs = xmEntitySearchRepository.findOne(Long.valueOf(id.toString()));
        assertThat(xmEntityEs).isEqualToComparingFieldByField(testXmEntity);

    }

    @Test
    @Transactional
    public void createXmEntityWithLinks() throws Exception {

        XmEntity presaved = xmEntityService.save(createEntity(em));

        int databaseSizeBeforeCreate = xmEntityRepository.findAll().size();

        XmEntity entity = xmEntityIncoming;
        entity.getTargets().add(new Link()
            .typeKey(DEFAULT_LN_TARGET_KEY)
            .name(DEFAULT_LN_TARGET_NAME)
            .startDate(DEFAULT_LN_TARGET_START_DATE)
            .target(presaved)
        );

        // Create the XmEntity with tag
        MvcResult result = performPost("/api/xm-entities", entity)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.key").value(DEFAULT_KEY))
            .andReturn();

        Integer id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

        List<XmEntity> xmEntityList = validateEntityInDB(databaseSizeBeforeCreate + 1);
        XmEntity testXmEntity = xmEntityList.get(xmEntityList.size() - 1);

        // Get the xmEntityPersisted with tag by ID
        performGet("/api/xm-entities/{id}", id)
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.id").value(id))
            .andExpect(jsonPath("$.key").value(DEFAULT_KEY))
            .andExpect(jsonPath("$.typeKey").value(DEFAULT_TYPE_KEY))
            .andExpect(jsonPath("$.stateKey").value(DEFAULT_STATE_KEY))
            .andExpect(jsonPath("$.name").value(DEFAULT_NAME))
            .andExpect(jsonPath("$.startDate").value(sameInstant(MOCKED_START_DATE)))
            .andExpect(jsonPath("$.updateDate").value(sameInstant(MOCKED_UPDATE_DATE)))
            .andExpect(jsonPath("$.endDate").value(sameInstant(DEFAULT_END_DATE)))
            .andExpect(jsonPath("$.avatarUrl").value(containsString("aaaaa.jpg")))
            .andExpect(jsonPath("$.description").value(DEFAULT_DESCRIPTION))
            .andExpect(jsonPath("$.data.AAAAAAAAAA").value("BBBBBBBBBB"))

            .andExpect(jsonPath("$.targets[0].id").value(notNullValue()))
            .andExpect(jsonPath("$.targets[0].name").value(DEFAULT_LN_TARGET_NAME))
            .andExpect(jsonPath("$.targets[0].typeKey").value(DEFAULT_LN_TARGET_KEY))
            .andExpect(jsonPath("$.targets[0].source").value(id))
            .andExpect(jsonPath("$.targets[0].target.id").value(presaved.getId()))
            .andExpect(jsonPath("$.targets[0].target.typeKey").value(presaved.getTypeKey()));

        // Validate the XmEntity in Elasticsearch
        XmEntity xmEntityEs = xmEntitySearchRepository.findOne(Long.valueOf(id.toString()));
        assertThat(xmEntityEs).isEqualToComparingFieldByField(testXmEntity);

    }

    @Test
    @Transactional
    public void createXmEntityWithSourceLinks() throws Exception {

        XmEntity presaved = xmEntityService.save(createEntity(em));

        int databaseSizeBeforeCreate = xmEntityRepository.findAll().size();

        XmEntity entity = xmEntityIncoming;
        entity.setSources(Collections.singleton(new Link()
            .typeKey(DEFAULT_LN_TARGET_KEY)
            .name(DEFAULT_LN_TARGET_NAME)
            .startDate(DEFAULT_LN_TARGET_START_DATE)
            .source(presaved))
        );

        // Create the XmEntity with tag
        MvcResult result = performPost("/api/xm-entities", entity)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.key").value(DEFAULT_KEY))
            .andReturn();

        Integer id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

        em.detach(presaved);

        List<XmEntity> xmEntityList = validateEntityInDB(databaseSizeBeforeCreate + 1);
        XmEntity testXmEntity = xmEntityList.get(xmEntityList.size() - 1);

        presaved = xmEntityService.findOne(IdOrKey.of(presaved.getId()));

        // Get the xmEntityPersisted with tag by ID
        performGet("/api/xm-entities/{id}", presaved.getId())
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.id").value(presaved.getId()))
            .andExpect(jsonPath("$.key").value(DEFAULT_KEY))
            .andExpect(jsonPath("$.typeKey").value(DEFAULT_TYPE_KEY))
            .andExpect(jsonPath("$.stateKey").value(DEFAULT_STATE_KEY))
            .andExpect(jsonPath("$.name").value(DEFAULT_NAME))
            .andExpect(jsonPath("$.startDate").value(sameInstant(MOCKED_START_DATE)))
            .andExpect(jsonPath("$.updateDate").value(sameInstant(MOCKED_UPDATE_DATE)))
            .andExpect(jsonPath("$.endDate").value(sameInstant(DEFAULT_END_DATE)))
            .andExpect(jsonPath("$.avatarUrl").value(containsString("aaaaa.jpg")))
            .andExpect(jsonPath("$.description").value(DEFAULT_DESCRIPTION))
            .andExpect(jsonPath("$.data.AAAAAAAAAA").value("BBBBBBBBBB"))

            .andExpect(jsonPath("$.targets[0].id").value(notNullValue()))
            .andExpect(jsonPath("$.targets[0].name").value(DEFAULT_LN_TARGET_NAME))
            .andExpect(jsonPath("$.targets[0].typeKey").value(DEFAULT_LN_TARGET_KEY))
            .andExpect(jsonPath("$.targets[0].source").value(presaved.getId()))
            .andExpect(jsonPath("$.targets[0].target.id").value(id));

        // Validate the XmEntity in Elasticsearch
        XmEntity xmEntityEs = xmEntitySearchRepository.findOne(Long.valueOf(id.toString()));
        assertThat(xmEntityEs).isEqualToIgnoringGivenFields(testXmEntity, "sources", "avatarUrl");
    }

    @Test
    @Transactional
    public void createXmEntityWithLargeData() throws Exception {
        int databaseSizeBeforeCreate = xmEntityRepository.findAll().size();

        xmEntityIncoming.setData(LARGE_DATA);

        // Create the XmEntity
        MvcResult result = performPost("/api/xm-entities", xmEntityIncoming)
            .andExpect(status().isCreated())
            .andReturn();

        Integer id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

        // Get the xmEntityPersisted with tag by ID
        performGet("/api/xm-entities/{id}", id)
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.id").value(id))
            .andExpect(jsonPath("$.key").value(DEFAULT_KEY))
            .andExpect(jsonPath("$.typeKey").value(DEFAULT_TYPE_KEY))
            .andExpect(jsonPath("$.stateKey").value(DEFAULT_STATE_KEY))
            .andExpect(jsonPath("$.name").value(DEFAULT_NAME))
            .andExpect(jsonPath("$.startDate").value(sameInstant(MOCKED_START_DATE)))
            .andExpect(jsonPath("$.updateDate").value(sameInstant(MOCKED_UPDATE_DATE)))
            .andExpect(jsonPath("$.endDate").value(sameInstant(DEFAULT_END_DATE)))
            .andExpect(jsonPath("$.avatarUrl").value(containsString("aaaaa.jpg")))
            .andExpect(jsonPath("$.description").value(DEFAULT_DESCRIPTION))
            .andExpect(jsonPath("$.data.AAAAAAAAAA").value(LARGE_VALUE));

        // Validate the XmEntity in the database
        List<XmEntity> xmEntityList = validateEntityInDB(databaseSizeBeforeCreate + 1);

        XmEntity testXmEntity = xmEntityList.get(xmEntityList.size() - 1);
        assertThat(testXmEntity.getData()).isEqualTo(LARGE_DATA);

        // Validate the XmEntity in Elasticsearch
        XmEntity xmEntityEs = xmEntitySearchRepository.findOne(testXmEntity.getId());
        assertThat(xmEntityEs).isEqualToComparingFieldByField(testXmEntity);
        assertThat(xmEntityEs.getData()).isEqualTo(LARGE_DATA);
    }

    @Test
    @Transactional
    public void checkJsonShemeDateTypeProperties() throws Exception {

        int databaseSizeBeforeTest = xmEntityRepository.findAll().size();

        XmEntity entity = xmEntityIncoming;

        entity.setData(ImmutableMap.of("numberProperties", "5"));

        performPut("/api/xm-entities", entity)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("error.validation"));

        // Validate the XmEntity in the database
        validateEntityInDB(databaseSizeBeforeTest);

        entity.setData(ImmutableMap.of("numberProperties", "testse"));

        performPut("/api/xm-entities", entity)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("error.validation"));

        // Validate the XmEntity in the database
        validateEntityInDB(databaseSizeBeforeTest);

        entity.setData(ImmutableMap.of("numberProperties", Boolean.FALSE));

        performPut("/api/xm-entities", entity)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("error.validation"));

        // Validate the XmEntity in the database
        validateEntityInDB(databaseSizeBeforeTest);

    }

    @Test
    @Transactional
    public void checkJsonShemeDateIsRequired() throws Exception {
        int databaseSizeBeforeTest = xmEntityRepository.findAll().size();

        xmEntityIncoming.setData(emptyMap());

        performPut("/api/xm-entities", xmEntityIncoming)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("error.validation"));

        validateEntityInDB(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    @WithMockUser(authorities = "SUPER-ADMIN")
    public void getAllXmEntitiesByTypeKey() throws Exception {

        XmEntity entity = createEntityComplexPersisted(em);

        // Get all the xmEntityList
        performGet("/api/xm-entities?sort=id,desc&typeKey=ACCOUNT")
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(entity.getId().intValue())))
            .andExpect(jsonPath("$.[*].typeKey").value(hasItem(DEFAULT_TYPE_KEY)));
    }

    @Test
    @Transactional
    @WithMockUser(authorities = "SUPER-ADMIN")
    public void getAllXmEntitiesByTypeKeyNo() throws Exception {

        XmEntity entity = createEntityComplexPersisted(em);

        // Get all the xmEntityList
        performGet("/api/xm-entities?sort=id,desc&typeKey=PRICE")
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.[*].id").value(everyItem(nullValue())));
    }

    @Test
    @Transactional
    @WithMockUser(authorities = "SUPER-ADMIN")
    public void testSearchByTypeKeyAndQuery() throws Exception {

        // FIXME - fails if run test in Idea. But its needed for test running from console. need fix.
        try {
            xmEntitySearchRepository.deleteAll();
        } catch (Exception e) {
            log.warn("Suppress index deletion exception in tenant context: {}", String.valueOf(e));
        }

        // Initialize the database
        xmEntityService.save(createEntityComplexIncoming(em).typeKey(DEFAULT_TYPE_KEY));
        xmEntityService.save(createEntityComplexIncoming(em).typeKey(UPDATED_TYPE_KEY).description(UNIQ_DESCRIPTION));
        xmEntityService.save(createEntityComplexIncoming(em).typeKey(SEARCH_TEST_KEY));

        String urlTemplate = "/api/_search-with-typekey/xm-entities?typeKey=ACCOUNT&size=5";

        performGet(urlTemplate)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$.[0].tags[0].name").value(DEFAULT_TAG_NAME))
            .andExpect(jsonPath("$.[1].tags[0].name").value(DEFAULT_TAG_NAME));

        performGet(urlTemplate + "&query=" + UNIQ_DESCRIPTION)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$.[0].tags[0].name").value(DEFAULT_TAG_NAME));

        performGet(urlTemplate + "&query=" + NOT_PRESENT_UNIQ_DESCRIPTION)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));

    }

    @Test
    @Transactional
    @WithMockUser(authorities = "SUPER-ADMIN")
    public void testSearchByTypeKeyAndTemplate() throws Exception {

        // FIXME - fails if run test in Idea. But its needed for test running from console. need fix.
        try {
            xmEntitySearchRepository.deleteAll();
        } catch (Exception e) {
            log.warn("Suppress index deletion exception in tenant context: {}", String.valueOf(e));
        }

        // Initialize the database
        xmEntityService.save(createEntityComplexIncoming(em).typeKey(DEFAULT_TYPE_KEY));
        xmEntityService.save(createEntityComplexIncoming(em).typeKey(UPDATED_TYPE_KEY).description(UNIQ_DESCRIPTION));
        xmEntityService.save(createEntityComplexIncoming(em).typeKey(SEARCH_TEST_KEY));

        String urlTemplate = "/api/_search-with-typekey/xm-entities?typeKey=ACCOUNT&size=5";

        performGet(urlTemplate)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$.[0].tags[0].name").value(DEFAULT_TAG_NAME))
            .andExpect(jsonPath("$.[1].tags[0].name").value(DEFAULT_TAG_NAME));

        performGet(urlTemplate + "&template=UNIQ_DESCRIPTION&templateParams=" + UNIQ_DESCRIPTION)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$.[0].tags[0].name").value(DEFAULT_TAG_NAME));

        performGet(urlTemplate + "&template=NOT_PRESENT_UNIQ_DESCRIPTION&templateParams=" + NOT_PRESENT_UNIQ_DESCRIPTION)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));

    }

    @Test
    @Transactional
    public void xmEntityFildsNoRelationFields() throws Exception {

        assertThat(xmEntityIncoming.getAttachments().size()).isGreaterThan(0);
        assertThat(xmEntityIncoming.getTags().size()).isGreaterThan(0);
        assertThat(xmEntityIncoming.getLocations().size()).isGreaterThan(0);

        // Create the XmEntity with tag
        MvcResult result = performPost("/api/xm-entities", xmEntityIncoming)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.key").value(DEFAULT_KEY))
            .andReturn();

        int id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

        List<XmEntity> xmEntityList = xmEntityRepository.findAll();
        em.detach(xmEntityList.get(xmEntityList.size() - 1));
        //);

        performGet("/api/xm-entities/{id}?embed=id", id)
            .andExpect(status().isOk())
            .andDo(this::printMvcResult)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.id").value(id))
            .andExpect(jsonPath("$.key").value(DEFAULT_KEY))
            .andExpect(jsonPath("$.typeKey").value(DEFAULT_TYPE_KEY))
            .andExpect(jsonPath("$.stateKey").value(DEFAULT_STATE_KEY))
            .andExpect(jsonPath("$.name").value(DEFAULT_NAME))
            .andExpect(jsonPath("$.startDate").value(sameInstant(MOCKED_START_DATE)))
            .andExpect(jsonPath("$.updateDate").value(sameInstant(MOCKED_UPDATE_DATE)))
            .andExpect(jsonPath("$.endDate").value(sameInstant(DEFAULT_END_DATE)))
            .andExpect(jsonPath("$.description").value(DEFAULT_DESCRIPTION))
            .andExpect(jsonPath("$.data.AAAAAAAAAA").value("BBBBBBBBBB"))
            .andExpect(jsonPath("$.attachments").value(nullValue()))
            .andExpect(jsonPath("$.tags").value(nullValue()))
            .andExpect(jsonPath("$.locations").value(nullValue()));

    }

    @Test
    @Transactional
    public void xmEntityFildsTwoFields() throws Exception {

        assertThat(xmEntityIncoming.getAttachments().size()).isGreaterThan(0);
        assertThat(xmEntityIncoming.getTags().size()).isGreaterThan(0);
        assertThat(xmEntityIncoming.getLocations().size()).isGreaterThan(0);

        // Create the XmEntity with tag
        MvcResult result = performPost("/api/xm-entities", xmEntityIncoming)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.key").value(DEFAULT_KEY))
            .andReturn();

        int id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

        List<XmEntity> xmEntityList = xmEntityRepository.findAll();
        em.detach(xmEntityList.get(xmEntityList.size() - 1));
        //);

        performGet("/api/xm-entities/{id}?embed=id,attachments,tags", id)
            .andExpect(status().isOk())
            .andDo(this::printMvcResult)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.id").value(id))
            .andExpect(jsonPath("$.key").value(DEFAULT_KEY))
            .andExpect(jsonPath("$.typeKey").value(DEFAULT_TYPE_KEY))
            .andExpect(jsonPath("$.stateKey").value(DEFAULT_STATE_KEY))
            .andExpect(jsonPath("$.name").value(DEFAULT_NAME))
            .andExpect(jsonPath("$.startDate").value(sameInstant(MOCKED_START_DATE)))
            .andExpect(jsonPath("$.updateDate").value(sameInstant(MOCKED_UPDATE_DATE)))
            .andExpect(jsonPath("$.endDate").value(sameInstant(DEFAULT_END_DATE)))
            .andExpect(jsonPath("$.description").value(DEFAULT_DESCRIPTION))
            .andExpect(jsonPath("$.data.AAAAAAAAAA").value("BBBBBBBBBB"))
            .andExpect(jsonPath("$.attachments.length()").value(1))
            .andExpect(jsonPath("$.tags.length()").value(1))
            .andExpect(jsonPath("$.locations").value(nullValue()));

    }

    @Test
    @Transactional
    public void xmEntityFildsDefaultFields() throws Exception {

        assertThat(xmEntityIncoming.getAttachments().size()).isGreaterThan(0);
        assertThat(xmEntityIncoming.getTags().size()).isGreaterThan(0);
        assertThat(xmEntityIncoming.getLocations().size()).isGreaterThan(0);

        // Create the XmEntity with tag
        MvcResult result = performPost("/api/xm-entities", xmEntityIncoming)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.key").value(DEFAULT_KEY))
            .andReturn();

        int id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

        List<XmEntity> xmEntityList = xmEntityRepository.findAll();
        em.detach(xmEntityList.get(xmEntityList.size() - 1));
        //);

        performGet("/api/xm-entities/{id}", id)
            .andExpect(status().isOk())
            .andDo(this::printMvcResult)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.id").value(id))
            .andExpect(jsonPath("$.key").value(DEFAULT_KEY))
            .andExpect(jsonPath("$.typeKey").value(DEFAULT_TYPE_KEY))
            .andExpect(jsonPath("$.stateKey").value(DEFAULT_STATE_KEY))
            .andExpect(jsonPath("$.name").value(DEFAULT_NAME))
            .andExpect(jsonPath("$.startDate").value(sameInstant(MOCKED_START_DATE)))
            .andExpect(jsonPath("$.updateDate").value(sameInstant(MOCKED_UPDATE_DATE)))
            .andExpect(jsonPath("$.endDate").value(sameInstant(DEFAULT_END_DATE)))
            .andExpect(jsonPath("$.description").value(DEFAULT_DESCRIPTION))
            .andExpect(jsonPath("$.data.AAAAAAAAAA").value("BBBBBBBBBB"))
            .andExpect(jsonPath("$.attachments.length()").value(1))
            .andExpect(jsonPath("$.tags.length()").value(1))
            .andExpect(jsonPath("$.locations.length()").value(1));

    }

    @Test
    @Transactional
    @WithMockUser(authorities = "SUPER-ADMIN")
    public void xmEntityFildsCalendars() throws Exception {
        int id = prepareCalendar();

        performGet("/api/xm-entities/{id}?embed=calendars", id)
            .andExpect(status().isOk())
            .andDo(this::printMvcResult)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.id").value(id))
            .andExpect(jsonPath("$.key").value(DEFAULT_KEY))
            .andExpect(jsonPath("$.typeKey").value(DEFAULT_TYPE_KEY))
            .andExpect(jsonPath("$.stateKey").value(DEFAULT_STATE_KEY))
            .andExpect(jsonPath("$.name").value(DEFAULT_NAME))
            .andExpect(jsonPath("$.startDate").value(sameInstant(MOCKED_START_DATE)))
            .andExpect(jsonPath("$.updateDate").value(sameInstant(MOCKED_UPDATE_DATE)))
            .andExpect(jsonPath("$.endDate").value(sameInstant(DEFAULT_END_DATE)))
            .andExpect(jsonPath("$.description").value(DEFAULT_DESCRIPTION))
            .andExpect(jsonPath("$.data.AAAAAAAAAA").value("BBBBBBBBBB"))
            .andExpect(jsonPath("$.calendars.length()").value(1))
            .andExpect(jsonPath("$.calendars[0].events").value(nullValue()))

        ;

    }

    private int prepareCalendar() throws Exception {
        // Create the XmEntity with tag
        MvcResult result = performPost("/api/xm-entities", xmEntityIncoming)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.key").value(DEFAULT_KEY))
            .andReturn();

        int id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

        xmEntityIncoming.setId((long) id);

        val calendar = new Calendar().name("name").typeKey("TYPEKEY").startDate(Instant.now())
            .xmEntity(xmEntityIncoming);

        MvcResult resultSaveCalendar = performPost("/api/calendars", calendar)
            .andExpect(status().is2xxSuccessful())
            .andReturn();

        int calendarId = JsonPath.read(resultSaveCalendar.getResponse().getContentAsString(), "$.id");

        calendar.setId((long) calendarId);

        val event = new Event().typeKey("TYPEKEY").title("title").startDate(Instant.now())
            .calendar(calendar).assigned(xmEntityIncoming);

        MvcResult resultSaveEvent = performPost("/api/events", event)
            .andExpect(status().is2xxSuccessful())
            .andReturn();

        List<XmEntity> xmEntityList = xmEntityRepository.findAll();
        em.detach(xmEntityList.get(xmEntityList.size() - 1));

        List<Calendar> calendarList = calendarService.findAll(null);
        em.detach(calendarList.get(calendarList.size() - 1));
        return id;
    }

    @Test
    @Transactional
    @WithMockUser(authorities = "SUPER-ADMIN")
    public void xmEntityFildsCalendarsWithEvents() throws Exception {

        int id = prepareCalendar();

        performGet("/api/xm-entities/{id}?embed=calendars.events", id)
            .andExpect(status().isOk())
            .andDo(this::printMvcResult)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.id").value(id))
            .andExpect(jsonPath("$.key").value(DEFAULT_KEY))
            .andExpect(jsonPath("$.typeKey").value(DEFAULT_TYPE_KEY))
            .andExpect(jsonPath("$.stateKey").value(DEFAULT_STATE_KEY))
            .andExpect(jsonPath("$.name").value(DEFAULT_NAME))
            .andExpect(jsonPath("$.startDate").value(sameInstant(MOCKED_START_DATE)))
            .andExpect(jsonPath("$.updateDate").value(sameInstant(MOCKED_UPDATE_DATE)))
            .andExpect(jsonPath("$.endDate").value(sameInstant(DEFAULT_END_DATE)))
            .andExpect(jsonPath("$.description").value(DEFAULT_DESCRIPTION))
            .andExpect(jsonPath("$.data.AAAAAAAAAA").value("BBBBBBBBBB"))
            .andExpect(jsonPath("$.calendars.length()").value(1))
            .andExpect(jsonPath("$.calendars[0].events.length()").value(1));

    }

    @Test
    @Transactional
    public void updateTenantXmEntity() throws Exception {
        // Initialize the database
        XmEntity tenantEntity = createEntity(em);
        tenantEntity.setTypeKey(Constants.TENANT_TYPE_KEY);
        tenantEntity.setStateKey(null);
        tenantEntity.setData(null);
        xmEntityRepository.save(tenantEntity);

        int databaseSizeBeforeUpdate = xmEntityRepository.findAll().size();

        // Update the xmEntity
        XmEntity updatedTenantEntity = createEntity(em);
        updatedTenantEntity.setId(tenantEntity.getId());
        updatedTenantEntity.setTypeKey(Constants.TENANT_TYPE_KEY);
        updatedTenantEntity.setStateKey(null);
        updatedTenantEntity.setData(null);
        updatedTenantEntity.setName("updatedName");

        restXmEntityMockMvc.perform(put("/api/xm-entities")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(updatedTenantEntity)))
            .andExpect(status().isOk());

        // Validate the XmEntity in the database
        List<XmEntity> xmEntityList = xmEntityRepository.findAll();
        assertThat(xmEntityList).hasSize(databaseSizeBeforeUpdate);
        XmEntity testXmEntity = xmEntityList.get(xmEntityList.size() - 1);
        assertThat(testXmEntity.getName()).isEqualTo(DEFAULT_NAME);
    }


    @Test
    @Transactional
    public void checkUpdateDateIsRequiredInDb() throws Exception {

        // set the field null
        when(startUpdateDateGenerationStrategy.generateUpdateDate()).thenReturn(null);

        // Create the XmEntity.
        xmEntityService.save(xmEntityIncoming);

        try {
            xmEntityRepository.flush();
            fail("DataIntegrityViolationException exception was expected!");
        } catch (DataIntegrityViolationException e) {
            assertThat(e.getMostSpecificCause().getMessage())
                .containsIgnoringCase("NULL not allowed for column \"UPDATE_DATE\"");
        }

    }


    @Test
    @Transactional
    public void checkStartDateIsNotRequired() throws Exception {
        int databaseSizeBeforeTest = xmEntityRepository.findAll().size();
        // set the field null
        xmEntityIncoming.setStartDate(null);

        // Create the XmEntity.

        restXmEntityMockMvc.perform(post("/api/xm-entities")
                                        .contentType(TestUtil.APPLICATION_JSON_UTF8)
                                        .content(TestUtil.convertObjectToJsonBytes(xmEntityIncoming)))
                           .andExpect(status().isCreated())
                           .andExpect(jsonPath("$.startDate").value(MOCKED_START_DATE.toString()))
                           .andExpect(jsonPath("$.updateDate").value(MOCKED_UPDATE_DATE.toString()))
        ;

        List<XmEntity> xmEntityList = xmEntityRepository.findAll();
        assertThat(xmEntityList).hasSize(databaseSizeBeforeTest + 1);
    }

    @Test
    @Transactional
    public void checkUpdateDateIsNotRequired() throws Exception {
        int databaseSizeBeforeTest = xmEntityRepository.findAll().size();
        // set the field null
        xmEntityIncoming.setUpdateDate(null);

        // Create the XmEntity.

        restXmEntityMockMvc.perform(post("/api/xm-entities")
                                        .contentType(TestUtil.APPLICATION_JSON_UTF8)
                                        .content(TestUtil.convertObjectToJsonBytes(xmEntityIncoming)))
                           .andExpect(status().isCreated())
                           .andExpect(jsonPath("$.startDate").value(MOCKED_START_DATE.toString()))
                           .andExpect(jsonPath("$.updateDate").value(MOCKED_UPDATE_DATE.toString()))
        ;

        List<XmEntity> xmEntityList = xmEntityRepository.findAll();
        assertThat(xmEntityList).hasSize(databaseSizeBeforeTest + 1);
    }

    @Test(expected = JsonMappingException.class)
    @Transactional
    public void testNoCycleJson() throws Exception {
        XmEntity target = new XmEntity().typeKey("TARGET");
        XmEntity source = new XmEntity().typeKey("SOURCE");
        source.targets(Collections.singleton(
            new Link().typeKey("LINK1")
                      .source(source)
                      .target(target)
        )).setId(1L);
        target.targets(Collections.singleton(
            new Link().typeKey("LINK2")
                      .source(source)
                      .target(target)
        )).setId(2L);
        String targetJson = jacksonMessageConverter.getObjectMapper().writeValueAsString(target);
        String sourceJson = jacksonMessageConverter.getObjectMapper().writeValueAsString(source);
        assertEquals("1", JsonPath.read(targetJson, "$.targets[0].id"));
        assertEquals("2", JsonPath.read(sourceJson, "$.targets[0].id"));
    }


    @Test
    @Transactional
    public void testAttachmentStartDate() throws Exception {

        Attachment attachment = new Attachment().typeKey("A").name("1");
        XmEntity entity = new XmEntity().name(" ").key(randomUUID()).typeKey("TEST_DELETE").attachments(asSet(
            attachment
        ));

        MutableObject<Instant> startDate = new MutableObject<>();

        MutableObject<XmEntity> entityHolder = new MutableObject<>();

        byte[] content = TestUtil.convertObjectToJsonBytes(entity);
        restXmEntityMockMvc.perform(post("/api/xm-entities")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(content))
            .andDo(r -> entityHolder.setValue(readValue(r)))
            .andDo(r -> log.info(r.getResponse().getContentAsString()))
            .andExpect(status().is2xxSuccessful());

        Long id = entityHolder.getValue().getId();
        restXmEntityMockMvc.perform(get("/api/xm-entities/" + id))
            .andDo(r -> startDate.setValue(readValue(r).getAttachments().iterator().next().getStartDate()))
            .andDo(r -> log.info(r.getResponse().getContentAsString()))
            .andExpect(status().is2xxSuccessful());

        assertNotNull(startDate.getValue());

        content = TestUtil.convertObjectToJsonBytes(entityHolder.getValue());
        restXmEntityMockMvc.perform(put("/api/xm-entities")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(content))
            .andDo(r -> log.info(r.getResponse().getContentAsString()))
            .andExpect(status().is2xxSuccessful());


        restXmEntityMockMvc.perform(get("/api/xm-entities/" + id))
            .andDo(r -> log.info(r.getResponse().getContentAsString()))
            .andDo(r -> assertEquals(startDate.getValue(), readValue(r).getAttachments().iterator().next().getStartDate()))
            .andExpect(status().is2xxSuccessful());
    }

    @Test
    @Transactional
    @WithMockUser(authorities = "SUPER-ADMIN")
    public void testExportCsvFile() throws Exception {
        createEntityComplexPersisted(em);
        performGet("/api/xm-entities/export?typeKey=ACCOUNT&fileFormat=csv").andExpect(
                        status().isOk()).andExpect(
                        content().contentType(MediaType.parseMediaType("text/csv")));
    }

    @Test
    @Transactional
    @WithMockUser(authorities = "SUPER-ADMIN")
    public void testExportExcelFile() throws Exception {
        createEntityComplexPersisted(em);
        performGet("/api/xm-entities/export?typeKey=ACCOUNT&fileFormat=xlsx")
                        .andExpect(status().isOk())
                        .andExpect(content()
                                        .contentType(MediaType
                                                        .parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")));
    }

    @SneakyThrows
    private XmEntity readValue(MvcResult r) {
        return jacksonMessageConverter.getObjectMapper().readValue(r.getResponse().getContentAsString(), XmEntity.class);
    }

}
