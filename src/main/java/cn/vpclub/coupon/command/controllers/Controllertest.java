//package cn.vpclub.coupon.command.controllers;
//
//import cn.vpclub.coupon.api.entity.CouponIqiyi;
//import cn.vpclub.coupon.api.utils.JSONUtils;
//import cn.vpclub.coupon.command.rpc.CouponIqiyiService;
//import cn.vpclub.moses.core.model.response.BaseResponse;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.web.bind.annotation.PostMapping;
//import org.springframework.web.bind.annotation.RequestBody;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RestController;
//
//import javax.servlet.http.HttpServletRequest;
//import java.util.List;
//
///**
// * Created by zhangyingdong on 2018/7/11.
// */
//@RestController
//@RequestMapping("/Coupon")
//@Slf4j
//public class Controllertest {
//
//    @Autowired
//    private CouponIqiyiService couponIqiyiService;
//
//    @PostMapping(value = "/query")
//    public List<CouponIqiyi> query(@RequestBody CouponIqiyi request, HttpServletRequest servletRequest){
//
//        BaseResponse baseResponse=null;
//        CouponIqiyi couponIqiyi=new CouponIqiyi();
//        couponIqiyi.setProductId(request.getProductId());
//        baseResponse=couponIqiyiService.queryByProductId(couponIqiyi);
//        List<CouponIqiyi> couponIqiyiList = (List<CouponIqiyi>) baseResponse.getDataInfo();
//        return  couponIqiyiList;
//    }
//}
