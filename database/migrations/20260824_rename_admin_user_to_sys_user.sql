RENAME TABLE admin_user TO sys_user;

ALTER TABLE sys_user
    RENAME INDEX uk_admin_user_username TO uk_sys_user_username;
