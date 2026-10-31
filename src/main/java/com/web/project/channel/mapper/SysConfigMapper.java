package com.web.project.channel.mapper;

import com.web.project.channel.entity.SysConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 系统配置数据库操作接口。
 */
@Mapper
public interface SysConfigMapper {

    List<SysConfig> selectByKeys(@Param("keys") List<String> keys);

    /** 按固定顺序锁定指定服务商配置，保证密钥留空时保留的是最新值。 */
    List<SysConfig> selectByKeysForUpdate(@Param("keys") List<String> keys);

    /**
     * 按固定顺序锁定登录开关和短信服务商选择，避免并发更新相互覆盖。
     */
    List<SysConfig> selectLoginSettingsForUpdate();

    int updateValue(@Param("key") String key, @Param("value") String value);
}
