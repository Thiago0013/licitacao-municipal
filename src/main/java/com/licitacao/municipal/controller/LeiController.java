package com.licitacao.municipal.controller;

import com.licitacao.municipal.model.Lei;
import com.licitacao.municipal.service.LeiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/leis")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class LeiController {

    private final LeiService leiService;

    /**
     * Cadastra uma lei.
     *
     * O campo 'municipio' é OPCIONAL:
     *  - Se enviado (ex: "Icó", "Fortaleza", "Quixadá"), usa esse valor.
     *  - Se omitido, o sistema detecta o município automaticamente a partir
     *    do titulo e conteudo da lei.
     *  - Se não detectar nada → salva como lei federal/estadual (aparece para todos).
     *
     * Não precisa listar os municípios manualmente — funciona para qualquer
     * cidade do Ceará.
     */
    @PostMapping
    public ResponseEntity<Lei> salvar(
            @RequestParam String titulo,
            @RequestParam String referencia,
            @RequestParam String conteudo,
            @RequestParam(required = false) String municipio) {
        return ResponseEntity.ok(leiService.cadastrarLei(titulo, referencia, conteudo, municipio));
    }

    @GetMapping
    public ResponseEntity<List<Lei>> listarTodas() {
        return ResponseEntity.ok(leiService.listarTodas());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Lei> buscarPorId(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(leiService.buscarPorId(id));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Lei> atualizar(
            @PathVariable Long id,
            @RequestParam String titulo,
            @RequestParam String referencia,
            @RequestParam String conteudo,
            @RequestParam(required = false) String municipio) {
        try {
            return ResponseEntity.ok(leiService.atualizarLei(id, titulo, referencia, conteudo, municipio));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        try {
            leiService.deletarLei(id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Endpoint de administração: corrige automaticamente todas as leis
     * que estão sem município definido (cadastradas antes da correção do bug).
     *
     * O sistema lê o titulo + conteudo de cada lei e detecta o município
     * pela lista dos 184 municípios do Ceará — funciona para Icó, Fortaleza,
     * Quixadá, Sobral, Crato, ou qualquer outra cidade, sem precisar listar
     * manualmente.
     *
     * Retorna um relatório com quantas leis foram corrigidas e quantas
     * permaneceram sem município (= leis federais/estaduais, que é o comportamento correto).
     *
     * Use UMA VEZ após subir esta versão do sistema.
     * Chamada: POST /api/leis/auto-taggear
     */
    @PostMapping("/auto-taggear")
    public ResponseEntity<Map<String, Object>> autoTaggear() {
        LeiService.TaggingResult resultado = leiService.autoTaggearMunicipio();
        return ResponseEntity.ok(Map.of(
                "leisTaggeadasComMunicipio", resultado.leisTaggeadas(),
                "leisSemDeteccao_federal_estadual", resultado.leisSemDeteccao(),
                "mensagem", "Auto-tagging concluído. Leis sem detecção são federais/estaduais — comportamento correto."
        ));
    }
}
