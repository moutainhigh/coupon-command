package cn.vpclub.coupon.command.service;

import cn.vpclub.coupon.api.entity.ProductThirdpartyRelated;
import cn.vpclub.moses.common.api.events.pay.OrderPaidEvent;
import cn.vpclub.moses.core.model.response.BaseResponse;

import java.io.IOException;

/**
 * zhangyingdong
 * 2018-4-2
 */
public interface IKinggloryCouponService {
    void makeCoupon(OrderPaidEvent event, ProductThirdpartyRelated result, BaseResponse thirdPartyResponse) throws Exception;
}
