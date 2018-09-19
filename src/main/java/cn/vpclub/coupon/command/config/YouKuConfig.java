package cn.vpclub.coupon.command.config;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Created by zhangyingdong on 2018/6/27.
 */
@Component
@ConfigurationProperties(prefix = "app.youku")
@ApiModel("优酷第三方接口信息封装")
public class YouKuConfig {

    @ApiModelProperty("秘钥文件路径")
    private String keyFilepath;

    public String getKeyFilepath() {
        return keyFilepath;
    }

    public void setKeyFilepath(String keyFilepath) {
        this.keyFilepath = keyFilepath;
    }


}
