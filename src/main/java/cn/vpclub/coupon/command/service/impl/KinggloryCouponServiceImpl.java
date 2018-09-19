package cn.vpclub.coupon.command.service.impl;

import cn.vpclub.coupon.api.commands.o2ocoupon.CreateO2OCouponCommand;
import cn.vpclub.coupon.api.commands.other.ThirdPartyCouponLogCommand;
import cn.vpclub.coupon.api.commands.other.UpdateOrderFailCommand;
import cn.vpclub.coupon.api.commands.other.UpdateOrderSuccessCommand;
import cn.vpclub.coupon.api.constants.CouponConstant;
import cn.vpclub.coupon.api.entity.O2oCoupon;
import cn.vpclub.coupon.api.entity.ProductThirdpartyRelated;
import cn.vpclub.coupon.api.entity.ThirdPartyCouponLog;
import cn.vpclub.coupon.api.thirdparty.kingglory.AesCBC;
import cn.vpclub.coupon.api.utils.EnvPropertiesUtil;
import cn.vpclub.coupon.command.service.IKinggloryCouponService;
import cn.vpclub.moses.common.api.events.pay.OrderPaidEvent;
import cn.vpclub.moses.core.enums.ReturnCodeEnum;
import cn.vpclub.moses.core.model.response.BaseResponse;
import cn.vpclub.moses.utils.common.IdWorker;
import cn.vpclub.moses.utils.web.HttpRequestUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.cache.MapCache;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.text.MessageFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Properties;

@Service
@Slf4j
@AllArgsConstructor
public class KinggloryCouponServiceImpl implements IKinggloryCouponService {
    private CommandGateway commandGateway;

    @Override
    public void makeCoupon(OrderPaidEvent event, ProductThirdpartyRelated result, BaseResponse thirdPartyResponse) throws
            Exception {
        //读取第三方配置
        Properties thirdPartyConfig = new Properties();
        InputStream stream = MapCache.class.getResourceAsStream("/thirdPartyConfig.properties");
        thirdPartyConfig.load(stream);
        //组装调用接口参数
        String request = 1 + "," + event.getSubOrderList().get(0).getSubOrderId() + "," + event.getCustomerInfo().getBuyerPhone() + "," + event.getCustomerInfo().getBuyerName()
                + "," + event.getSubOrderList().get(0).getOrderItemList().get(0).getSellPrice();
        log.info("加密前的字串是:{}", request);
        Calendar date = Calendar.getInstance();
        String year = String.valueOf(date.get(Calendar.YEAR));
        String ivParameter = thirdPartyConfig.getProperty(MessageFormat.format("Kingglory.ivParameter.{0}", EnvPropertiesUtil
                .getEnv("SPRING_PROFILES_ACTIVE"))) + year;
        //加密参数
        String enString = AesCBC.getInstance().encrypt(request, "utf-8",
                thirdPartyConfig.getProperty(MessageFormat.format("Kingglory.sKey.{0}", EnvPropertiesUtil
                        .getEnv("SPRING_PROFILES_ACTIVE"))), ivParameter);
        log.info("加密后的字串是:{}", enString);
        String desc = null;
        String code = null;
        String response = null;
        try {
            //发送请求
            response = HttpRequestUtil.sendGet(result.getServiceUrl1() + enString, null, "application/json");
            if (StringUtils.isNotEmpty(response)) {
                Document doc = Jsoup.parse(response);
                Elements rows = doc.select("form[id=frmOrder]").get(0).select("div");
                //如果返回状态为成功，则存储券码信息
                if (rows.size() == 1) {
                    log.info("没有结果");
                } else {
                    Element row = rows.get(1);
                    desc = row.select("div").get(0).text();
                    log.info("返回表单字符描述:{}", desc);
                    //截取返回表单获取返回code
                    code = desc.substring(6, 9);
                    log.info("返回code:{}", code);
                }
                if (CouponConstant.KINGGLORY_RETURN_CODE_SUCCESS.equals(code) || CouponConstant
                        .KINGGLORY_RETURN_CODE_SUCCESS_FIRST.equals(code)
                        || CouponConstant.KINGGLORY_RETURN_CODE_SUCCESS_TWO.equals(code)) {
                    this.saveKinggloryCoupon(desc, event, code);
                    this.sendOrderSuccessCommand(event);
                } else {
                    log.info("王者荣耀制码失败,子订单id:{},response:{}", event.getSubOrderList().get(0).getSubOrderId(), desc);

                    this.sendOrderFailCommand(event);
                }
            } else {
                log.info("王者荣耀制码失败:{}");
                this.sendOrderFailCommand(event);
            }

        } catch (Exception e) {
            log.error("Exception e:{}", e);
            this.sendOrderFailCommand(event);
        } finally {
            this.sendThirdPartyLogEvent(request, desc, event, thirdPartyResponse, code, response, result);
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
    private void saveKinggloryCoupon(String desc, OrderPaidEvent event, String code) {
        //组装写表参数
        O2oCoupon o2oCoupon = null;
        //券码对象集合
        List<O2oCoupon> o2oCouponList = null;
        if (StringUtils.isNotEmpty(code)) {
            o2oCoupon = new O2oCoupon();
            o2oCoupon.setAppId(event.getAppId());
            o2oCoupon.setCreatedBy(event.getCustomerInfo().getUserId());
            o2oCoupon.setCreatedTime(System.currentTimeMillis());
            o2oCoupon.setUpdatedBy(o2oCoupon.getCreatedBy());
            o2oCoupon.setUpdatedTime(o2oCoupon.getCreatedTime());
            o2oCoupon.setOrderId(event.getSubOrderList().get(0).getSubOrderId());
            o2oCoupon.setOrderNo(event.getSubOrderList().get(0).getOrderNo());
            //真实券码，王者荣耀不返回券码，只给一个界面，现在记录的是返回code
            o2oCoupon.setCouponCode(code);
            //有效时间开始
            o2oCoupon.setEffectiveDateStart(event.getSubOrderList().get(0).getOrderItemList().get(0).getField11());
            //有效时间结束
            o2oCoupon.setEffectiveDateEnd(event.getSubOrderList().get(0).getOrderItemList().get(0).getField12());
            o2oCoupon.setBuyerId(event.getCustomerInfo().getUserId());
            o2oCoupon.setBuyerPhone(event.getCustomerInfo().getBuyerPhone());
            //卡券来源
            o2oCoupon.setCouponSource(CouponConstant.SERVICE_PARTY_KINGGLORY);
            o2oCouponList.add(o2oCoupon);
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
    private void sendThirdPartyLogEvent(String request, String desc, OrderPaidEvent event, BaseResponse thirdPartyResponse,
                                        String code, String response, ProductThirdpartyRelated result) {
        ThirdPartyCouponLogCommand thirdPartyCouponLogCommand = null;
        //查询记录为空，则新增
        if (thirdPartyResponse.getReturnCode().intValue() == ReturnCodeEnum.CODE_1002.getCode()
                .intValue()) {
            thirdPartyCouponLogCommand = this.sendThirdPartyLogAddEvent(request, desc, event, code, response, result);
        }
        //查询记录不为空，则修改
        else if (thirdPartyResponse.getReturnCode().intValue() == ReturnCodeEnum.CODE_1000.getCode()
                .intValue()) {
            thirdPartyCouponLogCommand = this.sendThirdPartyLogUpdateEvent((ThirdPartyCouponLog) thirdPartyResponse.getDataInfo
                    (), request, desc, event, code, response, result);
        }
        //发送第三方日志命令
        if (thirdPartyCouponLogCommand != null) {
            commandGateway.send(thirdPartyCouponLogCommand);
        }
    }

    /**
     * 新增第三方调用日志表
     */
    private ThirdPartyCouponLogCommand sendThirdPartyLogAddEvent(String request, String desc, OrderPaidEvent event, String
            code, String response, ProductThirdpartyRelated result) {
        //组装接口调用日志表参数
        ThirdPartyCouponLog thirdPartyCouponLog = new ThirdPartyCouponLog();
        thirdPartyCouponLog.setId(IdWorker.getId());
        thirdPartyCouponLog.setCreatedBy(event.getCustomerInfo().getUserId());
        thirdPartyCouponLog.setUpdatedBy(thirdPartyCouponLog.getCreatedBy());
        thirdPartyCouponLog.setAppId(event.getAppId());
        //返回标识
        thirdPartyCouponLog.setReturnCode(response == null ? CouponConstant.THIRD_PARTY_SERVICE_FLAG_FAILED : (CouponConstant
                .KINGGLORY_RETURN_CODE_SUCCESS.equals(code) ||
                CouponConstant.KINGGLORY_RETURN_CODE_SUCCESS_FIRST.equals(code)
                || CouponConstant.KINGGLORY_RETURN_CODE_SUCCESS_TWO.equals(code)) ?
                CouponConstant.THIRD_PARTY_SERVICE_FLAG_SUCCESS : CouponConstant.THIRD_PARTY_SERVICE_FLAG_FAILED);
        //请求参数
        thirdPartyCouponLog.setRequestContext(request);
        //响应参数
        thirdPartyCouponLog.setResponseContext(response != null ? response : null);
        //主订单id
        thirdPartyCouponLog.setMainOrderId(event.getOrderId());
        //子订单id
        thirdPartyCouponLog.setSubOrderId(event.getSubOrderList().get(0).getSubOrderId());
        //服务方
        thirdPartyCouponLog.setServiceParty(CouponConstant.SERVICE_PARTY_KINGGLORY);
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
    private ThirdPartyCouponLogCommand sendThirdPartyLogUpdateEvent(ThirdPartyCouponLog thirdPartyCouponLog, String request,
                                                                    String desc, OrderPaidEvent event, String code, String
                                                                            response, ProductThirdpartyRelated result) {
        thirdPartyCouponLog.setUpdatedBy(event.getCustomerInfo().getUserId());
        //服务方
        thirdPartyCouponLog.setServiceParty(CouponConstant.SERVICE_PARTY_KINGGLORY);
        //服务URL
        thirdPartyCouponLog.setServiceUrl(result.getServiceUrl1());
        //请求参数
        thirdPartyCouponLog.setRequestContext(request);
        //响应参数
        thirdPartyCouponLog.setResponseContext(response != null ? response : null);
        //返回标识
        thirdPartyCouponLog.setReturnCode(response == null ? CouponConstant.THIRD_PARTY_SERVICE_FLAG_FAILED : (CouponConstant
                .KINGGLORY_RETURN_CODE_SUCCESS.equals(code) ||
                CouponConstant.KINGGLORY_RETURN_CODE_SUCCESS_FIRST.equals(code)
                || CouponConstant.KINGGLORY_RETURN_CODE_SUCCESS_TWO.equals(code)) ?
                CouponConstant.THIRD_PARTY_SERVICE_FLAG_SUCCESS : CouponConstant.THIRD_PARTY_SERVICE_FLAG_FAILED);
        //组装第三方日志命令
        ThirdPartyCouponLogCommand thirdPartyCouponLogCommand = new ThirdPartyCouponLogCommand();
        thirdPartyCouponLogCommand.setId(thirdPartyCouponLog.getId());
        thirdPartyCouponLogCommand.setDoType(CouponConstant.DO_TYPE_UPDATE);
        thirdPartyCouponLogCommand.setThirdPartyCouponLog(thirdPartyCouponLog);
        return thirdPartyCouponLogCommand;
    }
}
