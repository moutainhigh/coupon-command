package cn.vpclub.coupon.command.aggregates;

import cn.vpclub.coupon.api.commands.coupon.CreateCouponDetailCommand;
import cn.vpclub.coupon.api.commands.coupon.ReleaseCouponDetailCommand;
import cn.vpclub.coupon.api.entity.CouponDetail;
import cn.vpclub.coupon.api.events.coupon.CouponDetailCreatedEvent;
import cn.vpclub.coupon.api.events.coupon.CouponDetailReleasedEvent;
import cn.vpclub.moses.common.api.events.pay.OrderPaidEvent;
import cn.vpclub.moses.core.entity.BaseEntity;
import cn.vpclub.moses.utils.common.IdWorker;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.model.AggregateIdentifier;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.spring.stereotype.Aggregate;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

import static org.axonframework.commandhandling.model.AggregateLifecycle.apply;

/**
 * Created by yangqiao on 2017/12/12.
 */
@NoArgsConstructor
@ToString
@Slf4j
@Aggregate(repository = "couponDetailAggregateRepository")
@Data
public class CouponDetailAggregate extends BaseEntity {

    @AggregateIdentifier
    private Long id;

    private List<CouponDetail> couponDetailList;

    private OrderPaidEvent orderPaidEvent;

    public CouponDetailAggregate(CreateCouponDetailCommand command) throws IllegalAccessException, NoSuchMethodException,
            InvocationTargetException {

        CouponDetailCreatedEvent event = new CouponDetailCreatedEvent();
        event.setCouponDetailList(command.getCouponDetailList());
        apply(event);
    }

    public CouponDetailAggregate(ReleaseCouponDetailCommand command) throws IllegalAccessException, NoSuchMethodException,
            InvocationTargetException {
        CouponDetailReleasedEvent event = new CouponDetailReleasedEvent();
        event.setOrderPaidEvent(command.getOrderPaidEvent());
        apply(event);
    }


    @EventHandler
    public void on(CouponDetailCreatedEvent event) {
        //赋值
        this.id = IdWorker.getId();
        this.couponDetailList = event.getCouponDetailList();
    }

    @EventHandler
    public void on(CouponDetailReleasedEvent event) {
        //赋值
        this.id = IdWorker.getId();
        this.orderPaidEvent = event.getOrderPaidEvent();
    }
}