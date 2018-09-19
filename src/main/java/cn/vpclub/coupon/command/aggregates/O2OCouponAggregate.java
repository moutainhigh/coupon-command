package cn.vpclub.coupon.command.aggregates;

import cn.vpclub.coupon.api.commands.o2ocoupon.ConsumeO2OCouponCommand;
import cn.vpclub.coupon.api.entity.O2oCoupon;
import cn.vpclub.coupon.api.events.o2ocoupon.ConsumeO2OCouponEvent;
import cn.vpclub.coupon.api.events.o2ocoupon.O2OCouponCreatedEvent;
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

import java.util.List;

import static org.axonframework.commandhandling.model.AggregateLifecycle.apply;

/**
 * Created by yangqiao on 2017/12/12.
 */
@NoArgsConstructor
@ToString
@Slf4j
@Aggregate(repository = "o2OCouponAggregateRepository")
public class O2OCouponAggregate extends BaseEntity {

    @AggregateIdentifier
    private Long id;

    private List<O2oCoupon> o2oCouponList;

    public O2OCouponAggregate(List<O2oCoupon> o2oCouponList, OrderPaidEvent orderPaidEvent) {
        this.id = IdWorker.getId();
        apply(new O2OCouponCreatedEvent(o2oCouponList, orderPaidEvent));
    }

    public O2OCouponAggregate(ConsumeO2OCouponCommand command) {
        this.id = IdWorker.getId();
        apply(new ConsumeO2OCouponEvent(command.getO2oCouponId(), command.getValidBy(), command.getValidOrg(), command
                .getOrgName(), command.getOrgPhone()));
    }

    @EventHandler
    public void on(O2OCouponCreatedEvent event) {

        this.o2oCouponList = event.getO2oCouponList();
        log.info("O2OCouponCreatedEvent.id:{}", JSONUtils.toJson(this.id));
    }

    @EventHandler
    public void on(ConsumeO2OCouponEvent event) {
        log.info("ConsumeO2OCouponEvent.id:{}", JSONUtils.toJson(this.id));
    }
}