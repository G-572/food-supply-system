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
import com.foodsupply.module.plan.vo.SendResultVO;
import com.foodsupply.module.order.entity.PurchaseOrder;
import com.foodsupply.module.order.entity.PurchaseOrderItem;
import com.foodsupply.module.order.mapper.PurchaseOrderItemMapper;
import com.foodsupply.module.order.mapper.PurchaseOrderMapper;
import com.foodsupply.module.order.vo.OrderBriefVO;
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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
    private static final String STATUS_SENT = "SENT";
    private static final String STATUS_PENDING_CONFIRM = "PENDING_CONFIRM";
    private static final String STATUS_ON_SALE = "ON_SALE";

    private final PurchasePlanMapper planMapper;
    private final PurchasePlanItemMapper planItemMapper;
    private final PurchaseOrderMapper orderMapper;
    private final PurchaseOrderItemMapper orderItemMapper;
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

    /**
     * 发送计划并拆单。按明细商品的所属供货商分组，一组生成一张子单，
     * 名称、单位、单价在这里快照进子单明细，之后供货商改价不影响已发出的单。
     * 整个动作一个事务，中途任何一张单失败都整体回滚。
     */
    @Transactional
    public SendResultVO send(Long id, Long currentUserId, boolean admin) {
        PurchasePlan plan = requirePlan(id, currentUserId, admin);
        if (!STATUS_DRAFT.equals(plan.getStatus())) {
            throw BizException.conflict("计划已发送，不能重复发送");
        }

        List<PurchasePlanItem> items = planItemMapper.selectList(
                Wrappers.<PurchasePlanItem>lambdaQuery()
                        .eq(PurchasePlanItem::getPlanId, id)
                        .orderByAsc(PurchasePlanItem::getId));
        if (items.isEmpty()) {
            throw BizException.badRequest("计划没有明细，不能发送");
        }

        Map<Long, Product> products = loadProductsByIds(
                items.stream().map(PurchasePlanItem::getProductId).toList());

        // 用 LinkedHashMap 保住明细顺序，拆出来的单子顺序稳定，前端好展示
        Map<Long, List<PurchasePlanItem>> grouped = new LinkedHashMap<>();
        for (PurchasePlanItem item : items) {
            Long supplierId = products.get(item.getProductId()).getSupplierId();
            grouped.computeIfAbsent(supplierId, k -> new ArrayList<>()).add(item);
        }

        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        int seq = todayOrderCount(date);
        BigDecimal total = BigDecimal.ZERO;
        List<OrderBriefVO> orders = new ArrayList<>();

        for (Map.Entry<Long, List<PurchasePlanItem>> entry : grouped.entrySet()) {
            PurchaseOrder order = buildOrder(entry.getKey(), id, date, ++seq);
            BigDecimal orderTotal = BigDecimal.ZERO;

            for (PurchasePlanItem item : entry.getValue()) {
                Product product = products.get(item.getProductId());
                BigDecimal subtotal = product.getPrice().multiply(item.getQuantity());
                orderTotal = orderTotal.add(subtotal);

                PurchaseOrderItem orderItem = new PurchaseOrderItem();
                orderItem.setOrderId(order.getId());
                orderItem.setProductId(product.getId());
                orderItem.setProductName(product.getName());
                orderItem.setUnit(product.getUnit());
                orderItem.setQuantity(item.getQuantity());
                orderItem.setUnitPrice(product.getPrice());
                orderItem.setSubtotal(subtotal.setScale(2, RoundingMode.HALF_UP));
                orderItemMapper.insert(orderItem);
            }

            orderTotal = orderTotal.setScale(2, RoundingMode.HALF_UP);
            order.setTotalAmount(orderTotal);
            orderMapper.updateById(order);
            total = total.add(orderTotal);

            OrderBriefVO brief = new OrderBriefVO();
            brief.setId(order.getId());
            brief.setOrderNo(order.getOrderNo());
            brief.setSupplierId(order.getSupplierId());
            brief.setTotalAmount(orderTotal);
            brief.setItemCount(entry.getValue().size());
            orders.add(brief);
        }

        // 供货商名字统一补一次，别在循环里查
        Map<Long, String> supplierNames = supplierMapper.selectBatchIds(grouped.keySet()).stream()
                .collect(Collectors.toMap(Supplier::getId, Supplier::getName));
        orders.forEach(o -> o.setSupplierName(supplierNames.get(o.getSupplierId())));

        planMapper.update(null, Wrappers.<PurchasePlan>lambdaUpdate()
                .eq(PurchasePlan::getId, id)
                .set(PurchasePlan::getStatus, STATUS_SENT));

        log.info("计划已发送: no={}, 拆成 {} 张单, 合计 {}", plan.getPlanNo(), orders.size(), total);

        SendResultVO result = new SendResultVO();
        result.setPlanId(id);
        result.setPlanNo(plan.getPlanNo());
        result.setOrderCount(orders.size());
        result.setTotalAmount(total);
        result.setOrders(orders);
        return result;
    }

    private PurchaseOrder buildOrder(Long supplierId, Long planId, String date, int seq) {
        PurchaseOrder order = new PurchaseOrder();
        order.setOrderNo("SO" + date + String.format("%03d", seq));
        order.setPlanId(planId);
        order.setSupplierId(supplierId);
        order.setStatus(STATUS_PENDING_CONFIRM);
        order.setTotalAmount(BigDecimal.ZERO);
        orderMapper.insert(order);
        return order;
    }

    /** 当天已生成的子单数，用作流水起点 */
    private int todayOrderCount(String date) {
        Long count = orderMapper.selectCount(Wrappers.<PurchaseOrder>lambdaQuery()
                .likeRight(PurchaseOrder::getOrderNo, "SO" + date));
        return count == null ? 0 : count.intValue();
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
        return loadProductsByIds(items.stream().map(PlanItemRequest::getProductId).toList());
    }

    private Map<Long, Product> loadProductsByIds(List<Long> productIds) {
        List<Long> distinctIds = productIds.stream().distinct().toList();
        Map<Long, Product> products = productMapper.selectBatchIds(distinctIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        for (Long productId : distinctIds) {
            Product product = products.get(productId);
            if (product == null) {
                throw BizException.badRequest("商品不存在: " + productId);
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
