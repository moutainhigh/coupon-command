package cn.vpclub.coupon.command.rpc;


import cn.vpclub.coupon.api.entity.CouponIqiyi;

import cn.vpclub.coupon.query.api.CouponIqiyiProto;
import cn.vpclub.coupon.query.api.CouponIqiyiServiceGrpc;
import cn.vpclub.moses.core.model.response.BaseResponse;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static cn.vpclub.moses.utils.grpc.GRpcMessageConverter.fromGRpcMessage;
import static cn.vpclub.moses.utils.grpc.GRpcMessageConverter.toGRpcMessage;

/**
 * @author zyd
 */
@Service
@Slf4j
@AllArgsConstructor
public class CouponIqiyiService {

    private CouponIqiyiServiceGrpc.CouponIqiyiServiceBlockingStub blockingStub;

    public BaseResponse add(CouponIqiyi request) {
        CouponIqiyiProto.CouponIqiyiDTO dto = (CouponIqiyiProto.CouponIqiyiDTO) toGRpcMessage(request, CouponIqiyiProto.CouponIqiyiDTO.newBuilder());
        CouponIqiyiProto.CouponIqiyiResponse response = blockingStub.add(dto);
        return (BaseResponse) fromGRpcMessage(response, BaseResponse.class, CouponIqiyi.class);
    }

    public BaseResponse update(CouponIqiyi request) {
        CouponIqiyiProto.CouponIqiyiDTO dto = (CouponIqiyiProto.CouponIqiyiDTO) toGRpcMessage(request, CouponIqiyiProto.CouponIqiyiDTO.newBuilder());
        CouponIqiyiProto.CouponIqiyiResponse response = blockingStub.update(dto);
        return (BaseResponse) fromGRpcMessage(response, BaseResponse.class, CouponIqiyi.class);
    }

    public BaseResponse query(CouponIqiyi request) {
        CouponIqiyiProto.CouponIqiyiDTO dto = (CouponIqiyiProto.CouponIqiyiDTO) toGRpcMessage(request, CouponIqiyiProto.CouponIqiyiDTO.newBuilder());
        CouponIqiyiProto.CouponIqiyiResponse response = blockingStub.query(dto);
        return (BaseResponse) fromGRpcMessage(response, BaseResponse.class, CouponIqiyi.class);
    }

    public BaseResponse delete(CouponIqiyi request) {
        CouponIqiyiProto.CouponIqiyiDTO dto = (CouponIqiyiProto.CouponIqiyiDTO) toGRpcMessage(request, CouponIqiyiProto.CouponIqiyiDTO.newBuilder());
        CouponIqiyiProto.CouponIqiyiResponse response = blockingStub.delete(dto);
        return (BaseResponse) fromGRpcMessage(response, BaseResponse.class, CouponIqiyi.class);
    }

    public BaseResponse queryByProductId(CouponIqiyi request) {
        CouponIqiyiProto.CouponIqiyiDTO dto = (CouponIqiyiProto.CouponIqiyiDTO) toGRpcMessage(request, CouponIqiyiProto.CouponIqiyiDTO.newBuilder());
        CouponIqiyiProto.CouponIqiyiListResponse response = blockingStub.queryByProductId(dto);
        return (BaseResponse) fromGRpcMessage(response, BaseResponse.class, CouponIqiyi.class);
    }


}
