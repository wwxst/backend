package com.web.project.support;

import com.web.project.admin.entity.AdminUser;
import com.web.project.admin.mapper.AdminUserMapper;
import com.web.project.auth.service.JwtTokenService;
import com.web.project.product.entity.Product;
import com.web.project.product.entity.ProductPlan;
import com.web.project.product.mapper.ProductMapper;
import com.web.project.product.mapper.ProductPlanMapper;
import com.web.project.redeem.entity.RedeemCode;
import com.web.project.redeem.entity.RedeemCodeBatch;
import com.web.project.redeem.mapper.RedeemCodeBatchMapper;
import com.web.project.redeem.mapper.RedeemCodeMapper;
import com.web.project.redeem.mapper.RedeemRecordMapper;
import com.web.project.redeem.support.RedeemCodeGenerator;
import com.web.project.subscription.mapper.UserSubscriptionMapper;
import com.web.project.user.entity.UserAccount;
import com.web.project.user.mapper.UserAccountMapper;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class BaseSpringBootTest {

    @LocalServerPort
    protected int port;

    protected RestTemplate restTemplate = createRestTemplate();

    private static RestTemplate createRestTemplate() {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
        RestTemplate rt = new RestTemplate(factory);
        return rt;
    }

    @Autowired
    protected JwtTokenService jwtTokenService;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected AdminUserMapper adminUserMapper;

    @Autowired
    protected UserAccountMapper userAccountMapper;

    @Autowired
    protected ProductMapper productMapper;

    @Autowired
    protected ProductPlanMapper productPlanMapper;

    @Autowired
    protected RedeemCodeBatchMapper redeemCodeBatchMapper;

    @Autowired
    protected RedeemCodeMapper redeemCodeMapper;

    @Autowired
    protected RedeemRecordMapper redeemRecordMapper;

    @Autowired
    protected UserSubscriptionMapper userSubscriptionMapper;

    @Autowired
    protected RedeemCodeGenerator redeemCodeGenerator;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    protected AdminUser testAdmin;
    protected UserAccount testUser;
    protected UserAccount disabledUser;
    protected Product testProduct;
    protected ProductPlan testPlan;
    protected ProductPlan disabledPlan;
    protected ProductPlan nonRedeemPlan;

    @BeforeEach
    void baseSetUp() {
        testAdmin = createAdmin("testadmin", "admin123", "测试管理员");
        testUser = createUser("testuser", "user123", "测试用户", 1);
        disabledUser = createUser("disableduser", "disabled123", "停用用户", 0);
        testProduct = createProduct("AUTO_EDIT_SYSTEM", "自动剪辑系统");
        testPlan = createPlan(testProduct.getId(), "AUTO_EDIT_30D", "30天标准版", 30);
        disabledPlan = createDisabledPlan(testProduct.getId(), "AUTO_EDIT_DISABLED", "已停用套餐", 30);
        nonRedeemPlan = createNonRedeemPlan(testProduct.getId(), "AUTO_EDIT_NOREDEEM", "不支持兑换套餐", 30);
    }

    @AfterEach
    void baseTearDown() {
        jdbcTemplate.execute("DELETE FROM redeem_record");
        jdbcTemplate.execute("DELETE FROM redeem_code");
        jdbcTemplate.execute("DELETE FROM redeem_code_batch");
        jdbcTemplate.execute("DELETE FROM user_subscription");
        jdbcTemplate.execute("DELETE FROM product_plan");
        jdbcTemplate.execute("DELETE FROM product");
        jdbcTemplate.execute("DELETE FROM user_account");
        jdbcTemplate.execute("DELETE FROM admin_user");
    }

    // ==================== HTTP helpers ====================

    protected String baseUrl() {
        return "http://localhost:" + port;
    }

    protected ResponseEntity<String> postJson(String path, String jsonBody, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) headers.setBearerAuth(token);
        try {
            return restTemplate.exchange(baseUrl() + path, HttpMethod.POST, new HttpEntity<>(jsonBody, headers), String.class);
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        }
    }

    protected ResponseEntity<String> get(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) headers.setBearerAuth(token);
        try {
            return restTemplate.exchange(baseUrl() + path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        }
    }

    protected ResponseEntity<String> patchJson(String path, String jsonBody, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) headers.setBearerAuth(token);
        try {
            return restTemplate.exchange(baseUrl() + path, HttpMethod.PATCH, new HttpEntity<>(jsonBody, headers), String.class);
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        }
    }

    // ==================== Token helpers ====================

    protected String getAdminToken() {
        return jwtTokenService.createAdminAccessToken(testAdmin);
    }

    protected String getAdminToken(AdminUser admin) {
        return jwtTokenService.createAdminAccessToken(admin);
    }

    protected String getUserToken() {
        return jwtTokenService.createUserAccessToken(testUser);
    }

    protected String getUserToken(UserAccount user) {
        return jwtTokenService.createUserAccessToken(user);
    }

    // ==================== Data creation helpers ====================

    protected AdminUser createAdmin(String username, String password, String nickname) {
        AdminUser admin = new AdminUser();
        admin.setUsername(username);
        admin.setPassword(passwordEncoder.encode(password));
        admin.setNickname(nickname);
        admin.setStatus(1);
        adminUserMapper.insert(admin);
        return admin;
    }

    protected UserAccount createUser(String username, String password, String nickname, int status) {
        UserAccount user = new UserAccount();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(nickname);
        user.setStatus(status);
        userAccountMapper.insert(user);
        return user;
    }

    protected Product createProduct(String productCode, String productName) {
        Product product = new Product();
        product.setProductCode(productCode);
        product.setProductName(productName);
        product.setStatus(1);
        productMapper.insert(product);
        return product;
    }

    protected ProductPlan createPlan(Long productId, String planCode, String planName, int durationDays) {
        ProductPlan plan = new ProductPlan();
        plan.setProductId(productId);
        plan.setPlanCode(planCode);
        plan.setPlanName(planName);
        plan.setDurationDays(durationDays);
        plan.setPrice(BigDecimal.valueOf(99.00));
        plan.setSupportRedeem(true);
        plan.setSupportPayment(false);
        plan.setStatus(1);
        plan.setSort(0);
        productPlanMapper.insert(plan);
        return plan;
    }

    protected ProductPlan createDisabledPlan(Long productId, String planCode, String planName, int durationDays) {
        ProductPlan plan = new ProductPlan();
        plan.setProductId(productId);
        plan.setPlanCode(planCode);
        plan.setPlanName(planName);
        plan.setDurationDays(durationDays);
        plan.setPrice(BigDecimal.valueOf(99.00));
        plan.setSupportRedeem(true);
        plan.setSupportPayment(false);
        plan.setStatus(0);
        plan.setSort(0);
        productPlanMapper.insert(plan);
        return plan;
    }

    protected ProductPlan createNonRedeemPlan(Long productId, String planCode, String planName, int durationDays) {
        ProductPlan plan = new ProductPlan();
        plan.setProductId(productId);
        plan.setPlanCode(planCode);
        plan.setPlanName(planName);
        plan.setDurationDays(durationDays);
        plan.setPrice(BigDecimal.valueOf(99.00));
        plan.setSupportRedeem(false);
        plan.setSupportPayment(false);
        plan.setStatus(1);
        plan.setSort(0);
        productPlanMapper.insert(plan);
        return plan;
    }

    protected RedeemCodeBatch createBatch(AdminUser admin, ProductPlan plan, int quantity) {
        RedeemCodeBatch batch = new RedeemCodeBatch();
        batch.setBatchNo(redeemCodeGenerator.generateBatchNo());
        batch.setPlanId(plan.getId());
        batch.setQuantity(quantity);
        batch.setStatus(1);
        batch.setCreatedBy(admin.getId());
        redeemCodeBatchMapper.insert(batch);
        for (int i = 0; i < quantity; i++) {
            String plainCode = redeemCodeGenerator.generateCode();
            RedeemCode code = new RedeemCode();
            code.setBatchId(batch.getId());
            code.setPlanId(plan.getId());
            code.setCodeHash(redeemCodeGenerator.hashCode(plainCode));
            code.setCodeMasked(redeemCodeGenerator.maskCode(plainCode));
            code.setStatus(0);
            redeemCodeMapper.insert(code);
        }
        return batch;
    }

    protected List<String> createBatchWithCodes(AdminUser admin, ProductPlan plan, int quantity) {
        RedeemCodeBatch batch = new RedeemCodeBatch();
        batch.setBatchNo(redeemCodeGenerator.generateBatchNo());
        batch.setPlanId(plan.getId());
        batch.setQuantity(quantity);
        batch.setStatus(1);
        batch.setCreatedBy(admin.getId());
        redeemCodeBatchMapper.insert(batch);
        ArrayList<String> plainCodes = new ArrayList<>();
        for (int i = 0; i < quantity; i++) {
            String plainCode = redeemCodeGenerator.generateCode();
            plainCodes.add(plainCode);
            RedeemCode code = new RedeemCode();
            code.setBatchId(batch.getId());
            code.setPlanId(plan.getId());
            code.setCodeHash(redeemCodeGenerator.hashCode(plainCode));
            code.setCodeMasked(redeemCodeGenerator.maskCode(plainCode));
            code.setStatus(0);
            redeemCodeMapper.insert(code);
        }
        return plainCodes;
    }
}
