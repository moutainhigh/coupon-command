package cn.vpclub.coupon.command.service.impl;

import cn.vpclub.coupon.api.commands.o2ocoupon.CreateO2OCouponCommand;
import cn.vpclub.coupon.api.commands.other.ThirdPartyCouponLogCommand;
import cn.vpclub.coupon.api.constants.CouponConstant;
import cn.vpclub.coupon.api.entity.O2oCoupon;
import cn.vpclub.coupon.api.entity.ProductThirdpartyRelated;
import cn.vpclub.coupon.api.entity.ThirdPartyCouponLog;
import cn.vpclub.coupon.api.thirdparty.kfc.KFCParms;
import cn.vpclub.coupon.api.thirdparty.kfc.KFCResponse;
import cn.vpclub.coupon.api.thirdparty.kfc.KFCWeiNengReqUtil;
import cn.vpclub.coupon.api.utils.JSONUtils;
import cn.vpclub.coupon.command.service.IKfcCouponService;
import cn.vpclub.moses.common.api.events.pay.OrderPaidEvent;
import cn.vpclub.moses.core.enums.ReturnCodeEnum;
import cn.vpclub.moses.core.model.response.BaseResponse;
import cn.vpclub.moses.utils.common.IdWorker;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.cache.MapCache;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * @author:yangqiao
 * @description:肯德基服务实现
 * @Date:2018/1/11
 */
@Service
@Slf4j
@AllArgsConstructor
public class KfcCouponServiceImpl implements IKfcCouponService {

    private CommandGateway commandGateway;

    /**
     * 发送肯德基接口，获取新卡券
     *
     * @param event,result
     * @return
     */
    public void makeCoupon(OrderPaidEvent event, ProductThirdpartyRelated result, BaseResponse thirdPartyResponse) throws
            IOException {
        //读取第三方配置
        Properties thirdPartyConfig = new Properties();
        InputStream stream = MapCache.class.getResourceAsStream("/thirdPartyConfig.properties");
        thirdPartyConfig.load(stream);

        //组装调用接口参数
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
        KFCParms kfcParms = new KFCParms();
        kfcParms.setOrderId(event.getOrderId().toString())
                .setSubmitTimestamp(String.valueOf(System.currentTimeMillis()));
        kfcParms.setCardBatchNo(result.getThirdProductId());
        kfcParms.setCustomerNo(event.getCustomerInfo().getBuyerPhone());
        kfcParms.setOrderQuantity(Long.valueOf(event.getSubOrderList().get(0)
                .getOrderItemList().get(0).getBuyQty()))
                .setOrderTime(simpleDateFormat.format(System.currentTimeMillis()))
                .setOrderPoints(50L);
        kfcParms.setServiceUrl(result.getServiceUrl1());
        kfcParms.setPid(thirdPartyConfig.getProperty("kfc.pid"));
        kfcParms.setPassword(thirdPartyConfig.getProperty("kfc.password"));
        KFCResponse kfcResponse = null;

        try {

            kfcResponse = KFCWeiNengReqUtil.tryOrder(kfcParms);

            //如果返回状态为成功，则存储KFC券码信息
            if (kfcResponse != null && CouponConstant.KFC_RETURN_CODE_SUCCESS.equals(kfcResponse.getRetResult())) {
                this.saveKfcCoupon(kfcResponse, event);
            }

        } catch (Exception e) {
            log.error("Exception e:{}", e);
        } finally {
            this.sendThirdPartyLogEvent(kfcParms, kfcResponse, event, thirdPartyResponse);
        }
    }

    /**
     * 记录kfc券码信息-发送记录O2O券码事件
     */
    private void saveKfcCoupon(KFCResponse kfcResponse, OrderPaidEvent event) {
        //组装写表参数
        O2oCoupon o2oCoupon = null;
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

                o2oCoupon.setAppId(event.getAppId());
                o2oCoupon.setCreatedBy(event.getCustomerInfo().getUserId());
                o2oCoupon.setCreatedTime(System.currentTimeMillis());
                o2oCoupon.setUpdatedBy(o2oCoupon.getCreatedBy());
                o2oCoupon.setUpdatedTime(o2oCoupon.getCreatedTime());
                o2oCoupon.setOrderId(event.getSubOrderList().get(0).getSubOrderId());
                o2oCoupon.setOrderNo(event.getSubOrderList().get(0).getOrderNo());
                //真实券码
                o2oCoupon.setCouponCode(code.split(",")[0]);
                //有效时间开始
                o2oCoupon.setEffectiveDateStart(event.getSubOrderList().get(0).getOrderItemList().get(0).getField11());
                //有效时间结束
                o2oCoupon.setEffectiveDateEnd(event.getSubOrderList().get(0).getOrderItemList().get(0).getField12());

                o2oCoupon.setBuyerId(event.getCustomerInfo().getUserId());
                o2oCoupon.setBuyerPhone(event.getCustomerInfo().getBuyerPhone());
                //卡券来源
                o2oCoupon.setCouponSource(CouponConstant.SERVICE_PARTY_KFC);

                o2oCouponList.add(o2oCoupon);
            }
        }

        //发送创建卡券命令
        if (CollectionUtils.isNotEmpty(o2oCouponList)) {
            CreateO2OCouponCommand createO2OCouponCommand = new CreateO2OCouponCommand(IdWorker.getId(), o2oCouponList, event);
            commandGateway.send(createO2OCouponCommand);
        }

    }

    /**
     * 记录第三方调用日志表
     */
    private void sendThirdPartyLogEvent(KFCParms kfcParms, KFCResponse kfcResponse, OrderPaidEvent event, BaseResponse
            thirdPartyResponse) throws IOException {

        ThirdPartyCouponLogCommand thirdPartyCouponLogCommand = null;

        //查询记录为空，则新增
        if (thirdPartyResponse.getReturnCode().intValue() == ReturnCodeEnum.CODE_1002.getCode()
                .intValue()) {
            thirdPartyCouponLogCommand = this.sendThirdPartyLogAddEvent(kfcParms, kfcResponse, event);
        }
        //查询记录不为空，则修改
        else if (thirdPartyResponse.getReturnCode().intValue() == ReturnCodeEnum.CODE_1000.getCode()
                .intValue()) {
            thirdPartyCouponLogCommand = this.sendThirdPartyLogUpdateEvent((ThirdPartyCouponLog) thirdPartyResponse.getDataInfo
                    (), kfcParms, kfcResponse, event);
        }

        //发送第三方日志命令
        if (thirdPartyCouponLogCommand != null) {
            commandGateway.send(thirdPartyCouponLogCommand);
        }
    }

    /**
     * 新增第三方调用日志表
     */
    private ThirdPartyCouponLogCommand sendThirdPartyLogAddEvent(KFCParms kfcParms, KFCResponse kfcResponse, OrderPaidEvent
            event) throws IOException {
        //组装接口调用日志表参数
        ThirdPartyCouponLog thirdPartyCouponLog = new ThirdPartyCouponLog();
        thirdPartyCouponLog.setId(IdWorker.getId());
        thirdPartyCouponLog.setCreatedBy(event.getCustomerInfo().getUserId());
        thirdPartyCouponLog.setUpdatedBy(thirdPartyCouponLog.getCreatedBy());
        thirdPartyCouponLog.setAppId(event.getAppId());

        //返回标识
        thirdPartyCouponLog.setReturnCode(kfcResponse == null ? CouponConstant.THIRD_PARTY_SERVICE_FLAG_FAILED : (CouponConstant
                .KFC_RETURN_CODE_SUCCESS.equals(kfcResponse.getRetResult()) ?
                CouponConstant.THIRD_PARTY_SERVICE_FLAG_SUCCESS : CouponConstant.THIRD_PARTY_SERVICE_FLAG_FAILED));

        //请求参数
        thirdPartyCouponLog.setRequestContext(JSONUtils.toJson(kfcParms));
        //响应参数
        thirdPartyCouponLog.setResponseContext(kfcResponse != null ? JSONUtils.toJson(kfcResponse) : null);
        //主订单id
        thirdPartyCouponLog.setMainOrderId(event.getOrderId());
        //子订单id
        thirdPartyCouponLog.setSubOrderId(event.getSubOrderList().get(0)
                .getSubOrderId());
        //服务方
        thirdPartyCouponLog.setServiceParty(CouponConstant.SERVICE_PARTY_KFC);
        //服务url
        thirdPartyCouponLog.setServiceUrl(kfcParms.getServiceUrl());

        //发送记录第三方服务调用日志事件
        //apply(new ThirdPartyCouponLogEvent(thirdPartyCouponLog, CouponConstant.DO_TYPE_INSERT));

        //组装第三方日志命令
        ThirdPartyCouponLogCommand thirdPartyCouponLogCommand = new ThirdPartyCouponLogCommand();

        thirdPartyCouponLogCommand.setId(thirdPartyCouponLog.getId());
        thirdPartyCouponLogCommand.setDoType(CouponConstant.DO_TYPE_INSERT);
        thirdPartyCouponLogCommand.setThirdPartyCouponLog(thirdPartyCouponLog);

        return thirdPartyCouponLogCommand;
    }


    /**
     * 修改第三方调用日志表
     */
    private ThirdPartyCouponLogCommand sendThirdPartyLogUpdateEvent(ThirdPartyCouponLog thirdPartyCouponLog, KFCParms kfcParms,
                                                                    KFCResponse kfcResponse,
                                                                    OrderPaidEvent event) {

        thirdPartyCouponLog.setUpdatedBy(event.getCustomerInfo().getUserId());
        //服务方
        thirdPartyCouponLog.setServiceParty(CouponConstant.SERVICE_PARTY_KFC);
        //服务URL
        thirdPartyCouponLog.setServiceUrl(kfcParms.getServiceUrl());
        //请求参数
        thirdPartyCouponLog.setRequestContext(JSONUtils.toJson(kfcParms));
        //响应参数
        thirdPartyCouponLog.setResponseContext(kfcResponse != null ? JSONUtils.toJson(kfcResponse) : null);
        //返回标识
        thirdPartyCouponLog.setReturnCode(kfcResponse == null ? CouponConstant.THIRD_PARTY_SERVICE_FLAG_FAILED : (CouponConstant
                .KFC_RETURN_CODE_SUCCESS.equals(kfcResponse.getRetResult()) ?
                CouponConstant.THIRD_PARTY_SERVICE_FLAG_SUCCESS : CouponConstant.THIRD_PARTY_SERVICE_FLAG_FAILED));

        //组装第三方日志命令
        ThirdPartyCouponLogCommand thirdPartyCouponLogCommand = new ThirdPartyCouponLogCommand();

        thirdPartyCouponLogCommand.setId(thirdPartyCouponLog.getId());
        thirdPartyCouponLogCommand.setDoType(CouponConstant.DO_TYPE_UPDATE);
        thirdPartyCouponLogCommand.setThirdPartyCouponLog(thirdPartyCouponLog);

        return thirdPartyCouponLogCommand;
    }
}