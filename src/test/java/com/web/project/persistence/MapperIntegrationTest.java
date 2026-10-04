package com.web.project.persistence;

import com.web.project.admin.entity.SysUser;
import com.web.project.admin.mapper.SysUserMapper;
import com.web.project.channel.dto.AuthChannelUpdateDTO;
import com.web.project.channel.mapper.SysConfigMapper;
import com.web.project.channel.model.AuthChannel;
import com.web.project.channel.service.AuthChannelService;
import com.web.project.user.entity.UserAccount;
import com.web.project.user.mapper.UserAccountMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 仅连接显式指定、按 web_project.sql 初始化的可丢弃 MySQL 库。
 */
@EnabledIfEnvironmentVariable(named = "ATLAS_MYSQL_TEST_URL", matches = "jdbc:mysql:.*")
@SpringBootTest(properties = {
        "spring.datasource.url=${ATLAS_MYSQL_TEST_URL}",
        "app.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@Transactional
class MapperIntegrationTest {

    @Autowired
    private UserAccountMapper userAccountMapper;
    @Autowired
    private SysUserMapper sysUserMapper;
    @Autowired
    private SysConfigMapper sysConfigMapper;
    @Autowired
    private AuthChannelService authChannelService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void userMappingAndRepeatedWritesPreserveDatabaseContract() {
        UserAccount account = new UserAccount();
        account.setUserNo("123456");
        account.setPhone("13800138000");
        account.setEmail("audit@example.com");
        assertEquals(1, userAccountMapper.insert(account));
        assertNotNull(account.getId());

        Long id = account.getId();
        UserAccount stored = userAccountMapper.selectById(id);
        assertEquals(0L, stored.getPasswordVersion());
        assertEquals(1, stored.getStatus());
        assertNull(stored.getPassword());
        assertNotNull(stored.getCreatedAt());
        assertNotNull(stored.getUpdatedAt());
        assertEquals(id, userAccountMapper.selectByPhone(account.getPhone()).getId());
        assertEquals(id, userAccountMapper.selectByEmail(account.getEmail()).getId());

        for (int attempt = 0; attempt < 2; attempt++) {
            assertEquals(1, userAccountMapper.updateProfile(id, "审查测试", "https://example.com/avatar.png"));
            assertEquals(1, userAccountMapper.updatePhone(id, account.getPhone()));
            assertEquals(1, userAccountMapper.updateEmail(id, account.getEmail()));
        }
        assertEquals(1, userAccountMapper.updatePassword(id, "test-password-hash"));
        assertEquals(1L, userAccountMapper.selectById(id).getPasswordVersion());
        assertEquals(1, userAccountMapper.updatePassword(id, "test-password-hash"));
        assertEquals(2L, userAccountMapper.selectById(id).getPasswordVersion());
        assertEquals(1L, userAccountMapper.countByCondition("123456", 1));
        UserAccount listed = userAccountMapper.selectPageByCondition("123456", 1, 0L, 10, null).getFirst();
        assertEquals(id, listed.getId());
        assertNull(listed.getPassword());
        assertNull(listed.getPasswordVersion());

        jdbcTemplate.update("DELETE FROM user_account WHERE id = ?", id);
        assertEquals(0, userAccountMapper.updateProfile(id, "审查测试", null));
        assertEquals(0, userAccountMapper.updatePassword(id, "test-password-hash"));
        assertEquals(0, userAccountMapper.updatePhone(id, account.getPhone()));
        assertEquals(0, userAccountMapper.updateEmail(id, account.getEmail()));
    }

    @Test
    void userCreatedAtSortingIsStableAcrossPages() {
        jdbcTemplate.update("""
                INSERT INTO user_account (user_no, nickname, status, created_at)
                VALUES ('sort-user-new', '普通排序新', 1, '2026-10-03 12:00:00'),
                       ('sort-user-old', '普通排序旧', 1, '2026-10-01 12:00:00'),
                       ('sort-user-tie', '普通排序同时间', 0, '2026-10-01 12:00:00')
                """);

        assertEquals(List.of("sort-user-old", "sort-user-tie", "sort-user-new"),
                userAccountMapper.selectPageByCondition("普通排序", null, 0L, 10, "asc")
                        .stream().map(UserAccount::getUserNo).toList());
        assertEquals(List.of("sort-user-new", "sort-user-tie", "sort-user-old"),
                userAccountMapper.selectPageByCondition("普通排序", null, 0L, 10, "desc")
                        .stream().map(UserAccount::getUserNo).toList());
        assertEquals(List.of("sort-user-new", "sort-user-tie", "sort-user-old"),
                userAccountMapper.selectPageByCondition("普通排序", null, 0L, 10, null)
                        .stream().map(UserAccount::getUserNo).toList());
        assertEquals("sort-user-tie", userAccountMapper.selectPageByCondition(
                "普通排序", null, 1L, 1, "asc").getFirst().getUserNo());
    }

    @Test
    void sysUserListExcludesPasswordWhileCredentialLookupRetainsIt() {
        SysUser credentials = sysUserMapper.selectByUsername("admin");
        assertNotNull(credentials.getPassword());
        assertEquals(credentials.getId(), sysUserMapper.selectById(credentials.getId()).getId());
        assertEquals(1L, sysUserMapper.countByCondition("admin", 1));
        SysUser listed = sysUserMapper.selectPageByCondition("admin", 1, 0L, 10, null).getFirst();
        assertEquals(credentials.getId(), listed.getId());
        assertNull(listed.getPassword());
    }

    @Test
    void sysUserCreatedAtSortingIsStableAcrossPages() {
        jdbcTemplate.update("""
                INSERT INTO sys_user (username, password, nickname, status, created_at)
                VALUES ('sort-new', 'test-hash', 'sort-test', 1, '2026-10-03 12:00:00'),
                       ('sort-old', 'test-hash', 'sort-test', 1, '2026-10-01 12:00:00'),
                       ('sort-tie', 'test-hash', 'sort-test', 0, '2026-10-01 12:00:00')
                """);

        assertEquals(List.of("sort-old", "sort-tie", "sort-new"),
                sysUserMapper.selectPageByCondition("sort-test", null, 0L, 10, "asc")
                        .stream().map(SysUser::getUsername).toList());
        assertEquals(List.of("sort-new", "sort-tie", "sort-old"),
                sysUserMapper.selectPageByCondition("sort-test", null, 0L, 10, "desc")
                        .stream().map(SysUser::getUsername).toList());
        assertEquals("sort-tie", sysUserMapper.selectPageByCondition("sort-test", null, 1L, 1, "asc")
                .getFirst().getUsername());
        assertEquals(List.of("sort-old", "sort-new"),
                sysUserMapper.selectPageByCondition("sort-test", 1, 0L, 10, "asc")
                        .stream().map(SysUser::getUsername).toList());
        assertEquals("sort-tie", sysUserMapper.selectPageByCondition("sort-test", null, 0L, 1, null)
                .getFirst().getUsername());
    }

    @Test
    void sysUserCrudWritesAndDeletesExpectedRows() {
        SysUser user = new SysUser();
        user.setUsername("crud-test-user");
        user.setPassword("initial-hash");
        user.setNickname("CRUD 测试");
        user.setStatus(1);

        assertEquals(1, sysUserMapper.insert(user));
        assertNotNull(user.getId());
        assertEquals(1, sysUserMapper.updateStatus(user.getId(), 0));
        assertEquals(1, sysUserMapper.update(user));
        assertEquals(1, sysUserMapper.updatePassword(user.getId(), "reset-hash"));
        assertEquals("reset-hash", sysUserMapper.selectById(user.getId()).getPassword());
        assertEquals(1, sysUserMapper.deleteById(user.getId()));
        assertEquals(0, sysUserMapper.deleteById(user.getId()));
    }

    @Test
    void repeatedChannelUpdateSucceedsAndInvalidConfigurationIsRejected() {
        for (int attempt = 0; attempt < 2; attempt++) {
            assertDoesNotThrow(() -> authChannelService.updateChannel(
                    AuthChannel.PHONE, new AuthChannelUpdateDTO(true, true)));
        }
        assertTrue(authChannelService.getChannels().phoneEnabled());
        assertEquals(1, sysConfigMapper.updateValue("auth.phone.enabled", "invalid"));
        assertThrows(IllegalStateException.class, authChannelService::getChannels);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void missingSecondConfigRowRollsBackFirstChannelWrite() {
        assertEquals(1, jdbcTemplate.update("DELETE FROM sys_config WHERE config_key = ?",
                "auth.phone.code.enabled"));
        try {
            assertThrows(IllegalStateException.class, () -> authChannelService.updateChannel(
                    AuthChannel.PHONE, new AuthChannelUpdateDTO(false, false)));
            assertEquals("true", jdbcTemplate.queryForObject(
                    "SELECT config_value FROM sys_config WHERE config_key = ?",
                    String.class, "auth.phone.enabled"));
        } finally {
            jdbcTemplate.update("INSERT INTO sys_config (config_key, config_value) VALUES (?, ?)",
                    "auth.phone.code.enabled", "true");
        }
    }
}
