package com.yr.wds.xinyidai.common.cev.flood.listener;


import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PowerStationListener extends AnalysisEventListener<Map<Integer, String>> {


    // 数据模型类
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public class PowerStation {
        private String name;
        private List<MonthlyData> monthlyData = new ArrayList<>();
        // 省略 getter/setter
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public class MonthlyData {
        private String month;
        private double mean;
        private double cv;
        private double cs;
        // 省略构造器/getter/setter
    }


    private List<PowerStation> STATIONS = new ArrayList<>();
    private PowerStation currentStation;
    private boolean skipHeader = false;
    List<List<String>> List=new ArrayList<>();
    @Override
    public void invoke(Map<Integer, String> row, AnalysisContext context) {

        if(MapUtil.isNotEmpty(row)){

            List<String> rowList=row.values().stream().filter(x-> StrUtil.isNotBlank(x)).collect(Collectors.toList());

            List.add(rowList);
        }

    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        organizeData();
    }



    private void organizeData(){

        if(CollUtil.isNotEmpty(List)){
            //按照行数电站均分
            List<List<List<String>>> mapList =   ListUtil.split(List,13);
            for(List<List<String>> stations:mapList){
                organizeData2(stations);
            }
        }
    }


    private void organizeData2(List<List<String>> stations){


        List<List<String>> value= ListUtil.split(stations.get(0),4);

        List<PowerStation> powerStationList=new ArrayList<>();

        for(List<String> ls:value){

           String name= ls.get(0);

            PowerStation powerStation=new PowerStation();
            powerStation.setName(name);

            powerStationList.add(powerStation);
        }



        for (int i=1;i<stations.size();i++){

            List<List<String>> rows=  ListUtil.split(stations.get(i),4);

            MonthlyData monthlyData=new MonthlyData();
            for(int j=0;j<rows.size();j++){

               List<String> tr= rows.get(j);

                monthlyData.setMonth(tr.get(0));
                monthlyData.setMean( Double.parseDouble(tr.get(1)));
                monthlyData.setCv(Double.parseDouble(tr.get(2)));
                monthlyData.setCs(Double.parseDouble(tr.get(3)));

                PowerStation powerStation=  powerStationList.get(j);

                powerStation.getMonthlyData().add(monthlyData);
            }


        }


        STATIONS.addAll(powerStationList);
    }

    public List<PowerStation> getStations() {
        return STATIONS;
    }


    public static void main(String[] args) {
        String filePath = "D:/QMDownload/微信down/WeChat Files/wxid_6806f6631kj422/FileStorage/File/2025-03/频率分析1.xlsx";

        PowerStationListener listener = new PowerStationListener();
        EasyExcel.read(filePath, listener)
                .sheet()
                .headRowNumber(0)  // 明确指定标题行数
//                .autoTrim(true)    // 自动去除空格
                .doRead();

        listener.getStations().forEach(station -> {
            System.out.println(" 电站: " + station.getName());
            station.getMonthlyData().forEach(data ->
                    System.out.printf(" 月份: %s 均值: %.2f CV: %.2f CS: %.2f%n",
                            data.getMonth(), data.getMean(), data.getCv(), data.getCs()));
        });
    }
}

