package cn.vpclub.coupon.command.aggregates;

import cn.vpclub.coupon.api.events.coupon.ResendCouponEvent;
import cn.vpclub.moses.utils.common.IdWorker;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.model.AggregateIdentifier;
import org.axonframework.spring.stereotype.Aggregate;

import static org.axonframework.commandhandling.model.AggregateLifecycle.apply;

/**
 * @author:yangqiao
 * @description:
 * @Date:2017/12/23
 */
@NoArgsConstructor
@ToString
@Slf4j
@Aggregate(repository = "couponResendAggregateRepository")
public class CouponResendAggregate {

    @AggregateIdentifier
    private Long id;

    public CouponResendAggregate(Long id, ResendCouponEvent event) {
        this.id = IdWorker.getId();
        apply(event);
    }

}