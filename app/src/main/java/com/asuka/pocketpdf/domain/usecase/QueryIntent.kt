package com.asuka.pocketpdf.domain.usecase

/**
 * 查询意图分类结果。
 *
 * - [FULL_DOCUMENT]: 全文总结、结构分析、核心观点等全局分析请求
 * - [TOP_K]: 日期、金额、人物、条款、定义等定点查找问题
 */
enum class QueryIntent {
    FULL_DOCUMENT,
    TOP_K
}
