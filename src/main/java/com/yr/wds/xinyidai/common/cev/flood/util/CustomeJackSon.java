package com.yr.wds.xinyidai.common.cev.flood.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.boot.jackson.JsonComponent;

import java.io.IOException;
import java.text.DecimalFormat;

@JsonComponent
public class CustomeJackSon {

    /**
     * 自定义序列化器,格式化数值
     */
    public static class MySerializer extends JsonSerializer<Double> {
        private DecimalFormat df = new DecimalFormat("##.00");
        /**
         * 序列化操作,继承JsonSerializer，重写Serialize函数
         */
        @Override
        public void serialize(Double value, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
            if(null != value){
                jsonGenerator.writeString(value.toString());
            }

        }
    }


}
