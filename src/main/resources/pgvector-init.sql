-- pgvector 扩展（必须最先创建）
CREATE EXTENSION IF NOT EXISTS vector;

-- 表由 PgVectorEmbeddingStore 自动创建（createTable=true），
-- 不需要手动建表，否则列名可能不匹配内置 SQL
