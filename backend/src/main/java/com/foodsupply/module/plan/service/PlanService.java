package com.foodsupply.module.plan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.foodsupply.common.BizException;
import com.foodsupply.common.PageResult;
import com.foodsupply.module.plan.dto.PlanItemRequest;
import com.foodsupply.module.plan.dto.PlanRequest;
import com.foodsupply.module.plan.entity.PurchasePlan;
import com.foodsupply.module.plan.entity.PurchasePlanItem;
import com.foodsupply.module.plan.mapper.PurchasePlanItemMapper;
import com.foodsupply.module.plan.mapper.PurchasePlanMapper;
import com.foodsupply.module.plan.vo.PlanItemVO;
import com.foodsupply.module.plan.vo.PlanVO;
import com.foodsupply.module.product.entity.Product;
import com.foodsupply.module.product.mapper.ProductMapper;
import com.foodsupply.module.rbac.entity.SysUser;
import com.foodsupply.module.rbac.mapper.SysUserMapper;
import com.foodsupply.module.supplier.entity.Supplier;
import com.foodsupply.module.supplier.mapper.SupplierMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlanService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_ON_SALE = "ON_SALE";

    private final PurchasePlanMapper planMapper;
    private final PurchasePlanItemMapper planItemMapper;
    private final ProductMapper productMapper;
    private final SupplierMapper supplierMapper;
    private final SysUserMapper userMapper;

    /**
     * 采购员只看自己的计划，管理员能看全部也可以按采购员筛。
     * admin 由 Controller 按权限码判断后传进来。
     */
    public PageResult<PlanVO> page(int pageNum, int pageSize, String status,
                                   LocalDate planDate, Long purchaserId,
                                   Long currentUserId, boolean admin) {
        int current = Math.max(pageNum, 1);
        int size = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        Long scopedPurchaserId = admin ? purchaserId : currentUserId;

        LambdaQueryWrapper<PurchasePlan> wrapper = Wrappers.<PurchasePlan>lambdaQuery()
                .eq(StringUtils.hasText(status), PurchasePlan::getStatus, status)
                .eq(planDate != null, PurchasePlan::getPlanDate, planDate)
                .eq(scopedPurchaserId != null, PurchasePlan::getPurchaserId, scopedPurchaserId)
                .orderByDesc(PurchasePlan::getId);

        Page<PurchasePlan> page = planMapper.selectPage(new Page<>(current, size), wrapper);
        return new PageResult<>(page.getTotal(), page.getCurrent(), page.getSize(),
                toVOList(page.getRecords(), false));
    }

    public PlanVO get(Long id, Long currentUserId, boolean admin) {
        PurchasePlan plan = requirePlan(id, currentUserId, admin);
        return toVOList(List.of(plan), true).get(0);
    }

    @Transactional
    public Long create(PlanRequest request, Long currentUserId) {
        List<PlanItemRequest> items = normalizeItems(request.getItems());
        Map<Long, Product> products = loadProducts(items);
        BigDecimal total = calcTotal(items, products);

        PurchasePlan plan = new PurchasePlan();
        plan.setPlanNo(nextPlanNo());
        plan.setPurchaserId(currentUserId);
        plan.setPlanDate(request.getPlanDate() != null ? request.getPlanDate() : LocalDate.now());
        plan.setStatus(STATUS_DRAFT);
        plan.setTotalAmount(total);
        plan.setRemark(request.getRemark());
        planMapper.insert(plan);

        insertItems(plan.getId(), items);
        log.info("新建采购计划: no={}, 明细 {} 行, 合计 {}", plan.getPlanNo(), items.size(), total);
        return plan.getId();
    }

    /**
     * 全量替换明细。草稿才允许改，已发送的重新编辑没有意义，
     * 那个阶段要改只能走子单的取消流程。
     */
    @Transactional
    public void update(Long id, PlanRequest request, Long currentUserId, boolean admin) {
        PurchasePlan plan = requirePlan(id, currentUserId, admin);
        if (!STATUS_DRAFT.equals(plan.getStatus())) {
            throw BizException.conflict("计划已发送，不能再修改");
        }

        List<PlanItemRequest> items = normalizeItems(request.getItems());
        Map<Long, Product> products = loadProducts(items);
        BigDecimal total = calcTotal(items, products);

        planItemMapper.delete(Wrappers.<PurchasePlanItem>lambdaQuery()
                .eq(PurchasePlanItem::getPlanId, id));
        insertItems(id, items);

        plan.setPlanDate(request.getPlanDate() != null ? request.getPlanDate() : plan.getPlanDate());
        plan.setTotalAmount(total);
        plan.setRemark(request.getRemark());
        planMapper.updateById(plan);

        log.info("修改采购计划: no={}, 明细 {} 行", plan.getPlanNo(), items.size());
    }

    @Transactional
    public void delete(Long id, Long currentUserId, boolean admin) {
        PurchasePlan plan = requirePlan(id, currentUserId, admin);
        if (!STATUS_DRAFT.equals(plan.getStatus())) {
            throw BizException.conflict("计划已发送，不能删除");
        }
        // 明细表外键带 ON DELETE CASCADE，删主表即可
        planMapper.deleteById(id);
        log.info("删除采购计划: no={}", plan.getPlanNo());
    }

    private PurchasePlan requirePlan(Long id, Long currentUserId, boolean admin) {
        PurchasePlan plan = planMapper.selectById(id);
        if (plan == null) {
            throw BizException.notFound("采购计划不存在");
        }
        if (!admin && !currentUserId.equals(plan.getPurchaserId())) {
            throw BizException.forbidden("只能操作自己的采购计划");
        }
        return plan;
    }

    /** 同一张计划里一个商品只允许出现一行，合并逻辑留给前端，后端直接拒 */
    private List<PlanItemRequest> normalizeItems(List<PlanItemRequest> items) {
        Set<Long> seen = new HashSet<>();
        for (PlanItemRequest item : items) {
            if (!seen.add(item.getProductId())) {
                throw BizException.badRequest("同一商品在一张计划里只能出现一次");
            }
        }
        return items;
    }

    /** 一次把涉及的商品取出来，同时校验存在与在售状态 */
    private Map<Long, Product> loadProducts(List<PlanItemRequest> items) {
        List<Long> productIds = items.stream().map(PlanItemRequest::getProductId).toList();
        Map<Long, Product> products = productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        for (PlanItemRequest item : items) {
            Product product = products.get(item.getProductId());
            if (product == null) {
                throw BizException.badRequest("商品不存在: " + item.getProductId());
            }
            if (!STATUS_ON_SALE.equals(product.getStatus())) {
                throw BizException.badRequest("商品已下架: " + product.getName());
            }
        }
        return products;
    }

    private BigDecimal calcTotal(List<PlanItemRequest> items, Map<Long, Product> products) {
        BigDecimal total = BigDecimal.ZERO;
        for (PlanItemRequest item : items) {
            BigDecimal price = products.get(item.getProductId()).getPrice();
            total = total.add(price.multiply(item.getQuantity()));
        }
        return total.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private void insertItems(Long planId, List<PlanItemRequest> items) {
        for (PlanItemRequest item : items) {
            PurchasePlanItem entity = new PurchasePlanItem();
            entity.setPlanId(planId);
            entity.setProductId(item.getProductId());
            entity.setQuantity(item.getQuantity());
            planItemMapper.insert(entity);
        }
    }

    private String nextPlanNo() {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        Long today = planMapper.selectCount(Wrappers.<PurchasePlan>lambdaQuery()
                .likeRight(PurchasePlan::getPlanNo, "PP" + date));
        return "PP" + date + String.format("%03d", (today == null ? 0 : today) + 1);
    }

    private List<PlanVO> toVOList(List<PurchasePlan> plans, boolean withItems) {
        if (plans.isEmpty()) {
            return List.of();
        }
        Map<Long, String> purchaserNames = userMapper.selectBatchIds(
                        plans.stream().map(PurchasePlan::getPurchaserId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(SysUser::getId, u ->
                        u.getRealName() == null ? u.getUsername() : u.getRealName()));

        return plans.stream().map(plan -> {
            PlanVO vo = new PlanVO();
            vo.setId(plan.getId());
            vo.setPlanNo(plan.getPlanNo());
            vo.setPurchaserId(plan.getPurchaserId());
            vo.setPurchaserName(purchaserNames.get(plan.getPurchaserId()));
            vo.setPlanDate(plan.getPlanDate());
            vo.setStatus(plan.getStatus());
            vo.setTotalAmount(plan.getTotalAmount());
            vo.setRemark(plan.getRemark());
            vo.setCreatedAt(plan.getCreatedAt());
            if (withItems) {
                vo.setItems(fillItems(plan.getId()));
            }
            return vo;
        }).toList();
    }

    /** 明细上的名称、单价、供货商都从商品表补，商品被改名了这里会跟着变 */
    private List<PlanItemVO> fillItems(Long planId) {
        List<PurchasePlanItem> items = planItemMapper.selectList(
                Wrappers.<PurchasePlanItem>lambdaQuery()
                        .eq(PurchasePlanItem::getPlanId, planId)
                        .orderByAsc(PurchasePlanItem::getId));
        if (items.isEmpty()) {
            return List.of();
        }

        Map<Long, Product> products = productMapper.selectBatchIds(
                        items.stream().map(PurchasePlanItem::getProductId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        Map<Long, String> supplierNames = supplierMapper.selectBatchIds(
                        products.values().stream().map(Product::getSupplierId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Supplier::getId, Supplier::getName));

        return items.stream().map(item -> {
            PlanItemVO vo = new PlanItemVO();
            vo.setId(item.getId());
            vo.setProductId(item.getProductId());
            vo.setQuantity(item.getQuantity());

            Product product = products.get(item.getProductId());
            if (product != null) {
                vo.setProductName(product.getName());
                vo.setUnit(product.getUnit());
                vo.setUnitPrice(product.getPrice());
                vo.setSupplierId(product.getSupplierId());
                vo.setSupplierName(supplierNames.get(product.getSupplierId()));
                vo.setSubtotal(product.getPrice().multiply(item.getQuantity()));
            }
            return vo;
        }).toList();
    }
}
