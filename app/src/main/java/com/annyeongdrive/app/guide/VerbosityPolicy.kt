package com.annyeongdrive.app.guide

/**
 * 몇 번째로 만난 규칙에 어떤 문장 역할을 넣을지 정한다.
 * encounter는 1부터 센다(1 = 처음 만남).
 */
fun interface VerbosityPolicy {
    fun roles(rule: Rule, encounter: Int): List<SentenceRole>
}

/**
 * 기본 정책: 규칙을 처음 detailedCount번 만날 때는 detailed, 그 뒤로는 brief.
 * 기본값은 "처음 1번은 CORE + CONSEQUENCE(2문장), 이후 CORE(1문장)"이다.
 * 규칙마다 다르게 하려면 overrides에 그 규칙의 정책을 넣는다.
 */
class EncounterBasedPolicy(
    private val detailedCount: Int = 1,
    private val detailed: List<SentenceRole> = listOf(SentenceRole.CORE, SentenceRole.CONSEQUENCE),
    private val brief: List<SentenceRole> = listOf(SentenceRole.CORE),
    private val overrides: Map<Rule, VerbosityPolicy> = emptyMap(),
) : VerbosityPolicy {
    override fun roles(rule: Rule, encounter: Int): List<SentenceRole> =
        overrides[rule]?.roles(rule, encounter)
            ?: if (encounter <= detailedCount) detailed else brief
}
