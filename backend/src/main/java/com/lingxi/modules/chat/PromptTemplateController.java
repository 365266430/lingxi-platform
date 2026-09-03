package com.lingxi.modules.chat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lingxi.common.api.Result;
import com.lingxi.common.exception.BizException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "快捷提示词")
@RestController
@RequiredArgsConstructor
public class PromptTemplateController {

    private final SysPromptTemplateMapper templateMapper;

    @Operation(summary = "启用的模板列表（对话页快捷指令）")
    @GetMapping("/api/prompt-templates")
    public Result<List<SysPromptTemplate>> list() {
        return Result.ok(templateMapper.selectList(new LambdaQueryWrapper<SysPromptTemplate>()
                .eq(SysPromptTemplate::getEnabled, 1)
                .orderByAsc(SysPromptTemplate::getSort)));
    }

    @Operation(summary = "新增模板（管理员）")
    @PostMapping("/api/admin/prompt-templates")
    public Result<SysPromptTemplate> create(@RequestBody SysPromptTemplate template) {
        if (template.getTitle() == null || template.getTitle().isBlank()) {
            throw new BizException("模板标题不能为空");
        }
        if (template.getContent() == null || template.getContent().isBlank()) {
            throw new BizException("模板内容不能为空");
        }
        template.setId(null);
        template.setEnabled(template.getEnabled() == null ? 1 : template.getEnabled());
        template.setSort(template.getSort() == null ? 99 : template.getSort());
        templateMapper.insert(template);
        return Result.ok(template);
    }

    @Operation(summary = "更新模板（管理员）")
    @PutMapping("/api/admin/prompt-templates/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody SysPromptTemplate template) {
        template.setId(id);
        templateMapper.updateById(template);
        return Result.ok();
    }

    @Operation(summary = "删除模板（管理员）")
    @DeleteMapping("/api/admin/prompt-templates/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        templateMapper.deleteById(id);
        return Result.ok();
    }
}
