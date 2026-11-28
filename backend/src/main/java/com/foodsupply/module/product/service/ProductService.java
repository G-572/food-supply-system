package com.foodsupply.module.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.foodsupply.common.BizException;
import com.foodsupply.common.PageResult;
import com.foodsupply.module.product.dto.ProductRequest;
import com.foodsupply.module.product.dto.ProductStatusRequest;
import com.foodsupply.module.product.entity.Product;
import com.foodsupply.module.product.mapper.ProductMapper;
import com.foodsupply.module.product.vo.ProductVO;
import com.foodsupply.module.supplier.entity.Supplier;
import com.foodsupply.module.supplier.mapper.SupplierMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String STATUS_ON_SALE = "ON_SALE";

    private final ProductMapper productMapper;
    private final SupplierMapper supplierMapper;

    /**
     * 采购员和管理员能看全部，可以按供货商筛；供货商账号只能看自家，
     * 传别的 supplierId 会被这里按死成自己的。
     */
    public PageResult<ProductVO> page(int pageNum, int pageSize, String keyword, Long supplierId,
                                      String category, String status, Long currentSupplierId) {
        int current = Math.max(pageNum, 1);
        int size = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        Long scopedSupplierId = currentSupplierId != null ? currentSupplierId : supplierId;

        LambdaQueryWrapper<Product> wrapper = Wrappers.<Product>lambdaQuery()
                .eq(scopedSupplierId != null, Product::getSupplierId, scopedSupplierId)
                .eq(StringUtils.hasText(category), Product::getCategory, category)
                .eq(StringUtils.hasText(status), Product::getStatus, status)
                .like(StringUtils.hasText(keyword), Product::getName, keyword)
                .orderByAsc(Product::getSupplierId)
                .orderByAsc(Product::getId);

        Page<Product> page = productMapper.selectPage(new Page<>(current, size), wrapper);
        return new PageResult<>(page.getTotal(), page.getCurrent(), page.getSize(), toVOList(page.getRecords()));
    }

    public ProductVO get(Long id, Long currentSupplierId) {
        Product product = requireOwned(id, currentSupplierId);
        return toVOList(List.of(product)).get(0);
    }

    @Transactional
    public Long create(ProductRequest request, Long currentSupplierId) {
        Long supplierId = supplierForCreate(request.getSupplierId(), currentSupplierId);
        assertNameAvailable(supplierId, request.getName(), null);

        Product product = new Product();
        product.setSupplierId(supplierId);
        product.setName(request.getName().trim());
        product.setCategory(request.getCategory());
        product.setUnit(request.getUnit());
        product.setPrice(request.getPrice());
        product.setStockQuantity(request.getStockQuantity());
        product.setRemark(request.getRemark());
        product.setStatus(STATUS_ON_SALE);
        productMapper.insert(product);

        log.info("新增商品: id={}, supplierId={}, name={}", product.getId(), supplierId, product.getName());
        return product.getId();
    }

    @Transactional
    public void update(Long id, ProductRequest request, Long currentSupplierId) {
        Product product = requireOwned(id, currentSupplierId);
        Long supplierId = supplierForUpdate(request.getSupplierId(), currentSupplierId, product.getSupplierId());
        assertNameAvailable(supplierId, request.getName(), id);

        product.setSupplierId(supplierId);
        product.setName(request.getName().trim());
        product.setCategory(request.getCategory());
        product.setUnit(request.getUnit());
        product.setPrice(request.getPrice());
        product.setStockQuantity(request.getStockQuantity());
        product.setRemark(request.getRemark());
        productMapper.updateById(product);
    }

    @Transactional
    public void changeStatus(Long id, ProductStatusRequest request, Long currentSupplierId) {
        requireOwned(id, currentSupplierId);
        productMapper.update(null, Wrappers.<Product>lambdaUpdate()
                .eq(Product::getId, id)
                .set(Product::getStatus, request.getStatus()));
        log.info("商品上下架: id={}, status={}", id, request.getStatus());
    }

    @Transactional
    public void delete(Long id, Long currentSupplierId) {
        requireOwned(id, currentSupplierId);

        long items = productMapper.countOrderItems(id);
        if (items > 0) {
            throw BizException.conflict("该商品已在 " + items + " 条订单明细里出现，不能删除，可以改成下架");
        }

        productMapper.deleteById(id);
        log.info("删除商品: id={}", id);
    }

    /**
     * 归属校验统一走这里：查不到 404，不是自家的 403。
     * currentSupplierId 为 null 代表管理员或采购员，不限定范围。
     */
    private Product requireOwned(Long id, Long currentSupplierId) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw BizException.notFound("商品不存在");
        }
        if (currentSupplierId != null && !currentSupplierId.equals(product.getSupplierId())) {
            throw BizException.forbidden("只能操作自己名下的商品");
        }
        return product;
    }

    /** 供货商账号一律用登录时绑定的那家，请求里的 supplierId 直接无视 */
    private Long supplierForCreate(Long fromRequest, Long currentSupplierId) {
        if (currentSupplierId != null) {
            return currentSupplierId;
        }
        if (fromRequest == null) {
            throw BizException.badRequest("管理员新增商品时需要指定供货商");
        }
        requireSupplierExists(fromRequest);
        return fromRequest;
    }

    /** 管理员不传就当没改归属，沿用原值 */
    private Long supplierForUpdate(Long fromRequest, Long currentSupplierId, Long current) {
        if (currentSupplierId != null) {
            return currentSupplierId;
        }
        if (fromRequest == null || fromRequest.equals(current)) {
            return current;
        }
        requireSupplierExists(fromRequest);
        return fromRequest;
    }

    private void requireSupplierExists(Long supplierId) {
        if (supplierMapper.selectById(supplierId) == null) {
            throw BizException.badRequest("指定的供货商不存在");
        }
    }

    /** 同一家供货商下商品名不能重复，不同供货商可以同名 */
    private void assertNameAvailable(Long supplierId, String name, Long excludeId) {
        Long count = productMapper.selectCount(Wrappers.<Product>lambdaQuery()
                .eq(Product::getSupplierId, supplierId)
                .eq(Product::getName, name.trim())
                .ne(excludeId != null, Product::getId, excludeId));
        if (count != null && count > 0) {
            throw BizException.conflict("这家供货商下已有同名商品");
        }
    }

    /** 供货商名称一次性批量取，避免逐条查 */
    private List<ProductVO> toVOList(List<Product> products) {
        if (products.isEmpty()) {
            return List.of();
        }
        List<Long> supplierIds = products.stream()
                .map(Product::getSupplierId)
                .distinct()
                .toList();
        Map<Long, String> nameMap = supplierMapper.selectBatchIds(supplierIds).stream()
                .collect(Collectors.toMap(Supplier::getId, Supplier::getName));

        return products.stream().map(product -> {
            ProductVO vo = new ProductVO();
            vo.setId(product.getId());
            vo.setSupplierId(product.getSupplierId());
            vo.setSupplierName(nameMap.get(product.getSupplierId()));
            vo.setName(product.getName());
            vo.setCategory(product.getCategory());
            vo.setUnit(product.getUnit());
            vo.setPrice(product.getPrice());
            vo.setStockQuantity(product.getStockQuantity());
            vo.setStatus(product.getStatus());
            vo.setRemark(product.getRemark());
            vo.setUpdatedAt(product.getUpdatedAt());
            return vo;
        }).toList();
    }
}
