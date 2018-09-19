package cn.vpclub.coupon.command.service.impl;

import cn.vpclub.coupon.api.commands.o2ocoupon.CreateO2OCouponCommand;
import cn.vpclub.coupon.api.commands.other.ThirdPartyCouponLogCommand;
import cn.vpclub.coupon.api.commands.other.UpdateOrderFailCommand;
import cn.vpclub.coupon.api.commands.other.UpdateOrderSuccessCommand;
import cn.vpclub.coupon.api.constants.CouponConstant;
import cn.vpclub.coupon.api.entity.O2oCoupon;
import cn.vpclub.coupon.api.entity.ProductThirdpartyRelated;
import cn.vpclub.coupon.api.entity.ThirdPartyCouponLog;
import cn.vpclub.coupon.api.thirdparty.sohu.SohuRequest;
import cn.vpclub.coupon.api.thirdparty.sohu.SohuResponse;
import cn.vpclub.coupon.api.thirdparty.sohu.SohuUtil;
import cn.vpclub.coupon.api.utils.EnvPropertiesUtil;
import cn.vpclub.coupon.api.utils.JSONUtils;
import cn.vpclub.coupon.command.service.ISohuCouponService;
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
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;


/**
 * @author:zhangyingdong
 * @description:搜狐服务实现
 * @Date:2018/1/31
 */
@Service
@Slf4j
@AllArgsConstructor
public class SohuCouponServiceImpl implements ISohuCouponService {
    private CommandGateway commandGateway;

    @Override
    public void makeCoupon(OrderPaidEvent event, ProductThirdpartyRelated result, BaseResponse thirdPartyResponse) throws
            IOException {
        //读取第三方配置
        Properties thirdPartyConfig = new Properties();
        InputStream stream = MapCache.class.getResourceAsStream("/thirdPartyConfig.properties");
        thirdPartyConfig.load(stream);
        //组装调用接口参数
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
        SohuRequest sohuRequest = new SohuRequest();
        sohuRequest.setSubmitTimestamp(String.valueOf(System.currentTimeMillis()))//需要时间戳
                .setPid(thirdPartyConfig.getProperty(MessageFormat.format("sohu.pid.{0}", EnvPropertiesUtil.getEnv
                        ("SPRING_PROFILES_ACTIVE"))))
                .setPassword(thirdPartyConfig.getProperty(MessageFormat.format("sohu.password.{0}", EnvPropertiesUtil.getEnv
                        ("SPRING_PROFILES_ACTIVE"))))//请求url中不需传递password参数
                .setCardBatchNo(result.getThirdProductId())
                .setOrderId(String.valueOf(event.getSubOrderList().get(0).getSubOrderId()))//子订单id
                .setCustomerNo(event.getCustomerInfo().getBuyerPhone())
                .setOrderQuantity(Long.valueOf(event.getSubOrderList().get(0).getOrderItemList().get(0).getBuyQty()))
                .setOrderTime(simpleDateFormat.format(System.currentTimeMillis()))//YYYYMMDDHH24MISS
                .setOrderPoints(Long.valueOf(event.getSubOrderList().get(0).getOrderItemList().get(0).getBuyQty()))
                .setServiceUrl(result.getServiceUrl1());
        SohuResponse sohuResponse = null;
        try {
            sohuResponse = SohuUtil.tryOrder(sohuRequest);
            //如果返回状态为成功，则存储sohu券码信息
            if (sohuResponse != null && CouponConstant.SOHU_RETURN_CODE_SUCCESS.equals(sohuResponse.getRetResult())) {
                this.saveSohuCoupon(sohuResponse, event);
                this.sendOrderSuccessCommand(event);
            } else {
                log.info("搜狐制码失败，子订单id: {},SohuResponse：{}", event.getSubOrderList().get(0).getSubOrderId(), JSONUtils.toJson
                        (sohuResponse));
                this.sendOrderFailCommand(event);
            }
        } catch (Exception e) {
            log.error("Exception e:{}", e);
            this.sendOrderFailCommand(event);
        } finally {
            this.sendThirdPartyLogEvent(sohuRequest, sohuResponse, event, thirdPartyResponse);
        }
    }

    private void sendOrderFailCommand(OrderPaidEvent event) {
        UpdateOrderFailCommand updateOrderFailCommand = new UpdateOrderFailCommand(IdWorker.getId(), event.getOrderId());
        commandGateway.send(updateOrderFailCommand);
    }

    private void sendOrderSuccessCommand(OrderPaidEvent event) {
        UpdateOrderSuccessCommand updateOrderSuccessCommand = new UpdateOrderSuccessCommand(IdWorker.getId(), event.getOrderId());
        commandGateway.send(updateOrderSuccessCommand);
    }

    /**
     * 记录券码信息-发送记录O2O券码事件
     */
    private void saveSohuCoupon(SohuResponse sohuResponse, OrderPaidEvent event) {
        //组装写表参数
        O2oCoupon o2oCoupon = null;
        //券码对象集合
        List<O2oCoupon> o2oCouponList = null;
        //获取返回的券码
        if (StringUtils.isNotEmpty(sohuResponse.getExchangeCode())) {
            o2oCouponList = new ArrayList<O2oCoupon>();
            String[] exchangeCodes = sohuResponse.getExchangeCode().split(";");
            for (String code : exchangeCodes) {
                o2oCoupon = new O2oCoupon();
                o2oCoupon.setAppId(event.getAppId());
                o2oCoupon.setCreatedBy(event.getCustomerInfo().getUserId());
                o2oCoupon.setCreatedTime(System.currentTimeMillis());
                o2oCoupon.setUpdatedBy(o2oCoupon.getCreatedBy());
                o2oCoupon.setUpdatedTime(o2oCoupon.getCreatedTime());
                o2oCoupon.setOrderId(event.getSubOrderList().get(0).getSubOrderId());
                o2oCoupon.setOrderNo(event.getSubOrderList().get(0).getOrderNo());
                o2oCoupon.setCouponCode(code.split(",")[0]);//真实的券码
                o2oCoupon.setEffectiveDateStart(event.getSubOrderList().get(0).getOrderItemList().get(0).getField11());
                o2oCoupon.setEffectiveDateEnd(event.getSubOrderList().get(0).getOrderItemList().get(0).getField12());
                o2oCoupon.setBuyerId(event.getCustomerInfo().getUserId());
                o2oCoupon.setBuyerPhone(event.getCustomerInfo().getBuyerPhone());
                o2oCoupon.setCouponSource(CouponConstant.SERVICE_PARTY_SOHU);
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
    private void sendThirdPartyLogEvent(SohuRequest sohuRequest, SohuResponse sohuResponse, OrderPaidEvent event, BaseResponse
            thirdPartyResponse) throws IOException {
        ThirdPartyCouponLogCommand thirdPartyCouponLogCommand = null;
        //查询记录为空，则新增
        if (thirdPartyResponse.getReturnCode().intValue() == ReturnCodeEnum.CODE_1002.getCode()
                .intValue()) {
            thirdPartyCouponLogCommand = this.sendThirdPartyLogAddEvent(sohuRequest, sohuResponse, event);
        }
        //查询记录不为空，则修改
        else if (thirdPartyResponse.getReturnCode().intValue() == ReturnCodeEnum.CODE_1000.getCode()
                .intValue()) {
            thirdPartyCouponLogCommand = this.sendThirdPartyLogUpdateEvent((ThirdPartyCouponLog) thirdPartyResponse.getDataInfo
                    (), sohuRequest, sohuResponse, event);

        }

        //发送第三方日志命令
        if (thirdPartyCouponLogCommand != null) {
            commandGateway.send(thirdPartyCouponLogCommand);
        }

    }

    /**
     * 修改第三方调用日志表
     */
    private ThirdPartyCouponLogCommand sendThirdPartyLogUpdateEvent(ThirdPartyCouponLog thirdPartyCouponLog, SohuRequest
            sohuRequest, SohuResponse sohuResponse, OrderPaidEvent event) throws IOException {
        thirdPartyCouponLog.setUpdatedBy(event.getCustomerInfo().getUserId());
        //服务方
        thirdPartyCouponLog.setServiceParty(CouponConstant.SERVICE_PARTY_SOHU);
        //服务URL
        thirdPartyCouponLog.setServiceUrl(sohuRequest.getServiceUrl());
        //请求参数
        thirdPartyCouponLog.setRequestContext(JSONUtils.toJson(sohuRequest));
        //响应参数
        thirdPartyCouponLog.setResponseContext(sohuResponse != null ? JSONUtils.toJson(sohuResponse) : null);
        //返回标识
        thirdPartyCouponLog.setReturnCode(sohuResponse == null ? CouponConstant.THIRD_PARTY_SERVICE_FLAG_FAILED : CouponConstant
                .SOHU_RETURN_CODE_SUCCESS.equals(sohuResponse.getRetResult()) ?
                CouponConstant.THIRD_PARTY_SERVICE_FLAG_SUCCESS : CouponConstant.THIRD_PARTY_SERVICE_FLAG_FAILED);

        //组装第三方日志命令
        ThirdPartyCouponLogCommand thirdPartyCouponLogCommand = new ThirdPartyCouponLogCommand();

        thirdPartyCouponLogCommand.setId(thirdPartyCouponLog.getId());
        thirdPartyCouponLogCommand.setDoType(CouponConstant.DO_TYPE_UPDATE);
        thirdPartyCouponLogCommand.setThirdPartyCouponLog(thirdPartyCouponLog);

        return thirdPartyCouponLogCommand;
    }


    /**
     * 新增第三方调用日志表
     */
    private ThirdPartyCouponLogCommand sendThirdPartyLogAddEvent(SohuRequest sohuRequest, SohuResponse sohuResponse,
                                                                 OrderPaidEvent event) throws IOException {
        //组装接口调用日志表参数
        ThirdPartyCouponLog thirdPartyCouponLog = new ThirdPartyCouponLog();
        thirdPartyCouponLog.setId(IdWorker.getId());
        thirdPartyCouponLog.setCreatedBy(event.getCustomerInfo().getUserId());
        thirdPartyCouponLog.setUpdatedBy(thirdPartyCouponLog.getCreatedBy());
        thirdPartyCouponLog.setAppId(event.getAppId());
        //返回标识
        thirdPartyCouponLog.setReturnCode(sohuResponse == null ? CouponConstant.THIRD_PARTY_SERVICE_FLAG_FAILED : CouponConstant
                .SOHU_RETURN_CODE_SUCCESS.equals(sohuResponse.getRetResult()) ?
                CouponConstant.THIRD_PARTY_SERVICE_FLAG_SUCCESS : CouponConstant.THIRD_PARTY_SERVICE_FLAG_FAILED);

        //请求参数
        thirdPartyCouponLog.setRequestContext(JSONUtils.toJson(sohuRequest));
        //响应参数
        thirdPartyCouponLog.setResponseContext(sohuResponse != null ? JSONUtils.toJson(sohuResponse) : null);
        //主订单id
        thirdPartyCouponLog.setMainOrderId(event.getOrderId());
        //子订单id
        thirdPartyCouponLog.setSubOrderId(event.getSubOrderList().get(0)
                .getSubOrderId());
        //服务方
        thirdPartyCouponLog.setServiceParty(CouponConstant.SERVICE_PARTY_SOHU);
        //服务url
        thirdPartyCouponLog.setServiceUrl(sohuRequest.getServiceUrl());

        //组装第三方日志命令
        ThirdPartyCouponLogCommand thirdPartyCouponLogCommand = new ThirdPartyCouponLogCommand();
        thirdPartyCouponLogCommand.setId(thirdPartyCouponLog.getId());
        thirdPartyCouponLogCommand.setDoType(CouponConstant.DO_TYPE_INSERT);
        thirdPartyCouponLogCommand.setThirdPartyCouponLog(thirdPartyCouponLog);
        return thirdPartyCouponLogCommand;
    }


}
