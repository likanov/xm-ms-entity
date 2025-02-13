package com.icthh.xm.ms.entity.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.module.jsonSchema.JsonSchemaGenerator;
import com.github.fge.jackson.JsonLoader;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import com.icthh.xm.commons.config.client.config.XmConfigProperties;
import com.icthh.xm.commons.config.client.repository.CommonConfigRepository;
import com.icthh.xm.commons.config.client.repository.TenantConfigRepository;
import com.icthh.xm.commons.config.domain.Configuration;
import com.icthh.xm.commons.permission.config.PermissionProperties;
import com.icthh.xm.commons.permission.domain.Role;
import com.icthh.xm.commons.permission.service.RoleService;
import com.icthh.xm.commons.tenant.TenantContext;
import com.icthh.xm.commons.tenant.TenantContextHolder;
import com.icthh.xm.commons.tenant.TenantKey;
import com.icthh.xm.ms.entity.AbstractUnitTest;
import com.icthh.xm.ms.entity.config.ApplicationProperties;
import com.icthh.xm.ms.entity.config.XmEntityTenantConfigService;
import com.icthh.xm.ms.entity.config.XmEntityTenantConfigService.XmEntityTenantConfig;
import com.icthh.xm.ms.entity.config.tenant.LocalXmEntitySpecService;
import com.icthh.xm.ms.entity.domain.spec.AttachmentSpec;
import com.icthh.xm.ms.entity.domain.spec.FunctionSpec;
import com.icthh.xm.ms.entity.domain.spec.LinkSpec;
import com.icthh.xm.ms.entity.domain.spec.LocationSpec;
import com.icthh.xm.ms.entity.domain.spec.NextSpec;
import com.icthh.xm.ms.entity.domain.spec.RatingSpec;
import com.icthh.xm.ms.entity.domain.spec.StateSpec;
import com.icthh.xm.ms.entity.domain.spec.TagSpec;
import com.icthh.xm.ms.entity.domain.spec.TypeSpec;
import com.icthh.xm.ms.entity.domain.spec.UniqueFieldSpec;
import com.icthh.xm.ms.entity.domain.spec.XmEntitySpec;
import com.icthh.xm.ms.entity.security.access.DynamicPermissionCheckService;
import com.icthh.xm.ms.entity.service.privileges.custom.ApplicationCustomPrivilegesExtractor;
import com.icthh.xm.ms.entity.service.privileges.custom.EntityCustomPrivilegeService;
import com.icthh.xm.ms.entity.service.privileges.custom.FunctionCustomPrivilegesExtractor;
import com.icthh.xm.ms.entity.service.privileges.custom.FunctionWithXmEntityCustomPrivilegesExtractor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.core.io.ClassPathResource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.collect.ImmutableMap.of;
import static com.google.common.collect.Maps.newHashMap;
import static com.icthh.xm.ms.entity.config.Constants.REGEX_EOL;
import static com.icthh.xm.ms.entity.config.TenantConfigMockConfiguration.getXmEntitySpec;
import static com.icthh.xm.ms.entity.security.access.DynamicPermissionCheckService.CONFIG_SECTION;
import static com.icthh.xm.ms.entity.util.CustomCollectionUtils.nullSafe;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static org.apache.commons.codec.digest.DigestUtils.sha1Hex;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@Slf4j
public class XmEntitySpecServiceUnitTest extends AbstractUnitTest {

    private static final String DYNAMIC_FUNCTION_PERMISSION_FEATURE = "dynamicPermissionCheckEnabled";

    private static final String TENANT = "TEST";

    private static final String SPEC_FOLDER_URL = "/config/tenants/{tenantName}/entity/specs/xmentityspecs";

    private static final String URL = SPEC_FOLDER_URL + ".yml";

    private static final String ROOT_KEY = "";

    private static final String KEY1 = "TYPE1";

    private static final String KEY2 = "TYPE2";

    private static final String KEY3 = "TYPE1.SUBTYPE1";

    private static final String KEY4 = "TYPE1.SUBTYPE1.SUBTYPE2";

    private static final String KEY5 = "TYPE1-OTHER";

    private static final String KEY6 = "TYPE3";
    private static final String PRIVILEGES_PATH = "/config/tenants/TEST/custom-privileges.yml";
    private static final Path SPEC_PATH = Paths.get("./src/test/resources/config/specs/xmentityspec-xm.yml");

    private XmEntitySpecService xmEntitySpecService;

    private TenantContextHolder tenantContextHolder;

    @Mock
    private CommonConfigRepository commonConfigRepository;
    private PermissionProperties permissionProperties = new PermissionProperties();
    @Mock
    private RoleService roleService;
    @Mock
    private TenantConfigRepository tenantConfigRepository;
    @InjectMocks
    @Spy
    private DynamicPermissionCheckService dynamicPermissionCheckService;
    @Spy
    private XmEntityTenantConfigService tenantConfig = new XmEntityTenantConfigService(new XmConfigProperties(),
                                                                                       tenantContextHolder());

    private TenantContextHolder tenantContextHolder() {
        TenantContextHolder mock = mock(TenantContextHolder.class);
        when(mock.getTenantKey()).thenReturn("XM");
        return mock;
    }

    @Before
    @SneakyThrows
    public void init() {
        MockitoAnnotations.initMocks(this);

        tenantContextHolder = mock(TenantContextHolder.class);
        mockTenant(TENANT);

        ApplicationProperties ap = new ApplicationProperties();
        ap.setSpecificationPathPattern(URL);
        ap.setSpecificationFolderPathPattern(SPEC_FOLDER_URL + "/*");
        xmEntitySpecService = createXmEntitySpecService(ap, tenantContextHolder);
    }

    private XmEntitySpecService createXmEntitySpecService(ApplicationProperties applicationProperties,
                                                                TenantContextHolder tenantContextHolder) {

        return new LocalXmEntitySpecService(tenantConfigRepository,
                                            applicationProperties,
                                            tenantContextHolder,
                                            new EntityCustomPrivilegeService(
                                                commonConfigRepository,
                                                permissionProperties,
                                                roleService,
                                                asList(
                                                    new ApplicationCustomPrivilegesExtractor(),
                                                    new FunctionCustomPrivilegesExtractor(tenantConfig),
                                                    new FunctionWithXmEntityCustomPrivilegesExtractor(tenantConfig)
                                                )
                                            ),
                                            dynamicPermissionCheckService,
                                            tenantConfig);
    }

    @Test
    public void testTypeSpecByKey() {
        TypeSpec type = xmEntitySpecService.getTypeSpecByKey(KEY1).orElse(null);
        assertNotNull(type);
        assertEquals(KEY1, type.getKey());

        type = xmEntitySpecService.getTypeSpecByKey(KEY3).orElse(null);
        assertNotNull(type);
        assertEquals(KEY3, type.getKey());

        Optional<TypeSpec> typeSpecByKey = xmEntitySpecService.getTypeSpecByKey(KEY4);
        assertThat(typeSpecByKey).isEmpty();
    }

    @Test
    public void testFindSpecByKey() {
        TypeSpec type = xmEntitySpecService.findTypeByKey(KEY1);
        assertNotNull(type);
        assertEquals(KEY1, type.getKey());

        type = xmEntitySpecService.findTypeByKey(KEY3);
        assertNotNull(type);
        assertEquals(KEY3, type.getKey());

        type = xmEntitySpecService.findTypeByKey(KEY4);
        assertThat(type).isNull();
    }

    @Test
    public void testFindAllTypes() {

        prepareConfig(newHashMap());
        List<TypeSpec> types = xmEntitySpecService.findAllTypes();
        assertNotNull(types);
        assertEquals(5, types.size());

        List<String> keys = types.stream().map(TypeSpec::getKey).collect(Collectors.toList());
        assertThat(keys).containsExactlyInAnyOrder(KEY1, KEY2, KEY3, KEY5, KEY6);

        List<FunctionSpec> functions = flattenFunctions(types);

        assertThat(functions.size()).isEqualTo(4);

    }

    @Test
    public void testRefreshWithIgnoreList() {

        mockTenant("IGNORE-INHERITANCE");
        ApplicationProperties ap = new ApplicationProperties();
        ap.setSpecificationPathPattern(URL);
        xmEntitySpecService = createXmEntitySpecService(ap, tenantContextHolder);

        Map<String, TypeSpec> typeSpecs = xmEntitySpecService.getTypeSpecs();

        TypeSpec typeSpec = typeSpecs.get("ACCOUNT.ADMIN");
        assertEquals(typeSpec.getFunctions().size(), 2);
        assertTrue(typeSpec.getFunctions().stream().allMatch(f-> Set.of("C", "D").contains(f.getKey())));
        assertEquals(typeSpec.getTags().size(), 2);
        assertTrue(typeSpec.getTags().stream().allMatch(t-> Set.of("TEST2", "TEST3").contains(t.getKey())));
        assertEquals(typeSpec.getStates().size(), 4);
    }

    @SneakyThrows
    public void prepareConfig(Map<String, Object> map) {
        tenantConfig.onRefresh("/config/tenants/XM/tenant-config.yml", new ObjectMapper().writeValueAsString(map));
    }

    @Test
    public void testFindAllTypesWithFunctionFilterAndNoPrivilege() {
        prepareConfig(getMockedConfig(CONFIG_SECTION,
            DYNAMIC_FUNCTION_PERMISSION_FEATURE, Boolean.TRUE));

        List<TypeSpec> types = xmEntitySpecService.findAllTypes();
        assertNotNull(types);
        assertEquals(5, types.size());

        List<String> keys = types.stream().map(TypeSpec::getKey).collect(Collectors.toList());
        assertThat(keys).containsExactlyInAnyOrder(KEY1, KEY2, KEY3, KEY5, KEY6);

        List<FunctionSpec> functions = flattenFunctions(types);

        assertThat(functions.size()).isEqualTo(0);
    }

    @Test
    public void testFindAllAppTypes() {
        prepareConfig(newHashMap());
        List<TypeSpec> types = xmEntitySpecService.findAllAppTypes();
        assertNotNull(types);
        assertEquals(3, types.size());

        List<String> keys = types.stream().map(TypeSpec::getKey).collect(Collectors.toList());
        assertThat(keys).containsExactlyInAnyOrder(KEY1, KEY2, KEY6);

        List<FunctionSpec> functions = flattenFunctions(types);
        assertThat(functions.size()).isEqualTo(3);
    }

    @Test
    public void testFindAllAppTypesWithFunctionFilterAndNoPrivilege() {
        prepareConfig(getMockedConfig(CONFIG_SECTION,
            DYNAMIC_FUNCTION_PERMISSION_FEATURE, Boolean.TRUE));
        List<TypeSpec> types = xmEntitySpecService.findAllAppTypes();
        assertNotNull(types);
        assertEquals(3, types.size());

        List<String> keys = types.stream().map(TypeSpec::getKey).collect(Collectors.toList());
        assertThat(keys).containsExactlyInAnyOrder(KEY1, KEY2, KEY6);

        List<FunctionSpec> functions = flattenFunctions(types);

        assertThat(functions.size()).isEqualTo(0);
    }

    @Test
    public void testFindAllNonAbstractTypes() {
        prepareConfig(newHashMap());
        List<TypeSpec> types = xmEntitySpecService.findAllNonAbstractTypes();
        assertNotNull(types);
        assertEquals(4, types.size());

        List<String> keys = types.stream().map(TypeSpec::getKey).collect(Collectors.toList());
        assertThat(keys).containsExactlyInAnyOrder(KEY2, KEY3, KEY5, KEY6);

        List<FunctionSpec> functions = flattenFunctions(types);
        assertThat(functions.size()).isEqualTo(3);
    }

    @Test
    public void testFindAllNonAbstractTypesWithFunctionFilterAndNoPrivilege() {
        prepareConfig(getMockedConfig(CONFIG_SECTION,
            DYNAMIC_FUNCTION_PERMISSION_FEATURE, Boolean.TRUE));

        List<TypeSpec> types = xmEntitySpecService.findAllNonAbstractTypes();
        assertNotNull(types);
        assertEquals(4, types.size());

        List<String> keys = types.stream().map(TypeSpec::getKey).collect(Collectors.toList());
        assertThat(keys).containsExactlyInAnyOrder(KEY2, KEY3, KEY5, KEY6);

        List<FunctionSpec> functions = flattenFunctions(types);
        assertThat(functions.size()).isEqualTo(0);
    }

    @Test
    public void testFindNonAbstractTypesByPrefix() {
        List<TypeSpec> types = xmEntitySpecService.findNonAbstractTypesByPrefix(KEY1);
        assertNotNull(types);
        assertEquals(1, types.size());

        List<String> keys = types.stream().map(TypeSpec::getKey).collect(Collectors.toList());
        assertThat(keys).containsExactlyInAnyOrder(KEY3);
    }

    @Test
    public void testFindNonAbstractTypesByRootPrefix() {
        List<TypeSpec> types = xmEntitySpecService.findNonAbstractTypesByPrefix(ROOT_KEY);
        assertNotNull(types);
        assertEquals(4, types.size());

        List<String> keys = types.stream().map(TypeSpec::getKey).collect(Collectors.toList());
        assertThat(keys).containsExactlyInAnyOrder(KEY2, KEY3, KEY5, KEY6);
    }

    @Test
    public void testFindNonAbstractTypesByPrefixEqualKey() {
        List<TypeSpec> types = xmEntitySpecService.findNonAbstractTypesByPrefix(KEY2);
        assertNotNull(types);
        assertEquals(1, types.size());

        List<String> keys = types.stream().map(TypeSpec::getKey).collect(Collectors.toList());
        assertThat(keys).containsExactlyInAnyOrder(KEY2);
    }

    @Test
    public void testFindAttachment() {
        Optional<AttachmentSpec> attachment = xmEntitySpecService.findAttachment(KEY3, "PDF");
        assertTrue(attachment.isPresent());
        assertEquals("PDF", attachment.get().getKey());
    }

    @Test
    public void testFindLink() {
        Optional<LinkSpec> link = xmEntitySpecService.findLink(KEY3, "LINK1");
        assertTrue(link.isPresent());
        assertEquals("LINK1", link.get().getKey());
    }

    @Test
    public void testFindLocation() {
        Optional<LocationSpec> location = xmEntitySpecService.findLocation(KEY2, "LOCATION1");
        assertTrue(location.isPresent());
        assertEquals("LOCATION1", location.get().getKey());
    }

    @Test
    public void testFindRating() {
        Optional<RatingSpec> rating = xmEntitySpecService.findRating(KEY3, "RATING1");
        assertTrue(rating.isPresent());
        assertEquals("RATING1", rating.get().getKey());
    }

    @Test
    public void testFindState() {
        Optional<StateSpec> state = xmEntitySpecService.findState(KEY3, "STATE2");
        assertTrue(state.isPresent());
        assertEquals("STATE2", state.get().getKey());
    }

    @Test
    public void testNext() {
        List<NextSpec> states = xmEntitySpecService.next(KEY3, "STATE2");
        assertNotNull(states);
        assertEquals(2, states.size());
        List<String> keys = states.stream().map(NextSpec::getStateKey).collect(Collectors.toList());
        assertThat(keys).containsExactlyInAnyOrder("STATE1", "STATE3");
    }

    @Test
    public void testNextStates() {
        List<StateSpec> states = xmEntitySpecService.nextStates(KEY3, "STATE2");
        assertNotNull(states);
        assertEquals(2, states.size());
        List<String> keys = states.stream().map(StateSpec::getKey).collect(Collectors.toList());
        assertThat(keys).containsExactlyInAnyOrder("STATE1", "STATE3");
    }

    @Test
    public void testFindTag() {
        Optional<TagSpec> tag = xmEntitySpecService.findTag(KEY3, "TAG1");
        assertTrue(tag.isPresent());
        assertEquals("TAG1", tag.get().getKey());
    }

    @Test
    public void testGetAllKeys() {
        Map<String, Map<String, Set<String>>> keys = xmEntitySpecService.getAllKeys();
        assertNotNull(keys);
        assertEquals(5, keys.size());
        assertEquals(1, keys.get("TYPE1").get("LinkSpec").size());
        assertEquals(3, keys.get("TYPE1").get("StateSpec").size());
    }

    @Test
    public void testUniqueField() {
        mockTenant("RESINTTEST");
        ApplicationProperties ap = new ApplicationProperties();
        ap.setSpecificationPathPattern(URL);
        xmEntitySpecService = createXmEntitySpecService(ap, tenantContextHolder);

        for(TypeSpec typeSpec: xmEntitySpecService.getTypeSpecs().values()) {
            if (typeSpec.getKey().equals("TEST_UNIQUE_FIELD")) {
                continue;
            }
            if (typeSpec.getKey().equals("TEST_UNIQ_FIELDS")) {
                assertEquals(typeSpec.getUniqueFields().size(), 4);
                assertTrue(typeSpec.getUniqueFields().contains(new UniqueFieldSpec("$.uniqField")));
                assertTrue(typeSpec.getUniqueFields().contains(new UniqueFieldSpec("$.notUniqObject.uniqueField")));
                assertTrue(typeSpec.getUniqueFields().contains(new UniqueFieldSpec("$.uniqObject")));
                assertTrue(typeSpec.getUniqueFields().contains(new UniqueFieldSpec("$.uniqObject.uniqueField")));
            } else {
                assertEquals(typeSpec.getUniqueFields().size(), 0);
            }
        }
    }

    public void mockTenant(String resinttest) {
        TenantContext tenantContext = mock(TenantContext.class);
        when(tenantContext.getTenantKey()).thenReturn(Optional.of(TenantKey.valueOf(resinttest)));
        when(tenantContextHolder.getContext()).thenReturn(tenantContext);
        when(tenantContextHolder.getTenantKey()).thenReturn(resinttest);
    }


    private void enableDynamicPermissionCheck() {
        XmEntityTenantConfig config = new XmEntityTenantConfig();
        when(tenantConfig.getXmEntityTenantConfig("TEST")).thenReturn(config);
        config.getEntityFunctions().setDynamicPermissionCheckEnabled(true);
    }

    @Test
    @SneakyThrows
    public void testUpdateCustomerPrivileges() {
        String customPrivileges = readFile("config/privileges/custom-privileges.yml");
        String expectedCustomPrivileges = readFile("config/privileges/expected-custom-privileges.yml");

        testUpdateCustomerPrivileges(customPrivileges, expectedCustomPrivileges);
    }

    @Test
    @SneakyThrows
    public void testUpdateCustomerPrivilegesWithEntity() {
        enableDynamicPermissionCheck();

        String customPrivileges = readFile("config/privileges/custom-privileges-with-function.yml");
        String expectedCustomPrivileges = readFile("config/privileges/expected-custom-privileges-with-function.yml");

        testUpdateCustomerPrivileges(customPrivileges, expectedCustomPrivileges);
    }

    private void testUpdateCustomerPrivileges(String customPrivileges, String expectedCustomPrivileges) {
        String privilegesPath = PRIVILEGES_PATH;
        Map<String, Configuration> configs = of(
            privilegesPath, new Configuration(privilegesPath, customPrivileges)
                                               );
        when(commonConfigRepository.getConfig(isNull(), eq(asList(privilegesPath)))).thenReturn(configs);
        when(roleService.getRoles("TEST")).thenReturn(of("TEST_ROLE", new Role()));

        xmEntitySpecService.getTypeSpecs();

        verify(commonConfigRepository).getConfig(isNull(), eq(asList(privilegesPath)));
        verify(commonConfigRepository).updateConfigFullPath(refEq(new Configuration(privilegesPath, expectedCustomPrivileges)), eq(sha1Hex(customPrivileges)));
        verifyNoMoreInteractions(commonConfigRepository);
    }

    @Test
    @SneakyThrows
    public void testCreateCustomPrivileges() {
        String privileges = readFile("config/privileges/new-privileges.yml");

        testCreateCustomPrivileges(privileges);
    }

    @Test
    @SneakyThrows
    public void testCreateCustomerPrivilegesWithFunctions() {
        enableDynamicPermissionCheck();

        String privileges = readFile("config/privileges/new-privileges-with-functions.yml");

        testCreateCustomPrivileges(privileges);
    }

    private void testCreateCustomPrivileges(String privileges) {
        String privilegesPath = PRIVILEGES_PATH;
        when(commonConfigRepository.getConfig(isNull(), eq(asList(privilegesPath)))).thenReturn(null);
        when(roleService.getRoles("TEST")).thenReturn(of("ROLE_ADMIN", new Role(), "ROLE_AGENT", new Role()));

        xmEntitySpecService.getTypeSpecs();

        verify(commonConfigRepository).getConfig(isNull(), eq(asList(privilegesPath)));
        verify(commonConfigRepository)
            .updateConfigFullPath(refEq(new Configuration(privilegesPath, privileges)), isNull());
        verifyNoMoreInteractions(commonConfigRepository);
    }

    @Test
    @SneakyThrows
    public void testUpdateRealPermissionFile() {
        String privileges = readFile("config/privileges/new-privileges.yml");

        testUpdateRealPermissionFile(privileges);
    }

    @Test
    @SneakyThrows
    public void testUpdateRealPermissionFileWithXmEntity() {
        enableDynamicPermissionCheck();

        String privileges = readFile("config/privileges/new-privileges-with-functions.yml");

        testUpdateRealPermissionFile(privileges);
    }

    @Test
    @SneakyThrows
    public void testXmEntitySpecSchemaGeneration() {
        String jsonSchema = xmEntitySpecService.generateJsonSchema();
        ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
        JsonNode xmentityspec = objectMapper.readTree(new File(SPEC_PATH.toString()));

        JsonNode schemaNode = JsonLoader.fromString(jsonSchema);
        JsonSchemaFactory factory = JsonSchemaFactory.byDefault();
        JsonSchema schema = factory.getJsonSchema(schemaNode);
        ProcessingReport report = schema.validate(xmentityspec);

        boolean isSuccess = report.isSuccess();
        assertTrue(report.toString(), isSuccess);
    }

    public void testUpdateRealPermissionFile(String privileges) {
        String privilegesPath = PRIVILEGES_PATH;
        Map<String, Configuration> configs = of();
        when(commonConfigRepository.getConfig(isNull(), eq(asList(privilegesPath)))).thenReturn(configs);
        when(roleService.getRoles("TEST")).thenReturn(of(
            "ROLE_ADMIN", new Role(),
            "ROLE_AGENT", new Role()
        ));

        xmEntitySpecService.getTypeSpecs();

        verify(commonConfigRepository).getConfig(isNull(), eq(asList(privilegesPath)));
        verify(commonConfigRepository).updateConfigFullPath(refEq(new Configuration(privilegesPath, privileges)), isNull());
        verifyNoMoreInteractions(commonConfigRepository);
    }

    @Test
    public void testExtendsWithSeparateFiles() {
        mockTenant("RESINTTEST");
        String config = getXmEntitySpec("resinttest-part-2");
        String key = SPEC_FOLDER_URL.replace("{tenantName}", "RESINTTEST") + "/file.yml";
        xmEntitySpecService.onRefresh(key, config);
        // refresh multiple times (important)
        xmEntitySpecService.getTypeSpecs();
        xmEntitySpecService.getTypeSpecs();
        var typeSpecs = xmEntitySpecService.getTypeSpecs();

        TypeSpec extendedEntity = typeSpecs.get("BASE_ENTITY.EXTENDS_ENABLED");
        assertEquals(extendedEntity.getFunctions().size(), 1);
        TypeSpec extendedEntityFromSeparateFile = typeSpecs.get("BASE_ENTITY.SEPARATE_FILE_EXTENDS_ENABLED");
        assertEquals(extendedEntityFromSeparateFile.getFunctions().size(), 1);
    }

    @Test
    public void testExtendsDataSpecWithSeparateFiles() {
        String config = getXmEntitySpec("resinttest-part-2");
        String key = SPEC_FOLDER_URL.replace("{tenantName}", "RESINTTEST") + "/file.yml";
        xmEntitySpecService.onRefresh(key, config);

        testExtendsDataSpec();

        var bothEntityData = Map.of(
                "field1", "field1value",
                "field5", "field4value",
                "object1", Map.of(
                        "field2", "field2value",
                        "field6", "field6value"
                )
        );

        setExtendsFormAndSpec(false);
        var typeSpecs = xmEntitySpecService.getTypeSpecs();
        TypeSpec extendedEntity = typeSpecs.get("BASE_ENTITY.SEPARATE_FILE_EXTENDS_ENABLED");
        assertTrue(validateByJsonSchema(extendedEntity.getDataSpec(), bothEntityData));

        setExtendsFormAndSpec(true);
        TypeSpec extendedEntityWithGlobalExtends = typeSpecs.get("BASE_ENTITY.SEPARATE_FILE_EXTENDS_ENABLED");
        assertTrue(validateByJsonSchema(extendedEntityWithGlobalExtends.getDataSpec(), bothEntityData));
    }

    @Test
    public void testExtendsDataSpec() {

        mockTenant("RESINTTEST");

        var baseEntityData = Map.of(
                "field1", "field1value",
                "object1", Map.of(
                        "field2", "field2value"
                )
        );
        var extendsEntityData = Map.of(
                "field3", "field3value",
                "object1", Map.of(
                        "field4", "field4value"
                )
        );
        var bothEntityData = Map.of(
                "field1", "field1value",
                "field3", "field3value",
                "object1", Map.of(
                        "field2", "field2value",
                        "field4", "field4value"
                )
        );

        var typeSpecs = xmEntitySpecService.getTypeSpecs();
        TypeSpec baseEntity = typeSpecs.get("BASE_ENTITY");
        TypeSpec extendedEntity = typeSpecs.get("BASE_ENTITY.EXTENDS");
        assertTrue(validateByJsonSchema(baseEntity.getDataSpec(), baseEntityData));
        assertTrue(validateByJsonSchema(extendedEntity.getDataSpec(), extendsEntityData));
        assertFalse(validateByJsonSchema(baseEntity.getDataSpec(), extendsEntityData));
        assertFalse(validateByJsonSchema(extendedEntity.getDataSpec(), baseEntityData));
        assertFalse(validateByJsonSchema(extendedEntity.getDataSpec(), bothEntityData));

        TypeSpec extendedEntityWithEnabledExtends = typeSpecs.get("BASE_ENTITY.EXTENDS_ENABLED");
        assertTrue(validateByJsonSchema(extendedEntityWithEnabledExtends.getDataSpec(), extendsEntityData));
        assertTrue(validateByJsonSchema(extendedEntityWithEnabledExtends.getDataSpec(), bothEntityData));

        setExtendsFormAndSpec(true);

        var typeSpecsWithExtends = xmEntitySpecService.getTypeSpecs();
        TypeSpec baseEntityWithExtends = typeSpecsWithExtends.get("BASE_ENTITY");
        TypeSpec extendedEntityWithExtends = typeSpecsWithExtends.get("BASE_ENTITY.EXTENDS");
        assertTrue(validateByJsonSchema(baseEntityWithExtends.getDataSpec(), baseEntityData));
        assertTrue(validateByJsonSchema(extendedEntityWithExtends.getDataSpec(), extendsEntityData));
        assertFalse(validateByJsonSchema(baseEntity.getDataSpec(), extendsEntityData));
        assertTrue(validateByJsonSchema(extendedEntityWithExtends.getDataSpec(), baseEntityData));
        assertTrue(validateByJsonSchema(extendedEntityWithExtends.getDataSpec(), bothEntityData));
    }

    @Test
    public void testExtendsDataForm() {

        mockTenant("RESINTTEST");

        var baseDataFrom = Map.of(
                "form", List.of(
                Map.of("key", "field1"),
                Map.of("key", "object1.field2")
            )
        );

        var extendsDataFrom = Map.of(
                "form", List.of(
                        Map.of("key", "field3"),
                        Map.of("key", "object1.field4")
                )
        );

        var bothDataFrom = Map.of(
                "form", List.of(
                        Map.of("key", "field1"),
                        Map.of("key", "object1.field2"),
                        Map.of("key", "field3"),
                        Map.of("key", "object1.field4")
                )
        );

        var typeSpecs = xmEntitySpecService.getTypeSpecs();
        TypeSpec baseEntity = typeSpecs.get("BASE_ENTITY");
        TypeSpec extendedEntity = typeSpecs.get("BASE_ENTITY.EXTENDS");
        TypeSpec extendedEntityWithEnabledExtends = typeSpecs.get("BASE_ENTITY.EXTENDS_ENABLED");
        assertEqualsJson(baseDataFrom, baseEntity.getDataForm());
        assertEqualsJson(extendsDataFrom, extendedEntity.getDataForm());
        assertEqualsJson(bothDataFrom, extendedEntityWithEnabledExtends.getDataForm());

        setExtendsFormAndSpec(true);

        var typeSpecsWithExtends = xmEntitySpecService.getTypeSpecs();
        TypeSpec baseEntityWithExtends = typeSpecsWithExtends.get("BASE_ENTITY");
        TypeSpec extendedEntityWithExtends = typeSpecsWithExtends.get("BASE_ENTITY.EXTENDS");
        assertEqualsJson(baseDataFrom, baseEntityWithExtends.getDataForm());
        assertEqualsJson(bothDataFrom, extendedEntityWithExtends.getDataForm());
    }

    @SneakyThrows
    private void assertEqualsJson(Map<String, ?> expected, String actual) {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode expectedTree = objectMapper.readTree(objectMapper.writeValueAsString(expected));
        JsonNode actualTree = objectMapper.readTree(actual);
        assertEquals(expectedTree, actualTree);
    }

    @SneakyThrows
    private boolean validateByJsonSchema(String schema, Map<String, Object> value) {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode valueJsonNode = objectMapper.readTree(objectMapper.writeValueAsString(value));
        JsonNode schemaNode = JsonLoader.fromString(schema);
        JsonSchemaFactory factory = JsonSchemaFactory.byDefault();
        JsonSchema jsonSchema = factory.getJsonSchema(schemaNode);
        ProcessingReport report = jsonSchema.validate(valueJsonNode);
        log.info("Validation: {}", report.toString());
        return report.isSuccess();
    }

    private void setExtendsFormAndSpec(Boolean value) {
        XmEntityTenantConfig config = new XmEntityTenantConfig();
        config.getEntitySpec().setEnableDataFromInheritance(value);
        config.getEntitySpec().setEnableDataSpecInheritance(value);
        when(tenantConfig.getXmEntityTenantConfig("RESINTTEST")).thenReturn(config);
    }

    private String readFile(String path1) throws IOException {
        InputStream cfgInputStream = new ClassPathResource(path1).getInputStream();
        return IOUtils.toString(cfgInputStream, UTF_8);
    }

    private Map<String, Object> getMockedConfig(String configSectionName, String featureName, Boolean status) {
        Map<String, Object> map = newHashMap();
        Map<String, Object> section = newHashMap();
        section.put(featureName, status);
        map.put(configSectionName, section);
        return map;
    }

    private List<FunctionSpec> flattenFunctions(List<TypeSpec> types) {
        return types.stream()
            .map(type -> nullSafe(type.getFunctions()) )
            .flatMap(List::stream)
            .collect(Collectors.toList());
    }
}
