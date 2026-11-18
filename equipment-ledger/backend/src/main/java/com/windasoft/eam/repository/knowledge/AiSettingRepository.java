package com.windasoft.eam.repository.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.windasoft.eam.entity.knowledge.AiSetting;
import org.springframework.stereotype.Repository;

@Repository
public class AiSettingRepository {
  private final AiSettingMapper mapper;

  public AiSettingRepository(AiSettingMapper mapper) {
    this.mapper = mapper;
  }

  /** 原配置读取会初始化；锁读只在外层预算/保存事务内使用，锁顺序保持配置在前。 */
  public AiSetting settings(long tenant, boolean lock) {
    mapper.ensure(tenant);
    var q = new LambdaQueryWrapper<AiSetting>().eq(AiSetting::getTenantId, tenant);
    if (lock) q.last("FOR UPDATE");
    return mapper.selectOne(q);
  }

  public void save(AiSetting c, long version) {
    if (mapper.update(
            null,
            new LambdaUpdateWrapper<AiSetting>()
                .eq(AiSetting::getTenantId, c.getTenantId())
                .eq(AiSetting::getRowVersion, version)
                .set(AiSetting::getEnabled, c.getEnabled())
                .set(AiSetting::getBaseUrl, c.getBaseUrl())
                .set(AiSetting::getApiSecret, c.getApiSecret())
                .set(AiSetting::getChatModel, c.getChatModel())
                .set(AiSetting::getEmbeddingModel, c.getEmbeddingModel())
                .set(AiSetting::getMonthlyLimit, c.getMonthlyLimit())
                .set(AiSetting::getWarningPercent, c.getWarningPercent())
                .set(AiSetting::getInputPrice, c.getInputPrice())
                .set(AiSetting::getOutputPrice, c.getOutputPrice())
                .set(AiSetting::getEmbeddingPrice, c.getEmbeddingPrice())
                .set(AiSetting::getVisionModel, c.getVisionModel())
                .set(AiSetting::getAudioModel, c.getAudioModel())
                .set(AiSetting::getVisionInputPrice, c.getVisionInputPrice())
                .set(AiSetting::getVisionOutputPrice, c.getVisionOutputPrice())
                .set(AiSetting::getAudioMinutePrice, c.getAudioMinutePrice())
                .setSql("row_version=row_version+1"))
        != 1) throw new IllegalStateException("AI设置已变更，请刷新");
  }
}
