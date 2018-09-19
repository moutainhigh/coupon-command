package cn.vpclub.coupon.command.aggregates;

import cn.vpclub.coupon.api.entity.O2oCoupon;
import cn.vpclub.coupon.api.events.o2ocoupon.O2OYoukuCouponCreatedEvent;
import cn.vpclub.coupon.api.utils.JSONUtils;
import cn.vpclub.moses.common.api.events.pay.OrderPaidEvent;
import cn.vpclub.moses.core.entity.BaseEntity;
import cn.vpclub.moses.utils.common.IdWorker;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.model.AggregateIdentifier;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.spring.stereotype.Aggregate;

import static org.axonframework.commandhandling.model.AggregateLifecycle.apply;

/**
 * Created by zhangyingdong on 2018/6/29.
 */
@NoArgsConstructor
@ToString
@Slf4j
@Aggregate(repository = "o2OYoukuCouponAggregateRepository")
public class O2OYoukuCouponAggregate extends BaseEntity {

    @AggregateIdentifier
    private Long id;

    private O2oCoupon o2oCoupon;

    public O2OYoukuCouponAggregate(O2oCoupon o2oCoupon, OrderPaidEvent orderPaidEvent) {
        this.id = IdWorker.getId();
        apply(new O2OYoukuCouponCreatedEvent(o2oCoupon, orderPaidEvent));
    }

    @EventHandler
    public void on(O2OYoukuCouponCreatedEvent event) {

        this.o2oCoupon = event.getO2oCoupon();
        log.info("O2OYoukuCouponCreatedEvent.id:{}", JSONUtils.toJson(this.id));
    }
}
