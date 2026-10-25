# -*- coding: utf-8 -*-
"""Generate 10 additional evaluation records QR-2026-1006..1015 (desensitized).

7 records mirror newly generated case types (retrieval should hit);
3 are out-of-knowledge-base generalization probes.
"""
import json
import os

RECORDS = [
    dict(recordId="QR-2026-1006", productCode="ZK-A7 控制板组件", batchNo="P2026-1101-01",
         process="高温老化", occurrenceTime="2026-10-23 09:40",
         defectDescription="高温老化 48h 后复测，4 块板 0402 电容容量衰降 28%-41%（超出 ±20% 容差），位置集中于分板边附近",
         inspectionData="容量复测 240 只，异常 11 只；断面分析 2 只确认弯曲裂纹贯穿介质层；失效件长轴均平行于分板边",
         quantity={"defectives": 4, "batchTotal": 60},
         detectedBy="老化后复测电测", disposition="该批停发出库，分板工序暂停，待 8D 分析",
         severity="B 级"),
    dict(recordId="QR-2026-1007", productCode="ZK-B4 功率模块", batchNo="P2026-1028-02",
         process="装配＋功能老化", occurrenceTime="2026-10-20 15:20",
         defectDescription="功能老化第 2 小时起 MOSFET 批量失效，集中于同一手插件工位生产的板号段，失效表现为输出短路",
         inspectionData="失效 9/210 只，全部同一工位段；失效分析 3 只确认栅极氧化层击穿；该工位腕带接地电阻实测无穷大",
         quantity={"defectives": 9, "batchTotal": 210},
         detectedBy="老化监控报警", disposition="该工位产品全数隔离，工位停线整改，待 8D 分析",
         severity="A 级"),
    dict(recordId="QR-2026-1008", productCode="ZK-C5 通信板", batchNo="P2026-1030-05",
         process="三防涂覆", occurrenceTime="2026-10-24 11:15",
         defectDescription="三防涂覆后荧光检查发现 J3、J5 连接器接触件上有漆料流入，测厚局部最高 118μm（标准 30-80μm）",
         inspectionData="流入接触件 6 块板；接触电阻复测 2 只超差；遮蔽夹具角部变形目视可见",
         quantity={"defectives": 6, "batchTotal": 88},
         detectedBy="荧光检查全检", disposition="流入件隔离待清洗重涂，遮蔽夹具停用，待 8D 分析",
         severity="B 级"),
    dict(recordId="QR-2026-1009", productCode="ZK-D2 图像处理板", batchNo="P2026-1025-01",
         process="AOI 检验（流出）", occurrenceTime="2026-10-21 16:48",
         defectDescription="下工序复检发现 3 块板钽电容极性反接流出，追溯 AOI 记录显示该料号全部通过，怀疑程序检查项被关闭",
         inspectionData="流出率 3/150；导出 AOI 当前程序库，该料号极性检查窗口参数为 disabled；程序版本上周曾回退过一次",
         quantity={"defectives": 3, "batchTotal": 150},
         detectedBy="下工序复检发现", disposition="当日产品全数复检，AOI 程序冻结版本，待 8D 分析",
         severity="A 级"),
    dict(recordId="QR-2026-1010", productCode="ZK-C6 主控单元", batchNo="P2026-1027-03",
         process="环境试验", occurrenceTime="2026-10-19 08:30",
         defectDescription="低温 -40℃ 保持阶段 4 块板时钟丢失，恢复常温后自恢复，示波器确认晶振输出停振",
         inspectionData="停振 4/20；晶振引脚负阻测试常温裕量 2.1 倍，低温实测仅 1.2 倍（要求 ≥2 倍）；常温功能测试历史全通过",
         quantity={"defectives": 4, "batchTotal": 20},
         detectedBy="低温试验监控", disposition="该批退出试验，低温批次全数晶振裕量复测，待 8D 分析",
         severity="B 级"),
    dict(recordId="QR-2026-1011", productCode="ZK-B4 功率模块", batchNo="P2026-1029-04",
         process="X-Ray 检验", occurrenceTime="2026-10-22 14:05",
         defectDescription="X-Ray 全检发现 0.8mm 节距 BGA 中心区域焊球空洞率 34%（标准 ≤25%），批量性分布",
         inspectionData="超标 12/95 块；空洞集中于中心第 3-5 圈焊球；当前回流曲线无排气平台段；X-Ray 阈值参数上月被调整为 35%",
         quantity={"defectives": 12, "batchTotal": 95},
         detectedBy="X-Ray 全检", disposition="超标板隔离待切片确认，炉温曲线冻结，待 8D 分析",
         severity="B 级"),
    dict(recordId="QR-2026-1012", productCode="ZK-A7 控制板组件", batchNo="P2026-1031-02",
         process="清洗", occurrenceTime="2026-10-24 17:10",
         defectDescription="盐雾试验后板面出现白色树枝晶状产物，离子污染度复测 1.9μgNaCl/cm²（标准 ≤1.5），同一清洗线当周产品",
         inspectionData="污染超标的 3 批次；清洗线末级水槽电导率记录缺失 2 天；盐雾 48h 后绝缘电阻下降一个量级",
         quantity={"defectives": 3, "batchTotal": 45},
         detectedBy="盐雾试验后检查", disposition="当周清洗线产品全部追回复测，清洗线停线，待 8D 分析",
         severity="A 级"),
    dict(recordId="QR-2026-1013", productCode="ZK-E2 显示终端", batchNo="P2026-1026-01",
         process="整机老化", occurrenceTime="2026-10-23 10:55",
         defectDescription="整机老化 12h 后液晶屏出现竖条纹花屏，断电静置后恢复，复现率随老化时间升高",
         inspectionData="花屏 5/80 台；LVDS 接口信号完整性常温合格；屏温监测老化中最高 68℃（规格上限 70℃）；同型号屏此前无类似记录",
         quantity={"defectives": 5, "batchTotal": 80},
         detectedBy="老化巡检", disposition="花屏整机隔离，该批暂缓交付，待 8D 分析",
         severity="B 级"),
    dict(recordId="QR-2026-1014", productCode="ZK-E2 显示终端", batchNo="P2026-1023-02",
         process="环境试验（振动）", occurrenceTime="2026-10-21 09:20",
         defectDescription="随机振动试验中 2 台整机出现机内异响，试验后拆解确认散热器固定簧片松动脱开",
         inspectionData="松动 2/16 台；簧片紧固力实测 6N·m（设计要求 9±1N·m）；同批簧片尺寸抽测在公差内",
         quantity={"defectives": 2, "batchTotal": 16},
         detectedBy="振动试验人员听感发现", disposition="该批整机组装工序复查，待 8D 分析",
         severity="B 级"),
    dict(recordId="QR-2026-1015", productCode="ZK-C6 主控单元", batchNo="P2026-1024-01",
         process="软件测试", occurrenceTime="2026-10-22 20:40",
         defectDescription="72h 连续拷机中 3 台主控周期性复位（约 26h 一次），复位前日志出现看门狗喂狗超时告警",
         inspectionData="复位 3/60 台；故障时段 CPU 占用率峰值 97%；喂狗任务优先级为最低级；内存曲线稳定无泄漏特征",
         quantity={"defectives": 3, "batchTotal": 60},
         detectedBy="自动测试平台日志", disposition="故障机保留现场数据，该批软件版本冻结，待 8D 分析",
         severity="B 级"),
]


def main():
    out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "records")
    for rec in RECORDS:
        path = os.path.join(out, rec["recordId"] + ".json")
        with open(path, "w", encoding="utf-8") as f:
            json.dump(rec, f, ensure_ascii=False, indent=2)
        print("wrote", path)


if __name__ == "__main__":
    main()
