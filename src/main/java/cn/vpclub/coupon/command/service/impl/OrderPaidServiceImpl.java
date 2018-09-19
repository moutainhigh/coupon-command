package cn.vpclub.coupon.command.service.impl;

import cn.vpclub.coupon.api.commands.coupon.ReleaseCouponDetailCommand;
import cn.vpclub.coupon.api.commands.o2ocoupon.CreateO2OCouponCommand;
import cn.vpclub.coupon.api.commands.other.UpdateOrderFailCommand;
import cn.vpclub.coupon.api.commands.other.UpdateOrderSuccessCommand;
import cn.vpclub.coupon.api.constants.CouponConstant;
import cn.vpclub.coupon.api.entity.O2oCoupon;
import cn.vpclub.coupon.api.entity.ProductThirdpartyRelated;
import cn.vpclub.coupon.api.entity.ThirdPartyCouponLog;
import cn.vpclub.coupon.api.requests.coupon.CouponMixRequest;
import cn.vpclub.coupon.api.utils.JSONUtils;
import cn.vpclub.coupon.command.rpc.CouponDetailRpcService;
import cn.vpclub.coupon.command.rpc.ProductThirdpartyRelatedRpcService;
import cn.vpclub.coupon.command.rpc.ThirdPartyCouponLogRpcService;
import cn.vpclub.coupon.command.service.*;
import cn.vpclub.moses.common.api.events.pay.OrderPaidEvent;
import cn.vpclub.moses.common.constant.OrderConstant;
import cn.vpclub.moses.core.enums.ReturnCodeEnum;
import cn.vpclub.moses.core.model.response.BaseResponse;
import cn.vpclub.moses.utils.common.IdWorker;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author:yangqiao
 * @description:肯德基服务实现
 * @Date:2018/1/11
 */
@Service
@Slf4j
@AllArgsConstructor
public class OrderPaidServiceImpl implements IOrderPaidService {

    private CommandGateway commandGateway;

    private ProductThirdpartyRelatedRpcService productThirdpartyRelatedRpcService;

    private ThirdPartyCouponLogRpcService thirdPartyCouponLogRpcService;

    private IKfcCouponService iKfcCouponService;

    private IXbkHgdsCouponService iXbkhgdsCouponService;

    private ISohuCouponService iSohuCouponService;

    private IMacDonaldCouponService iMacDonaldCouponService;

    private IKinggloryCouponService iKinggloryCouponService;
    private IYouKuCouponService iYouKuCouponService;
    private IIqiyiCouponService iIqiyiCouponService;

    @Autowired
    private CouponDetailRpcService couponDetailRpcService;

    public void handle(OrderPaidEvent event) throws IOException {

        //查询是否属于第三方服务的商品
        ProductThirdpartyRelated request = new ProductThirdpartyRelated();
        request.setSkuId(event.getSubOrderList().get(0).getOrderItemList().get(0).getSkuId());
        BaseResponse baseResponse = productThirdpartyRelatedRpcService.query(request);

        //如果查询结果不为空，则说明是此商品需调用第三方服务
        if (baseResponse != null && ReturnCodeEnum.CODE_1000.getCode().intValue() == baseResponse.getReturnCode().intValue()) {

            //调用第三方
            try {

                this.thirdPartyService(event, (ProductThirdpartyRelated) baseResponse.getDataInfo());
            } catch (Exception e) {
                log.error("调用第三方服务出错", e);
            }

        } else {

            // O2O
            if (OrderConstant.PURCHASETYPE_8.equals(event.getPurchaseType())) {

                this.makeO2OCoupon(event);
            }
            // 卡券-饵块生活目前卡券没有平台发券，故空置
            else if (OrderConstant.PURCHASETYPE_9.equals(event.getPurchaseType())) {

                this.releaseCoupon(event);
            }
        }

    }

    /**
     * yangqiao 20180402 平台发券,券码
     */
    private void releaseCoupon(OrderPaidEvent event) {
        //发送已导入的卡密
        log.info("releaseCoupon: {}", JSONUtils.toJson(event));

        //检查库存
        BaseResponse checkResult = this.checkInventory(event);

        //如果库存足够，则发送执行成功命令
        if (ReturnCodeEnum.CODE_1000.getCode().intValue() == checkResult.getReturnCode().intValue()) {

            ReleaseCouponDetailCommand command = new ReleaseCouponDetailCommand(IdWorker.getId(), event);
            commandGateway.send(command);

            //发送修改订单处理状态命令-成功
            this.sendOrderSuccessCommand(event);
        } else {

            //发送修改订单处理状态命令-失败
            this.sendOrderFailCommand(event);
        }
    }

    /**
     * 检查库存
     */
    private BaseResponse checkInventory(OrderPaidEvent event) {

        //组装查询参数
        CouponMixRequest couponMixRequest = new CouponMixRequest();
        //购买数量
        couponMixRequest.setBuyQty(event.getSubOrderList().get(0).getOrderItemList().get(0).getBuyQty());
        //skuId
        couponMixRequest.setSkuId(event.getSubOrderList().get(0).getOrderItemList().get(0).getSkuId());
        //appId
        couponMixRequest.setAppId(event.getAppId());

        return couponDetailRpcService.findCouponDetailToRelease(couponMixRequest);
    }

    /**
     * yangqiao 20171219 平台发券,o2o券码
     */
    private void makeO2OCoupon(OrderPaidEvent event) {
        //组装平台自己发券的O2OCoupon
        List<O2oCoupon> o2oCouponList = this.buildO2OCoupon(event);
        log.info("makeO2OCoupon: {}", JSONUtils.toJson(o2oCouponList));

        //发送新增O2O卡券命令
        CreateO2OCouponCommand command = new CreateO2OCouponCommand(IdWorker.getId(), o2oCouponList, event);
        commandGateway.send(command);

        //发送修改订单处理状态命令
        this.sendOrderSuccessCommand(event);

    }

    /**
     * yangqiao 发送修改订单处理状态命令-成功
     */
    private void sendOrderSuccessCommand(OrderPaidEvent event) {
        UpdateOrderSuccessCommand updateOrderSuccessCommand = new UpdateOrderSuccessCommand(IdWorker.getId(), event.getOrderId());
        commandGateway.send(updateOrderSuccessCommand);
    }

    /**
     * yangqiao 发送修改订单处理状态命令-失败
     */
    private void sendOrderFailCommand(OrderPaidEvent event) {
        UpdateOrderFailCommand updateOrderFailCommand = new UpdateOrderFailCommand(IdWorker.getId(), event.getOrderId());
        commandGateway.send(updateOrderFailCommand);
    }

    /**
     * yangqiao 20171219 组装平台自己发券的O2OCoupon
     */
    private List<O2oCoupon> buildO2OCoupon(OrderPaidEvent event) {

        //获取购买数量
        Integer buyQty = event.getSubOrderList().get(0).getOrderItemList().get(0).getBuyQty();

        //循环组装卡券信息
        List<O2oCoupon> o2oCouponList = null;
        O2oCoupon o2oCoupon = null;
        if (buyQty != null && buyQty > 0) {

            o2oCouponList = new ArrayList<O2oCoupon>();
            for (int i = 0; i < buyQty; i++) {

                o2oCoupon = new O2oCoupon();

                o2oCoupon.setAppId(event.getAppId());
                o2oCoupon.setCreatedBy(event
                        .getCustomerInfo().getUserId());

                if (o2oCoupon.getCreatedBy() == null) {
                    o2oCoupon.setCreatedBy(Long.parseLong(event.getCustomerInfo().getBuyerPhone()));
                }

                o2oCoupon.setCreatedTime(System.currentTimeMillis());
                o2oCoupon.setUpdatedBy(o2oCoupon.getCreatedBy());
                o2oCoupon.setUpdatedTime(o2oCoupon.getCreatedTime());
                o2oCoupon.setDeleted(1);
                o2oCoupon.setBuyerPhone(event.getCustomerInfo().getBuyerPhone());
                o2oCoupon.setBuyerId(event
                        .getCustomerInfo().getUserId());
                if (o2oCoupon.getBuyerId() == null) {
                    o2oCoupon.setBuyerId(o2oCoupon.getCreatedBy());
                }
                o2oCoupon.setAppId(event.getAppId());
                o2oCoupon.setConsumed(CouponConstant.O2O_COUPON_CONSUMED_N);
                //券码来源-平台
                o2oCoupon.setCouponSource(CouponConstant.SERVICE_PARTY_PLATFORM);
                o2oCoupon.setOrderId(event.getSubOrderList().get(0).getSubOrderId());
                //有效时间开始
                o2oCoupon.setEffectiveDateStart(event.getSubOrderList().get(0).getOrderItemList().get(0).getField11());
                //有效时间结束
                o2oCoupon.setEffectiveDateEnd(event.getSubOrderList().get(0).getOrderItemList().get(0).getField12());

                o2oCoupon.setOrderNo(event.getSubOrderList().get(0).getOrderNo());
                o2oCoupon.setCouponCode(String.valueOf(IdWorker.getId()).substring(9));

                o2oCouponList.add(o2oCoupon);
            }
        }


        return o2oCouponList;
    }

    /**
     * yangqiao 20171219 调用第三方服务
     */
    private void thirdPartyService(OrderPaidEvent event, ProductThirdpartyRelated result) throws Exception {
        log.info("调用第三方服务，thirdPartyService");

        //先根据订单id查询third_party_coupon_log表，判断是否是重发逻辑
        ThirdPartyCouponLog parm = new ThirdPartyCouponLog();
        parm.setMainOrderId(event.getOrderId());
        BaseResponse thirdPartyResponse = thirdPartyCouponLogRpcService.query(parm);

        // 肯德基
        if (CouponConstant.SERVICE_PARTY_KFC == result.getServiceParty()) {

            iKfcCouponService.makeCoupon(event, result, thirdPartyResponse);

        } else if (CouponConstant.SERVICE_PARTY_XBK_HGDS == result.getServiceParty()) {

            iXbkhgdsCouponService.makeCoupon(event, result, thirdPartyResponse);

        } else if (CouponConstant.SERVICE_PARTY_SOHU == result.getServiceParty()) {

            iSohuCouponService.makeCoupon(event, result, thirdPartyResponse);

        } else if (CouponConstant.SERVICE_PARTY_MacDonald == result.getServiceParty()) {

            iMacDonaldCouponService.makeCoupon(event, result, thirdPartyResponse);

        } else if (CouponConstant.SERVICE_PARTY_KINGGLORY == result.getServiceParty()) {

            iKinggloryCouponService.makeCoupon(event, result, thirdPartyResponse);

        } else if (CouponConstant.SERVICE_PARTY_YOUKU == result.getServiceParty()) {

            iYouKuCouponService.makeCoupon(event, result, thirdPartyResponse);

        }else if(CouponConstant.SERVICE_PARTY_IQIYI==result.getServiceParty()){
            iIqiyiCouponService.makeCoupon(event, result, thirdPartyResponse);
        }

    }
}