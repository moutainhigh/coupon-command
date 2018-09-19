package cn.vpclub.coupon.command.controllers;

import cn.vpclub.coupon.api.commands.o2ocoupon.ConsumeO2OCouponCommand;
import cn.vpclub.coupon.api.constants.CouponConstant;
import cn.vpclub.coupon.api.entity.O2oCoupon;
import cn.vpclub.coupon.api.entity.OrderPaidEventLog;
import cn.vpclub.coupon.api.requests.o2ocoupon.O2OCouponMixRequest;
import cn.vpclub.coupon.api.utils.JSONUtils;
import cn.vpclub.coupon.command.rpc.O2oCouponRpcService;
import cn.vpclub.coupon.command.rpc.OrderPaidEventLogRpcService;
import cn.vpclub.coupon.command.rpc.OrdertItemRpcService;
import cn.vpclub.moses.common.api.commands.order.VerifyCouponOrderCommand;
import cn.vpclub.moses.core.enums.ReturnCodeEnum;
import cn.vpclub.moses.core.model.response.BackResponseUtil;
import cn.vpclub.moses.core.model.response.BaseResponse;
import cn.vpclub.moses.order.query.api.OrderItemProto;
import cn.vpclub.moses.utils.common.IdWorker;
import cn.vpclub.moses.web.controller.AbstractController;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * @author:yangqiao
 * @description:
 * @Date:2017/12/12
 */
@RestController
@RequestMapping("/o2oCoupon")
@Slf4j
public class O2OCouponController extends AbstractController {

    private CommandGateway commandGateway;

    @Autowired
    private O2oCouponRpcService o2oCouponRpcService;

    @Autowired
    private OrdertItemRpcService ordertItemRpcService;

    @Autowired
    private OrderPaidEventLogRpcService orderPaidEventLogRpcService;

    public O2OCouponController(CommandGateway commandGateway) {
        this.commandGateway = commandGateway;
    }

    /**
     * 核销O2O卡券
     */
    @PostMapping(value = "/validCoupon")
    public BaseResponse validCoupon(@RequestBody O2OCouponMixRequest request, HttpServletRequest servletRequest) {

        log.info("O2OCouponController.validCoupon: {}", JSONUtils.toJson(request));

        //参数不全
        if (request == null || StringUtils.isEmpty(request.getCouponCode()) || request.getMerchantId() == null) {
            return BackResponseUtil.getBaseResponse(ReturnCodeEnum.CODE_1006.getCode());
        }

        //返回对象-默认成功
        BaseResponse baseResponse = BackResponseUtil.getBaseResponse(ReturnCodeEnum.CODE_1000.getCode());

        //组装查询参数
        O2oCoupon parm = new O2oCoupon();
        parm.setCouponCode(request.getCouponCode());
        //查询卡券
        BaseResponse o2oCouponResponse = o2oCouponRpcService.query(parm);

        //验证卡券状态，是否处于未核销状态，有效期
        String couponValid = this.validO2OCoupon(o2oCouponResponse, request);

        if (!"0".equals(couponValid.split("-")[0])) {
            //如果查无此券，则返回1006，其他返回1005
            baseResponse = BackResponseUtil.getBaseResponse("1".equals(couponValid.split("-")[0]) ? ReturnCodeEnum
                    .CODE_1006.getCode() : ReturnCodeEnum.CODE_1005.getCode());
            baseResponse.setMessage(couponValid.split("-")[1]);

            return baseResponse;
        }

        //卡券对象
        O2oCoupon o2oCoupon = (O2oCoupon) o2oCouponResponse.getDataInfo();

        //验证订单状态
        BaseResponse orderValidResponse = this.validOrder(o2oCoupon);
        //如果验证不通过，则直接返回
        if (orderValidResponse.getReturnCode().intValue() != ReturnCodeEnum.CODE_1000.getCode().intValue()) {
            return orderValidResponse;
        }

        baseResponse.setDataInfo(o2oCoupon);

        return baseResponse;
    }

    /**
     * 核销O2O卡券
     */
    @PostMapping(value = "/couponConsume")
    public BaseResponse couponConsume(@RequestBody O2OCouponMixRequest request, HttpServletRequest servletRequest) {

        log.info("O2OCouponController.couponConsume: {}", JSONUtils.toJson(request));

        //验证数据
        BaseResponse baseResponse = this.validCoupon(request, servletRequest);

        //验证不通过则直接返回
        if (baseResponse.getReturnCode().intValue() != ReturnCodeEnum.CODE_1000.getCode().intValue()) {
            return baseResponse;
        }

        //组装查询参数
        O2oCoupon parm = new O2oCoupon();
        parm.setCouponCode(request.getCouponCode());
        //查询卡券
        BaseResponse o2oCouponResponse = o2oCouponRpcService.query(parm);

        //卡券对象
        O2oCoupon o2oCoupon = (O2oCoupon) o2oCouponResponse.getDataInfo();

        //通知订单修改状态
        this.consumeOrder(o2oCoupon);

        //组装验券命令
        ConsumeO2OCouponCommand consumeO2OCouponCommand = new ConsumeO2OCouponCommand();
        consumeO2OCouponCommand.setId(IdWorker.getId());
        consumeO2OCouponCommand.setO2oCouponId(o2oCoupon.getId());
        consumeO2OCouponCommand.setOrgName(request.getOrgName());
        consumeO2OCouponCommand.setOrgPhone(request.getOrgPhone());
        consumeO2OCouponCommand.setValidBy(request.getValidBy());
        consumeO2OCouponCommand.setValidOrg(request.getValidOrg());
        try {

            commandGateway.sendAndWait(consumeO2OCouponCommand);
        } catch (Exception e) {
            log.error("couponConsume error: {}", e);
            baseResponse = BackResponseUtil.getBaseResponse(ReturnCodeEnum.CODE_1005.getCode());
        }

        return baseResponse;
    }

    /**
     * 验证订单状态
     */
    private BaseResponse validOrder(O2oCoupon o2oCoupon) {

        //验证结果-默认校验通过
        BaseResponse baseResponse = BackResponseUtil.getBaseResponse(ReturnCodeEnum.CODE_1000.getCode());

        OrderItemProto.OrderItemResponse orderItemResponse;

        //查询订单支付事件-获取subOrderId
        OrderPaidEventLog orderPaidEventLog = new OrderPaidEventLog();
        orderPaidEventLog.setSubOrderId(o2oCoupon.getOrderId());
        BaseResponse orderPaidLogResponse = orderPaidEventLogRpcService.query(orderPaidEventLog);
        if (orderPaidLogResponse.getReturnCode().intValue() != ReturnCodeEnum.CODE_1000.getCode().intValue()) {
            baseResponse = BackResponseUtil.getBaseResponse(ReturnCodeEnum.CODE_1005.getCode());
            baseResponse.setMessage("订单支付事件为空，请联系管理员");
            return baseResponse;
        }
        //订单支付事件
        orderPaidEventLog = (OrderPaidEventLog) orderPaidLogResponse.getDataInfo();

        //如果订单项id为空，则返回
        if (orderPaidEventLog.getOrderItemId() == null) {
            return baseResponse;
        }

        OrderItemProto.CheckCouponOrderDTO dto = OrderItemProto.CheckCouponOrderDTO.newBuilder().setOrderItemId
                (orderPaidEventLog.getOrderItemId()).build();

        log.info("validOrder: {}", JSONUtils.toJson(dto));

        //查询订单状态
        orderItemResponse = ordertItemRpcService.checkCouponOrder(dto);

        //如果返回结果不为1000，则说明订单校验不通过
        if (!ReturnCodeEnum.CODE_1000.getCode().equals(orderItemResponse.getReturnCode())) {
            baseResponse.setReturnCode(orderItemResponse.getReturnCode().getValue());
            baseResponse.setMessage(orderItemResponse.getMessage());
        }

        return baseResponse;
    }

    /**
     * 验证卡券状态，是否处于未核销状态，有效期
     */
    private String validO2OCoupon(BaseResponse o2oCouponResponse, O2OCouponMixRequest request) {
        //默认，验证通过
        String result = "0";

        //查询结果为空，标识无此卡券
        if (o2oCouponResponse.getReturnCode().intValue() == ReturnCodeEnum.CODE_1002.getCode().intValue()) {
            return "1-无此券码";
        }

        //卡券对象
        O2oCoupon o2oCoupon = (O2oCoupon) o2oCouponResponse.getDataInfo();

        //判断是否已消费
        if (CouponConstant.O2O_COUPON_CONSUMED_Y == o2oCoupon.getConsumed().intValue()) {
            return "2-此券码已消费";
        }

        //判断是否不在有效期
        if ((o2oCoupon.getEffectiveDateStart() != null && o2oCoupon.getEffectiveDateStart() > System.currentTimeMillis()) ||
                (o2oCoupon.getEffectiveDateEnd() != null && o2oCoupon.getEffectiveDateEnd() < System.currentTimeMillis())) {
            return "3-此券码已过期";
        }


        //判断是否是指定核销门店核销
        //查看商家信息
        O2OCouponMixRequest o2OCouponMixRequest = new O2OCouponMixRequest();
        o2OCouponMixRequest.setCouponCode(request.getCouponCode());
        BaseResponse merchantResponse = o2oCouponRpcService.findO2OCouponMerchant(o2OCouponMixRequest);

        if (request.getMerchantId() == null || ((List<O2OCouponMixRequest>) merchantResponse.getDataInfo()).get(0)
                .getMerchantId().longValue() != request.getMerchantId().longValue()) {
            return "4-商家错误，您不能核销此券码";
        }

        return result;
    }

    /**
     * 通知订单修改发货状态状态
     */
    private BaseResponse consumeOrder(O2oCoupon o2oCoupon) {

        //验证结果
        BaseResponse baseResponse;

        //查询订单支付事件-获取subOrderId
        OrderPaidEventLog orderPaidEventLog = new OrderPaidEventLog();
        orderPaidEventLog.setSubOrderId(o2oCoupon.getOrderId());
        BaseResponse orderPaidLogResponse = orderPaidEventLogRpcService.query(orderPaidEventLog);
        if (orderPaidLogResponse.getReturnCode().intValue() != ReturnCodeEnum.CODE_1000.getCode().intValue()) {
            baseResponse = BackResponseUtil.getBaseResponse(ReturnCodeEnum.CODE_1005.getCode());
            baseResponse.setMessage("查询订单支付事件为空，请联系管理员");

            return baseResponse;
        }
        //订单支付事件
        orderPaidEventLog = (OrderPaidEventLog) orderPaidLogResponse.getDataInfo();

        //调用订单的验券接口
        VerifyCouponOrderCommand verifyCouponOrderCommand = new VerifyCouponOrderCommand();
        verifyCouponOrderCommand.setOrderId(orderPaidEventLog.getMainOrderId());
        verifyCouponOrderCommand.setCouponCode(o2oCoupon.getCouponCode());
        verifyCouponOrderCommand.setSubOrderId(orderPaidEventLog.getSubOrderId());

        baseResponse = commandGateway.sendAndWait(verifyCouponOrderCommand);

        return baseResponse;
    }


}