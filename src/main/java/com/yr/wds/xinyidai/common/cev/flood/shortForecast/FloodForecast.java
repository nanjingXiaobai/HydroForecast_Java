package com.yr.wds.xinyidai.common.cev.flood.shortForecast;

import com.alibaba.fastjson.JSONObject;
import com.nari.slsd.hydro.backend.HFWebService;
import com.yr.wds.xinyidai.common.cev.flood.util.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;

@Service
public class FloodForecast {


    private static int defaulttype;
    @Value("${type}")
    private int type;

    @PostConstruct
    public void init() {
        this.defaulttype = type;
    }

    public static Object serviceSwitch(String query_flag, JSONObject query_params) throws Exception {
        Object return_data = new Object();
        switch (query_flag) {

            /****************************预报计算*************************/
            case "flood_first_init":                        //打开页面
                return_data = FloodResults.firstInit(query_params);
                break;
            case "flood_default":                           //页面静态数据
                return_data = FloodResults.defaultData(query_params);
                break;
            case "flood_init":                              //初始化
                return_data = FloodResults.init(query_params);
                break;
            case "flood_historical":                        //单站历史数据交互
                return_data = FloodResults.getHistoricalData(query_params);
                break;
            case "flood_update":                            //单站历史数据修改
                return_data = FloodResults.updateHistoricalData(query_params);
                break;
            case "flood_calculation":                       //预报计算
                return_data = FloodResults.floodCalculation(query_params,true);
                break;
            case "flood_achievement_display":               //预报成果展示
                return_data = FloodResults.achievementDisplay(query_params);
                break;
            case "flood_updateForecast":                    //更新预报界面序列展示控制策略
                return_data = FloodResults.updateForecast(query_params);
                break;
            case "flood_achievement_Simulation":            //仿真计算
                return_data = FloodResults.simulation(query_params);
                break;
            case "flood_achievement_update":                //修改指定预报方案下预报断面的某一条预报序列
                return_data = FloodResults.achievementUpdate(query_params);
                break;
            case "flood_data_save":                         //结果保存
                return_data = FloodResults.dataSave(query_params);
                break;
            case "flood_rainfall_data":                     //获取某个流域下的所有单元的面雨量历史数据
                return_data = FloodResults.getRainfallByLy(query_params);
                break;
            case "flood_getHisRegionRainMap":               //22.获取某个流域下的所有区间的面雨量历史数据
                return_data = FloodResults.getHisRegionRainMap(query_params);
                break;
            case "flood_rainfall_RainData":                 //获取某一个流域指定预报方案的雨量站实测数据
                return_data = FloodResults.getHisPreRainDataMap(query_params);
                break;
            case "flood_rainfall_source":                   //获取某个流域下所有单元的面雨量预报降雨数据
                return_data = FloodResults.getRainfallBySource(query_params);
                break;
            case "flood_getUnitData":                        //获取某个单元下的所有雨量站信息(实测或预报)
                return_data = FloodResults.getHistoryForecast(query_params);
                break;
            case "flood_waterDeficiency":                     //47.获取当前流域下所有单元的土壤缺水量
                return_data = FloodResults.waterDeficiency(query_params);
                break;
            case "flood_calculateTraffic":                     //48.获取当前流域下所有单元的单元的计算流量
                return_data = FloodResults.calculateTraffic(query_params);
                break;
            case "flood_basinSurfaceRainfall":                 //33.获取流域面雨量 实测or预报
                return_data = FloodResults.basinSurfaceRainfall(query_params);
                break;
            case "flood_updateSurfaceRainfall":                 //修改流域面雨量 实测or预报
                return_data = FloodResults.updateSurfaceRainfall(query_params);
                break;
            case "flood_updateByUnitData":                   //修改某个单元的预报雨量
                return_data = FloodResults.updateByUnitData(query_params);
                break;
            case "flood_modifyRegionForeRainData":
                return_data = FloodResults.modifyRegionForeRainData(query_params);
                break;
            case "flood_allSecForeStaticMap":                    //获取所有断面的预报统计信息
                return_data = FloodResults.getAllSecForeStaticMap(query_params);
                break;
            case "flood_modifyRainfall"://修改实测（面雨量）or（雨量站）
                return_data = FloodResults.updateModifyRainfall(query_params);
                break;
            case "flood_setMode":            //设置单个断面的水库调度模式
                return_data = FloodResults.floodSetMode(query_params);
                break;
            case "flood_regulation":                        //水库调洪调度
                return_data = FloodResults.regulation(query_params);
                break;
            case "flood_reservoirInteraction":                //水库调度交互
                return_data = FloodResults.reservoirInteraction(query_params);
                break;
            case "flood_updateInitialWater":                 //39.水库调度交互-修改水库断面时段初水位
                return_data = FloodResults.updateInitialWater(query_params);
                break;
            case "flood_updateReservoirInteraction":            //修改水库断面未来的出库流量或者是水位
                return_data = FloodResults.updateReservoirInteraction(query_params);
                break;
            case "flood_aSetOfProcesses":
                return_data = FloodResults.aSetOfProcesses(query_params);
                break;
            case "flood_selectDataForCalculation":
                return_data=FloodResults.selectDataForCalculation(query_params);
                break;
            case "flood_carryingCapacity":                                        //进行承洪能力分析
                return_data=FloodResults.carryingCapacity(query_params);
                break;
            case "flood_updatelnitialWater":                                        //进行承洪能力分析
                return_data=FloodResults.updateInitialWater(query_params);
                break;
            case "flood_getBasinFlow": //流域流量统计面板数据
                return_data = FloodResults.getBasinFlow(query_params);
                break;
            case "flood_precisionEvaluation":   //精度评定
                return_data = FloodResults.precisionEvaluation(query_params);
                break;


                /****************************方案管理***************************/
            case "flood_result_forecasters":                //获取数据库中的所有预报员集合
                return_data = new Message().ok(666, "success").addData("data", HFWebService.getAllYbrList(defaulttype));
                break;
            case "flood_result_select":                     //根据筛选条件查询范围内符合的所有预报方案
                return_data = FloodResults.selectResult(query_params);
                break;
            case "flood_result_information":                //根据选中的一条预报方案，展示该方案中所有预报序列的统计信息
                return_data = FloodResults.selectInformation(query_params);
                break;
            case "flood_resultData_byTime":                 //根据选中方案的某一个预报序列返回时间序列值
                return_data = new Message().ok(666, "success").addData("data", FloodResults.getResultDataByTime(query_params));
                break;
            case "flood_doDeleteResult":                    //39.结果管理-删除某一个预报制作时间下的预报方案及数据
                return_data = FloodResults.doDeleteResultList(query_params);
                break;
            case "flood_doDeleteSingleResult":                    //39.结果管理-删除某一个预报制作时间下某一个断面的预报方案及数据
                return_data = FloodResults.doDeleteSingleResult(query_params);
                break;
            case "flood_getPreStationListByUnit":              //获取获取单元下的所有雨量站
                return_data = FloodResults.getPreStationListByUnit(query_params);
                break;
            case "flood_getAllRegionForeRainMap":               //32.获取某个流域下所有区间的面预报降雨序列
                return_data = FloodResults.getAllRegionForeRainMap(query_params);
                break;
            /**************************预报设置*****************************/
            case "flood_getForecastSettings":               //获取设置数据
                return_data = FloodResults.getForecastSettings(query_params);
                break;
            case "flood_getForecastModification":          //修改设置数据
                return_data = FloodResults.getForecastModification(query_params);
                break;
            case "flood_getFloodAnalysisDefaultLevel":      //51.获取承洪能力分析的默认水位
                return_data = FloodResults.getFloodAnalysisDefaultLevel(query_params);
                break;

            /*****************************昌吉结果管理***************************/
            case "flood_getDataByTime":
                return_data = FloodResults.floodGetDataByTime(query_params);
                break;
            case "flood_selectByOne":
                return_data = FloodResults.floodSelectByOne(query_params);
                break;
            case "flood_selectAllDataTypeById":
                return_data = FloodResults.selectAllDataTypeById(query_params);
                break;

             /**************************大模型************************************/
            case "flood_modelComputation":
                return_data = FloodResults.modelComputation(query_params);
                break;


        }
        return return_data;
    }

    public static void main(String[] args) {


        File dir = new File(System.getProperty("user.dir")+"/lib");
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {

                    String name= file.getName();

                    String prefix=name.substring(0,name.lastIndexOf("."));

                    System.out.println("<dependency>\n" +
                            "            <groupId>com.nari</groupId>\n" +
                            "            <artifactId>"+prefix+"</artifactId>\n" +
                            "            <version>version</version>\n" +
                            "            <scope>system</scope>\n" +
                            "            <systemPath>${project.basedir}/lib/"+name+"</systemPath>\n" +
                            "        </dependency>");


                }
            }
        }


    }
}
