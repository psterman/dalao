package com.example.aifloatingball.service

/**
 * 测试文件，用于验证if表达式语法
 */
class TestFix {
    fun testIfExpression(query: String, engineKey: String): String {
        // 测试1：正常的if表达式
        val test1 = if (query.isBlank()) {
            "空查询"
        } else {
            "查询: $query"
        }
        
        // 测试2：嵌套的if表达式
        val test2 = if (query.isBlank()) {
            if (engineKey.isBlank()) {
                "空查询和空引擎"
            } else {
                "空查询但有引擎: $engineKey"
            }
        } else {
            "查询: $query, 引擎: $engineKey"
        }
        
        return "$test1, $test2"
    }
} 