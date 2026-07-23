package com.web.project.product.controller;

import com.web.project.common.result.PageResult;
import com.web.project.common.result.Result;
import com.web.project.product.dto.CreateProductDTO;
import com.web.project.product.dto.CreateProductPlanDTO;
import com.web.project.product.dto.ProductQueryDTO;
import com.web.project.product.service.ProductService;
import com.web.project.product.vo.ProductDetailVO;
import com.web.project.product.vo.ProductListVO;
import com.web.project.product.vo.ProductPlanVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台商品管理接口。
 */
@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /**
     * 创建商品。
     */
    @PostMapping
    public Result<ProductDetailVO> createProduct(
            @Valid @RequestBody
            CreateProductDTO createDTO
    ) {
        ProductDetailVO product =
                productService.createProduct(
                        createDTO
                );

        return Result.success(product);
    }

    /**
     * 分页查询商品。
     */
    @GetMapping
    public Result<PageResult<ProductListVO>> getProductPage(
            @Valid @ModelAttribute
            ProductQueryDTO queryDTO
    ) {
        PageResult<ProductListVO> pageResult =
                productService.getProductPage(
                        queryDTO
                );

        return Result.success(pageResult);
    }

    /**
     * 为指定商品创建套餐。
     */
    @PostMapping("/{productId}/plans")
    public Result<ProductPlanVO> createProductPlan(
            @PathVariable Long productId,
            @Valid @RequestBody
            CreateProductPlanDTO createDTO
    ) {
        ProductPlanVO productPlan =
                productService.createProductPlan(
                        productId,
                        createDTO
                );

        return Result.success(productPlan);
    }

    /**
     * 查询指定商品下的套餐。
     */
    @GetMapping("/{productId}/plans")
    public Result<List<ProductPlanVO>> getProductPlans(
            @PathVariable Long productId
    ) {
        List<ProductPlanVO> plans =
                productService.getProductPlans(
                        productId
                );

        return Result.success(plans);
    }
}