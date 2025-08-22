package com.hidewnd.costing.handler;

import cn.hutool.core.util.StrUtil;
import com.hidewnd.common.base.CommonException;
import com.hidewnd.common.base.response.R;
import com.hidewnd.costing.dto.Formulas;
import com.hidewnd.costing.service.Jx3BoxRemote;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DefaultFormulaParseAdapterImpl implements FormulaParseAdapter {
    private Jx3BoxRemote jx3BoxRemote;

    @Autowired
    public void setJx3BoxRemote(Jx3BoxRemote jx3BoxRemote) {
        this.jx3BoxRemote = jx3BoxRemote;
    }

    @Override
    public Formulas parse(String formulaName) {
        Formulas formulas = jx3BoxRemote.queryFormulasAndNumber(null, formulaName);
        if (formulas == null) {
            throw new CommonException(R.CODE_PARAM_ERROR, StrUtil.format("配方不存在: {}", formulaName));
        }
        return formulas;
    }
}
