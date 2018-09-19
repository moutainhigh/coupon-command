package cn.vpclub.coupon.command.rpc;


import cn.vpclub.coupon.api.entity.CouponDetail;
import cn.vpclub.coupon.api.model.request.CouponDetailPageParam;
import cn.vpclub.coupon.api.requests.coupon.CouponMixRequest;
import cn.vpclub.coupon.query.api.CouponDetailProto;
import cn.vpclub.coupon.query.api.CouponDetailProto.CouponDetailPageResponse;
import cn.vpclub.coupon.query.api.CouponDetailProto.CouponDetailDTO;
import cn.vpclub.coupon.query.api.CouponDetailProto.CouponDetailResponse;
import cn.vpclub.coupon.query.api.CouponDetailServiceGrpc.CouponDetailServiceBlockingStub;
import cn.vpclub.moses.core.model.response.BaseResponse;
import cn.vpclub.moses.core.model.response.PageResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static cn.vpclub.moses.utils.grpc.GRpcMessageConverter.fromGRpcMessage;
import static cn.vpclub.moses.utils.grpc.GRpcMessageConverter.toGRpcMessage;

/**
 * <p>
 * 卡券表 rpc层数据传输
 * </p>
 *
 * @author yangqiao
 * @since 2017-12-22
 */
@Service
@Slf4j
@AllArgsConstructor
public class CouponDetailRpcService {

    private CouponDetailServiceBlockingStub blockingStub;

    public BaseResponse add(CouponDetail request) {
        CouponDetailDTO dto = (CouponDetailDTO) toGRpcMessage(request, CouponDetailDTO.newBuilder());
        CouponDetailResponse response = blockingStub.add(dto);
        return (BaseResponse) fromGRpcMessage(response, BaseResponse.class, CouponDetail.class);
    }

    public BaseResponse update(CouponDetail request) {
        CouponDetailDTO dto = (CouponDetailDTO) toGRpcMessage(request, CouponDetailDTO.newBuilder());
        CouponDetailResponse response = blockingStub.update(dto);
        return (BaseResponse) fromGRpcMessage(response, BaseResponse.class, CouponDetail.class);
    }

    public BaseResponse query(CouponDetail request) {
        CouponDetailDTO dto = (CouponDetailDTO) toGRpcMessage(request, CouponDetailDTO.newBuilder());
        CouponDetailResponse response = blockingStub.query(dto);
        return (BaseResponse) fromGRpcMessage(response, BaseResponse.class, CouponDetail.class);
    }

    public BaseResponse delete(CouponDetail request) {
        CouponDetailDTO dto = (CouponDetailDTO) toGRpcMessage(request, CouponDetailDTO.newBuilder());
        CouponDetailResponse response = blockingStub.delete(dto);
        return (BaseResponse) fromGRpcMessage(response, BaseResponse.class, CouponDetail.class);
    }

    public BaseResponse findCouponDetailToRelease(CouponMixRequest request) {
        CouponDetailProto.CouponDetailMixRequest dto = (CouponDetailProto.CouponDetailMixRequest) toGRpcMessage(request,
                CouponDetailProto.CouponDetailMixRequest.newBuilder());
        CouponDetailResponse response = blockingStub.findCouponDetailToRelease(dto);
        return (BaseResponse) fromGRpcMessage(response, BaseResponse.class, CouponDetail.class);
    }
    
    public PageResponse page(CouponDetailPageParam request) {
        CouponDetailProto.CouponDetailRequest dto = (CouponDetailProto.CouponDetailRequest) toGRpcMessage(request,
                CouponDetailProto.CouponDetailRequest.newBuilder());
        CouponDetailPageResponse listResponse = blockingStub.page(dto);
        return (PageResponse) fromGRpcMessage(listResponse, PageResponse.class, CouponDetail.class);
    }
}
