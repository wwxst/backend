# Sys User Table Rename Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rename the backend system-user table from `admin_user` to `sys_user` without changing Java class names or public API paths, and document the approved two-frontend architecture.

**Architecture:** Keep the existing admin Java module and `/api/admin/**` contract stable. Change the fresh-install schema and every MyBatis query to `sys_user`, and provide an explicit MySQL 8 migration for existing databases.

**Tech Stack:** MySQL 8, MyBatis XML, Spring Boot, JUnit Jupiter, Markdown

---

### Task 1: Add a table-naming regression test

**Files:**
- Create: `src/test/java/com/web/project/admin/SysUserTableNamingTest.java`

- [ ] **Step 1: Write the failing schema and mapper test**

```java
assertTrue(schema.contains("CREATE TABLE sys_user"));
assertFalse(schema.contains("admin_user"));
assertFalse(mapper.contains("admin_user"));
```

- [ ] **Step 2: Write the failing existing-database migration test**

```java
assertTrue(Files.exists(migrationPath));
assertTrue(migration.contains("RENAME TABLE admin_user TO sys_user"));
```

- [ ] **Step 3: Run the focused test and verify RED**

Run: `./mvnw.cmd -Dtest=SysUserTableNamingTest test`

Expected: FAIL because `web_project.sql` and `AdminUserMapper.xml` still reference `admin_user`, and the migration file does not exist.

### Task 2: Rename the database table

**Files:**
- Modify: `web_project.sql`
- Modify: `src/main/resources/mapper/admin/AdminUserMapper.xml`
- Modify: `src/main/java/com/web/project/admin/entity/AdminUser.java`
- Create: `database/migrations/20260824_rename_admin_user_to_sys_user.sql`
- Modify: `README.md`

- [ ] **Step 1: Update the fresh-install schema**

```sql
CREATE TABLE sys_user
...
UNIQUE KEY uk_sys_user_username (username)
```

- [ ] **Step 2: Add the existing-database migration**

```sql
RENAME TABLE admin_user TO sys_user;
ALTER TABLE sys_user
    RENAME INDEX uk_admin_user_username TO uk_sys_user_username;
```

- [ ] **Step 3: Point all admin mapper queries at `sys_user`**

```xml
FROM sys_user
```

- [ ] **Step 4: Update the entity comment and database documentation**

Use `sys_user` for the database table name while retaining the `AdminUser` Java type and existing admin API paths.

- [ ] **Step 5: Run the focused test and verify GREEN**

Run: `./mvnw.cmd -Dtest=SysUserTableNamingTest test`

Expected: PASS with zero failures and zero errors.

### Task 3: Verify and publish

**Files:**
- Verify all files changed by Tasks 1 and 2

- [ ] **Step 1: Run the full test suite**

Run: `./mvnw.cmd test`

Expected: BUILD SUCCESS with zero test failures and zero test errors.

- [ ] **Step 2: Check naming and diff scope**

Run: `rg -n "admin_user|sys_user" .`

Expected: Runtime schema and mapper references use `sys_user`; `admin_user` appears only in the one-time migration source name and historical implementation plan context.

- [ ] **Step 3: Commit intended files**

```bash
git add README.md web_project.sql src/main/resources/mapper/admin/AdminUserMapper.xml src/main/java/com/web/project/admin/entity/AdminUser.java src/test/java/com/web/project/admin/SysUserTableNamingTest.java database/migrations/20260824_rename_admin_user_to_sys_user.sql docs/superpowers/specs/2026-08-24-two-frontends-design.md docs/superpowers/plans/2026-08-24-sys-user-table-rename.md
git commit -m "refactor: rename system user table"
```

- [ ] **Step 4: Push and verify remote parity**

Run: `git push origin main`, then compare `git rev-parse HEAD`, `git rev-parse origin/main`, and `git ls-remote origin refs/heads/main`.

Expected: all three hashes match and the worktree is clean.
