package com.web.project.product.service;

import com.web.project.common.result.PageResult;
import com.web.project.product.dto.CreateProductDTO;
import com.web.project.product.dto.CreateProductPlanDTO;
import com.web.project.product.dto.ProductQueryDTO;
import com.web.project.product.vo.ProductDetailVO;
import com.web.project.product.vo.ProductListVO;
import com.web.project.product.vo.ProductPlanVO;

import java.util.List;

/**
 * 商品管理业务接口。
 */
public interface ProductService {

    /**
     * 创建商品。
     */
    ProductDetailVO createProduct(
            CreateProductDTO createDTO
    );

    /**
     * 分页查询商品。
     */
    PageResult<ProductListVO> getProductPage(
            ProductQueryDTO queryDTO
    );

    /**
     * 为指定商品创建套餐。
     */
    ProductPlanVO createProductPlan(
            Long productId,
            CreateProductPlanDTO createDTO
    );

    /**
     * 查询指定商品下的套餐。
     */
    List<ProductPlanVO> getProductPlans(
            Long productId
    );
}