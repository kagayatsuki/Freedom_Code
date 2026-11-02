package com.coding.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.coding.agent.domain.R;
import com.coding.agent.domain.po.User;
import com.coding.agent.mapper.TUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import sun.misc.Unsafe;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 用户每日用量限流服务
 * <p>
 * 等级配额：
 * 0-禁用：不允许任何操作
 * 1-普通用户：3项目/天，6轮对话/天
 * 2-升级版：10项目/天，30轮对话/天
 * 3-专业版：50项目/天，200轮对话/天
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final JdbcTemplate jdbcTemplate;
    private final TUserMapper userMapper;

    private static final int[][] QUOTAS = {
            // {projects/day, modifies/day}
            {0, 0},    // 0-禁用
            {3, 6},    // 1-普通用户
            {10, 30},  // 2-升级版
            {50, 200}, // 3-专业版
    };

    /** 每日 LLM token 上限（输入+输出）：0-禁用 1-免费 2-升级版 3-专业版 */
    private static final long[] TOKEN_QUOTAS = {
            0L,             // 0-禁用
            500_000L,       // 1-普通用户（免费）：每天 500k token
            2_000_000L,     // 2-升级版：每天 200 万 token
            Long.MAX_VALUE, // 3-专业版：不限
    };

    /**
     * 配额信息（供前端展示）。
     * tokenLimit 为 -1 表示不限（专业版），避免 Long.MAX_VALUE 超出前端安全整数范围。
     */
    public record QuotaInfo(int status, String tierName,
                            int projectLimit, int projectUsed, int projectRemaining,
                            int modifyLimit, int modifyUsed, int modifyRemaining,
                            long tokenLimit, long tokenUsed, long tokenRemaining) {}

    /**
     * 仅检查配额是否足够（不递增，成功后才递增）
     *
     * @return null 表示通过；非 null 返回错误消息
     */
    public String checkQuota(Long userId, boolean isNewProject) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return "用户不存在";
        }
        int status = user.getStatus() != null ? user.getStatus() : 1;
        if (status < 0 || status > 3) status = 1;
        if (status == 0) {
            return "账号已被禁用，无法使用";
        }

        int[] quota = QUOTAS[status];
        int projectLimit = quota[0];
        int modifyLimit = quota[1];

        LocalDate today = LocalDate.now();

        // 确保今日记录存在
        String upsertSql = """
                INSERT INTO user_daily_usage (user_id, usage_date, project_count, modify_count)
                VALUES (?, ?, 0, 0)
                ON DUPLICATE KEY UPDATE id = id
                """;
        jdbcTemplate.update(upsertSql, userId, today);

        String sql = "SELECT project_count, modify_count FROM user_daily_usage WHERE user_id = ? AND usage_date = ?";
        Map<String, Object> row = jdbcTemplate.queryForMap(sql, userId, today);
        int projectCount = ((Number) row.get("project_count")).intValue();
        int modifyCount = ((Number) row.get("modify_count")).intValue();

        if (isNewProject && projectCount >= projectLimit) {
            return String.format("今日新建项目已达上限（%d/%d），请明天再试", projectCount, projectLimit);
        }
        if (modifyCount >= modifyLimit) {
            return String.format("今日对话轮数已达上限（%d/%d），请明天再试", modifyCount, modifyLimit);
        }

        return null; // 通过
    }

    /**
     * 递增用量计数（仅在项目成功后调用）
     */
    public void incrementUsage(Long userId, boolean isNewProject) {
        LocalDate today = LocalDate.now();

        if (isNewProject) {
            String sql = """
                    UPDATE user_daily_usage
                    SET project_count = project_count + 1, modify_count = modify_count + 1
                    WHERE user_id = ? AND usage_date = ?
                    """;
            jdbcTemplate.update(sql, userId, today);
        } else {
            String sql = """
                    UPDATE user_daily_usage
                    SET modify_count = modify_count + 1
                    WHERE user_id = ? AND usage_date = ?
                    """;
            jdbcTemplate.update(sql, userId, today);
        }

        log.info("[限流] userId={} 用量+1, isNewProject={}", userId, isNewProject);
    }

    /**
     * 检查今日 token 用量是否已达上限（仅检查不递增，成功后由 addTokenUsage 累加）
     *
     * @return null 表示通过；非 null 返回错误消息
     */
    public String checkTokenQuota(Long userId) {
        User user = userMapper.selectById(userId);
        int status = (user != null && user.getStatus() != null) ? user.getStatus() : 1;
        if (status < 0 || status > 3) status = 1;

        long limit = TOKEN_QUOTAS[status];
        if (limit == Long.MAX_VALUE) {
            return null; // 专业版不限
        }
        long used = getTokenUsed(userId);
        if (used >= limit) {
            return String.format("今日 token 用量已达上限（%d/%d），请明天再试", used, limit);
        }
        return null;
    }

    /**
     * 累加某用户今日消耗的 LLM token（任务成功后调用）
     */
    public void addTokenUsage(Long userId, long tokens) {
        if (userId == null || tokens <= 0) return;
        LocalDate today = LocalDate.now();
        String sql = """
                INSERT INTO user_daily_usage (user_id, usage_date, project_count, modify_count, token_count)
                VALUES (?, ?, 0, 0, ?)
                ON DUPLICATE KEY UPDATE token_count = token_count + ?
                """;
        jdbcTemplate.update(sql, userId, today, tokens, tokens);
        log.info("[限流] userId={} 今日 token +{}", userId, tokens);
    }

    /**
     * 查询某用户今日已消耗的 LLM token
     */
    public long getTokenUsed(Long userId) {
        LocalDate today = LocalDate.now();
        try {
            String sql = "SELECT token_count FROM user_daily_usage WHERE user_id = ? AND usage_date = ?";
            Map<String, Object> row = jdbcTemplate.queryForMap(sql, userId, today);
            return ((Number) row.get("token_count")).longValue();
        } catch (Exception e) {
            return 0L;
        }
    }

    /**
     * 查询用户今日用量和配额信息（供前端展示）
     */
    public QuotaInfo getQuotaInfo(Long userId) {
        User user = userMapper.selectById(userId);
        int status = (user != null && user.getStatus() != null) ? user.getStatus() : 1;
        if (status < 0 || status > 3) status = 1;

        int[] quota = QUOTAS[status];
        String[] tierNames = {"禁用", "普通用户", "升级版", "专业版"};

        LocalDate today = LocalDate.now();
        int projectUsed = 0;
        int modifyUsed = 0;
        long tokenUsed = 0L;

        try {
            String sql = "SELECT project_count, modify_count, token_count FROM user_daily_usage WHERE user_id = ? AND usage_date = ?";
            Map<String, Object> row = jdbcTemplate.queryForMap(sql, userId, today);
            projectUsed = ((Number) row.get("project_count")).intValue();
            modifyUsed = ((Number) row.get("modify_count")).intValue();
            Object tokenCount = row.get("token_count");
            tokenUsed = tokenCount != null ? ((Number) tokenCount).longValue() : 0L;
        } catch (Exception e) {
            // 今天还没有记录，用量为 0
        }

        long rawTokenLimit = TOKEN_QUOTAS[status];
        long tokenLimit = rawTokenLimit == Long.MAX_VALUE ? -1 : rawTokenLimit;
        long tokenRemaining = rawTokenLimit == Long.MAX_VALUE ? -1 : Math.max(0, rawTokenLimit - tokenUsed);

        return new QuotaInfo(
                status, tierNames[status],
                quota[0], projectUsed, Math.max(0, quota[0] - projectUsed),
                quota[1], modifyUsed, Math.max(0, quota[1] - modifyUsed),
                tokenLimit, tokenUsed, tokenRemaining
        );
    }

    public Long getUserIdByUsername(String username) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username)
        );
        return user != null ? user.getId() : null;
    }

}
