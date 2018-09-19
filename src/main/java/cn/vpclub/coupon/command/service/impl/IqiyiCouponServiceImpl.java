package cn.vpclub.coupon.command.service.impl;

import cn.vpclub.coupon.api.commands.other.UpdateCodeStatusCommand;
import cn.vpclub.coupon.api.entity.CouponIqiyi;
import cn.vpclub.coupon.api.entity.ProductThirdpartyRelated;
import cn.vpclub.coupon.command.rpc.CouponIqiyiService;
import cn.vpclub.coupon.command.service.IIqiyiCouponService;
import cn.vpclub.moses.common.api.events.pay.OrderPaidEvent;
import cn.vpclub.moses.core.model.response.BaseResponse;
import cn.vpclub.moses.utils.common.IdWorker;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

/**
 * Created by zhangyingdong on 2018/7/11.
 */
@Service
@Slf4j
@AllArgsConstructor
public class IqiyiCouponServiceImpl implements IIqiyiCouponService {

    private CommandGateway commandGateway;
    @Autowired
    private CouponIqiyiService couponIqiyiService;
    @Override
    public void makeCoupon(OrderPaidEvent event, ProductThirdpartyRelated result, BaseResponse thirdPartyResponse) throws IOException {
        //根据商品id查询券码
        CouponIqiyi couponIqiyi=new CouponIqiyi();
        couponIqiyi.setProductId(event.getSubOrderList().get(0).getOrderItemList().get(0).getProductId());
        BaseResponse baseResponse=couponIqiyiService.queryByProductId(couponIqiyi);
        List<CouponIqiyi> couponIqiyiList = (List<CouponIqiyi>) baseResponse.getDataInfo();
        if(CollectionUtils.isEmpty(couponIqiyiList)){
            log.info("爱奇艺发码失败，无可用库存");
            return ;
        }else {
            String code=couponIqiyiList.get(0).getCardNo();
            Long id=couponIqiyiList.get(0).getId();
            this.sendOrderSuccessCommand(id,code,event);
        }
    }

    //发送修改相应code的分配状态
    private void sendOrderSuccessCommand(Long id,String code,OrderPaidEvent event) {
        UpdateCodeStatusCommand updateCodeStatusCommand = new UpdateCodeStatusCommand(id, code,event);
        commandGateway.send(updateCodeStatusCommand);
    }
}
