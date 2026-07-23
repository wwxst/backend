package com.web.project.product.mapper;

import com.web.project.product.entity.ProductPlan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 商品套餐数据库操作接口。
 */
@Mapper
public interface ProductPlanMapper {

    /**
     * 根据套餐 ID 查询。
     */
    ProductPlan selectById(
            @Param("id") Long id
    );

    /**
     * 根据套餐编码查询。
     *
     * 用于检查套餐编码是否重复。
     */
    ProductPlan selectByCode(
            @Param("planCode") String planCode
    );

    /**
     * 新增商品套餐。
     */
    int insert(ProductPlan productPlan);

    /**
     * 查询指定商品下的全部套餐。
     */
    List<ProductPlan> selectByProductId(
            @Param("productId") Long productId
    );
}