package cn.vpclub.coupon.command.aggregates;

import cn.vpclub.coupon.api.commands.other.ThirdPartyCouponLogCommand;
import cn.vpclub.coupon.api.events.other.ThirdPartyCouponLogEvent;
import cn.vpclub.moses.core.entity.BaseEntity;
import cn.vpclub.moses.utils.common.IdWorker;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.model.AggregateIdentifier;
import org.axonframework.spring.stereotype.Aggregate;

import java.lang.reflect.InvocationTargetException;

import static org.axonframework.commandhandling.model.AggregateLifecycle.apply;

/**
 * Created by yangqiao on 2017/12/12.
 */
@NoArgsConstructor
@ToString
@Slf4j
@Aggregate(repository = "thirdPartyCouponLogAggregateRepository")
@Data
public class ThirdPartyCouponLogAggregate extends BaseEntity {

    @AggregateIdentifier
    private Long id;

    public ThirdPartyCouponLogAggregate(ThirdPartyCouponLogCommand command) throws IllegalAccessException, NoSuchMethodException,
            InvocationTargetException {
        this.id = IdWorker.getId();

        ThirdPartyCouponLogEvent event = new ThirdPartyCouponLogEvent();
        event.setDoType(command.getDoType());
        event.setThirdPartyCouponLog(command.getThirdPartyCouponLog());
        apply(event);
    }
}