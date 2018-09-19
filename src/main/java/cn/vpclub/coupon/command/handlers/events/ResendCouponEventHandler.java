package cn.vpclub.coupon.command.handlers.events;

import cn.vpclub.coupon.api.events.coupon.ResendCouponEvent;
import cn.vpclub.coupon.api.utils.JSONUtils;
import cn.vpclub.coupon.command.service.IOrderPaidService;
import cn.vpclub.moses.common.api.events.pay.OrderPaidEvent;
import cn.vpclub.moses.common.constant.OrderConstant;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * @author:yangqiao
 * @description:
 * @Date:2017/12/16
 */
@Component
@Slf4j
@AllArgsConstructor
//@ProcessingGroup("message")
public class ResendCouponEventHandler {

    @Autowired
    private IOrderPaidService iOrderPaidService;

    @EventHandler
    public void handle(ResendCouponEvent resendCouponEvent) throws IOException {

        OrderPaidEvent event = resendCouponEvent.getOrderPaidEvent();

        //校验订单支付事件参数是否齐全
        if (event == null || CollectionUtils.isEmpty(event.getSubOrderList()) || CollectionUtils.isEmpty(event.getSubOrderList
                ().get(0).getOrderItemList())) {
            log.error("ResendCouponEventHandler Not Complete: {}", JSONUtils.toJson(event));
            return;
        }

        // 监听订单支付事件，判断是否O2O订单或者卡券订单，如果不是，则不处理
        if (!OrderConstant.PURCHASETYPE_8.equals(event.getPurchaseType()) && !OrderConstant.PURCHASETYPE_9.equals(event
                .getPurchaseType())) {
            return;
        }

        iOrderPaidService.handle(event);
    }

}