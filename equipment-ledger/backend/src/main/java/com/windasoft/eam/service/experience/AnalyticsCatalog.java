package com.windasoft.eam.service.experience;

import java.util.*;

public final class AnalyticsCatalog {
  private AnalyticsCatalog() {}

  public record Metric(
      String id,
      String name,
      String domain,
      String chart,
      String period,
      String unit,
      String definition,
      boolean money) {}

  public static final List<Metric> ALL =
      List.of(
          new Metric(
              "repair_ranking",
              "设备报修次数排名",
              "repair",
              "bar",
              "last90",
              "次",
              "按报修时间统计有效工单；一张工单只计一次，不代表独立故障次数。",
              false),
          new Metric(
              "repair_trend",
              "报修与完成月度趋势",
              "repair",
              "line",
              "last12months",
              "单",
              "新增按报修时间；完成按验收通过时间。两条曲线并非同一批工单。",
              false),
          new Metric(
              "fault_types",
              "故障类型分布",
              "repair",
              "bar",
              "last90",
              "单",
              "按周期内有效报修工单统计；历史无法唯一关联的类型列入未归类。",
              false),
          new Metric(
              "fault_causes",
              "故障原因分布",
              "repair",
              "bar",
              "last90",
              "单",
              "按周期内已验收完成工单统计；未填写和历史未归类分别展示。",
              false),
          new Metric(
              "personnel",
              "人员工作量与质量",
              "repair",
              "table",
              "last7",
              "单",
              "完成量按最终验收时负责人；未完成量截至当前；评分显示样本数，协同不重复计为负责人完成。",
              false),
          new Metric(
              "maintenance_execution",
              "保养执行情况",
              "maintenance",
              "execution",
              "follow",
              "任务",
              "仅统计计划保养日在周期内的已生成任务；按时提交与验收完成分别计算，当前结果回看。",
              false),
          new Metric(
              "maintenance_departments",
              "部门保养执行对比",
              "maintenance",
              "departments",
              "follow",
              "任务",
              "人工取消单列；系统过期作废仍计入应执行任务；未来未到期任务不拉低按时提交率。",
              false),
          new Metric(
              "inspection_execution",
              "点巡检执行情况",
              "inspection",
              "execution",
              "follow",
              "任务",
              "按计划执行时间归属，区分日常点检与周期巡检；按有效提交和审核记录统计。",
              false),
          new Metric(
              "anomaly_ranking",
              "设备异常次数排名",
              "anomaly",
              "bar",
              "last90",
              "次",
              "按异常登记时间统计记录，作废记录排除；后续处理不重复计数，不与报修次数相加。",
              false),
          new Metric(
              "material_trend",
              "物料实际消耗月趋势",
              "material",
              "line",
              "last12months",
              "金额",
              "仅统计使用与安装事实，换下不冲减原消耗；币种分开，不使用参考价。",
              true),
          new Metric(
              "equipment_material_ranking",
              "设备物料消耗金额排名",
              "material",
              "bar",
              "follow",
              "金额",
              "设备与来源仓库授权交集；公共、历史未归属及设备归属不完整用料单列，不自动均摊。",
              true),
          new Metric(
              "material_ranking",
              "物料消耗金额排名",
              "material",
              "bar",
              "follow",
              "金额",
              "按料号汇总实际使用成本，已出库未用不计消耗；不跨单位累计数量。",
              true));

  public static Metric require(String id) {
    return ALL.stream()
        .filter(m -> m.id().equals(id))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("未知统计指标"));
  }

  public static boolean contains(String id) {
    return ALL.stream().anyMatch(m -> m.id().equals(id));
  }
}
