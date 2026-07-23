package com.web.project.product.mapper;

import com.web.project.product.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 商品数据库操作接口。
 */
@Mapper
public interface ProductMapper {

    /**
     * 根据商品 ID 查询商品。
     */
    Product selectById(@Param("id") Long id);

    /**
     * 根据商品编码查询商品。
     *
     * 用于检查商品编码是否重复。
     */
    Product selectByCode(@Param("productCode") String productCode);

    /**
     * 新增商品。
     *
     * 插入成功后，数据库生成的 ID
     * 会自动回填到 product.id。
     */
    int insert(Product product);

    /**
     * 查询符合条件的商品总数。
     */
    long countByCondition(
            @Param("keyword") String keyword,
            @Param("status") Integer status
    );

    /**
     * 分页查询商品列表。
     */
    List<Product> selectPageByCondition(
            @Param("keyword") String keyword,
            @Param("status") Integer status,
            @Param("offset") long offset,
            @Param("pageSize") int pageSize
    );
}