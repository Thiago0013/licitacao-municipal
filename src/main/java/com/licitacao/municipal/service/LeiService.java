package com.licitacao.municipal.service;

import com.licitacao.municipal.model.Lei;
import com.licitacao.municipal.repository.LeiRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeiService {

    private final LeiRepository leiRepository;
    private final EmbeddingService embeddingService;
    private final MunicipioExtractorService municipioExtractorService;

    // ─────────────────────────────────────────────────────────────────────────
    // CREATE
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Cadastra uma lei.
     *
     * Se 'municipio' for passado explicitamente (ex: "Icó", "Fortaleza"), usa ele.
     * Se não for passado (null/vazio), o sistema tenta detectar o município
     * automaticamente a partir do titulo + conteudo da própria lei.
     * Se mesmo assim não achar, salva como NULL = lei federal/estadual
     * (aparece para todos os municípios).
     *
     * Isso garante que funciona para qualquer cidade do Ceará, não apenas
     * Granja e Araripe.
     */
    public Lei cadastrarLei(String titulo, String referencia, String conteudo, String municipio) {
        String municipioFinal = resolverMunicipio(municipio, titulo, conteudo);

        double[] vetor = embeddingService.gerarVetorLocal(titulo + " " + conteudo);
        String embeddingString = Arrays.toString(vetor);
        leiRepository.salvarLeiManual(titulo, referencia, conteudo, municipioFinal, embeddingString);

        String escopo = municipioFinal != null ? municipioFinal : "FEDERAL/ESTADUAL (sem município detectado)";
        log.info("Lei cadastrada: '{}' | Município: {}", titulo, escopo);
        return new Lei();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // READ
    // ─────────────────────────────────────────────────────────────────────────

    public List<Lei> listarTodas() {
        return leiRepository.findAll();
    }

    public Lei buscarPorId(Long id) {
        return leiRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Lei não encontrada com ID: " + id));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UPDATE
    // ─────────────────────────────────────────────────────────────────────────

    public Lei atualizarLei(Long id, String titulo, String referencia, String conteudo, String municipio) {
        buscarPorId(id);

        String municipioFinal = resolverMunicipio(municipio, titulo, conteudo);

        log.info("Atualizando lei ID: {} | Município: {}", id,
                municipioFinal != null ? municipioFinal : "FEDERAL/ESTADUAL");
        double[] vetorNovo = embeddingService.gerarVetorLocal(titulo + " " + conteudo);
        leiRepository.atualizarLeiManual(id, titulo, referencia, conteudo, municipioFinal,
                Arrays.toString(vetorNovo));
        return buscarPorId(id);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DELETE
    // ─────────────────────────────────────────────────────────────────────────

    public void deletarLei(Long id) {
        buscarPorId(id);
        leiRepository.deleteById(id);
        log.info("Lei deletada ID: {}", id);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AUTO-TAGGER: corrige leis antigas sem município
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Percorre TODAS as leis do banco que estão sem município (municipio IS NULL)
     * e tenta detectar o município automaticamente a partir do titulo + conteudo.
     *
     * Funciona para qualquer cidade do Ceará — não precisa listar manualmente
     * "Granja", "Araripe", "Icó", "Fortaleza", etc.
     *
     * Chame via POST /api/leis/auto-taggear (ver LeiController).
     *
     * @return relatório de quantas leis foram atualizadas e quais ficaram sem município.
     */
    public TaggingResult autoTaggearMunicipio() {
        List<Lei> semMunicipio = leiRepository.findByMunicipioIsNull();

        int atualizadas = 0;
        int semDeteccao = 0;

        for (Lei lei : semMunicipio) {
            String textoParaAnalise = (lei.getTitulo() != null ? lei.getTitulo() : "")
                    + " " + (lei.getConteudo() != null ? lei.getConteudo() : "");

            String municipioDetectado = municipioExtractorService.extrairMunicipio(textoParaAnalise);

            if (municipioDetectado != null) {
                // Reutiliza o vetor existente — não precisa recalcular o embedding
                double[] vetor = embeddingService.gerarVetorLocal(lei.getTitulo() + " " + lei.getConteudo());
                leiRepository.atualizarLeiManual(
                        lei.getId(),
                        lei.getTitulo(),
                        lei.getReferencia(),
                        lei.getConteudo(),
                        municipioDetectado,
                        Arrays.toString(vetor)
                );
                log.info("Lei ID {} taggeada como '{}'", lei.getId(), municipioDetectado);
                atualizadas++;
            } else {
                log.info("Lei ID {} sem município detectado — mantida como federal/estadual: '{}'",
                        lei.getId(), lei.getTitulo());
                semDeteccao++;
            }
        }

        log.info("Auto-tagging concluído: {} atualizadas, {} sem detecção (federal/estadual)", atualizadas, semDeteccao);
        return new TaggingResult(atualizadas, semDeteccao);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPER: resolve município com detecção automática como fallback
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Resolve o município a usar ao salvar/atualizar uma lei.
     *
     * Prioridade:
     *  1. municipio passado explicitamente pelo usuário (não nulo e não vazio)
     *  2. município detectado automaticamente no titulo + conteudo da lei
     *  3. null → lei federal/estadual (aparece para todos os municípios)
     */
    private String resolverMunicipio(String municipioExplicito, String titulo, String conteudo) {
        // 1. Usuário informou explicitamente
        if (municipioExplicito != null && !municipioExplicito.isBlank()) {
            return municipioExplicito.trim();
        }
        // 2. Detecta automaticamente no texto da própria lei
        String textoLei = (titulo != null ? titulo : "") + " " + (conteudo != null ? conteudo : "");
        String detectado = municipioExtractorService.extrairMunicipio(textoLei);
        if (detectado != null) {
            log.info("Município auto-detectado na lei: '{}'", detectado);
        }
        // 3. null = federal/estadual
        return detectado;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DTO interno de resultado do auto-tagging
    // ─────────────────────────────────────────────────────────────────────────

    public record TaggingResult(int leisTaggeadas, int leisSemDeteccao) {}
}
