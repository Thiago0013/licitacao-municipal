-- =============================================================================
-- MIGRAÇÃO v3: Coluna "municipio" em diario_chunks e leis
-- Execute este script UMA VEZ no banco antes de subir a versão corrigida.
-- =============================================================================

-- ─────────────────────────────────────────────────────────────────────────────
-- TABELA: diario_chunks
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE diario_chunks ADD COLUMN IF NOT EXISTS municipio VARCHAR(255);
CREATE INDEX IF NOT EXISTS idx_diario_chunks_municipio ON diario_chunks (municipio);
CREATE INDEX IF NOT EXISTS idx_diario_chunks_municipio_data ON diario_chunks (municipio, data_diario);

-- ─────────────────────────────────────────────────────────────────────────────
-- TABELA: leis  ← NOVA coluna
-- ─────────────────────────────────────────────────────────────────────────────
--  NULL  → lei federal/estadual (Lei 8.666, Lei 14.133, etc.) — aparece para todos
--  texto → lei municipal específica — aparece apenas para o município em questão
ALTER TABLE leis ADD COLUMN IF NOT EXISTS municipio VARCHAR(255);
CREATE INDEX IF NOT EXISTS idx_leis_municipio ON leis (municipio);

-- ─────────────────────────────────────────────────────────────────────────────
-- ATUALIZAÇÃO AUTOMÁTICA DE LEIS LEGADAS
-- ─────────────────────────────────────────────────────────────────────────────
-- Não precisa listar municípios manualmente.
-- O sistema detecta automaticamente qualquer cidade do Ceará
-- a partir do titulo e conteudo de cada lei.
--
-- Após rodar este script, chame o endpoint de admin:
--
--   POST /api/leis/auto-taggear
--
-- Ele percorre todas as leis com municipio IS NULL, detecta o município
-- pelo titulo + conteudo, e atualiza o banco.
-- Leis federais/estaduais (Lei 8.666, Lei 14.133, etc.) continuam com NULL — correto.

-- ─────────────────────────────────────────────────────────────────────────────
-- VERIFICAÇÃO (opcional — rode para confirmar que tudo ficou certo)
-- ─────────────────────────────────────────────────────────────────────────────

-- Ver leis por município:
-- SELECT municipio, COUNT(*) FROM leis GROUP BY municipio ORDER BY municipio;

-- Ver leis sem município (deveriam ser apenas federais/estaduais):
-- SELECT id, titulo, referencia FROM leis WHERE municipio IS NULL ORDER BY titulo;

-- Ver chunks por município:
-- SELECT municipio, COUNT(*) FROM diario_chunks GROUP BY municipio ORDER BY municipio;
