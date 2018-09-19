package cn.vpclub.coupon.command.service;

import cn.vpclub.coupon.api.entity.ProductThirdpartyRelated;
import cn.vpclub.moses.common.api.events.pay.OrderPaidEvent;
import cn.vpclub.moses.core.model.response.BaseResponse;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.io.IOException;

public interface IMacDonaldCouponService {

    void makeCoupon(OrderPaidEvent event,ProductThirdpartyRelated result, BaseResponse thirdPartyResponse) throws IOException;
}
