package cn.vpclub.coupon.command.service;

import cn.vpclub.coupon.api.entity.ProductThirdpartyRelated;
import cn.vpclub.moses.common.api.events.pay.OrderPaidEvent;
import cn.vpclub.moses.core.model.response.BaseResponse;

import java.io.IOException;

/**
 * @author:yangqiao
 * @description:肯德基服务service
 * @Date:2018/1/11
 */
public interface IKfcCouponService {

    /**
     * 发送肯德基接口，获取新卡券
     *
     * @param event,result
     * @return
     */
    void makeCoupon(OrderPaidEvent event, ProductThirdpartyRelated result, BaseResponse thirdPartyResponse) throws IOException;
}