package cn.vpclub.coupon.command.aggregates;

import cn.vpclub.coupon.api.commands.other.UpdateOrderFailCommand;
import cn.vpclub.coupon.api.commands.other.UpdateOrderSuccessCommand;
import cn.vpclub.coupon.api.events.other.OrderUpdateFailedEvent;
import cn.vpclub.coupon.api.events.other.OrderUpdateSuccessedEvent;
import cn.vpclub.coupon.api.utils.JSONUtils;
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

import static org.axonframework.commandhandling.model.AggregateLifecycle.apply;

/**
 * Created by yangqiao on 2017/12/12.
 */
@NoArgsConstructor
@ToString
@Slf4j
@Aggregate(repository = "orderAggregateRepository")
@Data
public class OrderAggregate extends BaseEntity {

    @AggregateIdentifier
    private Long id;

    /**
     * 主订单id
     */
    private Long mainOrderId;

    /**
     * 状态
     */
    private String status;

    public OrderAggregate(UpdateOrderSuccessCommand command) throws IllegalAccessException, NoSuchMethodException,
            InvocationTargetException {
        OrderUpdateSuccessedEvent event = new OrderUpdateSuccessedEvent(command.getMainOrderId());
        apply(event);
    }

    public OrderAggregate(UpdateOrderFailCommand command) throws IllegalAccessException, NoSuchMethodException,
            InvocationTargetException {
        OrderUpdateFailedEvent event = new OrderUpdateFailedEvent(command.getMainOrderId());
        apply(event);
    }

    @EventHandler
    public void on(OrderUpdateSuccessedEvent event) {

        this.id = IdWorker.getId();
        this.mainOrderId = event.getMainOrderId();
        this.status = "success";

        log.info("OrderUpdateSuccessedEvent.id:{}", JSONUtils.toJson(this));
    }


    @EventHandler
    public void on(OrderUpdateFailedEvent event) {

        this.id = IdWorker.getId();
        this.mainOrderId = event.getMainOrderId();
        this.status = "fail";

        log.info("OrderUpdateFailedEvent.id:{}", JSONUtils.toJson(this));
    }

}