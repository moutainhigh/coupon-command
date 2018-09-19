package cn.vpclub.coupon.command.aggregates;

import cn.vpclub.coupon.api.commands.other.UpdateCodeStatusCommand;
import cn.vpclub.coupon.api.events.other.CodeUpdateSuccessedEvent;
import cn.vpclub.coupon.api.utils.JSONUtils;
import cn.vpclub.moses.common.api.events.pay.OrderPaidEvent;
import cn.vpclub.moses.core.entity.BaseEntity;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.model.AggregateIdentifier;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.spring.stereotype.Aggregate;

import static org.axonframework.commandhandling.model.AggregateLifecycle.apply;

/**
 * Created by zhangyingdong on 2018/7/11.
 */
@NoArgsConstructor
@ToString
@Slf4j
@Aggregate(repository = "iqiyiCouponAggregateRepository")
@Data
public class IqiyiCouponAggregate extends BaseEntity {
    @AggregateIdentifier
    private Long id;

    private String code;


    public IqiyiCouponAggregate( Long id,String code,OrderPaidEvent orderPaidEvent) {
        CodeUpdateSuccessedEvent codeUpdateSuccessedEvent=new CodeUpdateSuccessedEvent(id,code,orderPaidEvent);
        apply(codeUpdateSuccessedEvent);
    }

    @EventHandler
    public void on(CodeUpdateSuccessedEvent event) {
        this.id=event.getId();
        this.code=event.getCode();

        log.info("O2OiqiyiCouponCreatedEvent.id:{}", JSONUtils.toJson(this.id));
    }

}
