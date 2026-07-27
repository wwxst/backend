package com.web.project;

import com.web.project.redeem.enums.RedeemCodeStatus;
import com.web.project.subscription.entity.UserSubscription;
import com.web.project.subscription.enums.SubscriptionStatus;
import com.web.project.support.BaseSpringBootTest;
import org.junit.jupiter.api.*;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FullIntegrationTest extends BaseSpringBootTest {

    @Test @Order(1)
    void contextLoads() {
        assertNotNull(jdbcTemplate);
    }

    // ==================== Admin Auth ====================
    @Test @Order(2)
    void adminLoginSuccess() {
        ResponseEntity<String> resp = postJson("/api/admin/auth/login",
                "{\"username\":\"testadmin\",\"password\":\"admin123\"}", null);
        assertEquals(200, resp.getStatusCode().value());
        assertTrue(resp.getBody().contains("\"code\":200"));
        assertTrue(resp.getBody().contains("\"token\""));
    }

    @Test @Order(3)
    void adminLoginWrongPassword() {
        ResponseEntity<String> resp = postJson("/api/admin/auth/login",
                "{\"username\":\"testadmin\",\"password\":\"wrong\"}", null);
        assertEquals(401, resp.getStatusCode().value());
    }

    @Test @Order(4)
    void adminTokenAccessAdminApi() {
        ResponseEntity<String> resp = get("/api/admin/auth/me", getAdminToken());
        assertEquals(200, resp.getStatusCode().value());
        assertTrue(resp.getBody().contains("testadmin"));
    }

    @Test @Order(5)
    void adminTokenBlockedFromUserApi() {
        assertEquals(403, get("/api/user/auth/me", getAdminToken()).getStatusCode().value());
    }

    @Test @Order(6)
    void noTokenReturns401() {
        assertEquals(401, get("/api/admin/auth/me", null).getStatusCode().value());
    }

    // ==================== User Auth ====================
    @Test @Order(7)
    void userLoginSuccess() {
        ResponseEntity<String> resp = postJson("/api/user/auth/login",
                "{\"username\":\"testuser\",\"password\":\"user123\"}", null);
        assertEquals(200, resp.getStatusCode().value());
    }

    @Test @Order(8)
    void userLoginWrongPassword() {
        assertEquals(401, postJson("/api/user/auth/login",
                "{\"username\":\"testuser\",\"password\":\"wrong\"}", null).getStatusCode().value());
    }

    @Test @Order(9)
    void userTokenAccessUserApi() {
        assertEquals(200, get("/api/user/auth/me", getUserToken()).getStatusCode().value());
    }

    @Test @Order(10)
    void userTokenBlockedFromAdminApi() {
        assertEquals(403, get("/api/admin/auth/me", getUserToken()).getStatusCode().value());
    }

    @Test @Order(11)
    void disabledUserLoginFails() {
        ResponseEntity<String> resp = postJson("/api/user/auth/login",
                "{\"username\":\"disableduser\",\"password\":\"disabled123\"}", null);
        assertEquals(403, resp.getStatusCode().value());
        assertTrue(resp.getBody().contains("40303"));
    }

    // ==================== Product Management ====================
    @Test @Order(12)
    void createProductSuccess() {
        ResponseEntity<String> resp = postJson("/api/admin/products",
                "{\"productCode\":\"TEST_PROD\",\"productName\":\"测试商品\"}", getAdminToken());
        assertEquals(200, resp.getStatusCode().value());
    }

    @Test @Order(13)
    void createProductDuplicateCode() {
        ResponseEntity<String> resp = postJson("/api/admin/products",
                "{\"productCode\":\"AUTO_EDIT_SYSTEM\",\"productName\":\"重复\"}", getAdminToken());
        assertEquals(409, resp.getStatusCode().value());
    }

    @Test @Order(14)
    void normalUserCannotCreateProduct() {
        assertEquals(403, postJson("/api/admin/products",
                "{\"productCode\":\"USER_PROD\",\"productName\":\"x\"}", getUserToken()).getStatusCode().value());
    }

    // ==================== Product Plan ====================
    @Test @Order(15)
    void createPlanSuccess() {
        String json = String.format("{\"planCode\":\"PLAN_30D\",\"planName\":\"30天\",\"durationDays\":30,\"price\":99.00,\"supportRedeem\":true,\"supportPayment\":false,\"sort\":0}");
        assertEquals(200, postJson("/api/admin/products/" + testProduct.getId() + "/plans", json, getAdminToken()).getStatusCode().value());
    }

    // ==================== Redeem Batch ====================
    @Test @Order(16)
    void createBatchSuccess() {
        String json = String.format("{\"planId\":%d,\"quantity\":5,\"channel\":\"ch\",\"expiresAt\":\"%s\"}",
                testPlan.getId(), LocalDateTime.now().plusDays(30).toString());
        ResponseEntity<String> resp = postJson("/api/admin/redeem-code-batches", json, getAdminToken());
        assertEquals(200, resp.getStatusCode().value());
        assertTrue(resp.getBody().contains("KASI-"));
    }

    @Test @Order(17)
    void batchDisableEnable() {
        createBatch(testAdmin, testPlan, 2);
        var batches = redeemCodeBatchMapper.selectPageByCondition(null, null, null, 0, 1);
        Long batchId = batches.get(0).getId();
        assertEquals(200, patchJson("/api/admin/redeem-code-batches/" + batchId + "/status", "{\"status\":0}", getAdminToken()).getStatusCode().value());
        assertEquals(200, patchJson("/api/admin/redeem-code-batches/" + batchId + "/status", "{\"status\":1}", getAdminToken()).getStatusCode().value());
    }

    // ==================== Redeem Flow ====================
    @Test @Order(20)
    void redeemSuccess() {
        List<String> codes = createBatchWithCodes(testAdmin, testPlan, 1);
        ResponseEntity<String> resp = postJson("/api/user/redemptions",
                "{\"code\":\"" + codes.get(0) + "\"}", getUserToken());
        assertEquals(200, resp.getStatusCode().value());
        assertTrue(resp.getBody().contains("\"productName\""));
    }

    @Test @Order(21)
    void redeemCreatesSubscription() {
        List<String> codes = createBatchWithCodes(testAdmin, testPlan, 1);
        postJson("/api/user/redemptions", "{\"code\":\"" + codes.get(0) + "\"}", getUserToken());
        UserSubscription sub = userSubscriptionMapper.selectByUserIdAndProductId(testUser.getId(), testProduct.getId());
        assertNotNull(sub);
    }

    @Test @Order(22)
    void redeemSameCodeTwiceFails() {
        List<String> codes = createBatchWithCodes(testAdmin, testPlan, 1);
        String token = getUserToken();
        postJson("/api/user/redemptions", "{\"code\":\"" + codes.get(0) + "\"}", token);
        ResponseEntity<String> resp = postJson("/api/user/redemptions", "{\"code\":\"" + codes.get(0) + "\"}", token);
        assertEquals(409, resp.getStatusCode().value());
        assertTrue(resp.getBody().contains("40910"));
    }

    @Test @Order(23)
    void redeemNonExistentCode() {
        assertEquals(404, postJson("/api/user/redemptions",
                "{\"code\":\"KASI-ABCD1234EFGH5678\"}", getUserToken()).getStatusCode().value());
    }

    @Test @Order(24)
    void disabledUserRedeemFails() {
        List<String> codes = createBatchWithCodes(testAdmin, testPlan, 1);
        assertEquals(403, postJson("/api/user/redemptions",
                "{\"code\":\"" + codes.get(0) + "\"}", getUserToken(disabledUser)).getStatusCode().value());
    }

    @Test @Order(25)
    void adminTokenRedeemFails() {
        List<String> codes = createBatchWithCodes(testAdmin, testPlan, 1);
        assertEquals(403, postJson("/api/user/redemptions",
                "{\"code\":\"" + codes.get(0) + "\"}", getAdminToken()).getStatusCode().value());
    }

    @Test @Order(26)
    void redeemWithDisabledPlanFails() {
        var batch = new com.web.project.redeem.entity.RedeemCodeBatch();
        batch.setBatchNo(redeemCodeGenerator.generateBatchNo());
        batch.setPlanId(disabledPlan.getId()); batch.setQuantity(1); batch.setStatus(1);
        batch.setCreatedBy(testAdmin.getId()); redeemCodeBatchMapper.insert(batch);
        String code = redeemCodeGenerator.generateCode();
        var rc = new com.web.project.redeem.entity.RedeemCode();
        rc.setBatchId(batch.getId()); rc.setPlanId(disabledPlan.getId());
        rc.setCodeHash(redeemCodeGenerator.hashCode(code));
        rc.setCodeMasked(redeemCodeGenerator.maskCode(code)); rc.setStatus(0);
        redeemCodeMapper.insert(rc);
        assertEquals(409, postJson("/api/user/redemptions",
                "{\"code\":\"" + code + "\"}", getUserToken()).getStatusCode().value());
    }

    // ==================== CONCURRENT REDEEM A: Same Code ====================
    @Test @Order(30)
    void concurrentRedeemSameCodeExactlyOneSucceeds() throws Exception {
        List<String> codes = createBatchWithCodes(testAdmin, testPlan, 1);
        String code = codes.get(0);
        String token = getUserToken();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicInteger success200 = new AtomicInteger(0);
        AtomicInteger fail409 = new AtomicInteger(0);
        AtomicInteger otherFail = new AtomicInteger(0);

        for (int i = 0; i < 2; i++) {
            executor.submit(() -> {
                try {
                    barrier.await();
                    ResponseEntity<String> resp = postJson("/api/user/redemptions",
                            "{\"code\":\"" + code + "\"}", token);
                    if (resp.getStatusCode().value() == 200) success200.incrementAndGet();
                    else if (resp.getStatusCode().value() == 409) fail409.incrementAndGet();
                    else otherFail.incrementAndGet();
                } catch (Exception e) {
                    otherFail.incrementAndGet();
                } finally { latch.countDown(); }
            });
        }
        latch.await(); executor.shutdown();

        // STRICT: exactly 1 success (200), exactly 1 failure (409)
        assertEquals(1, success200.get(), "Exactly 1 request must return HTTP 200");
        assertEquals(1, fail409.get(), "Exactly 1 request must return HTTP 409 (already used)");
        assertEquals(0, otherFail.get(), "No other failures allowed");

        long records = redeemRecordMapper.countByCondition(null, null, null, null, null, null);
        assertEquals(1, records, "Exactly 1 redeem record");
    }

    // ==================== CONCURRENT REDEEM B: Same User, Different Codes ====================
    @Test @Order(31)
    void concurrentRedeemDifferentCodesBothSucceedAndAccumulate() throws Exception {
        List<String> codes = createBatchWithCodes(testAdmin, testPlan, 2);
        String token = getUserToken();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicInteger success200 = new AtomicInteger(0);

        for (int i = 0; i < 2; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    barrier.await();
                    ResponseEntity<String> resp = postJson("/api/user/redemptions",
                            "{\"code\":\"" + codes.get(idx) + "\"}", token);
                    if (resp.getStatusCode().value() == 200) success200.incrementAndGet();
                } catch (Exception ignored) {
                } finally { latch.countDown(); }
            });
        }
        latch.await(); executor.shutdown();

        // STRICT: both must succeed
        assertEquals(2, success200.get(), "Both concurrent redeems must succeed (HTTP 200)");

        // Verify subscription accumulated both plans
        UserSubscription sub = userSubscriptionMapper.selectByUserIdAndProductId(testUser.getId(), testProduct.getId());
        assertNotNull(sub, "Must have exactly 1 subscription record (not 2)");
        assertEquals(SubscriptionStatus.ENABLED.getCode(), sub.getStatus().intValue());

        // Verify 2 redeem records
        long records = redeemRecordMapper.countByCondition(null, null, null, null, null, null);
        assertEquals(2, records, "Exactly 2 redeem records");

        // Both codes must be redeemed
        for (String code : codes) {
            String hash = redeemCodeGenerator.hashCode(code);
            var rc = redeemCodeMapper.selectByCodeHash(hash);
            assertEquals(RedeemCodeStatus.REDEEMED.getCode(), rc.getStatus().intValue(), "Each code must be redeemed");
            assertEquals(testUser.getId(), rc.getRedeemedUserId());
        }
    }

    // ==================== Subscription ====================
    @Test @Order(40)
    void userNeverActivated() {
        ResponseEntity<String> resp = get("/api/user/subscription", getUserToken());
        assertEquals(200, resp.getStatusCode().value());
        assertTrue(resp.getBody().contains("NOT_ACTIVATED"));
        assertTrue(resp.getBody().contains("\"valid\":false"));
    }

    @Test @Order(41)
    void activeSubscription() {
        List<String> codes = createBatchWithCodes(testAdmin, testPlan, 1);
        postJson("/api/user/redemptions", "{\"code\":\"" + codes.get(0) + "\"}", getUserToken());
        ResponseEntity<String> resp = get("/api/user/subscription", getUserToken());
        assertEquals(200, resp.getStatusCode().value());
        assertTrue(resp.getBody().contains("ACTIVE"));
        assertTrue(resp.getBody().contains("\"valid\":true"));
    }

    @Test @Order(42)
    void adminCannotAccessSubscription() {
        assertEquals(403, get("/api/user/subscription", getAdminToken()).getStatusCode().value());
    }

    // ==================== Admin Redeem Records ====================
    @Test @Order(50)
    void adminRedeemRecordsPage() {
        List<String> codes = createBatchWithCodes(testAdmin, testPlan, 1);
        postJson("/api/user/redemptions", "{\"code\":\"" + codes.get(0) + "\"}", getUserToken());
        ResponseEntity<String> resp = get("/api/admin/redeem-records?page=1&pageSize=10", getAdminToken());
        assertEquals(200, resp.getStatusCode().value());
        assertTrue(resp.getBody().contains("codeMasked"));
    }

    @Test @Order(51)
    void redeemRecordsStartAfterEnd() {
        ResponseEntity<String> resp = get("/api/admin/redeem-records?startTime=2026-12-31T00:00:00&endTime=2026-01-01T00:00:00&page=1&pageSize=10", getAdminToken());
        assertEquals(400, resp.getStatusCode().value());
        assertTrue(resp.getBody().contains("40000"));
    }

    @Test @Order(52)
    void normalUserCannotAccessRedeemRecords() {
        assertEquals(403, get("/api/admin/redeem-records?page=1&pageSize=10", getUserToken()).getStatusCode().value());
    }

    // ==================== Admin Subscriptions ====================
    @Test @Order(55)
    void adminSubscriptionsPage() {
        List<String> codes = createBatchWithCodes(testAdmin, testPlan, 1);
        postJson("/api/user/redemptions", "{\"code\":\"" + codes.get(0) + "\"}", getUserToken());
        assertEquals(200, get("/api/admin/user-subscriptions?page=1&pageSize=10", getAdminToken()).getStatusCode().value());
    }

    @Test @Order(56)
    void normalUserCannotAccessSubscriptions() {
        assertEquals(403, get("/api/admin/user-subscriptions?page=1&pageSize=10", getUserToken()).getStatusCode().value());
    }

    // ==================== HTTP STATUS CODE TESTS (严格验证HTTP状态码) ====================
    @Test @Order(60)
    void pageZeroReturns400() {
        ResponseEntity<String> resp = get("/api/admin/products?page=0", getAdminToken());
        assertEquals(400, resp.getStatusCode().value(), "page=0 must return HTTP 400");
        assertTrue(resp.getBody().contains("40000"));
    }

    @Test @Order(61)
    void pageSizeOverMaxReturns400() {
        ResponseEntity<String> resp = get("/api/admin/products?pageSize=101", getAdminToken());
        assertEquals(400, resp.getStatusCode().value(), "pageSize=101 must return HTTP 400");
    }

    @Test @Order(62)
    void batchQuantityZeroReturns400() {
        String json = String.format("{\"planId\":%d,\"quantity\":0,\"expiresAt\":\"%s\"}",
                testPlan.getId(), LocalDateTime.now().plusDays(30).toString());
        ResponseEntity<String> resp = postJson("/api/admin/redeem-code-batches", json, getAdminToken());
        assertEquals(400, resp.getStatusCode().value(), "quantity=0 must return HTTP 400");
    }

    @Test @Order(63)
    void emptyRedeemCodeReturns400() {
        ResponseEntity<String> resp = postJson("/api/user/redemptions", "{\"code\":\"\"}", getUserToken());
        assertEquals(400, resp.getStatusCode().value(), "empty redeem code must return HTTP 400");
    }

    @Test @Order(64)
    void invalidProductCodeFormatReturns400() {
        ResponseEntity<String> resp = postJson("/api/admin/products",
                "{\"productCode\":\"lower_case\",\"productName\":\"x\"}", getAdminToken());
        assertEquals(400, resp.getStatusCode().value(), "invalid product code must return HTTP 400");
    }

    @Test @Order(65)
    void planDurationDaysZeroReturns400() {
        String json = String.format("{\"planCode\":\"ZERO_D\",\"planName\":\"0\",\"durationDays\":0,\"price\":0.00,\"supportRedeem\":true,\"supportPayment\":false,\"sort\":0}");
        ResponseEntity<String> resp = postJson("/api/admin/products/" + testProduct.getId() + "/plans", json, getAdminToken());
        assertEquals(400, resp.getStatusCode().value(), "durationDays=0 must return HTTP 400");
    }
}
