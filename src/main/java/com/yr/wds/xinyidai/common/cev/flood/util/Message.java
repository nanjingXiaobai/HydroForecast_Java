package com.yr.wds.xinyidai.common.cev.flood.util;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/* *
 *
 * @Description 前后端统一消息定义协议 Message  之后前后端数据交互都按照规定的类型进行交互
 * {
 *   meta:{"code":code,“msg”:message}
 *   data:{....}
 * }
 * @Date 10:48 2018/2/14
 */
public class Message {

    // 消息头meta 存放状态信息 code message
    private Map<String, Object> meta = new HashMap<String, Object>();
    // 消息内容  存储实体交互数据
    private LinkedHashMap<String, Object> data = new LinkedHashMap<String, Object>();

    public Map<String, Object> getMeta() {
        return meta;
    }

    public Message setMeta(Map<String, Object> meta) {
        this.meta = meta;
        return this;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public Message setData(LinkedHashMap<String, Object> data) {
        this.data = data;
        return this;
    }

    public Message addMeta(String key, Object object) {
        this.meta.put(key, object);
        return this;
    }

    public Message addData(String key, Object object) {
        this.data.put(key, object);
        return this;
    }

    public Message ok(int statusCode, String statusMsg) {
        this.addMeta("success", Boolean.TRUE);
        this.addMeta("code", statusCode);
        this.addMeta("msg", statusMsg);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        this.addMeta("time", sdf.format(new Date()));
        this.addMeta("timestamp", new Timestamp(new Date().getTime()));
        return this;
    }

    public Message error(int statusCode, String statusMsg) {
        this.addMeta("success", Boolean.FALSE);
        this.addMeta("code", statusCode);
        this.addMeta("msg", statusMsg);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        this.addMeta("time", sdf.format(new Date()));
        this.addMeta("timestamp", new Timestamp(new Date().getTime()));
        return this;
    }

    public Message warn(int statusCode, String statusMsg) {
        this.addMeta("code", statusCode);
        this.addMeta("msg", statusMsg);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        this.addMeta("time", sdf.format(new Date()));
        this.addMeta("timestamp", new Timestamp(new Date().getTime()));
        return this;
    }


    @Override
    public String toString() {
        return "Message{" +
                "meta=" + meta +
                ", data=" + data +
                '}';
    }
}
