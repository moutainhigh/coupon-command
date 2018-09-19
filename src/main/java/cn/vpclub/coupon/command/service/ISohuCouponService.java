package cn.vpclub.coupon.command.service;


import cn.vpclub.coupon.api.entity.ProductThirdpartyRelated;
import cn.vpclub.moses.common.api.events.pay.OrderPaidEvent;
import cn.vpclub.moses.core.model.response.BaseResponse;

import java.io.IOException;
import java.text.ParseException;

/**
* @author:zhangyingdong
* @description:搜狐服务service
* @Date:2018/1/31
*/
public interface ISohuCouponService {

    void makeCoupon(OrderPaidEvent event, ProductThirdpartyRelated result, BaseResponse thirdPartyResponse) throws IOException;
}
