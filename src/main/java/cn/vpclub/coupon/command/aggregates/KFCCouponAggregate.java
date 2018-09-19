package cn.vpclub.coupon.command.aggregates;

import cn.vpclub.coupon.api.commands.o2ocoupon.CreateKFCO2OCouponCommand;
import cn.vpclub.coupon.api.constants.CouponConstant;
import cn.vpclub.coupon.api.entity.O2oCoupon;
import cn.vpclub.coupon.api.entity.ProductThirdpartyRelated;
import cn.vpclub.coupon.api.entity.ThirdPartyCouponLog;
import cn.vpclub.coupon.api.events.o2ocoupon.O2OCouponCreatedEvent;
import cn.vpclub.coupon.api.events.other.ThirdPartyCouponLogEvent;
import cn.vpclub.coupon.api.thirdparty.kfc.KFCParms;
import cn.vpclub.coupon.api.thirdparty.kfc.KFCResponse;
import cn.vpclub.coupon.api.thirdparty.kfc.KFCWeiNengReqUtil;
import cn.vpclub.coupon.api.utils.JSONUtils;
import cn.vpclub.coupon.api.utils.PropertyFileUtils;
import cn.vpclub.coupon.command.rpc.ThirdPartyCouponLogRpcService;
import cn.vpclub.moses.common.api.events.pay.OrderPaidEvent;
import cn.vpclub.moses.core.entity.BaseEntity;
import cn.vpclub.moses.core.enums.ReturnCodeEnum;
import cn.vpclub.moses.core.model.response.BaseResponse;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.axonframework.commandhandling.model.AggregateIdentifier;
import org.axonframework.spring.stereotype.Aggregate;
import org.springframework.beans.BeanUtils;
import org.springframework.web.context.ContextLoader;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.axonframework.commandhandling.model.AggregateLifecycle.apply;

/**
 * 已废弃
 * Created by yangqiao on 2017/12/20.
 * 肯德基发送卡券聚合
 */
@NoArgsConstructor
@ToString
@Slf4j
@Aggregate(repository = "kFCCouponAggregateRepository")
public class KFCCouponAggregate extends BaseEntity {

    @AggregateIdentifier
    private Long id;

    public KFCCouponAggregate(Long id, CreateKFCO2OCouponCommand createKFCO2OCouponCommand) throws IOException {
        this.id = id;
        //读取第三方配置
        Properties thirdPartyConfig = PropertyFileUtils.getProperties("thirdPartyConfig.properties");

        //本系统商品与第三方商品关联关系
        ProductThirdpartyRelated productThirdpartyRelated = createKFCO2OCouponCommand.getProductThirdpartyRelated();

        //组装调用接口参数
        KFCParms kfcParms = new KFCParms();
        kfcParms.setOrderId(createKFCO2OCouponCommand.getOrderPaidEvent().getSubOrderList().get(0).getSubOrderId().toString());
        kfcParms.setCardBatchNo(productThirdpartyRelated.getThirdProductId());
        kfcParms.setCustomerNo(createKFCO2OCouponCommand
                .getOrderPaidEvent().getSubOrderList().get(0).getBuyerId().toString());
        kfcParms.setOrderQuantity(Long.valueOf(createKFCO2OCouponCommand.getOrderPaidEvent().getSubOrderList().get(0)
                .getOrderItemList().get(0).getBuyQty().toString()));
        kfcParms.setServiceUrl(productThirdpartyRelated.getServiceUrl1());
        kfcParms.setPid(thirdPartyConfig.getProperty("kfc.pid"));
        kfcParms.setPassword(thirdPartyConfig.getProperty("kfc.password"));
        KFCResponse kfcResponse = null;

        try {

            kfcResponse = KFCWeiNengReqUtil.tryOrder(kfcParms);

            //如果返回状态为成功，则存储KFC券码信息
            if (kfcResponse != null && CouponConstant.KFC_RETURN_CODE_SUCCESS.equals(kfcResponse.getRetResult())) {
                this.saveKfcCoupon(kfcResponse, createKFCO2OCouponCommand);
            }

        } catch (Exception e) {
            log.error("Exception e:{}", e);
        } finally {
            this.sendThirdPartyLogEvent(kfcParms, kfcResponse, createKFCO2OCouponCommand);
        }
    }

    /**
     * 记录kfc券码信息-发送记录O2O券码事件
     */
    private void saveKfcCoupon(KFCResponse kfcResponse, CreateKFCO2OCouponCommand comand) {
        //组装写表参数
        O2oCoupon o2oCoupon = null;
        //订单支付事件
        OrderPaidEvent orderPaidEvent = comand.getOrderPaidEvent();
        //券码对象集合
        List<O2oCoupon> o2oCouponList = null;
        //获取肯德基返回的券码
        if (StringUtils.isNotEmpty(kfcResponse.getExchangeCode())) {
            o2oCouponList = new ArrayList<O2oCoupon>();
            //如生成的码数量为多个，则以“;”分隔反馈多个，“，”分隔码和金额，格式为：1111111111,5000;2222222222,20表示返回一个50元码，一个20元码
            //获取返回券码数组
            String[] exchangeCodeArr = kfcResponse.getExchangeCode().split(";");
            for (String code : exchangeCodeArr) {

                o2oCoupon = new O2oCoupon();

                o2oCoupon.setAppId(orderPaidEvent.getAppId());
                o2oCoupon.setCreatedBy(orderPaidEvent.getCustomerInfo().getUserId());
                o2oCoupon.setUpdatedBy(o2oCoupon.getCreatedBy());
                o2oCoupon.setOrderId(orderPaidEvent.getSubOrderList().get(0).getSubOrderId());
                o2oCoupon.setOrderNo(orderPaidEvent.getSubOrderList().get(0).getOrderNo());
                //真实券码
                o2oCoupon.setCouponCode(code.split(",")[0]);
                //有效时间开始
                o2oCoupon.setEffectiveDateStart(orderPaidEvent.getSubOrderList().get(0).getOrderItemList().get(0).getField11());
                //有效时间结束
                o2oCoupon.setEffectiveDateEnd(orderPaidEvent.getSubOrderList().get(0).getOrderItemList().get(0).getField12());

                o2oCoupon.setBuyerId(orderPaidEvent.getCustomerInfo().getUserId());
                o2oCoupon.setBuyerPhone(orderPaidEvent.getCustomerInfo().getBuyerPhone());
                //卡券来源
                o2oCoupon.setCouponSource(CouponConstant.SERVICE_PARTY_KFC);

                o2oCouponList.add(o2oCoupon);
            }
        }

        //o2oCouponList = this.buildTestData();

        //发送事件
        if (CollectionUtils.isNotEmpty(o2oCouponList)) {
            apply(new O2OCouponCreatedEvent(o2oCouponList, orderPaidEvent));
        }

    }

    private List<O2oCoupon> buildTestData() {
        //券码对象集合
        List<O2oCoupon> o2oCouponList = new ArrayList<O2oCoupon>();
        O2oCoupon o1 = new O2oCoupon();

        o1.setAppId(10000186L);
        o1.setCreatedBy(123L);
        o1.setUpdatedBy(123L);
        o1.setOrderId(321L);
        o1.setOrderNo("orderNo");
        o1.setCouponSource(CouponConstant.SERVICE_PARTY_KFC);
        o1.setCouponCode("111 222");
        o1.setEffectiveDateStart(1111L);
        o1.setEffectiveDateEnd(2222L);
        o1.setBuyerId(9999L);
        o1.setBuyerPhone("1351");

        O2oCoupon o2 = new O2oCoupon();

        BeanUtils.copyProperties(o1, o2);

        o2oCouponList.add(o1);
        o2oCouponList.add(o2);
        return o2oCouponList;
    }


    /**
     * 记录第三方调用日志表
     */
    private void sendThirdPartyLogEvent(KFCParms kfcParms, KFCResponse kfcResponse, CreateKFCO2OCouponCommand
            createKFCO2OCouponCommand) {
        WebApplicationContext wac = ContextLoader.getCurrentWebApplicationContext();
        ThirdPartyCouponLogRpcService thirdPartyCouponLogRpcService = (ThirdPartyCouponLogRpcService) wac.getBean
                ("thirdPartyCouponLogRpcService");

        //先根据订单id查询third_party_coupon_log表，判断是否是重发逻辑
        ThirdPartyCouponLog parm = new ThirdPartyCouponLog();
        parm.setMainOrderId(createKFCO2OCouponCommand.getOrderPaidEvent().getOrderId());
        BaseResponse thirdPartyResponse = thirdPartyCouponLogRpcService.query(parm);

        //查询记录为空，则新增
        if (thirdPartyResponse.getReturnCode().intValue() == ReturnCodeEnum.CODE_1002.getCode()
                .intValue()) {

            this.sendThirdPartyLogAddEvent(kfcParms, kfcResponse,
                    createKFCO2OCouponCommand);
        }
        //查询记录不为空，则修改
        else if (thirdPartyResponse.getReturnCode().intValue() == ReturnCodeEnum.CODE_1000.getCode()
                .intValue()) {
            this.sendThirdPartyLogUpdateEvent((ThirdPartyCouponLog) thirdPartyResponse.getDataInfo(), kfcParms, kfcResponse,
                    createKFCO2OCouponCommand);

        }

    }

    /**
     * 新增第三方调用日志表
     */
    private void sendThirdPartyLogAddEvent(KFCParms kfcParms, KFCResponse kfcResponse, CreateKFCO2OCouponCommand
            createKFCO2OCouponCommand) {

        //组装接口调用日志表参数
        ThirdPartyCouponLog thirdPartyCouponLog = new ThirdPartyCouponLog();
        thirdPartyCouponLog.setCreatedBy(createKFCO2OCouponCommand.getOrderPaidEvent().getCustomerInfo().getUserId());
        thirdPartyCouponLog.setUpdatedBy(thirdPartyCouponLog.getCreatedBy());
        thirdPartyCouponLog.setAppId(createKFCO2OCouponCommand.getOrderPaidEvent().getAppId());

        //返回标识
        thirdPartyCouponLog.setReturnCode(kfcResponse == null ? CouponConstant.THIRD_PARTY_SERVICE_FLAG_FAILED : CouponConstant
                .KFC_RETURN_CODE_SUCCESS.equals(kfcResponse.getRetResult()) ?
                CouponConstant.THIRD_PARTY_SERVICE_FLAG_SUCCESS : CouponConstant.THIRD_PARTY_SERVICE_FLAG_FAILED);

        //请求参数
        thirdPartyCouponLog.setRequestContext(JSONUtils.toJson(kfcParms));
        //响应参数
        thirdPartyCouponLog.setResponseContext(kfcResponse != null ? JSONUtils.toJson(kfcResponse) : null);
        //主订单id
        thirdPartyCouponLog.setMainOrderId(createKFCO2OCouponCommand.getOrderPaidEvent().getOrderId());
        //子订单id
        thirdPartyCouponLog.setSubOrderId(createKFCO2OCouponCommand.getOrderPaidEvent().getSubOrderList().get(0)
                .getSubOrderId());
        //服务方
        thirdPartyCouponLog.setServiceParty(CouponConstant.SERVICE_PARTY_KFC);
        //服务url
        thirdPartyCouponLog.setServiceUrl(kfcParms.getServiceUrl());

        //发送记录第三方服务调用日志事件
        apply(new ThirdPartyCouponLogEvent(thirdPartyCouponLog, CouponConstant.DO_TYPE_INSERT));
    }


    /**
     * 修改第三方调用日志表
     */
    private void sendThirdPartyLogUpdateEvent(ThirdPartyCouponLog thirdPartyCouponLog, KFCParms kfcParms, KFCResponse kfcResponse,
                                              CreateKFCO2OCouponCommand createKFCO2OCouponCommand) {

        thirdPartyCouponLog.setUpdatedBy(createKFCO2OCouponCommand.getOrderPaidEvent().getCustomerInfo().getUserId());
        //服务方
        thirdPartyCouponLog.setServiceParty(CouponConstant.SERVICE_PARTY_KFC);
        //服务URL
        thirdPartyCouponLog.setServiceUrl(kfcParms.getServiceUrl());
        //请求参数
        thirdPartyCouponLog.setRequestContext(JSONUtils.toJson(kfcParms));
        //响应参数
        thirdPartyCouponLog.setResponseContext(kfcResponse != null ? JSONUtils.toJson(kfcResponse) : null);
        //返回标识
        thirdPartyCouponLog.setReturnCode(kfcResponse == null ? CouponConstant.THIRD_PARTY_SERVICE_FLAG_FAILED : CouponConstant
                .KFC_RETURN_CODE_SUCCESS.equals(kfcResponse.getRetResult()) ?
                CouponConstant.THIRD_PARTY_SERVICE_FLAG_SUCCESS : CouponConstant.THIRD_PARTY_SERVICE_FLAG_FAILED);

        //发送修改第三方服务调用日志事件
        apply(new ThirdPartyCouponLogEvent(thirdPartyCouponLog, CouponConstant.DO_TYPE_UPDATE));
    }

}