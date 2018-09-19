package cn.vpclub.coupon.command.handlers.commands;

import cn.vpclub.coupon.api.commands.coupon.CreateCouponBatchCommand;
import cn.vpclub.coupon.api.commands.coupon.CreateCouponCommand;
import cn.vpclub.coupon.api.commands.coupon.CreateCouponDetailCommand;
import cn.vpclub.coupon.api.commands.coupon.ReleaseCouponDetailCommand;
import cn.vpclub.coupon.api.commands.o2ocoupon.ConsumeO2OCouponCommand;
import cn.vpclub.coupon.api.commands.o2ocoupon.CreateKFCO2OCouponCommand;
import cn.vpclub.coupon.api.commands.o2ocoupon.CreateO2OCouponCommand;
import cn.vpclub.coupon.api.commands.o2ocoupon.CreateYoukuO2OCouponCommand;
import cn.vpclub.coupon.api.commands.other.*;
import cn.vpclub.coupon.command.aggregates.*;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.model.Repository;
import org.springframework.stereotype.Component;

/**
 * @author:yangqiao
 * @description:
 * @Date:2017/12/12
 */
@Component
@Slf4j
@AllArgsConstructor
public class CouponCommandHandler {

    private Repository<CouponAggregate> couponAggregateRepository;

    private Repository<KFCCouponAggregate> kFCCouponAggregateRepository;

    private Repository<O2OCouponAggregate> o2OCouponAggregateRepository;

    private Repository<O2OYoukuCouponAggregate> o2OYoukuCouponAggregateRepository;

    private Repository<CouponResendAggregate> couponResendAggregateRepository;

    private Repository<ThirdPartyCouponLogAggregate> thirdPartyCouponLogAggregateRepository;

    private Repository<CouponBatchAggregate> couponBatchAggregateRepository;

    private Repository<CouponDetailAggregate> couponDetailAggregateRepository;

    private Repository<OrderAggregate> orderAggregateRepository;

    private Repository<IqiyiCouponAggregate> iqiyiCouponAggregateRepository;

    @CommandHandler
    public void handle(CreateCouponCommand command) throws Exception {
        couponAggregateRepository.newInstance(() -> new CouponAggregate(command));
    }

    @CommandHandler
    public void handle(CreateKFCO2OCouponCommand command) throws Exception {
        kFCCouponAggregateRepository.newInstance(
                () -> new KFCCouponAggregate(command.getId(), command));
    }

    @CommandHandler
    public void handle(CreateO2OCouponCommand command) throws Exception {
        o2OCouponAggregateRepository.newInstance(
                () -> new O2OCouponAggregate(command.getO2oCouponList(), command.getOrderPaidEvent()));
    }

    @CommandHandler
    public void handle(CreateYoukuO2OCouponCommand command) throws Exception {
        o2OYoukuCouponAggregateRepository.newInstance(
                () -> new O2OYoukuCouponAggregate(command.getO2oCoupon(), command.getOrderPaidEvent()));
    }


    @CommandHandler
    public void handle(ConsumeO2OCouponCommand command) throws Exception {
        o2OCouponAggregateRepository.newInstance(() -> new O2OCouponAggregate(command));
    }

    @CommandHandler
    public void handle(CouponResendCommand command) throws Exception {
        couponResendAggregateRepository.newInstance(
                () -> new CouponResendAggregate(command.getId(), command.getResendCouponEvent()));
    }

    @CommandHandler
    public void handle(ThirdPartyCouponLogCommand command) throws Exception {
        thirdPartyCouponLogAggregateRepository.newInstance(
                () -> new ThirdPartyCouponLogAggregate(command));
    }

    @CommandHandler
    public void handle(CreateCouponBatchCommand command) throws Exception {
        couponBatchAggregateRepository.newInstance(
                () -> new CouponBatchAggregate(command));
    }

    @CommandHandler
    public void handle(UpdateOrderSuccessCommand command) throws Exception {
        orderAggregateRepository.newInstance(
                () -> new OrderAggregate(command));
    }

    @CommandHandler
    public void handle(UpdateOrderFailCommand command) throws Exception {
        orderAggregateRepository.newInstance(
                () -> new OrderAggregate(command));
    }

    @CommandHandler
    public void handle(UpdateCodeStatusCommand command) throws Exception {
        iqiyiCouponAggregateRepository.newInstance(
                () -> new IqiyiCouponAggregate(command.getId(),command.getCode(),command.getOrderPaidEvent()));
    }

    @CommandHandler
    public void handle(ReleaseCouponDetailCommand command) throws Exception {
        couponDetailAggregateRepository.newInstance(() -> new CouponDetailAggregate(command));
    }

    @CommandHandler
    public void handle(CreateCouponDetailCommand command) throws Exception {
        //发送
        couponDetailAggregateRepository.newInstance(() -> new CouponDetailAggregate(command));

        //库存由商品管理

        //导入卡密，新增卡券库存
//        Aggregate<CouponBatchAggregate> couponBatchAggregateAggregate = couponBatchAggregateRepository.load(
//                command.getRelatedGoodsSku().toString());
//        couponBatchAggregateAggregate.execute(ag -> {
//            ag.increaseInventory(command);
//        });
    }
}