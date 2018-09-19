package cn.vpclub.coupon.command.service;

import cn.vpclub.moses.common.api.events.pay.OrderPaidEvent;

import java.io.IOException;

/**
 * @author:yangqiao
 * @description:肯德基服务service
 * @Date:2018/1/11
 */
public interface IOrderPaidService {

    /**
     * 处理订单支付事件逻辑
     *
     * @param event
     * @return
     */
    void handle(OrderPaidEvent event) throws IOException;
}