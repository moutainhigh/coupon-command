package cn.vpclub.coupon.command.service.impl;

import cn.vpclub.coupon.api.commands.o2ocoupon.CreateO2OCouponCommand;
import cn.vpclub.coupon.api.commands.other.ThirdPartyCouponLogCommand;
import cn.vpclub.coupon.api.commands.other.UpdateOrderFailCommand;
import cn.vpclub.coupon.api.commands.other.UpdateOrderSuccessCommand;
import cn.vpclub.coupon.api.constants.CouponConstant;
import cn.vpclub.coupon.api.entity.O2oCoupon;
import cn.vpclub.coupon.api.entity.ProductThirdpartyRelated;
import cn.vpclub.coupon.api.entity.ThirdPartyCouponLog;
import cn.vpclub.coupon.api.thirdparty.hgds.HttpUtil;
import cn.vpclub.coupon.api.thirdparty.hgds.RandomUtils;
import cn.vpclub.coupon.api.thirdparty.xbkhgds.XbkHgdsRequest;
import cn.vpclub.coupon.api.thirdparty.xbkhgds.XbkHgdsResponse;
import cn.vpclub.coupon.api.thirdparty.xbkhgds.XbkHgdsResponseData;
import cn.vpclub.coupon.api.utils.EnvPropertiesUtil;
import cn.vpclub.coupon.api.utils.JSONUtils;
import cn.vpclub.coupon.command.service.IXbkHgdsCouponService;
import cn.vpclub.moses.common.api.events.pay.OrderPaidEvent;
import cn.vpclub.moses.core.enums.ReturnCodeEnum;
import cn.vpclub.moses.core.model.response.BaseResponse;
import cn.vpclub.moses.utils.common.IdWorker;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.har.util.AESUtil;
import com.har.util.code.B2BRSAUtil;
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
 * @description:星巴克哈根达斯服务实现
 * @Date:2018/2/28
 */
@Service
@Slf4j
@AllArgsConstructor
public class XbkHgdsCouponServiceImpl implements IXbkHgdsCouponService {
    private CommandGateway commandGateway;

    @Override
    // 请求第三方接口，获取新卡券
    public void makeCoupon(OrderPaidEvent event, ProductThirdpartyRelated result, BaseResponse thirdPartyResponse) throws
            Exception {
        //读取第三方配置
        Properties thirdPartyConfig = new Properties();
        InputStream stream = MapCache.class.getResourceAsStream("/thirdPartyConfig.properties");
        thirdPartyConfig.load(stream);
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
        //组装调用接口参数
        XbkHgdsRequest xbkhgdsRequest = null;
        B2BRSAUtil b2bRSAUtil = new B2BRSAUtil();
        b2bRSAUtil.loadPublicKeyStr(thirdPartyConfig.getProperty(MessageFormat.format("xbkhgds.publickey.{0}",
                EnvPropertiesUtil.getEnv("SPRING_PROFILES_ACTIVE")))); // 加载汇安融 RSA 公钥
        String msgKey = RandomUtils.generateString(16); // 对 msgKey 加密、编码

        JSONObject dataJson = new JSONObject();
        dataJson.put("orderNo", event.getSubOrderList().get(0).getSubOrderId());//子订单id
        dataJson.put("goodsNo", result.getThirdProductId());
        dataJson.put("channelNo", thirdPartyConfig.getProperty(MessageFormat.format("xbkhgds.channelNo.{0}", EnvPropertiesUtil
                .getEnv("SPRING_PROFILES_ACTIVE"))));
        dataJson.put("valiedStartDate", simpleDateFormat.format(event.getSubOrderList().get(0).getOrderItemList().get(0)
                .getField11()));
        dataJson.put("valiedEndDate", simpleDateFormat.format(event.getSubOrderList().get(0).getOrderItemList().get(0)
                .getField12()));
        dataJson.put("phone", event.getCustomerInfo().getBuyerPhone());
        dataJson.put("goodsPrice", event.getSubOrderList().get(0).getOrderItemList().get(0).getSellPrice());
        dataJson.put("goodsCount", event.getSubOrderList().get(0).getOrderItemList().get(0).getBuyQty());
        log.info("dataJson:" + dataJson.toJSONString());
        //将没加密的信息转为对象
        xbkhgdsRequest = JSONUtils.toObject(String.valueOf(dataJson), XbkHgdsRequest.class);

        String data = AESUtil.getInstance(msgKey).encrypt(dataJson.toJSONString()); // AES 加密 data 数据、编码
        b2bRSAUtil.loadPrivateKeyStr(thirdPartyConfig.getProperty(MessageFormat.format("xbkhgds.privatekey.{0}",
                EnvPropertiesUtil.getEnv("SPRING_PROFILES_ACTIVE"))));
        String sign = b2bRSAUtil.makeSign(dataJson.toJSONString());
        log.info("sign :" + sign);
        JSONObject json = new JSONObject();
        json.put("AppId", thirdPartyConfig.getProperty(MessageFormat.format("xbkhgds.Appid.{0}", EnvPropertiesUtil.getEnv
                ("SPRING_PROFILES_ACTIVE"))));
        json.put("MsgKey", b2bRSAUtil.encryptStr(msgKey));
        json.put("PartnerId", thirdPartyConfig.getProperty(MessageFormat.format("xbkhgds.PartnerId.{0}", EnvPropertiesUtil
                .getEnv("SPRING_PROFILES_ACTIVE"))));
        json.put("Data", data);
        json.put("Sign", sign);
        //合并当前请求路径
        String postUrlCode = result.getServiceUrl1();
        XbkHgdsResponse xbkhgdsResponse = null;
        String RespCode = null;
        String RespDesc = null;
        try {
            //发送请求过去 返回一个规定的字符串
            String xbkhgdsJson = HttpUtil.post(postUrlCode, json.toString());
            //将json转化为对象
            xbkhgdsResponse = JSONUtils.toObject(xbkhgdsJson, XbkHgdsResponse.class);
            //如果返回状态为成功，则存储券码信息
            if (xbkhgdsResponse != null && CouponConstant.XBK_HGDS_RETURN_CODE_SUCCESS.equals(xbkhgdsResponse.getRespCode())) {
                //返回的标识值
                RespCode = xbkhgdsResponse.getRespCode();
                //返回的描述
                RespDesc = xbkhgdsResponse.getRespDesc();
                log.info("返回的标识值描述:" + RespCode + RespDesc);
                //将Data解密
                String Data = xbkhgdsResponse.getData();
                String MsgKey = b2bRSAUtil.decryptStr(JSONObject.parseObject(xbkhgdsJson).getString("MsgKey"));
                String dataString = AESUtil.getInstance(MsgKey).decrypt(Data);
                log.info("dataString :" + dataString);
                com.alibaba.fastjson.JSONObject jsonObject = JSON.parseObject(dataString);
                List<XbkHgdsResponseData> codes = JSON.parseArray(jsonObject.getJSONArray("codes").toString(),
                        XbkHgdsResponseData.class);
                log.info("codes:" + codes.toString());
                xbkhgdsResponse.setCodes(codes);
                this.saveXbkhgdsCoupon(xbkhgdsResponse, event);

                this.sendOrderSuccessCommand(event);
            } else {
                log.info("星巴克哈根达斯制码失败,子订单id: {},xbkhgdsResponse: {}", event.getSubOrderList().get(0).getSubOrderId(), JSONUtils
                        .toJson(xbkhgdsResponse));

                this.sendOrderFailCommand(event);
            }
        } catch (Exception e) {
            log.error("Exception e:{}", e);
            this.sendOrderFailCommand(event);
        } finally {
            this.sendThirdPartyLogEvent(xbkhgdsRequest, xbkhgdsResponse, event, thirdPartyResponse, result, RespCode);
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
    private void saveXbkhgdsCoupon(XbkHgdsResponse xbkhgdsResponse, OrderPaidEvent event) {
        //组装写表参数
        O2oCoupon o2oCoupon = null;
        //券码对象集合
        List<O2oCoupon> o2oCouponList = null;
        //获取返回的券码
        if (StringUtils.isNotEmpty(xbkhgdsResponse.getCodes().get(0).getCode())) {
            o2oCouponList = new ArrayList<O2oCoupon>();
            String[] codeArr = xbkhgdsResponse.getCodes().get(0).getCode().split(";");
            for (String XbkhgdsCode : codeArr) {
                o2oCoupon = new O2oCoupon();
                o2oCoupon.setAppId(event.getAppId());
                o2oCoupon.setCreatedBy(event.getCustomerInfo().getUserId());
                o2oCoupon.setCreatedTime(System.currentTimeMillis());
                o2oCoupon.setUpdatedBy(o2oCoupon.getCreatedBy());
                o2oCoupon.setUpdatedTime(o2oCoupon.getCreatedTime());
                o2oCoupon.setOrderId(event.getSubOrderList().get(0).getSubOrderId());
                o2oCoupon.setOrderNo(event.getSubOrderList().get(0).getOrderNo());
                //真实券码
                o2oCoupon.setCouponCode(XbkhgdsCode.split(",")[0]);
                //有效时间开始
                o2oCoupon.setEffectiveDateStart(event.getSubOrderList().get(0).getOrderItemList().get(0).getField11());
                //有效时间结束
                o2oCoupon.setEffectiveDateEnd(event.getSubOrderList().get(0).getOrderItemList().get(0).getField12());
                o2oCoupon.setBuyerId(event.getCustomerInfo().getUserId());
                o2oCoupon.setBuyerPhone(event.getCustomerInfo().getBuyerPhone());
                //卡券来源
                o2oCoupon.setCouponSource(CouponConstant.SERVICE_PARTY_XBK_HGDS);
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
    private void sendThirdPartyLogEvent(XbkHgdsRequest xbkhgdsRequest, XbkHgdsResponse xbkhgdsResponse, OrderPaidEvent event,
                                        BaseResponse thirdPartyResponse, ProductThirdpartyRelated result, String RespCode)
            throws IOException {
        ThirdPartyCouponLogCommand thirdPartyCouponLogCommand = null;
        //查询记录为空，则新增
        if (thirdPartyResponse.getReturnCode().intValue() == ReturnCodeEnum.CODE_1002.getCode()
                .intValue()) {
            thirdPartyCouponLogCommand = this.sendThirdPartyLogAddEvent(xbkhgdsRequest, xbkhgdsResponse, event, result, RespCode);
        }
        //查询记录不为空，则修改
        else if (thirdPartyResponse.getReturnCode().intValue() == ReturnCodeEnum.CODE_1000.getCode()
                .intValue()) {
            thirdPartyCouponLogCommand = this.sendThirdPartyLogUpdateEvent((ThirdPartyCouponLog) thirdPartyResponse.getDataInfo
                    (), xbkhgdsRequest, xbkhgdsResponse, event, result, RespCode);
        }

        //发送第三方日志命令
        if (thirdPartyCouponLogCommand != null) {
            commandGateway.send(thirdPartyCouponLogCommand);
        }
    }

    /**
     * 新增第三方调用日志表
     */
    private ThirdPartyCouponLogCommand sendThirdPartyLogAddEvent(XbkHgdsRequest xbkhgdsRequest, XbkHgdsResponse
            xbkhgdsResponse, OrderPaidEvent event, ProductThirdpartyRelated result, String RespCode) throws IOException {
        //组装接口调用日志表参数
        ThirdPartyCouponLog thirdPartyCouponLog = new ThirdPartyCouponLog();
        thirdPartyCouponLog.setId(IdWorker.getId());
        thirdPartyCouponLog.setCreatedBy(event.getCustomerInfo().getUserId());
        thirdPartyCouponLog.setUpdatedBy(thirdPartyCouponLog.getCreatedBy());
        thirdPartyCouponLog.setAppId(event.getAppId());
        //返回标识
        thirdPartyCouponLog.setReturnCode(xbkhgdsResponse == null ? CouponConstant.THIRD_PARTY_SERVICE_FLAG_FAILED :
                (CouponConstant
                        .XBK_HGDS_RETURN_CODE_SUCCESS.equals(RespCode) ?
                        CouponConstant.THIRD_PARTY_SERVICE_FLAG_SUCCESS : CouponConstant.THIRD_PARTY_SERVICE_FLAG_FAILED));
        //请求参数
        thirdPartyCouponLog.setRequestContext(JSONUtils.toJson(xbkhgdsRequest));
        //响应参数
        thirdPartyCouponLog.setResponseContext(xbkhgdsResponse != null ? JSONUtils.toJson(xbkhgdsResponse) : null);
        //主订单id
        thirdPartyCouponLog.setMainOrderId(event.getOrderId());
        //子订单id
        thirdPartyCouponLog.setSubOrderId(event.getSubOrderList().get(0).getSubOrderId());
        //服务方
        thirdPartyCouponLog.setServiceParty(CouponConstant.SERVICE_PARTY_XBK_HGDS);
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
    private ThirdPartyCouponLogCommand sendThirdPartyLogUpdateEvent(ThirdPartyCouponLog thirdPartyCouponLog,
                                                                    XbkHgdsRequest xbkhgdsRequest, XbkHgdsResponse
                                                                            xbkhgdsResponse, OrderPaidEvent event,
                                                                    ProductThirdpartyRelated result, String RespCode) {
        thirdPartyCouponLog.setUpdatedBy(event.getCustomerInfo().getUserId());
        //服务方
        thirdPartyCouponLog.setServiceParty(CouponConstant.SERVICE_PARTY_XBK_HGDS);
        //服务URL
        thirdPartyCouponLog.setServiceUrl(result.getServiceUrl1());
        //请求参数
        thirdPartyCouponLog.setRequestContext(JSONUtils.toJson(xbkhgdsRequest));
        //响应参数
        thirdPartyCouponLog.setResponseContext(xbkhgdsResponse != null ? JSONUtils.toJson(xbkhgdsResponse) : null);
        //返回标识
        thirdPartyCouponLog.setReturnCode(xbkhgdsResponse == null ? CouponConstant.THIRD_PARTY_SERVICE_FLAG_FAILED :
                (CouponConstant
                        .KFC_RETURN_CODE_SUCCESS.equals(RespCode) ?
                        CouponConstant.THIRD_PARTY_SERVICE_FLAG_SUCCESS : CouponConstant.THIRD_PARTY_SERVICE_FLAG_FAILED));
        //组装第三方日志命令
        ThirdPartyCouponLogCommand thirdPartyCouponLogCommand = new ThirdPartyCouponLogCommand();
        thirdPartyCouponLogCommand.setId(thirdPartyCouponLog.getId());
        thirdPartyCouponLogCommand.setDoType(CouponConstant.DO_TYPE_UPDATE);
        thirdPartyCouponLogCommand.setThirdPartyCouponLog(thirdPartyCouponLog);
        return thirdPartyCouponLogCommand;

    }


}
