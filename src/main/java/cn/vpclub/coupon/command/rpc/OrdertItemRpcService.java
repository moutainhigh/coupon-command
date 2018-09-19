package cn.vpclub.coupon.command.rpc;

import cn.vpclub.moses.common.api.dto.order.OrderItemDTO;
import cn.vpclub.moses.core.model.response.BaseResponse;
import cn.vpclub.moses.order.query.api.OrderItemProto;
import cn.vpclub.moses.order.query.api.OrderItemServiceGrpc;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static cn.vpclub.moses.utils.grpc.GRpcMessageConverter.fromGRpcMessage;

/**
 * Created by Administrator on 2017/2/22.
 */
@Service
@Slf4j
@AllArgsConstructor
public class OrdertItemRpcService {

    private OrderItemServiceGrpc.OrderItemServiceBlockingStub orderItemServiceBlockingStub;

    public BaseResponse query(Long id) {
        OrderItemProto.OrderItemDTO dto = OrderItemProto.OrderItemDTO.newBuilder().setId(id)
                .build();
        OrderItemProto.OrderItemResponse response = orderItemServiceBlockingStub.query(dto);
        return (BaseResponse) fromGRpcMessage(response, BaseResponse.class, OrderItemDTO.class);
    }

    public OrderItemProto.OrderItemResponse checkCouponOrder(OrderItemProto.CheckCouponOrderDTO checkCouponOrderDTO) {
        return orderItemServiceBlockingStub.checkCouponOrder(checkCouponOrderDTO);
    }

}
