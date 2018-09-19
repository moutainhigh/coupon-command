package cn.vpclub.coupon.command.service.impl;

import cn.vpclub.coupon.api.commands.o2ocoupon.CreateO2OCouponCommand;
import cn.vpclub.coupon.api.commands.other.ThirdPartyCouponLogCommand;
import cn.vpclub.coupon.api.commands.other.UpdateOrderFailCommand;
import cn.vpclub.coupon.api.commands.other.UpdateOrderSuccessCommand;
import cn.vpclub.coupon.api.constants.CouponConstant;
import cn.vpclub.coupon.api.entity.O2oCoupon;
import cn.vpclub.coupon.api.entity.ProductThirdpartyRelated;
import cn.vpclub.coupon.api.entity.ThirdPartyCouponLog;
import cn.vpclub.coupon.api.thirdparty.macdonald.ActivityInfo;
import cn.vpclub.coupon.api.thirdparty.macdonald.Messages;
import cn.vpclub.coupon.api.thirdparty.macdonald.Mms;
import cn.vpclub.coupon.api.thirdparty.macdonald.Recipients;
import cn.vpclub.coupon.api.thirdparty.macdonald.Sms;
import cn.vpclub.coupon.api.thirdparty.macdonald.SubmitVerifyReq;
import cn.vpclub.coupon.api.thirdparty.macdonald.SubmitVerifyRes;
import cn.vpclub.coupon.api.thirdparty.macdonald.util.Send;
import cn.vpclub.coupon.api.utils.EnvPropertiesUtil;
import cn.vpclub.coupon.api.utils.JSONUtils;
import cn.vpclub.coupon.command.service.IMacDonaldCouponService;
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

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * @author:zhangyingdong
 * @description:麦当劳券码实现
 * @Date:2018/2/8
 */
@Service
@Slf4j
@AllArgsConstructor
public class MacDonaldCouponServiceImpl implements IMacDonaldCouponService {
    private CommandGateway commandGateway;

    @Override
    public void makeCoupon(OrderPaidEvent event, ProductThirdpartyRelated result, BaseResponse thirdPartyResponse) throws
            IOException {
        //读取第三方配置
        Properties thirdPartyConfig = new Properties();
        InputStream stream = MapCache.class.getResourceAsStream("/thirdPartyConfig.properties");
        thirdPartyConfig.load(stream);
        //组装请求报文
        Recipients recipients = new Recipients();
        recipients.setNumber(event.getCustomerInfo().getBuyerPhone());
        Messages messages = new Messages();
        Sms sms = new Sms();
        sms.setText("这里是短信内容");
        Mms mms = new Mms();
        mms.setSubject("这里是彩信标");
        messages.setSms(sms).setMms(mms);
        ActivityInfo activityInfo = new ActivityInfo();
        activityInfo.setActivityID(result.getThirdProductId()).setOrgTimes(1).setOrgAmt(event.getSubOrderList().get(0)
                .getOrderItemList().get(0).getSellPrice())
                .setPrintText("谢谢使用！");
        SubmitVerifyReq submitVerifyReq = new SubmitVerifyReq();
        //需要支撑平台提供的号码文档没有，后期需要配置
        submitVerifyReq.setSystemID(thirdPartyConfig.getProperty(MessageFormat.format("macDonald.SystemID.{0}",
                EnvPropertiesUtil.getEnv("SPRING_PROFILES_ACTIVE"))))
                .setISSPID(thirdPartyConfig.getProperty(MessageFormat.format("macDonald.ISSPID.{0}", EnvPropertiesUtil.getEnv
                        ("SPRING_PROFILES_ACTIVE"))))
                .setTransactionID(String.valueOf(event.getSubOrderList().get(0).getSubOrderId()))//流水号，这里发送的是子订单id
                .setRecipients(recipients)
                .setSendClass("SAM").setMessages(messages).setActivityInfo(activityInfo);
        //发送https请求 返回字符串
        String responseXml = Send.doPosts(result.getServiceUrl1(), thirdPartyConfig.getProperty(MessageFormat.format("macDonald" +
                        ".key.{0}",
                EnvPropertiesUtil.getEnv("SPRING_PROFILES_ACTIVE"))), submitVerifyReq);
        log.info(responseXml);
        String statusCode = responseXml.substring(148, 152);
        String statusText = responseXml.substring(177, 190);
        log.info("返回的编码和描述：" + statusCode + ":" + statusText);
        SubmitVerifyRes submitVerifyRes = null;
        try {
            try {
                //xml转化为实体
                JAXBContext context = JAXBContext.newInstance(SubmitVerifyRes.class);
                Unmarshaller unmarshaller = context.createUnmarshaller();
                submitVerifyRes = (SubmitVerifyRes) unmarshaller.unmarshal(new StringReader(responseXml));
                log.info(String.valueOf(submitVerifyRes));
            } catch (JAXBException e) {
                e.printStackTrace();
            }
            //如果返回状态为成功，则存储券码信息
            if (submitVerifyRes != null && CouponConstant.MACDONALD_RETURN_CODE_SUCCESS.equals(statusCode)) {
                this.saveCoupon(submitVerifyRes, statusCode, statusText, event);

                this.sendOrderSuccessCommand(event);
            } else {
                log.info("麦当劳制码失败,子订单id: {},submitVerifyRes: {}", event.getSubOrderList().get(0).getSubOrderId(), JSONUtils
                        .toJson(submitVerifyRes));

                this.sendOrderFailCommand(event);
            }
        } catch (Exception e) {
            log.error("Exception e:{}", e);
            this.sendOrderFailCommand(event);
        } finally {
            this.sendThirdPartyLogEvent(submitVerifyReq, submitVerifyRes, event, thirdPartyResponse, result, statusCode);
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
     * 记录mac券码信息-发送记录O2O券码事件
     */
    private void saveCoupon(SubmitVerifyRes submitVerifyRes, String statusCode, String statusText, OrderPaidEvent event) {
        //组装写表参数
        O2oCoupon o2oCoupon = null;
        //券码对象集合
        List<O2oCoupon> o2oCouponList = null;
        //获取返回的券码
        if (StringUtils.isNotEmpty(submitVerifyRes.getAssistNumber())) {
            o2oCouponList = new ArrayList<O2oCoupon>();
            //如生成的码数量为多个，则以“;”分隔反馈多个，“，”分隔码和金额
            //获取返回券码数组
            String[] AssistNumber = submitVerifyRes.getAssistNumber().split(";");
            for (String code : AssistNumber) {
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
                o2oCoupon.setCouponSource(CouponConstant.SERVICE_PARTY_MacDonald);
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
    private void sendThirdPartyLogEvent(SubmitVerifyReq submitVerifyReq, SubmitVerifyRes submitVerifyRes, OrderPaidEvent event,
                                        BaseResponse thirdPartyResponse, ProductThirdpartyRelated result, String statusCode)
            throws IOException {

        ThirdPartyCouponLogCommand thirdPartyCouponLogCommand = null;
        //查询记录为空，则新增
        if (thirdPartyResponse.getReturnCode().intValue() == ReturnCodeEnum.CODE_1002.getCode()
                .intValue()) {
            thirdPartyCouponLogCommand = this.sendThirdPartyLogAddEvent(submitVerifyReq, submitVerifyRes, event, result,
                    statusCode);
        }
        //查询记录不为空，则修改
        else if (thirdPartyResponse.getReturnCode().intValue() == ReturnCodeEnum.CODE_1000.getCode()
                .intValue()) {
            thirdPartyCouponLogCommand = this.sendThirdPartyLogUpdateEvent((ThirdPartyCouponLog) thirdPartyResponse.getDataInfo
                    (), submitVerifyReq, submitVerifyRes, event, result, statusCode);
        }
        //发送第三方日志命令
        if (thirdPartyCouponLogCommand != null) {
            commandGateway.send(thirdPartyCouponLogCommand);
        }
    }

    /**
     * 新增第三方调用日志表
     */
    private ThirdPartyCouponLogCommand sendThirdPartyLogAddEvent(SubmitVerifyReq submitVerifyReq, SubmitVerifyRes
            submitVerifyRes, OrderPaidEvent event
            , ProductThirdpartyRelated result, String statusCode) throws IOException {


        //组装接口调用日志表参数
        ThirdPartyCouponLog thirdPartyCouponLog = new ThirdPartyCouponLog();
        thirdPartyCouponLog.setId(IdWorker.getId());
        thirdPartyCouponLog.setCreatedBy(event.getCustomerInfo().getUserId());
        thirdPartyCouponLog.setUpdatedBy(thirdPartyCouponLog.getCreatedBy());
        thirdPartyCouponLog.setAppId(event.getAppId());
        //返回标识
        thirdPartyCouponLog.setReturnCode(submitVerifyRes == null ? CouponConstant.THIRD_PARTY_SERVICE_FLAG_FAILED :
                (CouponConstant
                        .MACDONALD_RETURN_CODE_SUCCESS.equals(statusCode) ?
                        CouponConstant.THIRD_PARTY_SERVICE_FLAG_SUCCESS : CouponConstant.THIRD_PARTY_SERVICE_FLAG_FAILED));
        //请求参数
        thirdPartyCouponLog.setRequestContext(JSONUtils.toJson(submitVerifyReq));
        //响应参数
        thirdPartyCouponLog.setResponseContext(submitVerifyRes != null ? JSONUtils.toJson(submitVerifyRes) : null);
        //主订单id
        thirdPartyCouponLog.setMainOrderId(event.getOrderId());
        //子订单id
        thirdPartyCouponLog.setSubOrderId(event.getSubOrderList().get(0).getSubOrderId());
        //服务方
        thirdPartyCouponLog.setServiceParty(CouponConstant.SERVICE_PARTY_MacDonald);
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
    private ThirdPartyCouponLogCommand sendThirdPartyLogUpdateEvent(ThirdPartyCouponLog thirdPartyCouponLog, SubmitVerifyReq
            submitVerifyReq,
                                                                    SubmitVerifyRes submitVerifyRes, OrderPaidEvent event,
                                                                    ProductThirdpartyRelated result, String statusCode) {
        thirdPartyCouponLog.setUpdatedBy(event.getCustomerInfo().getUserId());
        //服务方
        thirdPartyCouponLog.setServiceParty(CouponConstant.SERVICE_PARTY_MacDonald);
        //服务URL
        thirdPartyCouponLog.setServiceUrl(result.getServiceUrl1());
        //请求参数
        thirdPartyCouponLog.setRequestContext(JSONUtils.toJson(submitVerifyReq));
        //响应参数
        thirdPartyCouponLog.setResponseContext(submitVerifyRes != null ? JSONUtils.toJson(submitVerifyRes) : null);
        //返回标识
        thirdPartyCouponLog.setReturnCode(submitVerifyRes == null ? CouponConstant.THIRD_PARTY_SERVICE_FLAG_FAILED :
                (CouponConstant
                        .KFC_RETURN_CODE_SUCCESS.equals(statusCode) ?
                        CouponConstant.THIRD_PARTY_SERVICE_FLAG_SUCCESS : CouponConstant.THIRD_PARTY_SERVICE_FLAG_FAILED));
        //组装第三方日志命令
        ThirdPartyCouponLogCommand thirdPartyCouponLogCommand = new ThirdPartyCouponLogCommand();

        thirdPartyCouponLogCommand.setId(thirdPartyCouponLog.getId());
        thirdPartyCouponLogCommand.setDoType(CouponConstant.DO_TYPE_UPDATE);
        thirdPartyCouponLogCommand.setThirdPartyCouponLog(thirdPartyCouponLog);

        return thirdPartyCouponLogCommand;

    }

}
