package cn.vpclub.coupon.command.aggregates;

import cn.vpclub.coupon.api.commands.coupon.CreateCouponBatchCommand;
import cn.vpclub.coupon.api.commands.coupon.CreateCouponDetailCommand;
import cn.vpclub.coupon.api.entity.Coupon;
import cn.vpclub.coupon.api.events.coupon.CouponBatchCreatedEvent;
import cn.vpclub.coupon.api.events.coupon.CouponBatchInventoryIncreasedEvent;
import cn.vpclub.moses.core.entity.BaseEntity;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.model.AggregateIdentifier;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.spring.stereotype.Aggregate;

import java.lang.reflect.InvocationTargetException;

import static org.axonframework.commandhandling.model.AggregateLifecycle.apply;

/**
 * Created by yangqiao on 2017/12/12.
 */
@NoArgsConstructor
@ToString
@Slf4j
@Aggregate(repository = "couponBatchAggregateRepository")
@Data
public class CouponBatchAggregate extends BaseEntity {

    @AggregateIdentifier
    private Long id;
    /**
     * 应用id
     */
    private Long appId;
    /**
     * 批次号
     */
    private String batchNo;

    /**
     * 名称
     */
    private String name;
    /**
     * 关联商户
     */
    private Long relatedBusiness;
    /**
     * 关联商户名称
     */
    private String relatedBusinessName;
    /**
     * 关联商品
     */
    private Long relatedGoods;

    /**
     * 商品sku
     */
    private Long relatedGoodsSku;

    /**
     * 关联商品名称
     */
    private String relatedGoodsName;
    /**
     * 卡券使用有效期起始日期
     */
    private Long effectiveDateStart;
    /**
     * 卡券使用有效期终止日期
     */
    private Long effectiveDateEnd;
    /**
     * 下架日期
     */
    private Long offDate;
    /**
     * 是否失效，1-未失效，2-已失效
     */
    private Integer invalid;
    /**
     * 失效人
     */
    private Long invalidBy;
    /**
     * 失效时间
     */
    private Long invalidTime;
    /**
     * 库存
     */
    private Integer inventory;

    public CouponBatchAggregate(CreateCouponBatchCommand command) throws IllegalAccessException, NoSuchMethodException,
            InvocationTargetException {
        CouponBatchCreatedEvent event = new CouponBatchCreatedEvent();
        event.setCoupon(command.getCoupon());
        apply(event);
    }

    @EventHandler
    public void on(CouponBatchCreatedEvent event) {

        Coupon coupon = event.getCoupon();
        //赋值
        this.id = coupon.getId();
        this.appId = coupon.getAppId();
        this.batchNo = coupon.getBatchNo();
        this.name = coupon.getName();
        this.relatedBusiness = coupon.getRelatedBusiness();
        this.relatedBusinessName = coupon.getRelatedBusinessName();
        this.relatedGoods = coupon.getRelatedGoods();
        this.relatedGoodsSku = coupon.getRelatedGoodsSku();
        this.relatedGoodsName = coupon.getRelatedGoodsName();
        this.effectiveDateStart = coupon.getEffectiveDateStart();
        this.effectiveDateEnd = coupon.getEffectiveDateEnd();
        this.offDate = coupon.getOffDate();
        this.invalid = coupon.getInvalid();
        this.invalidBy = coupon.getInvalidBy();
        this.invalidTime = coupon.getInvalidTime();
        //创建批次，初始库存为0
        this.inventory = 0;
    }

    /**
     * 新增库存
     */
    public void increaseInventory(CreateCouponDetailCommand command) {
        CouponBatchInventoryIncreasedEvent event = new CouponBatchInventoryIncreasedEvent();
        event.setRelatedGoodsSku(command.getRelatedGoodsSku());
        event.setNumber(command.getCouponDetailList().size());
        apply(event);
    }

    @EventHandler
    public void on(CouponBatchInventoryIncreasedEvent event) {

        //增加库存
        this.inventory += event.getNumber();
    }

}