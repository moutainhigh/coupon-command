package cn.vpclub.coupon.command.service;

import cn.vpclub.coupon.api.entity.ProductThirdpartyRelated;
import cn.vpclub.moses.common.api.events.pay.OrderPaidEvent;
import cn.vpclub.moses.core.model.response.BaseResponse;

import java.io.IOException;

/**
 * Created by zhangyingdong on 2018/6/26.
 */
public interface IIqiyiCouponService {
    void makeCoupon(OrderPaidEvent event, ProductThirdpartyRelated result, BaseResponse thirdPartyResponse) throws IOException;
}
