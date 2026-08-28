package com.web.project.product.service.impl;

import com.web.project.common.error.ErrorCode;
import com.web.project.common.exception.BusinessException;
import com.web.project.common.result.PageResult;
import com.web.project.product.dto.CreateProductDTO;
import com.web.project.product.dto.CreateProductPlanDTO;
import com.web.project.product.dto.ProductQueryDTO;
import com.web.project.product.entity.Product;
import com.web.project.product.entity.ProductPlan;
import com.web.project.product.enums.ProductStatus;
import com.web.project.product.mapper.ProductMapper;
import com.web.project.product.mapper.ProductPlanMapper;
import com.web.project.product.service.ProductService;
import com.web.project.product.vo.ProductDetailVO;
import com.web.project.product.vo.ProductListVO;
import com.web.project.product.vo.ProductPlanVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 商品管理业务实现类。
 */
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productMapper;
    private final ProductPlanMapper productPlanMapper;

    /**
     * 创建商品。
     */
    @Override
    @Transactional
    public ProductDetailVO createProduct(
            CreateProductDTO createDTO
    ) {
        /*
         * 商品编码是系统内部的稳定标识，
         * 创建前必须检查是否重复。
         */
        Product existingProduct =
                productMapper.selectByCode(
                        createDTO.productCode()
                );

        if (existingProduct != null) {
            throw new BusinessException(
                    ErrorCode.PRODUCT_CODE_EXISTS
            );
        }

        Product product = new Product();

        product.setProductCode(
                createDTO.productCode()
        );
        product.setProductName(
                createDTO.productName().trim()
        );
        product.setDescription(
                normalizeNullableText(
                        createDTO.description()
                )
        );
        product.setStatus(
                ProductStatus.ENABLED.getCode()
        );

        productMapper.insert(product);

        /*
         * insert 执行后，数据库生成的 ID
         * 已经回填到 product 对象中。
         *
         * 再查询一次数据库，
         * 获取 createdAt、updatedAt 等数据库生成字段。
         */
        Product createdProduct =
                productMapper.selectById(
                        product.getId()
                );

        return toProductDetailVO(
                createdProduct,
                List.of()
        );
    }

    /**
     * 分页查询商品。
     */
    @Override
    public PageResult<ProductListVO> getProductPage(
            ProductQueryDTO queryDTO
    ) {
        int page = queryDTO.getPage();
        int pageSize = queryDTO.getPageSize();

        String keyword =
                normalizeNullableText(
                        queryDTO.getKeyword()
                );

        Integer status = queryDTO.getStatus();

        long total =
                productMapper.countByCondition(
                        keyword,
                        status
                );

        if (total == 0) {
            return new PageResult<>(
                    0L,
                    page,
                    pageSize,
                    List.of()
            );
        }

        long offset =
                (long) (page - 1) * pageSize;

        List<ProductListVO> records =
                productMapper
                        .selectPageByCondition(
                                keyword,
                                status,
                                offset,
                                pageSize
                        )
                        .stream()
                        .map(this::toProductListVO)
                        .toList();

        return new PageResult<>(
                total,
                page,
                pageSize,
                records
        );
    }

    /**
     * 创建商品套餐。
     */
    @Override
    @Transactional
    public ProductPlanVO createProductPlan(
            Long productId,
            CreateProductPlanDTO createDTO
    ) {
        Product product =
                productMapper.selectById(productId);

        if (product == null) {
            throw new BusinessException(
                    ErrorCode.PRODUCT_NOT_FOUND
            );
        }

        ProductPlan existingPlan =
                productPlanMapper.selectByCode(
                        createDTO.planCode()
                );

        if (existingPlan != null) {
            throw new BusinessException(
                    ErrorCode.PRODUCT_PLAN_CODE_EXISTS
            );
        }

        ProductPlan productPlan =
                new ProductPlan();

        productPlan.setProductId(productId);
        productPlan.setPlanCode(
                createDTO.planCode()
        );
        productPlan.setPlanName(
                createDTO.planName().trim()
        );
        productPlan.setDurationDays(
                createDTO.durationDays()
        );
        productPlan.setPrice(
                createDTO.price()
        );

        productPlan.setSupportRedeem(createDTO.supportRedeem());

        productPlan.setStatus(
                ProductStatus.ENABLED.getCode()
        );
        productPlan.setSort(
                createDTO.sort() == null
                        ? 0
                        : createDTO.sort()
        );

        productPlanMapper.insert(productPlan);

        ProductPlan createdPlan =
                productPlanMapper.selectById(
                        productPlan.getId()
                );

        return toProductPlanVO(createdPlan);
    }

    /**
     * 查询商品套餐。
     */
    @Override
    public List<ProductPlanVO> getProductPlans(
            Long productId
    ) {
        Product product =
                productMapper.selectById(productId);

        if (product == null) {
            throw new BusinessException(
                    ErrorCode.PRODUCT_NOT_FOUND
            );
        }

        return productPlanMapper
                .selectByProductId(productId)
                .stream()
                .map(this::toProductPlanVO)
                .toList();
    }

    /**
     * Product 转换为商品列表 VO。
     */
    private ProductListVO toProductListVO(
            Product product
    ) {
        return new ProductListVO(
                product.getId(),
                product.getProductCode(),
                product.getProductName(),
                product.getDescription(),
                product.getStatus(),
                ProductStatus.descriptionOf(
                        product.getStatus()
                ),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }

    /**
     * Product 转换为商品详情 VO。
     */
    private ProductDetailVO toProductDetailVO(
            Product product,
            List<ProductPlanVO> plans
    ) {
        return new ProductDetailVO(
                product.getId(),
                product.getProductCode(),
                product.getProductName(),
                product.getDescription(),
                product.getStatus(),
                ProductStatus.descriptionOf(
                        product.getStatus()
                ),
                product.getCreatedAt(),
                product.getUpdatedAt(),
                plans
        );
    }

    /**
     * ProductPlan 转换为套餐 VO。
     */
    private ProductPlanVO toProductPlanVO(
            ProductPlan productPlan
    ) {
        return new ProductPlanVO(
                productPlan.getId(),
                productPlan.getProductId(),
                productPlan.getPlanCode(),
                productPlan.getPlanName(),
                productPlan.getDurationDays(),
                productPlan.getPrice(),
                productPlan.getSupportRedeem(),
                productPlan.getStatus(),
                ProductStatus.descriptionOf(
                        productPlan.getStatus()
                ),
                productPlan.getSort(),
                productPlan.getCreatedAt(),
                productPlan.getUpdatedAt()
        );
    }

    /**
     * 清理可为空的文字。
     *
     * null 或纯空格统一转成 null；
     * 其他内容去除前后空格。
     */
    private String normalizeNullableText(
            String value
    ) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();

        return normalized.isEmpty()
                ? null
                : normalized;
    }
}
