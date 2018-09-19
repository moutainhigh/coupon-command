package cn.vpclub.coupon.command.service.impl;

import cn.vpclub.coupon.api.commands.o2ocoupon.CreateO2OCouponCommand;
import cn.vpclub.coupon.api.commands.o2ocoupon.CreateYoukuO2OCouponCommand;
import cn.vpclub.coupon.api.commands.other.ThirdPartyCouponLogCommand;
import cn.vpclub.coupon.api.commands.other.UpdateOrderFailCommand;
import cn.vpclub.coupon.api.commands.other.UpdateOrderSuccessCommand;
import cn.vpclub.coupon.api.constants.CouponConstant;
import cn.vpclub.coupon.api.entity.O2oCoupon;
import cn.vpclub.coupon.api.entity.ProductThirdpartyRelated;
import cn.vpclub.coupon.api.entity.ThirdPartyCouponLog;
import cn.vpclub.coupon.api.thirdparty.youku.YouKuRequest;
import cn.vpclub.coupon.api.thirdparty.youku.YouKuResponse;
import cn.vpclub.coupon.api.utils.EnvPropertiesUtil;
import cn.vpclub.coupon.api.utils.JSONUtils;
import cn.vpclub.coupon.command.config.YouKuConfig;
import cn.vpclub.coupon.command.service.IYouKuCouponService;
import cn.vpclub.moses.common.api.events.pay.OrderPaidEvent;
import cn.vpclub.moses.core.enums.ReturnCodeEnum;
import cn.vpclub.moses.core.model.response.BaseResponse;
import cn.vpclub.moses.utils.common.IdWorker;
import cn.vpclub.moses.utils.web.HttpRequestUtil;
import com.alibaba.fastjson.JSON;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.cache.MapCache;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import static cn.vpclub.coupon.command.util.YouKuUtil.doGenerateSign;
import static cn.vpclub.coupon.command.util.YouKuUtil.getURL;
import static cn.vpclub.coupon.command.util.YouKuUtil.xml2Json;

/**
 * Created by zhangyingdong on 2018/6/26.
 */
@Service
@Slf4j
@AllArgsConstructor
public class YouKuCouponServiceImpl implements IYouKuCouponService {

    @Autowired
    YouKuConfig youKuConfig;

    private CommandGateway commandGateway;

    @Override
    // 请求第三方接口，获取新卡券
    public void makeCoupon(OrderPaidEvent event, ProductThirdpartyRelated result, BaseResponse thirdPartyResponse) throws IOException {
        //读取第三方配置
        Properties thirdPartyConfig = new Properties();
        InputStream stream = MapCache.class.getResourceAsStream("/thirdPartyConfig.properties");
        thirdPartyConfig.load(stream);
        LinkedHashMap<String, String> params = new LinkedHashMap<String, String>();
        SimpleDateFormat dayformater = new SimpleDateFormat("yyyyMMdd");
        SimpleDateFormat timeformater = new SimpleDateFormat("HHmmss");
        //组装参数
        params.put("FUNCODE", thirdPartyConfig.getProperty(MessageFormat.format("Youku.FUNCODE.{0}",
                EnvPropertiesUtil.getEnv("SPRING_PROFILES_ACTIVE"))));
        params.put("MID", String.valueOf(event.getTransactionNo()));
        params.put("MERID", thirdPartyConfig.getProperty(MessageFormat.format("Youku.MER_ID.{0}",
                EnvPropertiesUtil.getEnv("SPRING_PROFILES_ACTIVE"))));
        params.put("REQDATE", dayformater.format(new Date()));
        params.put("REQTIME", timeformater.format(new Date()));
        params.put("ORDERID", String.valueOf(event.getSubOrderList().get(0).getSubOrderId()));

        params.put("ORDERDATE", dayformater.format(new Date()));

        params.put("COUPID", result.getThirdProductId());
        params.put("COUNT", String.valueOf(event.getSubOrderList().get(0).getOrderItemList().get(0).getBuyQty()));
        params.put("CHANNEL", thirdPartyConfig.getProperty(MessageFormat.format("Youku.CHANNEL_ID.{0}",
                EnvPropertiesUtil.getEnv("SPRING_PROFILES_ACTIVE"))));//03
        params.put("MEDIANO", event.getCustomerInfo().getBuyerPhone());
        params.put("MEDIATYPE", "00");

        String sign = "";
        String keyFilePath = youKuConfig.getKeyFilepath();
        sign = doGenerateSign(getURL(params),keyFilePath);
        params.put("SIGN", sign);
        log.info("sign :" + sign);
        //map转为对象
        String youKuJson= JSON.toJSONString(params);
        YouKuRequest youKuRequest = JSON.parseObject(youKuJson,YouKuRequest.class);
        //拼接请求Body
        StringBuffer body = new StringBuffer();
        body.append("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>");
        body.append("<MESSAGE>");
        body.append("<FUNCODE>" + params.get("FUNCODE") + "</FUNCODE>");
        body.append("<MID>" + params.get("MID") + "</MID>");
        body.append("<MERID>" + params.get("MERID") + "</MERID>");
        body.append("<REQDATE>" + params.get("REQDATE") + "</REQDATE>");
        body.append("<REQTIME>" + params.get("REQTIME") + "</REQTIME>");
        body.append("<ORDERID>" + params.get("ORDERID") + "</ORDERID>");
        body.append("<ORDERDATE>" + params.get("ORDERDATE") + "</ORDERDATE>");
        body.append("<COUPID>" + params.get("COUPID") + "</COUPID>");
        body.append("<COUNT>" + params.get("COUNT") + "</COUNT>");
        body.append("<CHANNEL>" + params.get("CHANNEL") + "</CHANNEL>");
        body.append("<MEDIANO>" + params.get("MEDIANO") + "</MEDIANO>");
        body.append("<MEDIATYPE>" + params.get("MEDIATYPE") + "</MEDIATYPE>");
        body.append("<SIGN>" + params.get("SIGN") + "</SIGN>");
        body.append("</MESSAGE>");
        log.info("请求报文 body："+body.toString());
        YouKuResponse youKuResponse = null;
        try {
            //给第三方发送请求
            String url = thirdPartyConfig.getProperty(MessageFormat.format("Youku.BASE_URL.{0}",
                    EnvPropertiesUtil.getEnv("SPRING_PROFILES_ACTIVE")));
            String response = HttpRequestUtil.sendJsonPost(url, body.toString());
            log.info("返回报文 response："+response);
            //xml转json字符串
            String json= String.valueOf(xml2Json(response));
            //将json字符串转化为对象
            youKuResponse = JSON.parseObject(json,YouKuResponse.class);
            log.info("返回的信息:" + youKuResponse.toString());
            if (youKuResponse != null && CouponConstant.YOUKU_RETURN_CODE_SUCCESS.equals(youKuResponse.getRETCODE())) {

                this.saveSohuCoupon(youKuResponse, event);
                this.sendOrderSuccessCommand(event);
            } else {
                log.info("优酷制码失败,子订单id: {},youKuResponse: {}", event.getSubOrderList().get(0).getSubOrderId(), JSONUtils
                        .toJson(youKuResponse));

                this.sendOrderFailCommand(event);
            }

        } catch (Exception e) {
            log.error("Exception e:{}", e);
            this.sendOrderFailCommand(event);
        } finally {
            this.sendThirdPartyLogEvent(youKuRequest, youKuResponse, event, thirdPartyResponse, result);
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
    private void saveSohuCoupon(YouKuResponse youKuResponse, OrderPaidEvent event) {
        //组装写表参数
        O2oCoupon o2oCoupon = null;
        //获取返回的券码,因为优酷是直充的，不会返回券码，这个地方不需要判断券码号是不是为空

                o2oCoupon = new O2oCoupon();
                o2oCoupon.setAppId(event.getAppId());
                o2oCoupon.setCreatedBy(event.getCustomerInfo().getUserId());
                o2oCoupon.setCreatedTime(System.currentTimeMillis());
                o2oCoupon.setUpdatedBy(o2oCoupon.getCreatedBy());
                o2oCoupon.setUpdatedTime(o2oCoupon.getCreatedTime());
                o2oCoupon.setDeleted(1);
                o2oCoupon.setOrderId(event.getSubOrderList().get(0).getSubOrderId());
                o2oCoupon.setOrderNo(event.getSubOrderList().get(0).getOrderNo());
                //真实券码
                o2oCoupon.setCouponCode("优酷直充手机号，没券码");
                //有效时间开始
                o2oCoupon.setEffectiveDateStart(Long.valueOf(youKuResponse.getEFFECTDATE()));
                //有效时间结束
                o2oCoupon.setEffectiveDateEnd(Long.valueOf(youKuResponse.getEXPIREDATE()));
                o2oCoupon.setBuyerId(event.getCustomerInfo().getUserId());
                o2oCoupon.setBuyerPhone(event.getCustomerInfo().getBuyerPhone());
                //卡券来源
                o2oCoupon.setCouponSource(CouponConstant.SERVICE_PARTY_YOUKU);
                //是否已使用
                o2oCoupon.setConsumed(CouponConstant.O2O_COUPON_CONSUMED_N);



        //发送创建卡券命令
        if(o2oCoupon!=null){
            CreateYoukuO2OCouponCommand createYoukuO2OCouponCommand = new CreateYoukuO2OCouponCommand(IdWorker.getId(), o2oCoupon, event);
            commandGateway.send(createYoukuO2OCouponCommand);
        }
    }


    /**
     * 记录第三方调用日志表
     */
    private void sendThirdPartyLogEvent(YouKuRequest youKuRequest, YouKuResponse youKuResponse, OrderPaidEvent event, BaseResponse thirdPartyResponse,
    ProductThirdpartyRelated result) {
        ThirdPartyCouponLogCommand thirdPartyCouponLogCommand = null;
        //查询记录为空，则新增
        if (thirdPartyResponse.getReturnCode().intValue() == ReturnCodeEnum.CODE_1002.getCode()
                .intValue()) {
            thirdPartyCouponLogCommand = this.sendThirdPartyLogAddEvent(youKuRequest, youKuResponse, event,result);
        }
        //查询记录不为空，则修改
        else if (thirdPartyResponse.getReturnCode().intValue() == ReturnCodeEnum.CODE_1000.getCode()
                .intValue()) {
            thirdPartyCouponLogCommand = this.sendThirdPartyLogUpdateEvent((ThirdPartyCouponLog) thirdPartyResponse.getDataInfo
                    (), youKuRequest, youKuResponse, event,result);

        }
        //发送第三方日志命令
        if (thirdPartyCouponLogCommand != null) {
            commandGateway.send(thirdPartyCouponLogCommand);
        }
}


    /**
     * 新增第三方调用日志表
     */
    private ThirdPartyCouponLogCommand sendThirdPartyLogAddEvent(YouKuRequest youKuRequest, YouKuResponse youKuResponse, OrderPaidEvent event,
    ProductThirdpartyRelated result) {
        //组装接口调用日志表参数
        ThirdPartyCouponLog thirdPartyCouponLog = new ThirdPartyCouponLog();
        thirdPartyCouponLog.setId(IdWorker.getId());
        thirdPartyCouponLog.setCreatedBy(event.getCustomerInfo().getUserId());
        thirdPartyCouponLog.setUpdatedBy(thirdPartyCouponLog.getCreatedBy());
        thirdPartyCouponLog.setAppId(event.getAppId());
        //返回标识
        thirdPartyCouponLog.setReturnCode(youKuResponse == null ? CouponConstant.THIRD_PARTY_SERVICE_FLAG_FAILED : CouponConstant
                .YOUKU_RETURN_CODE_SUCCESS.equals(youKuResponse.getRETCODE()) ?
                CouponConstant.THIRD_PARTY_SERVICE_FLAG_SUCCESS : CouponConstant.THIRD_PARTY_SERVICE_FLAG_FAILED);

        //请求参数
        thirdPartyCouponLog.setRequestContext(JSONUtils.toJson(youKuRequest));
        //响应参数
        thirdPartyCouponLog.setResponseContext(youKuResponse != null ? JSONUtils.toJson(youKuResponse) : null);
        //主订单id
        thirdPartyCouponLog.setMainOrderId(event.getOrderId());
        //子订单id
        thirdPartyCouponLog.setSubOrderId(event.getSubOrderList().get(0).getSubOrderId());
        //服务方
        thirdPartyCouponLog.setServiceParty(CouponConstant.SERVICE_PARTY_YOUKU);
        //服务url
        thirdPartyCouponLog.setServiceUrl(result.getServiceUrl1());

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
    private ThirdPartyCouponLogCommand sendThirdPartyLogUpdateEvent(ThirdPartyCouponLog thirdPartyCouponLog, YouKuRequest youKuRequest, YouKuResponse youKuResponse, OrderPaidEvent event,
                                                                    ProductThirdpartyRelated result) {
        thirdPartyCouponLog.setUpdatedBy(event.getCustomerInfo().getUserId());
        //服务方
        thirdPartyCouponLog.setServiceParty(CouponConstant.SERVICE_PARTY_YOUKU);
        //服务URL
        thirdPartyCouponLog.setServiceUrl(result.getServiceUrl1());
        //请求参数
        thirdPartyCouponLog.setRequestContext(JSONUtils.toJson(youKuRequest));
        //响应参数
        thirdPartyCouponLog.setResponseContext(youKuResponse != null ? JSONUtils.toJson(youKuResponse) : null);
        //返回标识
        thirdPartyCouponLog.setReturnCode(youKuResponse == null ? CouponConstant.THIRD_PARTY_SERVICE_FLAG_FAILED : CouponConstant
                .YOUKU_RETURN_CODE_SUCCESS.equals(youKuResponse.getRETCODE()) ?
                CouponConstant.THIRD_PARTY_SERVICE_FLAG_SUCCESS : CouponConstant.THIRD_PARTY_SERVICE_FLAG_FAILED);

        //组装第三方日志命令
        ThirdPartyCouponLogCommand thirdPartyCouponLogCommand = new ThirdPartyCouponLogCommand();

        thirdPartyCouponLogCommand.setId(thirdPartyCouponLog.getId());
        thirdPartyCouponLogCommand.setDoType(CouponConstant.DO_TYPE_UPDATE);
        thirdPartyCouponLogCommand.setThirdPartyCouponLog(thirdPartyCouponLog);

        return thirdPartyCouponLogCommand;
    }


    }
