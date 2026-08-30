package com.classdeduction.system.modules.base.service;

import com.classdeduction.system.modules.base.dto.ProductGradeConfigSaveRequest;
import com.classdeduction.system.modules.base.vo.ProductGradeConfigVO;

import java.util.List;

/**
 * 课程产品维度的 grade 名称与显示顺序配置服务。
 */
public interface ProductGradeConfigService {

    /**
     * 列出某课程产品下全部 grade 配置：配置表活跃行 ∪ 价格行自动发现的 grade_band，
     * 按 displayOrder → 字典序排序。未入库项 id 为 null。
     */
    List<ProductGradeConfigVO> listGradeConfigs(Long productId);

    /**
     * 批量保存某课程产品的 grade 配置（改名 + 排序）。
     * 单事务内：重名校验 → 级联更新价格行/班级/老师费率的 grade_band → 全量重建配置行。
     * 历史订单快照不动。
     */
    void saveGradeConfigs(Long productId, ProductGradeConfigSaveRequest request);
}
