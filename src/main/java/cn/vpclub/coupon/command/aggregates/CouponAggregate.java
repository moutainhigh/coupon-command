package cn.vpclub.coupon.command.aggregates;

import cn.vpclub.coupon.api.commands.coupon.CreateCouponCommand;
import cn.vpclub.coupon.api.events.o2ocoupon.CouponCreatedEvent;
import cn.vpclub.moses.core.entity.BaseEntity;
import cn.vpclub.moses.utils.common.IdWorker;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.model.AggregateIdentifier;
import org.axonframework.spring.stereotype.Aggregate;
import org.springframework.beans.BeanUtils;

import java.lang.reflect.InvocationTargetException;

import static org.axonframework.commandhandling.model.AggregateLifecycle.apply;

/**
 * Created by yangqiao on 2017/12/12.
 */
@NoArgsConstructor
@ToString
@Slf4j
@Aggregate(repository = "couponAggregateRepository")
@Data
public class CouponAggregate extends BaseEntity {

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
    private String relatedBusiness;
    /**
     * 关联商品
     */
    private String relatedGoods;
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
    private Long off_date;

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

    public CouponAggregate(CreateCouponCommand command) throws IllegalAccessException, NoSuchMethodException,
            InvocationTargetException {
        this.id = IdWorker.getId();
        CouponCreatedEvent event = new CouponCreatedEvent();
        BeanUtils.copyProperties(command, event);
        apply(event);
    }
}